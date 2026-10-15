"""Build the private 005E pack from hash-verified official exports only.

No network, login, personal recording or INT8 state. Public Alba is hash-pinned.
By default requires the exact manifest embedded in the APK. --lock-output is
for developers rebuilding the APK, not for bypassing verification on a phone.
"""
import argparse
import hashlib
import json
import math
import shutil
import struct
import tempfile
import zipfile
from pathlib import Path
import numpy as np
import onnx
import torch
from scipy.io import wavfile
from scipy.signal import resample_poly
from matched_model import load_verified_model
from download_official_checkpoint import SHA256

ROOT=Path(__file__).resolve().parents[2]
REFERENCE_SHA='46264e83cb99115c3d210260e029117566d9c64f20266d10daa78107759ede3e'
TOKENIZER_SHA='6211acffe9cb95172f71f0051f18e26ea305b2b32ea00b331ee976551c70887f'


def spec(path):
    with path.open('rb') as source: digest=hashlib.file_digest(source,'sha256').hexdigest()
    return {'sizeBytes':path.stat().st_size,'sha256':digest}


def main():
    p=argparse.ArgumentParser()
    for name in ['checkpoint','exports','tokenizer','bundle','reference','work-dir','output']:
        p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--lock-output',type=Path)
    a=p.parse_args(); a.work_dir.mkdir(parents=True,exist_ok=True)
    if spec(a.reference)['sha256']!=REFERENCE_SHA or spec(a.tokenizer)['sha256']!=TOKENIZER_SHA:raise RuntimeError('Pinned public source mismatch')
    proofs={name:json.loads((a.exports/name).read_text()) for name in ['encoder-validation.json','flow-validation.json','decoder-validation.json','fp32-pack-validation.json']}
    if len(proofs['encoder-validation.json']['syntheticParity'])!=9 or not proofs['flow-validation.json']['strictParity'] or not proofs['decoder-validation.json']['strictParity'] or not proofs['fp32-pack-validation.json']['strictCacheParity'] or not proofs['fp32-pack-validation.json']['strictComponentParity']:
        raise RuntimeError('Required component validations missing')
    expected={'mimi_encoder_with_bos.onnx':proofs['encoder-validation.json']['encoderSha256'],
              'flow_lm_main_fp32.onnx':proofs['flow-validation.json']['modelSha256'],
              'flow_lm_flow_fp32.onnx':proofs['decoder-validation.json']['models']['flow_lm_flow'],
              'mimi_decoder_fp32.onnx':proofs['decoder-validation.json']['models']['mimi_decoder']}
    torch.set_num_threads(1)
    with tempfile.TemporaryDirectory(prefix='private-fp32-',dir=a.work_dir) as temporary:
        root=Path(temporary); model=load_verified_model(a.checkpoint,root)
        torch.onnx.export(model.flow_lm.conditioner,(torch.zeros(1,3,dtype=torch.long),),str(root/'text_conditioner_fp32.onnx'),opset_version=17,dynamo=False,input_names=['token_ids'],output_names=['text_embeddings'],dynamic_axes={'token_ids':{1:'tokens'},'text_embeddings':{1:'tokens'}})
        for name,sha in expected.items():
            if spec(a.exports/name)['sha256']!=sha:raise RuntimeError('Export hash differs from validation')
            shutil.copyfile(a.exports/name,root/name)
        shutil.copyfile(a.tokenizer,root/'tokenizer.json')
        bundle=json.loads(a.bundle.read_text());bundle.pop('predefined_voices',None)
        bundle.update(experimental_schema=1,precision='float32',checkpoint_sha256=SHA256)
        (root/'bundle.json').write_text(json.dumps(bundle,indent=2)+'\n')
        identity=hashlib.sha256(json.dumps({f.name:spec(f) for f in root.iterdir() if f.is_file()},sort_keys=True,separators=(',',':')).encode()).hexdigest()
        (root/'pack-identity.txt').write_text(identity+'\n')
        rate,wave=wavfile.read(a.reference)
        if wave.dtype.kind=='i':wave=wave.astype(np.float32)/max(abs(np.iinfo(wave.dtype).min),np.iinfo(wave.dtype).max)
        if wave.ndim==2:wave=wave.mean(1)
        divisor=math.gcd(rate,24000);wave=resample_poly(wave,24000//divisor,rate//divisor).astype(np.float32)[:240000]
        with torch.no_grad():states=model.get_state_for_audio_prompt(torch.from_numpy(wave).reshape(1,-1))
        def string(value):
            data=value.encode();return struct.pack('<H',len(data))+data
        payload=b'JVFP1'+struct.pack('<I',1)+string(identity)+struct.pack('<H',18)
        for module,state in states.items():
            for key in ['offset','pad','cache']:
                tensor=state[key].numpy();kind=0 if key=='cache' else 1;data=tensor.astype('<f4' if kind==0 else '<i8').tobytes()
                payload+=string(module+'/'+key)+struct.pack('<BB',kind,tensor.ndim)+struct.pack('<'+'I'*tensor.ndim,*tensor.shape)+struct.pack('<I',len(data))+data
        (root/'public-alba-fp32.bin').write_bytes(payload)
        shutil.copyfile(ROOT/'scripts/pocket/EXPERIMENTAL_MODEL_LICENSE.txt',root/'MODEL_LICENSE.txt')
        for path in root.glob('*.onnx'):
            graph=onnx.load(str(path))
            if any(n.op_type in ['MatMulInteger','DynamicQuantizeLinear','QLinearMatMul'] for n in graph.graph.node):raise RuntimeError('Quantized graph forbidden')
        lock={'schemaVersion':1,'experimental':True,'precision':'float32','packId':identity,'checkpointSha256':SHA256,'strictIndependentParity':False,'files':{f.name:spec(f) for f in root.iterdir() if f.is_file()}}
        if a.lock_output:a.lock_output.write_text(json.dumps(lock,indent=2)+'\n')
        else:
            pinned=json.loads((ROOT/'pocketRuntime/src/main/resources/experimental-fp32-lock.json').read_text())
            if lock!=pinned:raise RuntimeError('Pack differs from APK manifest: use the pinned export toolchain or rebuild the matching APK')
        a.output.parent.mkdir(parents=True,exist_ok=True)
        pending=a.output.with_suffix('.pending')
        try:
            with zipfile.ZipFile(pending,'w',zipfile.ZIP_DEFLATED,compresslevel=1) as zip:
                for file in root.iterdir():zip.write(file,file.name)
            pending.replace(a.output)
        finally:pending.unlink(missing_ok=True)
    print('Pinned coherent FP32 pack built privately. Never upload this ZIP to public Actions.')


if __name__=='__main__':main()
