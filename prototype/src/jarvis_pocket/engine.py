import hashlib
import importlib.metadata
import io
import json
import threading
import time
from pathlib import Path

import numpy as np
import psutil
from scipy.io import wavfile

from jarvis_pocket.storage import Store, write_json

MODEL_LANGUAGE = "french"
MODEL_REVISION = "d0ebc5deb50bc6b225f686782f124dd37e2dca0a"
PACKAGE_VERSION = "3.3.0"
PUBLIC_MODEL_REVISION = "8843db76457a91db32077edf8dfcd1c0e3e755fd"


def digest(path: Path) -> str:
    result = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


class PeakMemory:
    def __enter__(self):
        self.process = psutil.Process()
        self.peak = self.process.memory_info().rss
        self.stop = threading.Event()

        def sample():
            while not self.stop.wait(0.01):
                self.peak = max(self.peak, self.process.memory_info().rss)

        self.thread = threading.Thread(target=sample, daemon=True)
        self.thread.start()
        return self

    def __exit__(self, *args):
        self.peak = max(self.peak, self.process.memory_info().rss)
        self.stop.set()
        self.thread.join()


class Engine:
    def __init__(self, threads: int = 2):
        if not 1 <= threads <= 4:
            raise ValueError("Use 1 to 4 CPU threads")
        if importlib.metadata.version("pocket-tts") != PACKAGE_VERSION:
            raise ValueError("Install the pinned Pocket TTS version")
        import torch
        from pocket_tts import TTSModel

        started = time.perf_counter()
        with PeakMemory() as memory:
            try:
                self.model = TTSModel.load_model(language=MODEL_LANGUAGE)
            except Exception as exc:
                raise RuntimeError(
                    f"French model loading failed ({type(exc).__name__}). "
                    "Check Hugging Face access, the model license and the private cache."
                ) from exc
        # Pocket TTS sets its own default; override only after loading.
        torch.set_num_threads(threads)
        if self.model.device.type != "cpu":
            raise RuntimeError("This prototype must run on CPU")
        self.threads = threads
        self.profile_model_revision = (
            MODEL_REVISION if self.model.has_voice_cloning else PUBLIC_MODEL_REVISION
        )
        self.load_metrics = {
            "model_load_seconds": time.perf_counter() - started,
            "load_peak_rss_mib": memory.peak / 1024**2,
            "voice_cloning_available": self.model.has_voice_cloning,
            "device": str(self.model.device),
            "threads": threads,
            "torch_version": torch.__version__,
            "pocket_tts_version": PACKAGE_VERSION,
            "model_language": MODEL_LANGUAGE,
            "model_revision": MODEL_REVISION
            if self.model.has_voice_cloning
            else PUBLIC_MODEL_REVISION,
            "parameter_count": sum(p.numel() for p in self.model.parameters()),
        }

    def export(
        self, store: Store, reference_id: str, name: str, authorized: bool, reviewed: bool
    ) -> dict:
        if not authorized or not reviewed:
            raise ValueError("Confirm authorization and listen to the extracted reference first")
        if not self.model.has_voice_cloning:
            raise ValueError(
                "Full voice-cloning weights are unavailable; predefined voices are not a personal profile"
            )
        from pocket_tts import export_model_state

        reference = store.directory("references", reference_id) / "reference.wav"
        started = time.perf_counter()
        with PeakMemory() as memory:
            state = self.model.get_state_for_audio_prompt(reference)
            identifier, folder = store.create("profiles")
            export_model_state(state, folder / "voice.safetensors")
            (folder / "voice.safetensors").chmod(0o600)
        metadata = {
            "profile_id": identifier,
            "name": name,
            "language": MODEL_LANGUAGE,
            "package_version": PACKAGE_VERSION,
            "model_revision": MODEL_REVISION,
            "voice_sha256": digest(folder / "voice.safetensors"),
            "reference_sha256": digest(reference),
            "kind": "personal",
            "authorized": True,
            "reviewed": True,
            "export_seconds": time.perf_counter() - started,
            "export_peak_rss_mib": memory.peak / 1024**2,
            "profile_bytes": (folder / "voice.safetensors").stat().st_size,
        }
        write_json(folder / "profile.json", metadata)
        return metadata

    def export_predefined(self, store: Store, voice: str) -> dict:
        """Export the public French preset for a reproducible server smoke test.

        This does not encode an MP3 or create a personal cloned voice.
        """
        from pocket_tts import export_model_state

        started = time.perf_counter()
        state = self.predefined_state(voice)
        identifier, folder = store.create("profiles")
        export_model_state(state, folder / "voice.safetensors")
        (folder / "voice.safetensors").chmod(0o600)
        metadata = {
            "profile_id": identifier,
            "name": "Essai français Estelle",
            "language": MODEL_LANGUAGE,
            "package_version": PACKAGE_VERSION,
            "model_revision": self.profile_model_revision,
            "voice_sha256": digest(folder / "voice.safetensors"),
            "kind": "predefined",
            "reference_license": "CC0",
            "reference_source": "kyutai/tts-voices/unmute-prod-website/developpeuse-3.wav",
            "export_seconds": time.perf_counter() - started,
            "profile_bytes": (folder / "voice.safetensors").stat().st_size,
        }
        write_json(folder / "profile.json", metadata)
        return metadata

    def state(self, store: Store, profile_id: str):
        folder = store.directory("profiles", profile_id)
        metadata = json.loads((folder / "profile.json").read_text(encoding="utf-8"))
        if (
            metadata.get("model_revision"),
            metadata.get("package_version"),
            metadata.get("language"),
        ) != (self.profile_model_revision, PACKAGE_VERSION, MODEL_LANGUAGE):
            raise ValueError("Profile is incompatible with this pinned French model")
        path = folder / "voice.safetensors"
        if path.resolve().parent != folder or digest(path) != metadata.get("voice_sha256"):
            raise ValueError("Profile integrity check failed")
        return self.model.get_state_for_audio_prompt(path)

    def predefined_state(self, voice: str):
        if voice != "estelle":
            raise ValueError("Only the documented French test voice estelle is supported")
        return self.model.get_state_for_audio_prompt(voice)

    def synthesize(self, state, text: str, seed: int = 42) -> tuple[bytes, dict]:
        text = text.strip()
        if not 1 <= len(text) <= 500:
            raise ValueError("Text must contain 1 to 500 characters")
        import torch

        torch.manual_seed(seed)
        process = psutil.Process()
        before = process.cpu_times()
        started = time.perf_counter()
        first = None
        chunks = []
        with PeakMemory() as memory:
            # copy_state preserves the reusable profile across requests.
            stream = self.model.generate_audio_stream(state, text, copy_state=True)
            try:
                for chunk in stream:
                    if first is None and chunk.numel():
                        first = time.perf_counter() - started
                    chunks.append(chunk.detach().cpu().numpy())
            finally:
                stream.close()
        elapsed = time.perf_counter() - started
        after = process.cpu_times()
        if not chunks:
            raise ValueError("The model produced no audio")
        audio = np.concatenate(chunks)
        if not audio.size or not np.isfinite(audio).all():
            raise ValueError("The model produced invalid audio")
        duration = audio.size / self.model.sample_rate
        metrics = {
            "generation_seconds": elapsed,
            "audio_seconds": duration,
            "first_chunk_seconds": first,
            "real_time_factor": elapsed / duration,
            "times_real_time": duration / elapsed,
            "cpu_seconds": after.user + after.system - before.user - before.system,
            "peak_rss_mib": memory.peak / 1024**2,
            "sample_rate": self.model.sample_rate,
            "seed": seed,
        }
        output = io.BytesIO()
        wavfile.write(
            output, self.model.sample_rate, (np.clip(audio, -1, 1) * 32767).astype(np.int16)
        )
        return output.getvalue(), metrics
