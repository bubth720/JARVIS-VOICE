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
import numpy as np
import torch
from torch import nn
from torch.nn import functional as F
import onnxruntime as ort
from pocket_tts import TTSModel
import pocket_tts
import yaml
from download_official_checkpoint import SHA256, SIZE

EXPECTED = 'hf://kyutai/pocket-tts/languages/french/model.safetensors@d0ebc5deb50bc6b225f686782f124dd37e2dca0a'
p = argparse.ArgumentParser()
p.add_argument('--output-dir', type=Path, required=True)
p.add_argument('--checkpoint', type=Path, required=True, help='Privately downloaded exact official checkpoint; never a fallback')
a = p.parse_args()
if importlib.metadata.version('pocket-tts') != '3.3.0':
    raise SystemExit('pocket-tts==3.3.0 required; do not mix checkpoint revisions')
torch.set_num_threads(1)
if not a.checkpoint.is_file() or a.checkpoint.stat().st_size != SIZE:
    raise SystemExit('BLOCKED: exact official checkpoint missing or incorrect size')
with a.checkpoint.open('rb') as source:
    checkpoint_hash = hashlib.file_digest(source, 'sha256').hexdigest()
if checkpoint_hash != SHA256:
    raise SystemExit('BLOCKED: official checkpoint SHA256 mismatch')
config_path = Path(pocket_tts.__file__).parent / 'config/french.yaml'
config = yaml.safe_load(config_path.read_text())
if config['weights_path'] != EXPECTED or config['flow_lm']['transformer']['num_layers'] != 6:
    raise SystemExit('BLOCKED: official French configuration mismatch')
a.output_dir.mkdir(parents=True, exist_ok=True)
local_config = dict(config)
local_config['weights_path'] = str(a.checkpoint.resolve())
local_config['weights_path_without_voice_cloning'] = None
private_config = a.output_dir / 'private-export-config.yaml'
private_config.write_text(yaml.safe_dump(local_config))
try:
    model = TTSModel.load_model(config=private_config)
finally:
    private_config.unlink(missing_ok=True)
if not model.has_voice_cloning or len(model.flow_lm.transformer.layers) != 6:
    raise SystemExit('BLOCKED: full French six-layer checkpoint required')
if not model.flow_lm.insert_bos_before_voice:
    raise SystemExit('BLOCKED: expected BOS configuration missing')
model.eval()

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
        for signal in ['silence', 'sine', 'noise']:
            torch.manual_seed(42)
            wave = torch.sin(torch.arange(samples).float() * (2 * torch.pi * 220 / 24000)).reshape(1,1,-1)*.1
            if signal == 'silence': wave.zero_()
            if signal == 'noise': wave = torch.randn_like(wave) * .05
            expected = torch.cat([model.flow_lm.bos_before_voice, model._encode_audio(wave)],dim=1).numpy()
            actual = session.run(None,{'audio': wave.numpy()})[0]
            np.testing.assert_allclose(actual, expected, atol=2e-4, rtol=2e-4)
            assert actual.shape == (1, 1 + (samples + 1919) // 1920, 1024)
            assert np.isfinite(actual).all()
            checks.append({'samples': samples, 'signal': signal, 'shape': list(actual.shape),
                           'maxAbs': float(np.max(np.abs(actual-expected))),
                           'relativeL2': float(np.linalg.norm(actual-expected)/max(np.linalg.norm(expected),1e-12))})
report={'source': EXPECTED, 'checkpointSha256': checkpoint_hash,
        'versions': {name: importlib.metadata.version(name) for name in ['pocket-tts','torch','onnx','onnxruntime']},
        'tolerances': {'atol':2e-4,'rtol':2e-4}, 'dtype':'float32',
        'packCompatibilityValidated':False, 'cacheParityValidated':False,
        'tokenizerSource':config['flow_lm']['lookup_table']['tokenizer_path'],'encoderSha256':hashlib.sha256(path.read_bytes()).hexdigest(),
        'bosIncluded':True,'syntheticParity':checks,'endToEndVoiceValidated':False,
        'androidImportEnabled':False,'license':'CC-BY-4.0; official additional conditions apply'}
(a.output_dir/'encoder-validation.json').write_text(json.dumps(report,indent=2)+'\n')
print('Synthetic encoder parity passed. APK integration remains disabled until cache and French voice generation validation.')
