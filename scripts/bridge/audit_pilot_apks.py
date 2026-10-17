#!/usr/bin/env python3
"""Audit locally built fixture APKs; never installs or downloads anything."""
import argparse,hashlib,json,re,subprocess,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--build-tools',type=Path,required=True);args=p.parse_args()
expected={
 'voiceBridgePilot/build/outputs/apk/debug/voiceBridgePilot-debug.apk':'com.jarvis.voice.bridgepilot',
 'voiceBridgePilotClient/build/outputs/apk/authorized/debug/voiceBridgePilotClient-authorized-debug.apk':'com.jarvis.core.voicepilot.fixture',
 'voiceBridgePilotClient/build/outputs/apk/wrongPackage/debug/voiceBridgePilotClient-wrongPackage-debug.apk':'com.jarvis.core.voicepilot.unknown',
 'voiceBridgePilotClient/build/outputs/apk/wrongSignature/debug/voiceBridgePilotClient-wrongSignature-debug.apk':'com.jarvis.core.voicepilot.untrusted',
}
report={}
for rel,pkg in expected.items():
 apk=root/rel
 def run(tool,*a):return subprocess.check_output([str(args.build_tools/tool),*a],text=True,stderr=subprocess.STDOUT)
 cert=run('apksigner','verify','--print-certs',str(apk));sha=re.findall(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})',cert);assert len(sha)==1
 badging=run('aapt','dump','badging',str(apk));assert "name='"+pkg+"'" in badging and "sdkVersion:'26'" in badging
 permissions=run('aapt','dump','permissions',str(apk))
 assert not any(x in permissions for x in ['android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE','android.permission.RECORD_AUDIO','android.permission.READ_EXTERNAL_STORAGE','android.permission.MANAGE_EXTERNAL_STORAGE'])
 manifest=run('aapt','dump','xmltree',str(apk),'AndroidManifest.xml')
 if pkg=='com.jarvis.voice.bridgepilot':
  service=manifest[manifest.index('E: service'):]
  assert 'com.jarvis.voice.bridgepilot.VoicePilotService' in service and 'com.jarvis.voice.bridgepilot.permission.BIND' in service
  assert re.search(r'android:exported[^\n]*0xffffffff',service)
  assert re.search(r'android:protectionLevel[^\n]*0x2',manifest)
 else:
  # The only client service is an unexported, second-process lifetime fixture.
  assert manifest.count('E: service')==1
  service=manifest[manifest.index('E: service'):]
  assert 'com.jarvis.core.voicepilot.fixture.LifetimeService' in service and ':lifetime' in service
  assert re.search(r'android:exported[^\n]*0x0',service)
 with zipfile.ZipFile(apk) as z:
  assert not any(name.endswith(('.so','.onnx','.safetensors','.gguf','.jvi','.npy','.wav','.pcm')) for name in z.namelist())
 report[pkg]={'apk':rel,'size_bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'certificate_sha256':sha[0],'minSdk':26,'signature_verified':True,'network_microphone_storage_permissions':False,'model_profile_pcm_or_native_library_packaged':False}
common=report['com.jarvis.voice.bridgepilot']['certificate_sha256']
assert report['com.jarvis.core.voicepilot.fixture']['certificate_sha256']==common
assert report['com.jarvis.core.voicepilot.unknown']['certificate_sha256']==common
assert report['com.jarvis.core.voicepilot.untrusted']['certificate_sha256']!=common
assert common!='aba9ee43361fd342e119a33ae5a628e63199b3b6ef4f14d3a3619f6d5678d89e','historical Studio signer reused'
print(json.dumps({'level':'STATIC_APK_ONLY_NOT_KERNEL_BINDER','ephemeral_test_signing_only':True,'apks':report},indent=2))
