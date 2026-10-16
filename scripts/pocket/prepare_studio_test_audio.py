"""Synthetic-only instrumentation fixtures, never private audio/models."""
from pathlib import Path
import subprocess
out=Path(__file__).resolve().parents[2]/'voiceStudio/build/generated/androidTestAssets'
out.mkdir(parents=True,exist_ok=True)
for name,rate,channels,seconds,ext in [('stereo44',44100,2,12,'mp3'),('mono48',48000,1,12,'mp3'),('short3',24000,1,3,'wav'),('short4',24000,1,4,'wav'),('exact5',24000,1,5,'wav')]:
    args=['ffmpeg','-v','error','-y','-f','lavfi','-i',f'sine=frequency=440:sample_rate={rate}:duration={seconds}','-ac',str(channels)]
    args+=['-codec:a','libmp3lame','-b:a','128k'] if ext=='mp3' else ['-codec:a','pcm_s16le']
    subprocess.run([*args,str(out/f'{name}.{ext}')],check=True)
import zipfile
with zipfile.ZipFile(out/'invalid-pack.zip','w') as z:z.writestr('not-the-official-model.txt','Synthetic invalid import, must be refused')
