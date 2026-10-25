#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_SERIAL:?Select an emulator explicitly}"
[[ "$ANDROID_SERIAL" == emulator-* ]]
[[ "$(adb -s "$ANDROID_SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
./gradlew :voiceFinalStudio:assembleDebug :voiceFinalStudio:assembleDebugAndroidTest -PprototypeTestAbi=x86_64 --no-daemon --max-workers=2
adb -s "$ANDROID_SERIAL" install -r voiceFinalStudio/build/outputs/apk/debug/voiceFinalStudio-debug.apk
adb -s "$ANDROID_SERIAL" install -r voiceFinalStudio/build/outputs/apk/androidTest/debug/voiceFinalStudio-debug-androidTest.apk
adb -s "$ANDROID_SERIAL" shell input keyevent KEYCODE_WAKEUP
adb -s "$ANDROID_SERIAL" shell wm dismiss-keyguard
mkdir -p voiceFinalStudio/build/device-proof
adb -s "$ANDROID_SERIAL" shell am instrument -w -r -e class com.jarvis.voice.voicestudio.FinalAndroidTests com.jarvis.voice.studio.final01.test/androidx.test.runner.AndroidJUnitRunner > voiceFinalStudio/build/device-proof/audio.txt
python - <<'PY'
from pathlib import Path
s=Path('voiceFinalStudio/build/device-proof/audio.txt').read_text()
assert 'OK (8 tests)' in s and 'FAILURES!!!' not in s and 'shortMsg=' not in s,s
print('FINAL01_ANDROID_8_TESTS_PASSED')
PY
