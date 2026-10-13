#!/usr/bin/env bash
set -euo pipefail
cd /workspace/JARVIS-VOICE
command -v uv >/dev/null
command -v ffmpeg >/dev/null
command -v ffprobe >/dev/null
export UV_PROJECT_ENVIRONMENT=/workspace/toolchains/pocket-venv
export UV_CACHE_DIR=/workspace/toolchains/pocket-cache
export UV_SYSTEM_CERTS=true
uv sync --frozen --project prototype --group dev --python 3.12
"$UV_PROJECT_ENVIRONMENT/bin/ruff" check prototype
"$UV_PROJECT_ENVIRONMENT/bin/ruff" format --check prototype
"$UV_PROJECT_ENVIRONMENT/bin/pytest" prototype/tests -q
# Model downloads and personal voice processing are separate from installation.
# Keep them private, after checking the exact model and reference licenses.
