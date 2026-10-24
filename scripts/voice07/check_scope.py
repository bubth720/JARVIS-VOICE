import subprocess,json,hashlib
from pathlib import Path
base='0e9e31553e15cc24b64f9edc8196344cf2b2d5f6'
protected=['app','pocketProbe','voicePrototype','voiceQuality','voiceIdentity','voiceExpression','voiceStudio','voice04Studio','pocketRuntime','identityRuntime','expressionRuntime','studioRuntime','voice04Runtime','voiceSdk','voiceBridgeContract','voiceBridgeHarness','deliveries']
subprocess.run(['git','diff','--exit-code',base,'--',*protected],check=True)
files=subprocess.check_output(['git','ls-tree','-r','--name-only',base,'--','voiceBridgePilot'],text=True).splitlines()
build='voiceBridgePilot/build.gradle.kts'
subprocess.run(['git','diff','--exit-code',base,'--',*[p for p in files if p!=build]],check=True)
old=subprocess.check_output(['git','show',base+':'+build],text=True)
assert Path(build).read_text()==old.replace('dependencies {','dependencies {\n    implementation(project(":voiceBridgeClient"))')
pin=json.loads(Path('voiceBridgePilot/contract/PIN.json').read_text())
h=hashlib.sha256()
for name,path in sorted({'CONTRACT.md':'voiceBridgePilot/contract/CONTRACT.md','IVoicePilotFixture.aidl':'voiceBridgePilot/src/main/aidl/com/jarvis/voice/bridgepilot/ipc/IVoicePilotFixture.aidl'}.items()):
 data=Path(path).read_bytes();h.update(name.encode()+b'\0'+data+b'\0')
assert h.hexdigest()==pin['sha256']

# Only fixture-test lifecycle isolation and an observable consent wait may change.
fixture='voiceBridgePilotClient/src/androidTest/java/com/jarvis/core/voicepilot/fixture/BinderFixtureTests.kt'
client_files=subprocess.check_output(['git','ls-tree','-r','--name-only',base,'--','voiceBridgePilotClient'],text=True).splitlines()
subprocess.run(['git','diff','--exit-code',base,'--',*[p for p in client_files if p!=fixture]],check=True)
expected=subprocess.check_output(['git','show',base+':'+fixture],text=True)
expected=expected.replace('        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard")','        // Isolate lifecycle callbacks and pending onUnbind from the preceding fixture test.\n        device.executeShellCommand("am force-stop ${component.packageName}")\n        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard")').replace('        allow.click()','        allow.click()\n        assertTrue("Visible consent must be armed before any authorized IPC",\n            device.wait(Until.hasObject(By.pkg(component.packageName).textStartsWith("Fixture autorisée temporairement")),5000))')
assert Path(fixture).read_text()==expected
print(json.dumps({'base':base,'critical_files_unchanged':True,'frozen_v1_sha256':h.hexdigest(),'native_generation':'DISABLED','core_connection_activated':False,'signatures_permissions_jni_changed':False},indent=2))
