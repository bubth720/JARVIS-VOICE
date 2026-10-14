#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
if [ -f /workspace/toolchains/jarvis-env.sh ]; then
  source /workspace/toolchains/jarvis-env.sh
fi
cd "$ROOT"
MODEL_WORK=${POCKET_MODEL_WORK:-/workspace/toolchains/pocket-probe-pack}
MODEL_ZIP=${POCKET_MODEL_ZIP:-/workspace/JARVIS-Pocket-Probe-fr-6l-alba.zip}
python scripts/pocket/prepare_model.py --work-dir "$MODEL_WORK" --output "$MODEL_ZIP"
POCKET_MODEL_DIR="$MODEL_WORK/fr" ./gradlew :pocketRuntime:test :pocketProbe:assembleDebug :pocketProbe:lintDebug :app:testDebugUnitTest --no-daemon --max-workers=2
python scripts/pocket/verify_apk.py pocketProbe/build/outputs/apk/debug/pocketProbe-debug.apk \
  --build-tools "$ANDROID_HOME/build-tools/35.0.0" --report pocketProbe/build/apk-verification.json
