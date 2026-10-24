#!/usr/bin/env python3
"""VOICE-04-only audit, copied from the unchanged historical verifier; all security checks preserved."""

import argparse
import hashlib
import json
import re
import subprocess
import tempfile
import zipfile
from pathlib import Path


def run(command):
    return subprocess.run(
        [str(x) for x in command], check=True, text=True, capture_output=True
    ).stdout


def main():
    p = argparse.ArgumentParser()
    p.add_argument("apk", type=Path)
    p.add_argument("--build-tools", type=Path, required=True)
    p.add_argument("--report", type=Path, required=True)
    p.add_argument("--application-id", choices=["com.jarvis.voice.studio.voice04"], default="com.jarvis.voice.studio.voice04")
    args = p.parse_args()
    signer = run([args.build_tools / "apksigner", "verify", "--print-certs", args.apk])
    run([args.build_tools / "zipalign", "-c", "-P", "16", "4", args.apk])
    manifest = run([args.build_tools / "aapt", "dump", "badging", args.apk])
    assert f"name='{args.application_id}'" in manifest
    assert "sdkVersion:'26'" in manifest and "targetSdkVersion:'35'" in manifest
    assert "native-code: 'arm64-v8a'" in manifest
    for permission in [
        "INTERNET",
        "RECORD_AUDIO",
        "READ_EXTERNAL_STORAGE",
        "MANAGE_EXTERNAL_STORAGE",
    ]:
        assert f"android.permission.{permission}" not in manifest
    libraries = {}
    expected_native = {
        "libonnxruntime.so": "12c870ee77349d0e80e0d85eb293849ebe8f56717a81eec62b53ecb1446e7de8",
        "libonnxruntime4j_jni.so": "8cd202ef5ad8fb13754abc8767b892a6ae8ded41ee0a30bd1917007720aa56bb",
    }
    with zipfile.ZipFile(args.apk) as apk, tempfile.TemporaryDirectory() as directory:
        assert not any(n.lower().endswith((".onnx", ".safetensors", ".wav", ".mp3", ".bin")) for n in apk.namelist()), "APK must not contain weights, profiles or recordings"
        lock_path = (
            Path(__file__).resolve().parents[2]
            / "pocketRuntime/src/main/resources/experimental-fp32-lock.json"
        )
        assert apk.read("experimental-fp32-lock.json") == lock_path.read_bytes(), (
            "APK has a stale/missing model lock"
        )
        names = [
            n for n in apk.namelist() if n.startswith("lib/") and n.endswith(".so")
        ]
        assert set(names) == {
            "lib/arm64-v8a/libonnxruntime.so",
            "lib/arm64-v8a/libonnxruntime4j_jni.so",
        }
        for name in names:
            f = Path(directory) / Path(name).name
            f.write_bytes(apk.read(name))
            assert hashlib.sha256(f.read_bytes()).hexdigest() == expected_native[f.name]
            header = run(["readelf", "-h", f])
            assert "AArch64" in header
            loads = [
                line.split()
                for line in run(["readelf", "-lW", f]).splitlines()
                if line.strip().startswith("LOAD ")
            ]
            assert loads
            for load in loads:
                assert int(load[-1], 16) >= 16384
                assert (int(load[1], 16) - int(load[2], 16)) % 16384 == 0
            assert apk.getinfo(name).compress_type == zipfile.ZIP_STORED
            libraries[Path(name).name] = {
                "sha256": hashlib.sha256(f.read_bytes()).hexdigest(),
                "loadAlignment": "16384",
                "zipCompression": "stored",
            }
    certificate = re.search(r"certificate SHA-256 digest: ([0-9a-f]+)", signer).group(1)
    report = {
        "apkSha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
        "apkBytes": args.apk.stat().st_size,
        "applicationId": args.application_id,
        "minSdk": 26,
        "targetSdk": 35,
        "abi": ["arm64-v8a"],
        "signatureVerified": True,
        "certificateSha256": certificate,
        "zipalign16K": True,
        "libraries": libraries,
        "internetPermission": False,
        "physicalAndroidDeviceTest": False,
        "experimental": True,
        "modelWeightsIncluded": False,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
