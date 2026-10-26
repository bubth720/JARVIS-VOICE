"""Protect every existing tracked byte except exact additive test/build wiring."""
import json,subprocess
from pathlib import Path
BASE='fe57452fcb33f5b6870070f30ba6c5f1d349c4ba'
def git(*args): return subprocess.check_output(['git',*args])
expected={
 'settings.gradle.kts':lambda s:s+b'\ninclude(":voiceFusion02")\n',
 'voiceBridgePilot/build.gradle.kts':lambda s:s.replace(b'    testImplementation("junit:',b'    testImplementation(project(":voiceFusion02"))\n    testImplementation("junit:',1),
 'voiceFinalStudio/build.gradle.kts':lambda s:s.replace(b'    androidTestImplementation("androidx.test:runner:',b'    androidTestImplementation(project(":voiceFusion02"))\n    androidTestImplementation("androidx.test:runner:',1),
}
original=git('ls-tree','-r','--name-only',BASE).decode().splitlines()
for name in original:
 old=git('show',BASE+':'+name)
 assert Path(name).read_bytes()==(expected[name](old) if name in expected else old),name
allowed=('voiceFusion02/','docs/fusion-02/','scripts/fusion-02/')
exact={'.github/workflows/fusion-02-voice.yml','voiceBridgePilot/src/test/java/com/jarvis/voice/bridgepilot/Fusion02ProposalTests.kt','voiceFinalStudio/src/androidTest/java/com/jarvis/voice/voicestudio/Fusion02AudioTests.kt'}
# Include untracked files in the review; respect .gitignore for generated outputs and private work.
current=set(git('ls-files','--cached','--others','--exclude-standard').decode().splitlines())
added=current-set(original)
for name in added:
 assert name.startswith(allowed) or name in exact,name
 assert Path(name).suffix.lower() not in {'.wav','.mp3','.onnx','.safetensors','.jks','.keystore','.apk','.zip','.bin'},name
 assert not Path(name).is_symlink(),name
print(json.dumps({'base':BASE,'protectedExistingFiles':len(original),'onlyExistingChanges':list(expected),'addedFiles':sorted(added),'runtimeManifestJniPermissionsSignaturesUnchanged':True},indent=2))
