#!/usr/bin/env bash
# One shell: android-emulator-runner executes separate script lines in separate shells.
set -euo pipefail
./gradlew :voiceExpression:assembleDebug -PprototypeTestAbi=x86_64 --no-daemon --max-workers=2
adb install -r voiceExpression/build/outputs/apk/debug/voiceExpression-debug.apk
adb shell run-as com.jarvis.voice.voiceexpression005i mkdir -p files
adb shell run-as com.jarvis.voice.voiceexpression005i touch files/005j-synthetic-isolation-sentinel
studio_test_status=0
./gradlew :voiceStudio:connectedDebugAndroidTest -PprototypeTestAbi=x86_64 --no-daemon --max-workers=2 || studio_test_status=$?
adb install -r voiceStudio/build/outputs/apk/debug/voiceStudio-debug.apk
adb shell pm path com.jarvis.voice.voiceexpression005i
adb shell pm path com.jarvis.voice.voicestudio005j
adb shell run-as com.jarvis.voice.voiceexpression005i test -f files/005j-synthetic-isolation-sentinel
mkdir -p voiceStudio/build/visual-proof
adb pull /sdcard/Download/005j-empty-light.png voiceStudio/build/visual-proof/
adb pull /sdcard/Download/005j-empty-dark.png voiceStudio/build/visual-proof/
test "$studio_test_status" -eq 0
