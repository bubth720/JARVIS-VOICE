"""Generate only synthetic MP3 fixtures; no recording or network access."""
import subprocess
from pathlib import Path
root = Path(__file__).resolve().parents[2]
out = root / 'pocketProbe/build/generated/androidTestAssets'
out.mkdir(parents=True, exist_ok=True)
for name, rate, channels in [('synthetic', 44100, 2), ('mono', 44100, 1), ('stereo48', 48000, 2)]:
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'lavfi', '-i',
                    f'sine=frequency=1000:sample_rate={rate}:duration=12', '-ac', str(channels),
                    '-codec:a', 'libmp3lame', '-b:a', '128k', str(out / f'{name}.mp3')], check=True)
