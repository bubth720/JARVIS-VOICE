"""Export private FP32 FlowLM with the schema-3 offset/pad/cache interface.

Weights come exclusively from the hash-verified official French checkpoint.
Uses explicit scatter/masking because official streaming code calls .item().
This is a validation candidate, not an Android model pack.
"""
import argparse
import hashlib
import json
from pathlib import Path
import numpy as np
import torch
from torch import nn
from torch.nn import functional as F
import onnxruntime as ort
from pocket_tts.modules.stateful_module import init_states, increment_steps
from download_official_checkpoint import SHA256, SIZE
from matched_model import load_verified_model


class Main(nn.Module):
    def __init__(self, model):
        super().__init__()
        self.lm = model.flow_lm

    def forward(self, sequence, text_embeddings, *states):
        sequence = torch.where(torch.isnan(sequence), self.lm.bos_emb, sequence)
        x = torch.cat([text_embeddings, self.lm.input_linear(sequence)], dim=1)
        updated = []
        for i, layer in enumerate(self.lm.transformer.layers):
            offset, pad, cache = states[3*i:3*i+3]
            attn = layer.self_attn
            normalized = layer.norm1(x)
            b, length, _ = normalized.shape
            q, k, v = attn.in_proj(normalized).reshape(b, length, 3, attn.num_heads, attn.dim_per_head).unbind(2)
            q, k = attn.rope(q, k, offset[0])
            positions = offset[0] + torch.arange(length, device=x.device)
            indices = positions.reshape(1, 1, -1, 1, 1).expand(2, b, length, attn.num_heads, attn.dim_per_head)
            cache = cache.scatter(2, indices, torch.stack([k, v]))
            key_pos = torch.arange(cache.shape[2], device=x.device).reshape(1, -1) - pad[:, None]
            query_pos = positions.reshape(1, -1) - pad[:, None]
            delta = query_pos[:, :, None] - key_pos[:, None, :]
            mask = (key_pos[:, None, :] >= 0) & (delta >= 0)
            if attn.context is not None:
                mask = mask & (delta < attn.context)
            # Unused NaNs must never enter a masked matmul (0 * NaN is NaN).
            valid = torch.arange(cache.shape[2], device=x.device) < offset[0] + length
            safe = torch.where(valid.reshape(1, 1, -1, 1, 1), cache, torch.zeros_like(cache))
            scores = torch.matmul(q.transpose(1, 2), safe[0].permute(0, 2, 3, 1)) * (attn.dim_per_head ** -.5)
            scores = torch.where(mask[:, None], scores, torch.full_like(scores, -torch.inf))
            # PyTorch SDPA returns zero for a fully masked padded query. ONNX's
            # decomposed softmax(-inf,...) returns NaN without this explicit case.
            any_key = mask.any(dim=-1, keepdim=True)[:, None]
            scores = torch.where(any_key, scores, torch.zeros_like(scores))
            probabilities = torch.where(any_key, scores.softmax(dim=-1), torch.zeros_like(scores))
            attention = torch.matmul(probabilities, safe[1].transpose(1, 2))
            attention = attn.out_proj(attention.transpose(1, 2).reshape(b, length, -1))
            x = x + layer.layer_scale_1(attention)
            x = layer._ff_block(x)
            updated.extend([offset + length, pad, cache])
        cond = self.lm.out_norm(x)[:, -1]
        return (cond, self.lm.out_eos(cond), *updated)


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--checkpoint', type=Path, required=True)
    p.add_argument('--output-dir', type=Path, required=True)
    a = p.parse_args()
    torch.set_num_threads(1)
    a.output_dir.mkdir(parents=True, exist_ok=True)
    (a.output_dir / 'flow-validation.json').unlink(missing_ok=True)
    model = load_verified_model(a.checkpoint, a.output_dir)
    wrapper = Main(model).eval()
    states = []
    for _ in range(6): states.extend([torch.zeros(1, dtype=torch.int64), torch.zeros(1, dtype=torch.int64), torch.zeros(2, 1, 1024, 16, 64)])
    path = a.output_dir / 'flow_lm_main_fp32.onnx'
    torch.onnx.export(wrapper, (torch.full((1, 1, 32), torch.nan), torch.zeros(1, 0, 1024), *states), str(path),
                      opset_version=17, dynamo=False, input_names=['sequence', 'text_embeddings'] + [f'state_{i}' for i in range(18)],
                      output_names=['conditioning', 'eos_logit'] + [f'next_state_{i}' for i in range(18)],
                      dynamic_axes={'sequence': {1: 'sequence_length'}, 'text_embeddings': {1: 'prompt_length'}})
    options = ort.SessionOptions(); options.intra_op_num_threads = options.inter_op_num_threads = 1
    options.log_severity_level = 3
    session = ort.InferenceSession(str(path), sess_options=options, providers=['CPUExecutionProvider'])
    checks = []
    with torch.no_grad():
        for prompt_length, pad in [(64, 0), (127, 0), (189, 0), (64, 3)]:
            torch.manual_seed(42)
            py_state = init_states(model.flow_lm, 1, 1024)
            ort_state = {f'state_{i}': s.numpy().copy() for i, s in enumerate(states)}
            for i in range(6):
                py_state[f'transformer.layers.{i}.self_attn']['pad'].fill_(pad)
                ort_state[f'state_{i*3+1}'].fill(pad)
            for step in range(4):
                print(f'Validating prompt={prompt_length} pad={pad} step={step}', flush=True)
                sequence = torch.empty(1, 0, 32) if step == 0 else torch.randn(1, 1, 32)
                prompt = torch.randn(1, prompt_length, 1024) * .1 if step == 0 else torch.empty(1, 0, 1024)
                expected = model.flow_lm.backbone(model.flow_lm.input_linear(sequence), prompt, sequence, py_state)[:, -1]
                increment_steps(model.flow_lm, py_state, sequence.shape[1] + prompt.shape[1])
                values = session.run(None, dict(sequence=sequence.numpy(), text_embeddings=prompt.numpy(), **ort_state))
                np.testing.assert_allclose(values[0], expected.numpy(), atol=2e-4, rtol=2e-4)
                np.testing.assert_allclose(values[1], model.flow_lm.out_eos(expected).numpy(), atol=2e-4, rtol=2e-4)
                count = prompt_length + step
                errors = []
                for i in range(6):
                    st = py_state[f'transformer.layers.{i}.self_attn']
                    for j, key in enumerate(['offset', 'pad', 'cache']):
                        actual = values[2+i*3+j]
                        target = st[key].numpy()
                        if key == 'cache':
                            actual, target = actual[:, :, :count], target[:, :, :count]
                            np.testing.assert_allclose(actual, target, atol=2e-4, rtol=2e-4)
                            errors.append(float(np.max(np.abs(actual-target))))
                        else: np.testing.assert_array_equal(actual, target)
                checks.append({'promptLength': prompt_length, 'pad': pad, 'step': step, 'maxCacheAbs': max(errors), 'maxConditioningAbs': float(np.max(np.abs(values[0]-expected.numpy())))})
                ort_state = {f'state_{i}': v for i, v in enumerate(values[2:])}
    report = {'checkpointSha256': SHA256, 'modelSha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'stateCount': 18,
              'dtype': 'float32', 'strictParity': True, 'tolerances': {'atol': 2e-4, 'rtol': 2e-4}, 'cases': checks,
              'androidImportEnabled': False, 'endToEndVoiceValidated': False}
    (a.output_dir / 'flow-validation.json').write_text(json.dumps(report, indent=2) + '\n')
    print('FP32 FlowLM and all 18 states passed multistep parity, including nonzero padding.')


if __name__ == '__main__': main()
