import json
import os
import uuid
from pathlib import Path


def is_checkout(path: Path) -> bool:
    for parent in (path, *path.parents):
        marker = parent / ".git"
        if marker.is_file() or (marker / "HEAD").is_file():
            return True
    return False


def validate_id(value: str) -> str:
    try:
        parsed = uuid.UUID(value)
    except ValueError as exc:
        raise ValueError("Invalid private resource ID") from exc
    if str(parsed) != value:
        raise ValueError("Invalid private resource ID")
    return value


class Store:
    def __init__(self, root: Path):
        self.root = root.expanduser().resolve()
        if is_checkout(self.root):
            raise ValueError("Voice data must be stored outside every Git checkout")
        self.root.mkdir(parents=True, exist_ok=True, mode=0o700)
        if os.name != "nt":
            self.root.chmod(0o700)

    def create(self, kind: str) -> tuple[str, Path]:
        if kind not in {"references", "profiles"}:
            raise ValueError("Unknown private resource type")
        base = (self.root / kind).resolve()
        if not base.is_relative_to(self.root) or is_checkout(base):
            raise ValueError("Resource directory escapes private storage")
        identifier = str(uuid.uuid4())
        path = base / identifier
        path.mkdir(parents=True, mode=0o700)
        return identifier, path

    def directory(self, kind: str, identifier: str) -> Path:
        path = (self.root / kind / validate_id(identifier)).resolve()
        if not path.is_relative_to(self.root) or is_checkout(path):
            raise ValueError("Resource escapes private data directory")
        if not path.is_dir():
            raise FileNotFoundError("Private resource not found")
        return path

    def output(self, path: Path) -> Path:
        path = path.expanduser().resolve()
        if not path.is_relative_to(self.root) or is_checkout(path):
            raise ValueError("Audio and measurements must remain in the private data directory")
        path.parent.mkdir(parents=True, exist_ok=True)
        return path


def write_json(path: Path, value: dict):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if os.name != "nt":
        path.chmod(0o600)
