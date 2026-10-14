#!/usr/bin/env python3
"""Download checksum-pinned public graphs; retain only the licensed Alba preset. No private audio."""

import argparse
import hashlib
import json
import shutil
import struct
import tempfile
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def verify(path, spec):
    return (
        path.is_file()
        and path.stat().st_size == spec["sizeBytes"]
        and hashlib.sha256(path.read_bytes()).hexdigest() == spec["sha256"]
    )


def alba_only(source, target):
    with source.open("rb") as f:
        assert f.read(5) == b"PTVB1"
        assert struct.unpack("<I", f.read(4))[0] == 8
        start = f.tell()
        n = struct.unpack("<H", f.read(2))[0]
        assert f.read(n) == b"alba"
        count = struct.unpack("<H", f.read(2))[0]
        assert count == 18
        for _ in range(count):
            n = struct.unpack("<H", f.read(2))[0]
            f.read(n)
            dtype, rank = struct.unpack("<BB", f.read(2))
            assert dtype in (0, 1) and 1 <= rank <= 5
            f.read(rank * 4)
            size = struct.unpack("<I", f.read(4))[0]
            f.seek(size, 1)
        end = f.tell()
        f.seek(start)
        payload = f.read(end - start)
    target.write_bytes(b"PTVB1" + struct.pack("<I", 1) + payload)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--work-dir", type=Path, required=True, help="Model cache outside Git"
    )
    parser.add_argument(
        "--output",
        type=Path,
        required=True,
        help="Installable ZIP outside Git or under build/",
    )
    args = parser.parse_args()
    source_lock = json.loads((ROOT / "scripts/pocket/source-lock.json").read_text())
    lock = json.loads(
        (ROOT / "pocketRuntime/src/main/resources/model-lock.json").read_text()
    )
    source = args.work_dir / "source"
    source.mkdir(parents=True, exist_ok=True)
    base = f"https://huggingface.co/{source_lock['repository']}/resolve/{source_lock['revision']}/fr/"
    for name, spec in source_lock["files"].items():
        path = source / name
        if not verify(path, spec):
            with (
                urllib.request.urlopen(base + name, timeout=120) as response,
                path.open("wb") as out,
            ):
                shutil.copyfileobj(response, out, 1 << 20)
        if not verify(path, spec):
            raise SystemExit(f"Checksum/size mismatch: {name}")
        print(f"Verified public model file: {name}", flush=True)
    target = args.work_dir / "fr"
    target.mkdir(exist_ok=True)
    for name in source_lock["files"]:
        shutil.copyfile(source / name, target / name)
    shutil.copyfile(
        ROOT / "scripts/pocket/MODEL_LICENSE.txt", target / "MODEL_LICENSE.txt"
    )
    alba_only(source / "voices.bin", target / "voices.bin")
    bundle = json.loads((source / "bundle.json").read_text())
    bundle["predefined_voices"] = ["alba"]
    (target / "bundle.json").write_text(
        json.dumps(bundle, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    for name, spec in lock["files"].items():
        if not verify(target / name, spec):
            raise SystemExit(f"Derived Alba pack mismatch: {name}")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        dir=args.output.parent, delete=False, suffix=".zip"
    ) as tmp:
        temporary = Path(tmp.name)
    try:
        with zipfile.ZipFile(
            temporary, "w", zipfile.ZIP_DEFLATED, compresslevel=6
        ) as z:
            for name in sorted(lock["files"]):
                entry = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                z.writestr(entry, (target / name).read_bytes())
        temporary.replace(args.output)
    finally:
        temporary.unlink(missing_ok=True)
    print(f"Model directory: {target}")
    print(f"Installable pack: {args.output}")
    print(f"ZIP SHA-256: {hashlib.sha256(args.output.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
