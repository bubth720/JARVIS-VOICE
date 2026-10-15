"""Multiple authorized public references/phrases/seeds, fully independent recurrences.

Strict numeric tolerances remain 2e-4. Functional checks, acoustic proxies and
offline ASR observations are separate; no personal/perceptive validation claimed.
Run privately: never publish the model pack, reference recordings or credentials.
"""
import argparse
import functools
from pocket_tts.models.flow_lm import lsd_decode
import copy
import hashlib
import json
import math
import resource
import time
from pathlib import Path
import numpy as np
import torch
import onnxruntime as ort
from scipy.io import wavfile
from scipy.signal import resample_poly
from tokenizers import Tokenizer
from pocket_tts.modules.stateful_module import init_states, increment_steps
from matched_model import load_verified_model
from validate_cloning_pack import difference


PHRASES = ['Bonjour, cette nouvelle phrase fonctionne hors ligne.',
           'Je garde ma voix et mes fichiers sur mon téléphone.',
           'Demain, je prendrai le train pour Paris.',
           'Vraiment ? Je pensais arriver plus tôt !']
SEEDS = [0, 42, 123]


def wave24k(path):
    rate, wave = wavfile.read(path)
    if wave.dtype.kind == 'i': wave = wave.astype(np.float32) / max(abs(np.iinfo(wave.dtype).min), np.iinfo(wave.dtype).max)
    if wave.ndim == 2: wave = wave.mean(1)
    divisor = math.gcd(rate, 24000)
    wave = resample_poly(wave, 24000 // divisor, rate // divisor).astype(np.float32)[:240000]
    if not 120000 <= len(wave) <= 360000 or not np.isfinite(wave).all(): raise RuntimeError('Invalid public reference')
    return wave


def acoustic(pcm):
    result = {'finite': bool(np.isfinite(pcm).all()), 'samples': pcm.size, 'seconds': pcm.size/24000,
              'peak': float(np.max(np.abs(pcm))), 'rms': float(np.sqrt(np.mean(pcm**2))),
              'clippedFraction': float(np.mean(np.abs(pcm) >= 1)), 'pitchProxyHz': None}
    pitches = []
    for begin in range(0, len(pcm)-960, 480):
        frame = pcm[begin:begin+960].astype(np.float64)
        if np.sqrt(np.mean(frame**2)) < .015: continue
        frame -= frame.mean(); correlation = np.correlate(frame, frame, 'full')[959:]
        lag = 48 + np.argmax(correlation[48:481])
        if correlation[0] > 0 and correlation[lag]/correlation[0] > .4: pitches.append(24000/lag)
    if pitches: result['pitchProxyHz'] = float(np.median(pitches))
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--checkpoint', type=Path, required=True)
    parser.add_argument('--pack', type=Path, required=True)
    parser.add_argument('--references', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    torch.set_num_threads(1)
    model = load_verified_model(args.checkpoint, args.output_dir)
    b = json.loads((args.pack / 'bundle.json').read_text())
    options = ort.SessionOptions(); options.intra_op_num_threads = options.inter_op_num_threads = 1; options.log_severity_level = 3
    sessions = {name: ort.InferenceSession(str(args.pack / file), sess_options=options, providers=['CPUExecutionProvider']) for name, file in
                {'encoder':'mimi_encoder_with_bos.onnx', 'text':'text_conditioner_fp32.onnx', 'main':'flow_lm_main_fp32.onnx', 'flow':'flow_lm_flow_fp32.onnx', 'decoder':'mimi_decoder_fp32.onnx'}.items()}
    tokenizer = Tokenizer.from_file(str(args.pack / 'tokenizer.json'))
    refs = json.loads(args.references.read_text())
    def initial(specs): return {s['input_name']: np.full(s['shape'], int(s['fill']=='ones'), dtype={'float32':np.float32,'int64':np.int64,'bool':np.bool_}[s['dtype']]) for s in specs}
    def ortmain(sequence, text, state):
        out = sessions['main'].run(None, dict(sequence=sequence, text_embeddings=text, **state))
        return out[:2], {s['input_name']: v for s,v in zip(b['flow_lm_state_manifest'],out[2:])}
    def decode(latents, ort):
        chunks=[]
        state = initial(b['mimi_state_manifest']) if ort else init_states(model.mimi,1,9216)
        for begin in range(0,len(latents),4):
            z=np.concatenate(latents[begin:begin+4],1)
            if ort:
                out=sessions['decoder'].run(None,dict(latent=z,**state)); state={s['input_name']:v for s,v in zip(b['mimi_state_manifest'],out[1:])}; audio=out[0]
            else:
                audio=model.mimi.decode_from_latent(torch.from_numpy(z)*model.flow_lm.emb_std+model.flow_lm.emb_mean,state).numpy(); increment_steps(model.mimi,state,z.shape[1]*16)
            chunks.append(audio.ravel())
        return np.concatenate(chunks)
    def generate(voice, embeddings, seed, ort, steps=1, temperature=.3, threshold=-4., tail=3):
        if ort:
            _, state=ortmain(np.empty((1,0,32),np.float32),voice,initial(b['flow_lm_state_manifest']))
            _, state=ortmain(np.empty((1,0,32),np.float32),embeddings,state)
        else:
            state=copy.deepcopy(voice); model._expand_kv_cache(state,1024)
            seq=torch.empty(1,0,32); model.flow_lm.backbone(model.flow_lm.input_linear(seq),torch.from_numpy(embeddings),seq,state); increment_steps(model.flow_lm,state,embeddings.shape[1])
        rng=np.random.default_rng(seed); previous=np.full((1,1,32),np.nan,np.float32); latents=[]; eos=None
        for step in range(250):
            if ort: (c,e),state=ortmain(previous,np.empty((1,0,1024),np.float32),state)
            else:
                seq=torch.where(torch.isnan(torch.from_numpy(previous)),model.flow_lm.bos_emb,torch.from_numpy(previous))
                ct=model.flow_lm.backbone(model.flow_lm.input_linear(seq),torch.empty(1,0,1024),seq,state)[:,-1]; increment_steps(model.flow_lm,state,1)
                c=ct.numpy(); e=model.flow_lm.out_eos(ct).numpy()
            if step>=6 and float(e[0,0])>threshold and eos is None: eos=step
            if eos is not None and step>=eos+tail: break
            noise=(rng.standard_normal((1,32))*np.sqrt(temperature)).astype(np.float32)
            if ort:
                current=noise.copy()
                for sub in range(steps):
                    s=np.full((1,1),sub/steps,np.float32);t=np.full((1,1),(sub+1)/steps,np.float32)
                    direction=sessions['flow'].run(None,dict(c=c,s=s,t=t,x=current))[0]
                    current=current+direction/np.float32(steps)
            else:
                current=lsd_decode(functools.partial(model.flow_lm.flow_net,torch.from_numpy(c)),torch.from_numpy(noise),steps).numpy()
            previous=current.reshape(1,1,32);latents.append(previous.copy())
        if not latents: raise RuntimeError('No latent audio')
        return decode(latents,ort),np.concatenate(latents,1),eos,len(latents)
    report={'experimental':True,'strictTolerances':{'atol':2e-4,'rtol':2e-4},'references':refs,'seeds':SEEDS,'cases':[],
            'personalRecordingUsed':False,'perceptualValidation':False,'deviceValidation':False,'energyMeasuredOnCloud':False,'sampler':'official lsd_decode vs ONNX variable s/t/current','noiseAlgorithm':'common seeded NumPy PCG64; not claimed bit-identical to Java or torch RNG'}
    voice_outputs={}
    with torch.no_grad():
        for ref in refs:
            path=args.references.parent/(ref['id']+'.wav')
            if hashlib.sha256(path.read_bytes()).hexdigest()!=ref['sha256']:raise RuntimeError('Public reference hash mismatch')
            wave=wave24k(path); py_voice=model.get_state_for_audio_prompt(torch.from_numpy(wave).reshape(1,-1))
            ort_voice=sessions['encoder'].run(None,dict(audio=wave.reshape(1,1,-1)))[0]
            for phrase_index,phrase in enumerate(PHRASES):
                ids=np.array([tokenizer.encode(phrase).ids],np.int64)
                embedding=model.flow_lm.conditioner(torch.from_numpy(ids)).numpy()
                np.testing.assert_array_equal(embedding,sessions['text'].run(None,dict(token_ids=ids))[0])
                settings=[(seed,n,.3,-4.,3) for seed in SEEDS for n in [1,2,3,5]]
                if phrase_index==2:
                    settings += [(42,n,temp,-4.,3) for n in [1,3,5] for temp in [.2,.5]]
                    settings += [(42,3,.3,threshold,tail) for threshold,tail in [(-5.,3),(-3.,3),(-4.,5)]]
                for seed,steps,temperature,threshold,tail in settings:
                    print(f"Public case {ref['id']} phrase={phrase_index} seed={seed} steps={steps} temp={temperature} eos={threshold} tail={tail}",flush=True)
                    begin=time.perf_counter();cpu=time.process_time()
                    py_audio,py_latent,py_eos,py_frames=generate(py_voice,embedding,seed,False,steps,temperature,threshold,tail)
                    before_ort=time.perf_counter();cpu_ort=time.process_time()
                    audio,latent,eos,frames=generate(ort_voice,embedding,seed,True,steps,temperature,threshold,tail)
                    elapsed=time.perf_counter()-before_ort;cpu_elapsed=time.process_time()-cpu_ort
                    name=f"{ref['id']}_p{phrase_index}_s{seed}_n{steps}_t{temperature}_e{threshold}_tail{tail}.wav"
                    wavfile.write(args.output_dir/name,24000,audio)
                    clipped=np.clip(audio,-1,1);pcm16=np.floor((clipped*np.float32(32767)).astype(np.float64)+.5).astype(np.int16).astype(np.float32)/32768
                    error=pcm16-clipped;noise_power=float(np.mean(error.astype(np.float64)**2));power=float(np.mean(clipped.astype(np.float64)**2))
                    spectrum=np.abs(np.fft.rfft(audio))**2;frequency=np.fft.rfftfreq(len(audio),1/24000)
                    case={'reference':ref['id'],'phrase':phrase,'phraseIndex':phrase_index,'seed':seed,'steps':steps,'temperature':temperature,'eosThreshold':threshold,'framesAfterEos':tail,'audioFile':name,
                          'eosPyTorch':py_eos,'eosOnnx':eos,'framesPyTorch':py_frames,'framesOnnx':frames,
                          'functional':{'finite':bool(np.isfinite(audio).all()),'nonSilent':bool(np.max(np.abs(audio))>.001),'eosReached':eos is not None and frames<250},
                          'acoustic':acoustic(audio),'spectralHighFrequencyEnergyFraction':float(spectrum[frequency>=6000].sum()/max(spectrum.sum(),1e-20)),
                          'pcm16Error':{'maxAbs':float(np.max(np.abs(error))),'snrDb':10*math.log10(max(power,1e-30)/max(noise_power,1e-30)),'artificialFilterApplied':False},
                          'strictAudio':difference(py_audio,audio),'strictLatents':difference(py_latent,latent),
                          'timing':{'onnxWallSeconds':elapsed,'onnxCpuSeconds':cpu_elapsed,'onnxRtf':elapsed/max(len(audio)/24000,1e-9),'comparisonWallSeconds':time.perf_counter()-begin,'comparisonCpuSeconds':time.process_time()-cpu}}
                    report['cases'].append(case);voice_outputs[(ref['id'],phrase_index,seed,steps)]=latent.copy()
                    (args.output_dir/'evaluation.json').write_text(json.dumps(report,indent=2)+'\n')
    report['functionalCasesPassed']=sum(all(c['functional'].values()) for c in report['cases'])
    report['strictAudioCasesPassed']=sum(c['strictAudio']['strictParity'] for c in report['cases'])
    report['linuxPeakRssMiB']=resource.getrusage(resource.RUSAGE_SELF).ru_maxrss/1024
    (args.output_dir/'evaluation.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({k:report[k] for k in ['functionalCasesPassed','strictAudioCasesPassed','linuxPeakRssMiB']}))


if __name__=='__main__':main()
