#!/usr/bin/env python3
"""Audit only the independently signed, model-free 005G APK. Never inputs a private key/model/profile."""
import argparse,hashlib,json,subprocess,sys,zipfile
from pathlib import Path
SIGNER='aba9ee43361fd342e119a33ae5a628e63199b3b6ef4f14d3a3619f6d5678d89e'
def main():
 p=argparse.ArgumentParser();p.add_argument('archive',type=Path);p.add_argument('--build-tools',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
 expected=json.loads(a.archive.with_name('delivery.json').read_text());a.out.mkdir(parents=True,exist_ok=True);name='JARVIS-VOICE-005G-experimental.apk'
 with zipfile.ZipFile(a.archive) as z:
  assert z.namelist()==[name];data=z.read(name)
 assert len(data)==expected['apkBytes'] and hashlib.sha256(data).hexdigest()==expected['apkSha256']
 apk=a.out/name;apk.write_bytes(data);report=a.out/'apk-verification.json'
 subprocess.run([sys.executable,str(Path(__file__).with_name('verify_experimental_apk.py')),str(apk),'--application-id','com.jarvis.voice.voicequality005g','--build-tools',str(a.build_tools),'--report',str(report)],check=True)
 actual=json.loads(report.read_text());assert actual['certificateSha256']==expected['certificateSha256']==SIGNER
 assert not actual['modelWeightsIncluded'] and not actual['internetPermission']
 assert expected['stableSigning'] and expected['preservesInstalled005f']
 subprocess.run(['git','merge-base','--is-ancestor',expected['codeCommit'],'HEAD'],check=True)
 (a.out/'delivery.json').write_text(json.dumps(expected,indent=2)+'\n')
if __name__=='__main__':main()
