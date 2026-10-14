#!/usr/bin/env python3
"""Audit only a public locally signed APK archive. Keys and private models are never inputs."""
import argparse
import hashlib
import json
import subprocess
import sys
import zipfile
from pathlib import Path


def main():
    p=argparse.ArgumentParser()
    p.add_argument('archive',type=Path)
    p.add_argument('--build-tools',type=Path,required=True)
    p.add_argument('--out',type=Path,required=True)
    args=p.parse_args()
    expected=json.loads(args.archive.with_name('delivery.json').read_text())
    args.out.mkdir(parents=True,exist_ok=True)
    name='JARVIS-VOICE-005F-stable.apk'
    with zipfile.ZipFile(args.archive) as archive:
        assert archive.namelist()==[name], 'Delivery archive must contain only the public APK'
        data=archive.read(name)
    assert len(data)==expected['apkBytes']
    assert hashlib.sha256(data).hexdigest()==expected['apkSha256']
    apk=args.out/name;apk.write_bytes(data)
    report=args.out/'apk-verification.json'
    subprocess.run([sys.executable,str(Path(__file__).with_name('verify_experimental_apk.py')),str(apk),
        '--application-id','com.jarvis.voice.voiceprototype005f','--build-tools',str(args.build_tools),'--report',str(report)],check=True)
    actual=json.loads(report.read_text())
    assert actual['certificateSha256']==expected['certificateSha256']
    assert actual['modelWeightsIncluded'] is False and actual['internetPermission'] is False
    assert expected['stableSigning'] is True and expected['compatibleWithInstalled005eCi'] is False
    subprocess.run(['git','merge-base','--is-ancestor',expected['codeCommit'],'HEAD'],check=True)
    (args.out/'delivery.json').write_text(json.dumps(expected,indent=2)+'\n')

if __name__=='__main__':main()
