import pytest
import torch
from pocket_tts import export_model_state
from pocket_tts.models.model_state import _import_model_state

from jarvis_pocket.engine import Engine, MODEL_LANGUAGE, MODEL_REVISION, PACKAGE_VERSION, digest
from jarvis_pocket.storage import Store, write_json


def test_safetensors_state_roundtrip(tmp_path):
    # Format contract only; these tensors do not represent a cloned voice.
    state = {"flow.layer": {"offset": torch.tensor([3]), "cache": torch.zeros(1, 2, 3)}}
    path = tmp_path / "synthetic-state.safetensors"
    export_model_state(state, path)
    loaded = _import_model_state(path, torch.device("cpu"))
    assert torch.equal(loaded["flow.layer"]["offset"], state["flow.layer"]["offset"])
    assert torch.equal(loaded["flow.layer"]["cache"], state["flow.layer"]["cache"])


@pytest.mark.parametrize(
    "field,value",
    [("language", "english"), ("model_revision", "other"), ("package_version", "1.0.0")],
)
def test_incompatible_profile_rejected_before_loading(tmp_path, field, value):
    store = Store(tmp_path / "private")
    identifier, folder = store.create("profiles")
    metadata = {
        "language": MODEL_LANGUAGE,
        "model_revision": MODEL_REVISION,
        "package_version": PACKAGE_VERSION,
    }
    metadata[field] = value
    write_json(folder / "profile.json", metadata)
    engine = Engine.__new__(Engine)
    engine.profile_model_revision = MODEL_REVISION
    with pytest.raises(ValueError, match="incompatible"):
        engine.state(store, identifier)


def test_changed_profile_rejected(tmp_path):
    store = Store(tmp_path / "private")
    identifier, folder = store.create("profiles")
    voice = folder / "voice.safetensors"
    voice.write_bytes(b"old")
    metadata = {
        "language": MODEL_LANGUAGE,
        "model_revision": MODEL_REVISION,
        "package_version": PACKAGE_VERSION,
        "voice_sha256": digest(voice),
    }
    write_json(folder / "profile.json", metadata)
    voice.write_bytes(b"tampered")
    engine = Engine.__new__(Engine)
    engine.profile_model_revision = MODEL_REVISION
    with pytest.raises(ValueError, match="integrity"):
        engine.state(store, identifier)


def test_export_requires_full_model_and_consent(tmp_path):
    store = Store(tmp_path / "private")
    engine = Engine.__new__(Engine)
    engine.profile_model_revision = MODEL_REVISION
    with pytest.raises(ValueError, match="Confirm"):
        engine.export(store, "unused", "Test", False, False)

    class NoCloning:
        has_voice_cloning = False

    engine.model = NoCloning()
    with pytest.raises(ValueError, match="unavailable"):
        engine.export(store, "unused", "Test", True, True)
