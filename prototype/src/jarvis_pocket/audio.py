import json
import subprocess
from pathlib import Path

import numpy as np
from scipy.io import wavfile

from jarvis_pocket.storage import Store, write_json

SAMPLE_RATE = 24000
MAX_BYTES = 32 * 1024 * 1024
MAX_DURATION = 300


def run_media(args: list[str]) -> bytes:
    try:
        return subprocess.run(args, check=True, capture_output=True, timeout=60).stdout
    except FileNotFoundError as exc:
        raise ValueError("Install ffmpeg and ffprobe first") from exc
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as exc:
        # Avoid exposing local paths or verbose decoder output to HTTP clients.
        raise ValueError("Audio could not be decoded within the limits") from exc


def decode_mp3(path: Path) -> tuple[np.ndarray, dict]:
    path = path.expanduser().resolve()
    if not path.is_file() or not 0 < path.stat().st_size <= MAX_BYTES:
        raise ValueError("MP3 must be a nonempty local file, at most 32 MiB")
    info = json.loads(
        run_media(
            [
                "ffprobe",
                "-v",
                "error",
                "-protocol_whitelist",
                "file,pipe",
                "-show_streams",
                "-show_format",
                "-of",
                "json",
                str(path),
            ]
        )
    )
    if info.get("format", {}).get("format_name") != "mp3":
        raise ValueError("Expected an MP3 container")
    streams = [s for s in info.get("streams", []) if s.get("codec_type") == "audio"]
    if len(streams) != 1 or streams[0].get("codec_name") != "mp3":
        raise ValueError("Expected a real MP3 with exactly one audio stream")
    duration = float(info.get("format", {}).get("duration", 0))
    if not 3 <= duration <= MAX_DURATION:
        raise ValueError("MP3 duration must be between 3 and 300 seconds")
    pcm = run_media(
        [
            "ffmpeg",
            "-v",
            "error",
            "-nostdin",
            "-protocol_whitelist",
            "file,pipe",
            "-i",
            str(path),
            "-map",
            "0:a:0",
            "-t",
            str(MAX_DURATION + 1),
            "-ac",
            "1",
            "-ar",
            str(SAMPLE_RATE),
            "-f",
            "f32le",
            "pipe:1",
        ]
    )
    audio = np.frombuffer(pcm, dtype="<f4").copy()
    if not np.isfinite(audio).all() or not 3 <= len(audio) / SAMPLE_RATE <= MAX_DURATION:
        raise ValueError("Decoded duration or samples are invalid")
    return audio, {
        "duration_seconds": len(audio) / SAMPLE_RATE,
        "source_sample_rate": int(streams[0].get("sample_rate", 0)),
        "source_channels": streams[0].get("channels"),
        "source_bitrate": streams[0].get("bit_rate"),
        "bytes": path.stat().st_size,
    }


def quality(audio: np.ndarray) -> dict:
    if audio.size == 0 or not np.isfinite(audio).all():
        raise ValueError("Empty or invalid reference")
    rms = float(np.sqrt(np.mean(audio.astype(np.float64) ** 2)))
    frames = audio[: len(audio) // 480 * 480].reshape(-1, 480)
    active = np.sqrt(np.mean(frames.astype(np.float64) ** 2, axis=1)) > 0.01
    silence = 1 - float(active.mean()) if len(frames) else 1.0
    clipping = float(np.mean(np.abs(audio) >= 0.995))
    warnings = []
    if silence > 0.35:
        warnings.append("Many quiet frames: listen to the reference")
    if clipping > 0.002:
        warnings.append("Possible clipping")
    if rms < 10 ** (-35 / 20):
        warnings.append("Low recording level")
    return {
        "duration_seconds": len(audio) / SAMPLE_RATE,
        "rms_dbfs": 20 * float(np.log10(max(rms, 1e-12))),
        "quiet_frame_fraction": silence,
        "clipped_sample_fraction": clipping,
        "peak": float(np.max(np.abs(audio))),
        "warnings": warnings,
        "limitations": "Energy checks cannot detect speaker count, music or intelligibility. Listen before export.",
    }


def select_reference(
    audio: np.ndarray, seconds: float, start: float | None
) -> tuple[np.ndarray, float]:
    if not 3 <= seconds <= 30:
        raise ValueError("Reference duration must be 3 to 30 seconds")
    count = int(seconds * SAMPLE_RATE)
    if count > len(audio):
        raise ValueError("Recording is shorter than the requested reference")
    if start is not None:
        if not np.isfinite(start) or start < 0 or int(start * SAMPLE_RATE) + count > len(audio):
            raise ValueError("Reference interval is outside the recording")
        index = int(start * SAMPLE_RATE)
    else:
        # Scan 1-second offsets; prefer active speech at a usable level without clipping.
        candidates = list(range(0, len(audio) - count + 1, SAMPLE_RATE))
        candidates.append(len(audio) - count)

        def score(index):
            report = quality(audio[index : index + count])
            return (
                report["quiet_frame_fraction"]
                + 10 * report["clipped_sample_fraction"]
                + max(0, -35 - report["rms_dbfs"]) / 35
            )

        index = min(candidates, key=score)
    selected = audio[index : index + count]
    report = quality(selected)
    if report["rms_dbfs"] < -45 or report["quiet_frame_fraction"] > 0.8:
        raise ValueError("Reference is mostly silent; choose another interval")
    if report["clipped_sample_fraction"] > 0.02:
        raise ValueError("Reference is heavily clipped; choose another interval")
    return selected, index / SAMPLE_RATE


def prepare(store: Store, path: Path, seconds: float = 12, start: float | None = None) -> dict:
    audio, source = decode_mp3(path)
    selected, offset = select_reference(audio, seconds, start)
    identifier, folder = store.create("references")
    wavfile.write(
        folder / "reference.wav", SAMPLE_RATE, (np.clip(selected, -1, 1) * 32767).astype(np.int16)
    )
    (folder / "reference.wav").chmod(0o600)
    report = {
        "reference_id": identifier,
        "start_seconds": offset,
        "source": source,
        "reference": quality(selected),
    }
    write_json(folder / "analysis.json", report)
    return report
