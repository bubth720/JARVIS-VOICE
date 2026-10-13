import os
import uuid

import pytest

from jarvis_pocket.storage import Store, validate_id


def test_git_directory_is_never_a_data_root(tmp_path):
    repo = tmp_path / "repo"
    (repo / ".git").mkdir(parents=True)
    (repo / ".git" / "HEAD").write_text("ref: refs/heads/main")
    with pytest.raises(ValueError, match="outside"):
        Store(repo / "data")


def test_linked_checkout_is_rejected(tmp_path):
    repo = tmp_path / "repo"
    repo.mkdir()
    (repo / ".git").write_text("gitdir: elsewhere")
    with pytest.raises(ValueError):
        Store(repo)


def test_output_cannot_escape_private_storage(tmp_path):
    store = Store(tmp_path / "private")
    with pytest.raises(ValueError):
        store.output(tmp_path / "published.wav")


def test_symlink_cannot_escape_private_storage(tmp_path):
    store = Store(tmp_path / "private")
    outside = tmp_path / "outside"
    outside.mkdir()
    identifier = str(uuid.uuid4())
    (store.root / "profiles").mkdir()
    (store.root / "profiles" / identifier).symlink_to(outside, target_is_directory=True)
    with pytest.raises(ValueError):
        store.directory("profiles", identifier)


@pytest.mark.parametrize("value", ["../outside", "https://example.com", "", "abc"])
def test_resource_ids_are_not_paths(value):
    with pytest.raises(ValueError):
        validate_id(value)


def test_private_root_permissions(tmp_path):
    store = Store(tmp_path / "private")
    if os.name != "nt":
        assert store.root.stat().st_mode & 0o777 == 0o700


def test_creation_cannot_follow_an_external_symlink(tmp_path):
    store = Store(tmp_path / "private")
    outside = tmp_path / "outside"
    outside.mkdir()
    (store.root / "profiles").symlink_to(outside, target_is_directory=True)
    with pytest.raises(ValueError):
        store.create("profiles")
    assert list(outside.iterdir()) == []
