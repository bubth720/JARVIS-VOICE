import subprocess,json
from pathlib import Path
BASE='1a9f713dff28f6e4fb145728815e9ed118f5a929'
original=subprocess.check_output(['git','ls-tree','-r','--name-only',BASE],text=True).splitlines()
subprocess.run(['git','diff','--exit-code',BASE,'--',*[p for p in original if p!='settings.gradle.kts']],check=True)
settings=subprocess.check_output(['git','show',BASE+':settings.gradle.kts'],text=True)
assert Path('settings.gradle.kts').read_text()==settings+'\ninclude(":voiceFinalSdk", ":voiceFinalRuntime", ":voiceFinalStudio")\n'
added=subprocess.check_output(['git','diff','--name-only','--diff-filter=A',BASE],text=True).splitlines()
allowed=('voiceFinalSdk/','voiceFinalRuntime/','voiceFinalStudio/','docs/mission-voice-final-01/','scripts/voice-final-01/','.github/workflows/voice-final-01.yml')
assert all(p.startswith(allowed) for p in added)
assert not any(p.lower().endswith(('.wav','.mp3','.onnx','.safetensors','.jks','.keystore')) for p in added)
print(json.dumps({'base':BASE,'all_original_tracked_files_unchanged':True,'only_original_configuration_change':'append three independent modules','jni_permissions_signatures_contracts_unchanged':True,'personal_audio_or_weights_added':False},indent=2))
