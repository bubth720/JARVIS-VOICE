"""Private JVM golden fixture from official PyTorch and a synthetic sine only."""
import argparse
import json
import struct
from pathlib import Path
import torch
from matched_model import load_verified_model


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--checkpoint', type=Path, required=True)
    p.add_argument('--output-dir', type=Path, required=True)
    a = p.parse_args()
    torch.set_num_threads(1)
    a.output_dir.mkdir(parents=True, exist_ok=True)
    model = load_verified_model(a.checkpoint, a.output_dir)
    wave = torch.sin(torch.arange(120000).float() * (2 * torch.pi * 220 / 24000)) * .1
    wave.numpy().astype('<f4').tofile(a.output_dir / 'synthetic-pcm.f32')
    with torch.no_grad(): state = model.get_state_for_audio_prompt(wave.reshape(1, -1))
    def string(value):
        value = value.encode(); return struct.pack('<H', len(value)) + value
    payload = b'PTVB1' + struct.pack('<I', 1) + string('synthetic') + struct.pack('<H', 18)
    for module, values in state.items():
        for key in ['offset', 'pad', 'cache']:
            tensor = values[key].numpy()
            kind = 0 if key == 'cache' else 1
            data = tensor.astype('<f4' if kind == 0 else '<i8').tobytes()
            payload += string(module + '/' + key) + struct.pack('<BB', kind, tensor.ndim)
            payload += struct.pack('<' + 'I' * tensor.ndim, *tensor.shape) + struct.pack('<I', len(data)) + data
    (a.output_dir / 'synthetic-state.bin').write_bytes(payload)
    (a.output_dir / 'fixture-info.json').write_text(json.dumps({'syntheticOnly': True, 'signal': 'sine220Hz', 'samples': 120000, 'framesWithBos': 64, 'stateCount': 18}, indent=2) + '\n')
    print('Synthetic golden fixture generated from official checkpoint; no recording used.')


if __name__ == '__main__': main()
