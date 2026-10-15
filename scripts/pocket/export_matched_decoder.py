"""Export full-checkpoint FP32 Flow and streaming decoder privately.

Export adapters are bound only to a deep copy, never the official comparator.
No token, recordings or model weights are written to a public artifact.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
from types import MethodType
import numpy as np
import torch
from torch import nn
import onnxruntime as ort
from pocket_tts.modules.stateful_module import init_states, increment_steps
from pocket_tts.modules.attention import StreamingMultiheadAttention
from pocket_tts.modules.conv import StreamingConv1d, StreamingConvTranspose1d
from download_official_checkpoint import SHA256, SIZE
from matched_model import load_verified_model


def attention_forward(self, query, model_state, attn_mask=None):
    state = self.get_state(model_state)
    b, length, _ = query.shape
    q, k, v = self.in_proj(query).reshape(b, length, 3, self.num_heads, self.dim_per_head).unbind(2)
    offset, pad = state['offset'], state['pad']
    q, k = self.rope(q, k, offset[0])
    positions = offset[0] + torch.arange(length, device=query.device)
    indices = positions.reshape(1, 1, -1, 1, 1).expand(2, b, length, self.num_heads, self.dim_per_head)
    cache = state['cache'].scatter(2, indices, torch.stack([k, v]))
    state['cache'] = cache
    key_pos = torch.arange(cache.shape[2], device=query.device).reshape(1, -1) - pad[:, None]
    query_pos = positions.reshape(1, -1) - pad[:, None]
    delta = query_pos[:, :, None] - key_pos[:, None, :]
    mask = (key_pos[:, None, :] >= 0) & (delta >= 0)
    if self.context is not None: mask = mask & (delta < self.context)
    valid = torch.arange(cache.shape[2], device=query.device) < offset[0] + length
    safe = torch.where(valid.reshape(1, 1, -1, 1, 1), cache, torch.zeros_like(cache))
    scores = torch.matmul(q.transpose(1, 2), safe[0].permute(0, 2, 3, 1)) * (self.dim_per_head ** -.5)
    scores = torch.where(mask[:, None], scores, torch.full_like(scores, -torch.inf))
    any_key = mask.any(-1, keepdim=True)[:, None]
    scores = torch.where(any_key, scores, torch.zeros_like(scores))
    probabilities = torch.where(any_key, scores.softmax(-1), torch.zeros_like(scores))
    x = torch.matmul(probabilities, safe[1].transpose(1, 2))
    return self.out_proj(x.transpose(1, 2).reshape(b, length, -1))


def conv_forward(self, x, model_state):
    state = self.get_state(model_state)
    count = state['previous'].shape[-1]
    if count:
        previous = state['previous']
        if self.pad_mode == 'replicate':
            previous = torch.where(state['first'].reshape(-1, 1, 1), x[..., :1].expand_as(previous), previous)
            state['first'] = torch.zeros_like(state['first'])
        x = torch.cat([previous, x], -1)
        state['previous'] = x[..., -count:]
    return self.conv(x)


def transpose_forward(self, x, model_state):
    state = self.get_state(model_state)
    y = self.convtr(x)
    count = state['partial'].shape[-1]
    if count:
        start = y[..., :count] + state['partial']
        partial = y[..., -count:]
        if self.convtr.bias is not None: partial = partial - self.convtr.bias[:, None]
        state['partial'] = partial
        y = torch.cat([start, y[..., count:-count]], -1)
    return y


class Decoder(nn.Module):
    def __init__(self, model, specs):
        super().__init__()
        self.mimi = copy.deepcopy(model.mimi)
        self.register_buffer('mean', model.flow_lm.emb_mean.detach())
        self.register_buffer('std', model.flow_lm.emb_std.detach())
        self.specs = specs
        for name, module in self.mimi.named_modules():
            if name.startswith(('decoder.', 'decoder_transformer.', 'upsample.')):
                if isinstance(module, StreamingMultiheadAttention): module.forward = MethodType(attention_forward, module)
                if isinstance(module, StreamingConv1d): module.forward = MethodType(conv_forward, module)
                if isinstance(module, StreamingConvTranspose1d): module.forward = MethodType(transpose_forward, module)

    def forward(self, latent, *states):
        state = {}
        for spec, value in zip(self.specs, states): state.setdefault(spec['module'], {})[spec['key']] = value
        audio = self.mimi.decode_from_latent(latent * self.std + self.mean, state)
        for name, values in state.items():
            if name.startswith('decoder_transformer.') and 'offset' in values:
                values['offset'] = values['offset'] + latent.shape[1] * 16
        return (audio, *(state[s['module']][s['key']] for s in self.specs))


class Flow(nn.Module):
    def __init__(self, model):
        super().__init__(); self.flow = model.flow_lm.flow_net
    def forward(self, c, s, t, x): return self.flow(c, s, t, x)


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--checkpoint', type=Path, required=True)
    p.add_argument('--bundle', type=Path, required=True)
    p.add_argument('--output-dir', type=Path, required=True)
    a = p.parse_args()
    torch.set_num_threads(1)
    a.output_dir.mkdir(parents=True, exist_ok=True)
    (a.output_dir / 'decoder-validation.json').unlink(missing_ok=True)
    model = load_verified_model(a.checkpoint, a.output_dir)
    specs = json.loads(a.bundle.read_text())['mimi_state_manifest']
    dtype = {'float32': torch.float32, 'int64': torch.int64, 'bool': torch.bool}
    initial = [torch.full(s['shape'], int(s['fill'] == 'ones'), dtype=dtype[s['dtype']]) for s in specs]
    decoder = Decoder(model, specs).eval()
    flow = Flow(model).eval()
    torch.onnx.export(flow, (torch.zeros(1, 1024), torch.zeros(1, 1), torch.ones(1, 1), torch.zeros(1, 32)),
                      str(a.output_dir / 'flow_lm_flow_fp32.onnx'), opset_version=17, dynamo=False,
                      input_names=['c', 's', 't', 'x'], output_names=['flow_dir'])
    torch.onnx.export(decoder, (torch.zeros(1, 2, 32), *initial), str(a.output_dir / 'mimi_decoder_fp32.onnx'),
                      opset_version=17, dynamo=False, input_names=['latent'] + [s['input_name'] for s in specs],
                      output_names=['audio'] + [s['output_name'] for s in specs],
                      dynamic_axes={'latent': {1: 'frames'}, 'audio': {2: 'samples'}})
    options = ort.SessionOptions(); options.intra_op_num_threads = options.inter_op_num_threads = 1; options.log_severity_level = 3
    sessions = {name: ort.InferenceSession(str(a.output_dir / (name + '_fp32.onnx')), sess_options=options, providers=['CPUExecutionProvider']) for name in ['flow_lm_flow', 'mimi_decoder']}
    report = {'checkpointSha256': SHA256, 'strictParity': False, 'decoderCases': [], 'flowCases': [], 'androidImportEnabled': False}
    rng = np.random.default_rng(42)
    with torch.no_grad():
        for index in range(9):
            c = rng.standard_normal((1, 1024)).astype(np.float32)
            x = rng.standard_normal((1, 32)).astype(np.float32)
            s = np.full((1, 1), index / 10, np.float32); t = np.ones((1, 1), np.float32)
            expected = model.flow_lm.flow_net(*[torch.from_numpy(v) for v in [c, s, t, x]]).numpy()
            actual = sessions['flow_lm_flow'].run(None, dict(c=c, s=s, t=t, x=x))[0]
            np.testing.assert_allclose(actual, expected, atol=2e-4, rtol=2e-4)
            report['flowCases'].append({'case': index, 'maxAbs': float(np.max(np.abs(actual-expected)))})
        py_state = init_states(model.mimi, 1, 9216)
        ort_state = {s['input_name']: v.numpy().copy() for s, v in zip(specs, initial)}
        for frames in [2, 4, 1, 3, 8]:
            latent = rng.standard_normal((1, frames, 32)).astype(np.float32)
            expected = model.mimi.decode_from_latent(torch.from_numpy(latent) * model.flow_lm.emb_std + model.flow_lm.emb_mean, py_state).numpy()
            increment_steps(model.mimi, py_state, frames * 16)
            values = sessions['mimi_decoder'].run(None, dict(latent=latent, **ort_state))
            np.testing.assert_allclose(values[0], expected, atol=2e-4, rtol=2e-4)
            assert values[0].shape == (1, 1, frames * 1920) and np.isfinite(values[0]).all()
            outputs = dict(zip([o.name for o in sessions['mimi_decoder'].get_outputs()], values))
            errors = []
            for spec in specs:
                target = py_state[spec['module']][spec['key']].numpy()
                actual = outputs[spec['output_name']]
                if spec['key'] == 'cache':
                    count = int(py_state[spec['module']]['offset'][0]); target, actual = target[:, :, :count], actual[:, :, :count]
                if spec['dtype'] == 'float32':
                    np.testing.assert_allclose(actual, target, atol=2e-4, rtol=2e-4)
                    if target.size: errors.append(float(np.max(np.abs(actual-target))))
                else: np.testing.assert_array_equal(actual, target)
            report['decoderCases'].append({'frames': frames, 'samples': values[0].size, 'maxAudioAbs': float(np.max(np.abs(values[0]-expected))), 'maxStateAbs': max(errors)})
            ort_state = {s['input_name']: outputs[s['output_name']] for s in specs}
    report['strictParity'] = True
    report['models'] = {name: hashlib.sha256((a.output_dir / (name + '_fp32.onnx')).read_bytes()).hexdigest() for name in sessions}
    (a.output_dir / 'decoder-validation.json').write_text(json.dumps(report, indent=2) + '\n')
    print('FP32 Flow and French decoder passed strict multistep audio/state parity.')


if __name__ == '__main__': main()
