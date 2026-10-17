#!/usr/bin/env python3
"""Optional adb-shell==0.4.4 fallback for a fixed localhost emulator; no HOME override or private keys read.
Build/audit the seven signed fixture APKs first. No owner device or installation outside this emulator.
"""
import argparse,json,subprocess
from pathlib import Path
from adb_shell.adb_device import AdbDeviceTcp
p=argparse.ArgumentParser();p.add_argument('--out',default='work/voice-bridge-pilot/shared-android');p.add_argument('--variant',choices=['authorized','wrongPackage','wrongSignature']);p.add_argument('--preinstalled',action='store_true');a=p.parse_args()
out=Path(a.out);out.mkdir(parents=True,exist_ok=True)
x=AdbDeviceTcp('127.0.0.1',5555,default_transport_timeout_s=30)
assert x.connect(rsa_keys=[],auth_timeout_s=10)
def shell(cmd,timeout=180):
 return x.shell(cmd,transport_timeout_s=timeout,read_timeout_s=timeout,timeout_s=timeout)
assert shell('getprop ro.kernel.qemu').strip()=='1','Refuse non-emulator target'
assert shell('getprop sys.boot_completed').strip()=='1','Emulator still booting; no installation'
api=int(shell('getprop ro.build.version.sdk').strip())
files=[('voiceBridgePilot/build/outputs/apk/debug/voiceBridgePilot-debug.apk','pilot')]
for flavor in ['authorized','wrongPackage','wrongSignature']:
 files += [(f'voiceBridgePilotClient/build/outputs/apk/{flavor}/debug/voiceBridgePilotClient-{flavor}-debug.apk',flavor),
           (f'voiceBridgePilotClient/build/outputs/apk/androidTest/{flavor}/debug/voiceBridgePilotClient-{flavor}-debug-androidTest.apk',flavor+'-test')]
for src,name in ([] if a.preinstalled else files):
 dst='/data/local/tmp/voice-pilot-'+name+'.apk';x.push(src,dst,read_timeout_s=60)
 result=shell('pm install -r -t '+dst)
 assert 'Success' in result,(name,result)
 shell('rm '+dst);print('installed fixture',name,flush=True)
packages={'authorized':'com.jarvis.core.voicepilot.fixture','wrongPackage':'com.jarvis.core.voicepilot.unknown','wrongSignature':'com.jarvis.core.voicepilot.untrusted'}
for flavor in ([a.variant] if a.variant else list(packages)):
 path=out/(flavor+'.txt')
 with path.open('w') as report:
  for chunk in x.streaming_shell('am instrument -w -r '+packages[flavor]+'.test/androidx.test.runner.AndroidJUnitRunner',transport_timeout_s=90,read_timeout_s=90):
   report.write(chunk);report.flush();print(chunk,end='',flush=True)
 subprocess.run(['python','scripts/bridge/check_instrumentation.py',str(path),'--expected','9','--skipped',str(0 if flavor=='authorized' else 7)],check=True)
(out/'device.json').write_text(json.dumps({'api':api,'emulator':True,'hardware_acceleration':False,
 'transport':'adb-shell0.4.4 localhost5555','personal_data':False},indent=2)+'\n')
x.close()
