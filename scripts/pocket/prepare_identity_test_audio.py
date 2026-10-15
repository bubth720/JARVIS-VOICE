"""Only synthetic instrumentation assets. No network or personal recordings."""
from pathlib import Path
import subprocess
root = Path(__file__).resolve().parents[2]
out = root / 'voiceIdentity/build/generated/androidTestAssets'
out.mkdir(parents=True, exist_ok=True)
for name, rate, channels in [('stereo44',44100,2),('mono48',48000,1)]:
    subprocess.run(['ffmpeg','-v','error','-y','-f','lavfi','-i',
                    f'sine=frequency=440:sample_rate={rate}:duration=12','-ac',str(channels),
                    '-codec:a','libmp3lame','-b:a','128k',str(out/(name+'.mp3'))],check=True)
