"""Public Alba reference -> encoder -> 18 states -> new French text -> audio.

Runs independent FP32 PyTorch/ONNX recurrences with the same noise. This proves
numerical generation parity, not personal resemblance or phone performance.
"""
import argparse
import hashlib
import json
from pathlib import Path
import numpy as np
import torch
import onnxruntime as ort
from scipy.io import wavfile
from scipy.signal import resample_poly
from tokenizers import Tokenizer
from pocket_tts.modules.stateful_module import init_states, increment_steps
from download_official_checkpoint import SHA256, SIZE
from validate_cloning_pack import difference
from matched_model import load_verified_model


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--checkpoint', type=Path, required=True)
    p.add_argument('--reference', type=Path, required=True)
    p.add_argument('--model-dir', type=Path, required=True)
    p.add_argument('--public-pack', type=Path, required=True)
    p.add_argument('--report', type=Path, required=True)
    p.add_argument('--teacher-forced', action='store_true', help='Diagnostic: feed identical official preceding latents and decoder inputs; does not prove independent recurrence')
    a = p.parse_args()
    torch.set_num_threads(1)
    a.report.parent.mkdir(parents=True, exist_ok=True)
    a.report.unlink(missing_ok=True)
    model = load_verified_model(a.checkpoint, a.model_dir)
    rate, wave = wavfile.read(a.reference)
    if wave.dtype.kind == 'i': wave = wave.astype(np.float32) / max(abs(np.iinfo(wave.dtype).min), np.iinfo(wave.dtype).max)
    if wave.ndim == 2: wave = wave.mean(1)
    import math
    divisor = math.gcd(rate, 24000)
    wave = resample_poly(wave, 24000 // divisor, rate // divisor).astype(np.float32)[:240000]
    assert len(wave) >= 120000 and np.isfinite(wave).all()
    bundle = json.loads((a.public_pack / 'bundle.json').read_text())
    options = ort.SessionOptions(); options.intra_op_num_threads = options.inter_op_num_threads = 1; options.log_severity_level = 3
    def session(name): return ort.InferenceSession(str(a.model_dir / name), sess_options=options, providers=['CPUExecutionProvider'])
    encoder = session('mimi_encoder_with_bos.onnx'); lm = session('flow_lm_main_fp32.onnx')
    flow = session('flow_lm_flow_fp32.onnx'); decoder = session('mimi_decoder_fp32.onnx')
    tokenizer = Tokenizer.from_file(str(a.public_pack / 'tokenizer.json'))
    def initial(specs): return {s['input_name']: np.full(s['shape'], int(s['fill'] == 'ones'), dtype={'float32': np.float32, 'int64': np.int64, 'bool': np.bool_}[s['dtype']]) for s in specs}
    def run_lm(sequence, embeddings, state):
        output = lm.run(None, dict(sequence=sequence, text_embeddings=embeddings, **state))
        return output[:2], {s['input_name']: v for s, v in zip(bundle['flow_lm_state_manifest'], output[2:])}
    report = {'checkpointSha256': SHA256, 'mode': 'teacher_forced_diagnostic' if a.teacher_forced else 'independent_recurrence', 'reference': {'repository': 'kyutai/tts-voices', 'revision': '323332d33f997de8394f24a193e1a76df720e01a', 'file': 'alba-mackenna/casual.wav', 'sha256': hashlib.sha256(a.reference.read_bytes()).hexdigest(), 'usedSamples': len(wave)}, 'phrases': [], 'personalVoiceValidated': False, 'androidValidated': False}
    with torch.no_grad():
        prompt = encoder.run(None, {'audio': wave.reshape(1, 1, -1)})[0]
        official_prompt = torch.cat([model.flow_lm.bos_before_voice, model._encode_audio(torch.from_numpy(wave).reshape(1, 1, -1))], 1)
        np.testing.assert_allclose(prompt, official_prompt.numpy(), atol=2e-4, rtol=2e-4)
        for index, phrase in enumerate(['Bonjour, ma voix fonctionne hors ligne.', 'Le téléphone conserve mes fichiers privés.', 'Je peux arrêter la lecture à tout moment.']):
            print(f'Generating public-reference case {index+1}', flush=True)
            py_state = model.get_state_for_audio_prompt(torch.from_numpy(wave).reshape(1, -1))
            model._expand_kv_cache(py_state, sequence_length=1024)
            _, state = run_lm(np.empty((1, 0, 32), np.float32), prompt, initial(bundle['flow_lm_state_manifest']))
            ids = np.array([tokenizer.encode(phrase).ids], np.int64)
            embeddings = model.flow_lm.conditioner(torch.from_numpy(ids))
            model.flow_lm.backbone(model.flow_lm.input_linear(torch.empty(1, 0, 32)), embeddings, torch.empty(1, 0, 32), py_state)
            increment_steps(model.flow_lm, py_state, ids.shape[1])
            _, state = run_lm(np.empty((1, 0, 32), np.float32), embeddings.numpy(), state)
            prev_py = torch.full((1, 1, 32), torch.nan); prev_ort = np.full((1, 1, 32), np.nan, np.float32)
            rng = np.random.default_rng(42)
            py_latents, ort_latents, checks = [], [], []
            eos_py = eos_ort = None
            for step in range(250):
                seq_py = torch.where(torch.isnan(prev_py), model.flow_lm.bos_emb, prev_py)
                c_py = model.flow_lm.backbone(model.flow_lm.input_linear(seq_py), torch.empty(1, 0, 1024), seq_py, py_state)[:, -1]
                increment_steps(model.flow_lm, py_state, 1)
                (c_ort, e_ort), state = run_lm(prev_py.numpy() if a.teacher_forced else prev_ort, np.empty((1, 0, 1024), np.float32), state)
                e_py = model.flow_lm.out_eos(c_py).numpy()
                cond_check = difference(c_py.numpy(), c_ort)
                eos_check = difference(e_py, e_ort)
                if step >= 6 and float(e_py[0, 0]) > -4 and eos_py is None: eos_py = step
                if step >= 6 and float(e_ort[0, 0]) > -4 and eos_ort is None: eos_ort = step
                if eos_py is not None and step >= eos_py + 3: break
                noise = (rng.standard_normal((1, 32)) * np.sqrt(.3)).astype(np.float32)
                s, t = np.zeros((1, 1), np.float32), np.ones((1, 1), np.float32)
                py_direction = model.flow_lm.flow_net(c_py, torch.from_numpy(s), torch.from_numpy(t), torch.from_numpy(noise))
                ort_direction = flow.run(None, dict(c=c_ort, s=s, t=t, x=noise))[0]
                prev_py = (torch.from_numpy(noise) + py_direction).reshape(1, 1, 32)
                prev_ort = (noise + ort_direction).reshape(1, 1, 32)
                latent_check = difference(prev_py.numpy(), prev_ort)
                checks.append({'step': step, 'conditioning': cond_check, 'eos': eos_check, 'latent': latent_check})
                py_latents.append(prev_py.numpy()); ort_latents.append(prev_ort.copy())
            assert eos_py is not None and len(checks) < 250
            py_decoder_state = init_states(model.mimi, 1, 9216); ort_decoder_state = initial(bundle['mimi_state_manifest'])
            pcm_py, pcm_ort = [], []
            for begin in range(0, len(py_latents), 4):
                py_latent = np.concatenate(py_latents[begin:begin+4], 1); ort_latent = np.concatenate(ort_latents[begin:begin+4], 1)
                if a.teacher_forced: ort_latent = py_latent
                audio_py = model.mimi.decode_from_latent(torch.from_numpy(py_latent) * model.flow_lm.emb_std + model.flow_lm.emb_mean, py_decoder_state).numpy()
                increment_steps(model.mimi, py_decoder_state, py_latent.shape[1] * 16)
                output = decoder.run(None, dict(latent=ort_latent, **ort_decoder_state))
                ort_decoder_state = {s['input_name']: v for s, v in zip(bundle['mimi_state_manifest'], output[1:])}
                pcm_py.append(audio_py.ravel()); pcm_ort.append(output[0].ravel())
            py_audio, ort_audio = np.concatenate(pcm_py), np.concatenate(pcm_ort)
            assert np.isfinite(ort_audio).all() and np.max(np.abs(ort_audio)) > .001
            if not a.teacher_forced: wavfile.write(a.model_dir / f'public-reference-synthesis-{index+1}.wav', 24000, ort_audio)
            report['phrases'].append({'text': phrase, 'frames': len(checks), 'eosPyTorch': eos_py, 'eosOnnx': eos_ort, 'audioSeconds': len(ort_audio)/24000, 'audio': difference(py_audio, ort_audio), 'steps': checks})
    report['strictGenerationParity'] = all(c['audio']['strictParity'] and c['eosPyTorch'] == c['eosOnnx'] and all(s[k]['strictParity'] for s in c['steps'] for k in ['conditioning', 'eos', 'latent']) for c in report['phrases'])
    a.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'strictGenerationParity': report['strictGenerationParity'], 'phrases': [{k: c[k] for k in ['text', 'frames', 'eosPyTorch', 'eosOnnx', 'audioSeconds', 'audio']} for c in report['phrases']]}))
    if not report['strictGenerationParity']: raise SystemExit(1)


if __name__ == '__main__': main()
