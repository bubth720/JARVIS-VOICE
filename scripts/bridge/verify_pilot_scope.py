#!/usr/bin/env python3
"""Offline pilot/provenance preservation checks; does not touch devices or private data."""
import argparse,hashlib,json,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser();parser.add_argument('--core-root',type=Path);args=parser.parse_args()
base='4479afb0c5604bb82ae8d84dd0267c4d6e7cb953'
protected=['app','pocketProbe','voicePrototype','voiceQuality','voiceIdentity','voiceExpression','voiceStudio','pocketRuntime','identityRuntime','expressionRuntime','studioRuntime','deliveries','voiceBridgeContract','voiceBridgeHarness','build.gradle.kts','gradle.properties','.github','docs/mission005k']
assert not subprocess.check_output(['git','diff',base,'--',*protected],cwd=root),'historical apps/contracts modified'
legacy=json.loads((root/'docs/mission005k/CONTRACT-PIN.json').read_text())
for name,sha in legacy['files'].items(): assert hashlib.sha256((root/name).read_bytes()).hexdigest()==sha,name
assert hashlib.sha256(''.join(k+'\0'+v+'\n' for k,v in sorted(legacy['files'].items())).encode()).hexdigest()==legacy['bundleSha256']
archive=root/'deliveries/mission005j/005j-experimental-apk.zip'
assert hashlib.sha256(archive.read_bytes()).hexdigest()=='82db41ca499077fc062d6300c6ee48f816cca728d346588de5ee2cc3a04260b0'
files={'CONTRACT.md':root/'voiceBridgePilot/contract/CONTRACT.md','IVoicePilotFixture.aidl':root/'voiceBridgePilot/src/main/aidl/com/jarvis/voice/bridgepilot/ipc/IVoicePilotFixture.aidl'}
pin=json.loads((root/'voiceBridgePilot/contract/PIN.json').read_text());h=hashlib.sha256()
for name,p in sorted(files.items()):
 data=p.read_bytes();assert hashlib.sha256(data).hexdigest()==pin['files'][name];h.update(name.encode()+b'\0'+data+b'\0')
assert h.hexdigest()==pin['sha256']
if args.core_root:
 dest=args.core_root.resolve()/'docs/mission-core-voice-pilot/fixture-contract'
 for name,p in files.items():assert p.read_bytes()==(dest/name).read_bytes()
 assert (dest/'PIN.json').read_bytes()==(root/'voiceBridgePilot/contract/PIN.json').read_bytes()
for module in ['voiceBridgePilot','voiceBridgePilotClient']:
 manifest=(root/module/'src/main/AndroidManifest.xml').read_text()
 assert not any(p in manifest for p in ['android.permission.INTERNET','android.permission.RECORD_AUDIO','android.permission.READ_EXTERNAL_STORAGE','android.permission.MANAGE_EXTERNAL_STORAGE'])
 assert not any(p in (root/module/'build.gradle.kts').read_text() for p in ['onnxruntime','project(":pocketRuntime")','project(":identityRuntime")','project(":studioRuntime")'])
print(json.dumps({'base':base,'historical_apps_libraries_and_deliveries_unchanged':True,'archive005j_preserved':True,'legacy005k_sha256':legacy['bundleSha256'],'fixture_contract_sha256':h.hexdigest(),'cross_repository_checked':bool(args.core_root),'private_profile_access':False,'native_generation':'DISABLED'},indent=2))
