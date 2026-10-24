import subprocess
subprocess.run(['python','scripts/voice04/check_scope.py'],check=True)
subprocess.run(['git','diff','--exit-code','b1e9c9400072650443cc3c2ccd48bfa810d40a3e','--','pocketRuntime','identityRuntime','expressionRuntime','voiceSdk','voiceStudio','deliveries','voiceBridgePilot','voiceBridgePilotClient','voiceBridgeContract'],check=True)
print('VOICE05: original engine/JNI, archives, SDK, original Studio and frozen Binder preserved')
