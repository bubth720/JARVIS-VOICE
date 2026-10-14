"""Compare persistent streaming decoder state with a single call on synthetic latents.
No private audio is read or produced. This verifies PCM boundaries, not AudioTrack.
"""
import argparse
import json
from pathlib import Path
import numpy as np
import onnxruntime as ort
p = argparse.ArgumentParser()
p.add_argument('--model-dir', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
b = json.loads((a.model_dir / 'bundle.json').read_text())
o = ort.SessionOptions(); o.intra_op_num_threads = 1; o.inter_op_num_threads = 1
o.log_severity_level = 3
s = ort.InferenceSession(str(a.model_dir / 'mimi_decoder_int8.onnx'), sess_options=o, providers=['CPUExecutionProvider'])
manifest = b['mimi_state_manifest']
def initial():
    return {v['input_name']: np.full(v['shape'], int(v['fill'] == 'ones'), dtype=v['dtype']) for v in manifest}
x = np.random.default_rng(42).normal(size=(1, 14, 32)).astype('float32')
whole = s.run(None, dict(latent=x, **initial()))[0].reshape(-1)
states = initial(); blocks = []; offset = 0; boundaries = []
for n in [2, 4, 4, 4]:
    out = dict(zip([v.name for v in s.get_outputs()], s.run(None, dict(latent=x[:, offset:offset+n], **states))))
    states = {v['input_name']: out[v['output_name']] for v in manifest}
    blocks.append(out['audio'].reshape(-1)); offset += n
    boundaries.append(sum(len(v) for v in blocks))
stream = np.concatenate(blocks)
assert whole.shape == stream.shape
error = np.abs(whole - stream)
result = {'samples': len(stream), 'relativeL2': float(np.linalg.norm(whole-stream)/max(np.linalg.norm(whole), 1e-9)),
          'maxAbs': float(error.max()), 'boundaryErrors': [float(error[max(0,i-100):min(len(error),i+100)].max()) for i in boundaries[:-1]],
          'test': 'synthetic latents; persistent INT8 state; single call versus 2/4/4/4 frames'}
a.output.write_text(json.dumps(result, indent=2)+'\n'); print(json.dumps(result))
# This is diagnostic evidence, not a claim of acoustic equivalence. The INT8 graph
# differs with chunk size; report the difference instead of hiding it.
assert np.isfinite(whole).all() and np.isfinite(stream).all()
assert len(stream) == 14 * 1920
assert np.count_nonzero(stream) > len(stream) * 0.95
