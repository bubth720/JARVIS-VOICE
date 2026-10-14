"""Generate an explicitly synthetic instrumented-test fixture outside version control.
No user audio, microphone, repository media or network source is accessed.
"""
import subprocess
from pathlib import Path
root = Path(__file__).resolve().parents[2]
out = root / 'pocketProbe/build/generated/androidTestAssets/synthetic.mp3'
out.parent.mkdir(parents=True, exist_ok=True)
subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'lavfi', '-i',
                'sine=frequency=1000:sample_rate=44100:duration=6', '-ac', '2',
                '-codec:a', 'libmp3lame', '-b:a', '64k', str(out)], check=True)
