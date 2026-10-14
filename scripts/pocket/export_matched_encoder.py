"""Prepare a CPU encoder export from the exact official French 3.3.0 full checkpoint.
Requires prior acceptance of the model terms and a secure HF_TOKEN environment binding.
Reads no recordings. Validation uses synthetic tensors. Never exports fallback random weights.
Output is NOT automatically importable by the APK: end-to-end validation remains mandatory.
"""
import argparse
import importlib.metadata
import json
from pathlib import Path
import hashlib
# Older exporter environments use beartype; Pocket 3.3.0 may omit it.
try:
    import beartype
    import beartype.claw
    beartype.beartype = lambda func: func
    beartype.claw.beartype_this_package = lambda **kwargs: None
except ImportError:
    pass
import numpy as np
import torch
from torch import nn
from torch.nn import functional as F
import onnxruntime as ort
from pocket_tts import TTSModel

EXPECTED = 'hf://kyutai/pocket-tts/languages/french/model.safetensors@d0ebc5deb50bc6b225f686782f124dd37e2dca0a'
p = argparse.ArgumentParser()
p.add_argument('--output-dir', type=Path, required=True)
a = p.parse_args()
if importlib.metadata.version('pocket-tts') != '3.3.0':
    raise SystemExit('pocket-tts==3.3.0 required; do not mix checkpoint revisions')
torch.set_num_threads(1)
model = TTSModel.load_model(language='french')
if not model.has_voice_cloning:
    raise SystemExit('BLOCKED: access to official full checkpoint unavailable. Public preset weights cannot create an encoder. Accept terms on Hugging Face and bind a read token securely; never put it in Git or logs.')
assert model.config.weights_path == EXPECTED
assert len(model.flow_lm.transformer.layers) == 6

# Adapted from netai369/PocketTTS.cpp-multilingual export_onnx.py (MIT),
# revision 3ed176a2b238b7ddb62b4aea4e7469ae4bcc3489. BOS is included exactly once.
class Encoder(nn.Module):
    def __init__(self, m):
        super().__init__()
        self.encoder = m.mimi.encoder
        self.transformer = m.mimi.encoder_transformer
        self.downsample = m.mimi.downsample
        self.register_buffer('speaker_projection', m.flow_lm.speaker_proj_weight.detach())
        self.register_buffer('bos', m.flow_lm.bos_before_voice.detach())
    def forward(self, audio):
        audio = F.pad(audio, (0, (1920 - audio.shape[-1] % 1920) % 1920))
        emb = self.encoder(audio, model_state=None)
        (emb,) = self.transformer(emb, model_state=None)
        emb = self.downsample(emb, model_state=None)
        conditioning = F.linear(emb.transpose(-1, -2).float(), self.speaker_projection)
        return torch.cat([self.bos, conditioning], dim=1)

wrapper = Encoder(model).eval()
a.output_dir.mkdir(parents=True, exist_ok=True)
path = a.output_dir / 'mimi_encoder_with_bos.onnx'
torch.onnx.export(wrapper, (torch.zeros(1,1,240000),), str(path), opset_version=17,
                  input_names=['audio'], output_names=['conditioning_with_bos'],
                  dynamic_axes={'audio': {2: 'samples'}, 'conditioning_with_bos': {1: 'frames'}}, dynamo=False)
o = ort.SessionOptions(); o.intra_op_num_threads=1; o.inter_op_num_threads=1; o.log_severity_level=3
session = ort.InferenceSession(str(path), sess_options=o, providers=['CPUExecutionProvider'])
checks = []
with torch.no_grad():
    for samples in [120000, 240017, 360000]:
        wave = torch.sin(torch.arange(samples).float() * (2 * torch.pi * 220 / 24000)).reshape(1,1,-1)*.1
        padded = F.pad(wave,(0,(1920-samples%1920)%1920))
        expected = torch.cat([model.flow_lm.bos_before_voice, model._encode_audio(padded)],dim=1).numpy()
        actual = session.run(None,{'audio': wave.numpy()})[0]
        np.testing.assert_allclose(actual, expected, atol=2e-4, rtol=2e-4)
        checks.append({'samples': samples, 'shape': list(actual.shape), 'maxAbs': float(np.max(np.abs(actual-expected)))})
report={'source': EXPECTED,'encoderSha256':hashlib.sha256(path.read_bytes()).hexdigest(),
        'bosIncluded':True,'syntheticParity':checks,'endToEndVoiceValidated':False,
        'androidImportEnabled':False,'license':'CC-BY-4.0; official additional conditions apply'}
(a.output_dir/'encoder-validation.json').write_text(json.dumps(report,indent=2)+'\n')
print('Synthetic encoder parity passed. APK integration remains disabled until cache and French voice generation validation.')
