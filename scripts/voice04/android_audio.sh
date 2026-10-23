#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_SERIAL:?Select an emulator explicitly}"
[[ "$ANDROID_SERIAL" == emulator-* ]]
[[ "$(adb -s "$ANDROID_SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
./gradlew :voice04Studio:assembleDebug :voice04Studio:assembleDebugAndroidTest -PprototypeTestAbi=x86_64 --no-daemon --max-workers=2
adb -s "$ANDROID_SERIAL" install -r voice04Studio/build/outputs/apk/debug/voice04Studio-debug.apk
adb -s "$ANDROID_SERIAL" install -r voice04Studio/build/outputs/apk/androidTest/debug/voice04Studio-debug-androidTest.apk
mkdir -p voice04Studio/build/device-proof
adb -s "$ANDROID_SERIAL" shell am instrument -w -r -e class com.jarvis.voice.voicestudio.Voice04AudioTests com.jarvis.voice.studio.voice04.test/androidx.test.runner.AndroidJUnitRunner > voice04Studio/build/device-proof/audio.txt
python - <<'PY'
from pathlib import Path
s=Path('voice04Studio/build/device-proof/audio.txt').read_text()
assert 'OK (6 tests)' in s and 'FAILURES!!!' not in s and 'shortMsg=' not in s,s
print('VOICE04_REAL_AUDIOTRACK_6_TESTS_PASSED')
PY
