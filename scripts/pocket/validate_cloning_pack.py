"""Private full-checkpoint comparison. Synthetic inputs only; never enables Android.

Strict FP32 parity is reported separately from INT8 approximation. A shape match
or loose component similarity must not be presented as validated voice cloning.
"""
import argparse
import hashlib
import json
from pathlib import Path
import numpy as np
import torch
import onnxruntime as ort
from pocket_tts.modules.stateful_module import init_states
from tokenizers import Tokenizer
from download_official_checkpoint import SIZE, SHA256
from matched_model import load_verified_model


def difference(expected, actual):
    expected, actual = np.asarray(expected), np.asarray(actual)
    if expected.shape != actual.shape:
        return {'shapeMatch': False, 'expected': list(expected.shape), 'actual': list(actual.shape), 'strictParity': False}
    finite = np.isfinite(expected) & np.isfinite(actual)
    mask_match = np.array_equal(np.isfinite(expected), np.isfinite(actual))
    delta = actual[finite] - expected[finite]
    return {'shapeMatch': True, 'finiteMaskMatch': mask_match,
            'maxAbs': float(np.max(np.abs(delta))) if delta.size else 0.,
            'relativeL2': float(np.linalg.norm(delta) / max(np.linalg.norm(expected[finite]), 1e-12)),
            'strictParity': bool(mask_match and np.allclose(expected, actual, atol=2e-4, rtol=2e-4, equal_nan=True))}


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--checkpoint', type=Path, required=True)
    p.add_argument('--model-dir', type=Path, required=True)
    p.add_argument('--encoder', type=Path, required=True)
    p.add_argument('--fp32-dir', type=Path, help='Compare the separate coherent FP32 candidate instead of Alba INT8 graphs')
    p.add_argument('--report', type=Path, required=True)
    a = p.parse_args()
    torch.set_num_threads(1)
    a.report.parent.mkdir(parents=True, exist_ok=True)
    a.report.unlink(missing_ok=True)
    model = load_verified_model(a.checkpoint, a.report.parent)
    opts = ort.SessionOptions()
    opts.intra_op_num_threads = opts.inter_op_num_threads = 1
    opts.log_severity_level = 3
    def session(path):
        return ort.InferenceSession(str(path), sess_options=opts, providers=['CPUExecutionProvider'])
    encoder = session(a.encoder)
    sessions = {n: session((a.fp32_dir / (n + '_fp32.onnx')) if a.fp32_dir and n != 'text_conditioner' else a.model_dir / (n + '_int8.onnx')) for n in ['text_conditioner', 'flow_lm_main', 'flow_lm_flow', 'mimi_decoder']}
    bundle = json.loads((a.model_dir / 'bundle.json').read_text())
    specs = bundle['flow_lm_state_manifest']
    assert len(specs) == 18 and model.flow_lm.insert_bos_before_voice
    def initial(manifest):
        return {s['input_name']: np.full(s['shape'], int(s['fill'] == 'ones'), dtype={'float32': np.float32, 'int64': np.int64, 'bool': np.bool_}[s['dtype']]) for s in manifest}
    report = {'checkpointSha256': SHA256, 'candidatePrecision': 'float32' if a.fp32_dir else 'int8', 'bosIncludedExactlyOnce': True, 'cacheCases': [], 'components': {}, 'androidImportEnabled': False, 'endToEndVoiceValidated': False}
    with torch.no_grad():
        for samples in [120000, 240017, 360000]:
            wave = (torch.sin(torch.arange(samples) * (2 * torch.pi * 220 / 24000)) * .1).reshape(1, 1, -1)
            prompt = encoder.run(None, {'audio': wave.numpy()})[0]
            expected = model.get_state_for_audio_prompt(wave[0])
            values = sessions['flow_lm_main'].run(None, dict(sequence=np.empty((1, 0, 32), np.float32), text_embeddings=prompt, **initial(specs)))
            outputs = dict(zip([o.name for o in sessions['flow_lm_main'].get_outputs()], values))
            checks = []
            for s in specs:
                py = expected[s['module']][s['key']].numpy()
                actual = outputs[s['output_name']]
                if s['key'] == 'cache': actual = actual[:, :, :prompt.shape[1]]
                check = difference(py, actual)
                if s['dtype'] == 'int64': check['exactIntegerParity'] = bool(np.array_equal(py, actual))
                checks.append(dict(module=s['module'], key=s['key'], **check))
            report['cacheCases'].append({'samples': samples, 'framesWithBos': prompt.shape[1], 'states': checks})
        ids = np.array([Tokenizer.from_file(str(a.model_dir / 'tokenizer.json')).encode('Bonjour, ma voix fonctionne hors ligne.').ids], np.int64)
        emb = model.flow_lm.conditioner(torch.from_numpy(ids)).numpy()
        report['components']['text'] = difference(emb, sessions['text_conditioner'].run(None, {'token_ids': ids})[0])
        state = init_states(model.flow_lm, batch_size=1, sequence_length=1024)
        seq = np.full((1, 1, 32), np.nan, np.float32)
        ts = torch.where(torch.isnan(torch.from_numpy(seq)), model.flow_lm.bos_emb, torch.from_numpy(seq))
        cond = model.flow_lm.backbone(model.flow_lm.input_linear(ts), torch.from_numpy(emb), ts, state)[:, -1]
        values = sessions['flow_lm_main'].run(None, dict(sequence=seq, text_embeddings=emb, **initial(specs)))
        report['components']['main'] = difference(cond.numpy(), values[0])
        report['components']['eos'] = difference(model.flow_lm.out_eos(cond).numpy(), values[1])
        rng = np.random.default_rng(42)
        x = rng.standard_normal((1, 32)).astype(np.float32)
        s, t = np.zeros((1, 1), np.float32), np.ones((1, 1), np.float32)
        py = model.flow_lm.flow_net(cond, torch.from_numpy(s), torch.from_numpy(t), torch.from_numpy(x)).numpy()
        report['components']['flow'] = difference(py, sessions['flow_lm_flow'].run(None, dict(c=cond.numpy(), s=s, t=t, x=x))[0])
        latent = rng.standard_normal((1, 2, 32)).astype(np.float32)
        py = model.mimi.decode_from_latent(torch.from_numpy(latent) * model.flow_lm.emb_std + model.flow_lm.emb_mean, init_states(model.mimi, batch_size=1, sequence_length=9216)).numpy()
        report['components']['decoder'] = difference(py, sessions['mimi_decoder'].run(None, dict(latent=latent, **initial(bundle['mimi_state_manifest'])))[0])
    report['strictCacheParity'] = all(s['strictParity'] for c in report['cacheCases'] for s in c['states'])
    report['strictComponentParity'] = all(c['strictParity'] for c in report['components'].values())
    a.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({k: report[k] for k in ['strictCacheParity', 'strictComponentParity', 'androidImportEnabled']}))
    if not report['strictCacheParity'] or not report['strictComponentParity']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
