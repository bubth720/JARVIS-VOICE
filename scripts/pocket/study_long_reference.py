"""Study only: repeat an authorized public reference to test existing encoder/18 states, no new profile format."""
import argparse,json,copy
from pathlib import Path
import numpy as np,torch,onnxruntime as ort
from matched_model import load_verified_model
from pocket_tts.modules.stateful_module import init_states
from evaluate_experimental_fp32 import wave24k
from validate_cloning_pack import difference
p=argparse.ArgumentParser();p.add_argument('--checkpoint',type=Path,required=True);p.add_argument('--pack',type=Path,required=True);p.add_argument('--reference',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();a.out.parent.mkdir(parents=True,exist_ok=True)
torch.set_num_threads(1);model=load_verified_model(a.checkpoint,a.out.parent)
opts=ort.SessionOptions();opts.intra_op_num_threads=opts.inter_op_num_threads=1;opts.log_severity_level=3
enc=ort.InferenceSession(str(a.pack/'mimi_encoder_with_bos.onnx'),sess_options=opts,providers=['CPUExecutionProvider']);main=ort.InferenceSession(str(a.pack/'flow_lm_main_fp32.onnx'),sess_options=opts,providers=['CPUExecutionProvider']);bundle=json.loads((a.pack/'bundle.json').read_text());specs=bundle['flow_lm_state_manifest'];wave=wave24k(a.reference)
r={'personalRecordingUsed':False,'publicReferenceRepeatedForNumericStudy':True,'profileFormatLongerThan15sEnabled':False,'cases':[]}
with torch.no_grad():
 for seconds in [15,20,30]:
  pcm=np.tile(wave,int(np.ceil(seconds*24000/len(wave))))[:seconds*24000]
  py=model.get_state_for_audio_prompt(torch.from_numpy(pcm).reshape(1,-1));prompt=enc.run(None,{'audio':pcm.reshape(1,1,-1)})[0]
  state={s['input_name']:np.full(s['shape'],int(s['fill']=='ones'),dtype={'float32':np.float32,'int64':np.int64,'bool':np.bool_}[s['dtype']]) for s in specs}
  out=main.run(None,dict(sequence=np.empty((1,0,32),np.float32),text_embeddings=prompt,**state));checks=[]
  for s,v in zip(specs,out[2:]):
   expected=py[s['module']][s['key']].numpy()
   if s['key']=='cache':v=v[:,:,:prompt.shape[1]]
   checks.append(difference(expected,v))
  r['cases'].append({'seconds':seconds,'framesWithBos':prompt.shape[1],'all18StatesStrictParity':all(x['strictParity'] for x in checks),'states':checks})
a.out.write_text(json.dumps(r,indent=2)+'\n');print([(x['seconds'],x['framesWithBos'],x['all18StatesStrictParity']) for x in r['cases']])
