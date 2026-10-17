#!/usr/bin/env python3
"""Read-only preservation and inactive transport checks, no private payload/model required."""
from pathlib import Path
import hashlib,json,re,subprocess
root=Path(__file__).resolve().parents[2]
base='cce9a4549c35e721ee37daff014d427ef3656b2a'
preserved=['app','pocketProbe','voicePrototype','voiceQuality','voiceIdentity','voiceExpression','voiceStudio','pocketRuntime','identityRuntime','expressionRuntime','studioRuntime','deliveries']
subprocess.run(['git','diff','--exit-code',base,'--',*preserved],cwd=root,check=True)
source=json.loads((root/'docs/mission005k/SOURCES.json').read_text())
resources=root/'voiceBridgeContract/src/main/resources/unified-v1'
lock=json.loads((resources/'source-lock.json').read_text())
assert lock['commit']==source['unified']['commit']=='41f53501fb18fe727d5fd453dd715f0811d2e897'
assert lock['sha256']==source['schemas']
assert len(list(resources.glob('*.schema.json')))==5
for name,digest in lock['sha256'].items():assert hashlib.sha256((resources/name).read_bytes()).hexdigest()==digest
pin=json.loads((root/'docs/mission005k/CONTRACT-PIN.json').read_text())
for name,digest in pin['files'].items():assert hashlib.sha256((root/name).read_bytes()).hexdigest()==digest
canonical=''.join(k+'\0'+v+'\n' for k,v in sorted(pin['files'].items())).encode()
assert hashlib.sha256(canonical).hexdigest()==pin['bundleSha256']
for module in ('voiceBridgeContract','voiceBridgeHarness'):
    assert not list((root/module).glob('src/**/AndroidManifest.xml'))
    for file in (root/module/'src/main').rglob('*.kt'):
        text=file.read_text()
        assert not re.search(r'import\s+(android\.|java\.net\.)|ProcessBuilder\s*\(|Runtime\.getRuntime\(\)\.exec\s*\(',text),file
for name in ('voiceStudio','voiceExpression','voiceIdentity','voiceQuality','voicePrototype','pocketProbe','app'):
    assert 'voiceBridge' not in (root/name/'build.gradle.kts').read_text()
zip=root/'deliveries/mission005j/005j-experimental-apk.zip'
assert zip.stat().st_size==15259381 and hashlib.sha256(zip.read_bytes()).hexdigest()=='82db41ca499077fc062d6300c6ee48f816cca728d346588de5ee2cc3a04260b0'
report={'preservedBase':base,'unchangedPaths':preserved,'schemasPinned':True,'historicalAppsDependOnBridge':False,'ipcEnabled':False,'coreConnected':False,'newApkBuilt':False,'stable005jZipPreserved':True}
print(json.dumps(report,indent=2))
