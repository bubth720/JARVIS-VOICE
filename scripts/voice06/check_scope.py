import subprocess
base='1a9f713dff28f6e4fb145728815e9ed118f5a929'
protected=['pocketRuntime','identityRuntime','voiceSdk','voiceStudio','deliveries','voiceBridgePilot','voiceBridgePilotClient','voiceBridgeContract']
subprocess.run(['git','diff','--exit-code',base,'--',*protected],check=True)
files=subprocess.check_output(['git','ls-tree','-r','--name-only',base,'--','expressionRuntime'],text=True).splitlines()
subprocess.run(['git','diff','--exit-code',base,'--',*files],check=True)
print('VOICE06: original engines/JNI, all previous expression files, vault/archives, permissions/signatures/Binder untouched')
