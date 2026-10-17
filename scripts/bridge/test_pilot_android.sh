#!/usr/bin/env bash
# Fixtures ONLY. Never choose all connected devices or an owner handset.
set -euo pipefail
serial="${ANDROID_SERIAL:-}"
[[ "$serial" == emulator-* ]] || { echo 'Refusing a non-explicit emulator target' >&2; exit 2; }
adb -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r' | rg -x '1'
gradle_bin="${GRADLE_BIN:-./gradlew}"
"$gradle_bin" -PincludeVoicePilot=true :voiceBridgePilot:assembleDebug \
  :voiceBridgePilotClient:assembleAuthorizedDebug :voiceBridgePilotClient:assembleWrongPackageDebug :voiceBridgePilotClient:assembleWrongSignatureDebug \
  :voiceBridgePilotClient:assembleAuthorizedDebugAndroidTest :voiceBridgePilotClient:assembleWrongPackageDebugAndroidTest :voiceBridgePilotClient:assembleWrongSignatureDebugAndroidTest \
  --no-daemon --max-workers=2
adb -s "$serial" install -r -t voiceBridgePilot/build/outputs/apk/debug/voiceBridgePilot-debug.apk
mkdir -p voiceBridgePilotClient/build/outputs/androidTest-results
for flavor in authorized wrongPackage wrongSignature; do
  case "$flavor" in
    authorized) package=com.jarvis.core.voicepilot.fixture; skipped=0 ;;
    wrongPackage) package=com.jarvis.core.voicepilot.unknown; skipped=7 ;;
    wrongSignature) package=com.jarvis.core.voicepilot.untrusted; skipped=7 ;;
  esac
  adb -s "$serial" install -r -t "voiceBridgePilotClient/build/outputs/apk/$flavor/debug/voiceBridgePilotClient-$flavor-debug.apk"
  adb -s "$serial" install -r -t "voiceBridgePilotClient/build/outputs/apk/androidTest/$flavor/debug/voiceBridgePilotClient-$flavor-debug-androidTest.apk"
  report="voiceBridgePilotClient/build/outputs/androidTest-results/$flavor.txt"
  adb -s "$serial" shell am instrument -w -r "$package.test/androidx.test.runner.AndroidJUnitRunner" > "$report"
  cat "$report"
  python scripts/bridge/check_instrumentation.py "$report" --expected 9 --skipped "$skipped"
done
