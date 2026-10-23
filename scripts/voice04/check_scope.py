#!/usr/bin/env python3
"""Keep old apps, vault/archive formats, delivery signatures, models and Binder byte-identical."""
import subprocess
from pathlib import Path
BASE='4f47eb3'
protected=['app','pocketProbe','voicePrototype','voiceQuality','voiceIdentity','voiceExpression','voiceStudio','studioRuntime','identityRuntime','expressionRuntime','voiceSdk','voiceBridgeContract','voiceBridgeHarness','voiceBridgePilot','voiceBridgePilotClient','deliveries']
subprocess.run(['git','diff','--exit-code',BASE,'--',*protected],check=True)
paths=subprocess.check_output(['git','diff','--name-only',BASE,'--','pocketRuntime'],text=True).splitlines()
allowed='pocketRuntime/src/main/kotlin/com/jarvis/voice/pocket/PocketEngine.kt'
assert set(paths)<= {allowed},'Historical engine changed'
if allowed in paths:
 old=subprocess.check_output(['git','show',f'{BASE}:{allowed}'],text=True)
 expected=old.replace('    internal fun <T> withOptions','    /** Shared termination scope for the isolated VOICE-04 observer; unchanged cancellation semantics. */\n    fun <T> withOptions')
 assert Path(allowed).read_text()==expected,'Cancellation implementation changed'
print('VOICE04_SCOPE_OK: historical apps, PCM engine, models, profiles, archives, signatures and Binder unchanged; termination scope visibility only')
