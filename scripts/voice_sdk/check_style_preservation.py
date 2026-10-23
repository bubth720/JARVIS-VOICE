#!/usr/bin/env python3
"""Read-only VOICE-02 scope guard; original pilot guards remain unchanged."""
import json
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[2]
base = "99d4073426896c992141bc010a69bb2690e03e71"
protected = ["app", "pocketRuntime", "identityRuntime", "studioRuntime", "voiceBridgeContract",
             "voiceBridgeHarness", "voiceBridgePilot", "voiceBridgePilotClient", "deliveries",
             "voiceSdk/src/main/kotlin/com/jarvis/voice/sdk/SpeechSession.kt",
             "voiceSdk/src/main/kotlin/com/jarvis/voice/sdk/VaultQualitySpeechEngine.kt",
             "voiceSdk/src/main/kotlin/com/jarvis/voice/sdk/PcmFrameQueue.kt",
             "voiceStudio/src/main/AndroidManifest.xml", "voiceStudio/build.gradle.kts",
             "voiceStudio/src/main/java/com/jarvis/voice/voicestudio/PcmPlayer.kt",
             "voiceStudio/src/main/java/com/jarvis/voice/voicestudio/DeviceMetrics.kt",
             "voiceStudio/src/main/java/com/jarvis/voice/voicestudio/OperationMetrics.kt",
             "build.gradle.kts", "settings.gradle.kts", "gradle.properties"]
subprocess.check_call(["git", "diff", "--exit-code", base, "--", *protected], cwd=root)
old_expression = subprocess.check_output(["git", "ls-tree", "-r", "--name-only", base, "--", "expressionRuntime"], cwd=root, text=True).splitlines()
subprocess.check_call(["git", "diff", "--exit-code", base, "--", *old_expression], cwd=root)
print(json.dumps({"base": base, "original_expression_files_preserved": len(old_expression),
                  "native_identity_admission_and_fixture_sources_preserved": True}))
