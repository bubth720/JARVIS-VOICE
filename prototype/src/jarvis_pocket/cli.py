import argparse
import json
import os
import secrets
import time
from pathlib import Path

from jarvis_pocket.audio import prepare
from jarvis_pocket.storage import Store, write_json

DEFAULT_TEXT = "Bonjour, je suis JARVIS. Cette voix est générée en français sur le processeur de cet ordinateur."


def parser():
    root = argparse.ArgumentParser(description="Private French Pocket TTS CPU prototype")
    root.add_argument(
        "--data-dir",
        type=Path,
        default=Path(os.environ.get("JARVIS_VOICE_DATA_DIR", "~/.local/share/jarvis-voice")),
    )
    root.add_argument("--threads", type=int, default=2)
    commands = root.add_subparsers(dest="command", required=True)
    prep = commands.add_parser("prepare")
    prep.add_argument("input", type=Path)
    prep.add_argument("--seconds", type=float, default=12)
    prep.add_argument("--start", type=float)
    export = commands.add_parser("export")
    export.add_argument("reference_id")
    export.add_argument("--name", default="Ma voix")
    export.add_argument("--authorized", action="store_true", required=True)
    export.add_argument("--reviewed", action="store_true", required=True)
    for command in ["generate", "benchmark"]:
        sub = commands.add_parser(command)
        voices = sub.add_mutually_exclusive_group(required=True)
        voices.add_argument("--profile")
        voices.add_argument("--voice", choices=["estelle"])
        sub.add_argument("--text", default=DEFAULT_TEXT)
        sub.add_argument("--output", type=Path, required=True)
        sub.add_argument("--seed", type=int, default=42)
        if command == "benchmark":
            sub.add_argument("--repeats", type=int, default=3)
    preset = commands.add_parser("preset")
    preset.add_argument("--voice", choices=["estelle"], default="estelle")
    commands.add_parser("token")
    serve = commands.add_parser("serve")
    serve.add_argument("--port", type=int, default=8765)
    return root


def main():
    options = parser().parse_args()
    try:
        store = Store(options.data_dir)
        # Cache models privately and disable optional telemetry/Xet transfer service.
        os.environ.setdefault("HF_HOME", str(store.root / "hf-cache"))
        os.environ.setdefault("HF_HUB_DISABLE_TELEMETRY", "1")
        os.environ.setdefault("HF_HUB_DISABLE_XET", "1")
        if options.command == "prepare":
            result = prepare(store, options.input, options.seconds, options.start)
        elif options.command == "token":
            path = store.root / "server-token"
            if path.exists():
                raise ValueError(
                    "Token already exists; preserve it or explicitly remove it to rotate"
                )
            path.write_text(secrets.token_hex(32), encoding="ascii")
            path.chmod(0o600)
            result = {
                "token_file": str(path),
                "note": "Keep this file private; token value is not printed",
            }
        else:
            from jarvis_pocket.engine import Engine

            # Validate local inputs before downloading any model.
            if options.command in {"benchmark", "generate"}:
                if not options.text.strip() or len(options.text.strip()) > 500:
                    raise ValueError("Text must contain 1 to 500 characters")
                store.output(options.output)
                if options.command == "benchmark" and not 1 <= options.repeats <= 10:
                    raise ValueError("Benchmark repeats must be 1 to 10")
            if options.command == "serve":
                token = (store.root / "server-token").read_text(encoding="ascii").strip()
                if len(token) < 32:
                    raise ValueError("Invalid private token file")
                if not 1024 <= options.port <= 65535:
                    raise ValueError("Port must be 1024 to 65535")
            engine = Engine(options.threads)
            if options.command == "export":
                result = engine.export(
                    store, options.reference_id, options.name, options.authorized, options.reviewed
                )
            elif options.command == "preset":
                result = engine.export_predefined(store, options.voice)
            elif options.command == "serve":
                import uvicorn
                from jarvis_pocket.server import create_app

                uvicorn.run(
                    create_app(store, engine, token),
                    host="127.0.0.1",
                    port=options.port,
                    workers=1,
                    access_log=False,
                )
                return
            else:
                state_started = time.perf_counter()
                state = (
                    engine.state(store, options.profile)
                    if options.profile
                    else engine.predefined_state(options.voice)
                )
                repeats = options.repeats if options.command == "benchmark" else 1
                state_seconds = time.perf_counter() - state_started
                measurements = []
                for index in range(repeats):
                    audio, metrics = engine.synthesize(state, options.text, options.seed + index)
                    measurements.append(metrics)
                    output = store.output(
                        options.output
                        if index == 0
                        else options.output.with_name(f"{options.output.stem}-{index + 1}.wav")
                    )
                    output.write_bytes(audio)
                    output.chmod(0o600)
                result = {
                    "load": engine.load_metrics,
                    "state_load_seconds": state_seconds,
                    "runs": measurements,
                    "note": "First run includes warm-up. CPU seconds include all process threads. RTF < 1 means faster than real-time.",
                }
                write_json(store.output(options.output.with_suffix(".metrics.json")), result)
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except (ValueError, FileNotFoundError, RuntimeError, OSError) as exc:
        # CLI diagnostics stay local; HTTP clients receive sanitized errors.
        raise SystemExit(f"Prototype failed: {exc}") from exc


if __name__ == "__main__":
    main()
