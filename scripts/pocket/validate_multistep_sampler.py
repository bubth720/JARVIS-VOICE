"""Private official LSD vs unchanged ONNX validation. No model export or personal reference."""
import argparse,functools,hashlib,inspect,json,time
from pathlib import Path
import numpy as np
import torch
import onnxruntime as ort
from pocket_tts.models.flow_lm import lsd_decode
from matched_model import load_verified_model
from validate_cloning_pack import difference


def main():
 p=argparse.ArgumentParser();p.add_argument('--checkpoint',type=Path,required=True);p.add_argument('--pack',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
 torch.set_num_threads(1);m=load_verified_model(a.checkpoint,a.out);assert m.flow_lm.flow_type=='lsd'
 opts=ort.SessionOptions();opts.intra_op_num_threads=opts.inter_op_num_threads=1;opts.log_severity_level=3
 session=ort.InferenceSession(str(a.pack/'flow_lm_flow_fp32.onnx'),sess_options=opts,providers=['CPUExecutionProvider'])
 r={'upstreamVersion':'3.3.0','flowType':m.flow_lm.flow_type,'sourceSha256':hashlib.sha256(inspect.getsource(lsd_decode).encode()).hexdigest(),'checkpointSha256':hashlib.file_digest(a.checkpoint.open('rb'),'sha256').hexdigest(),'strictTolerance':{'atol':2e-4,'rtol':2e-4},'cases':[],'personalRecordingUsed':False};fixtures=[]
 with torch.no_grad():
  for cs in [7,19]:
   c=np.random.default_rng(cs).standard_normal((1,1024)).astype(np.float32)
   for seed in [0,42,123]:
    for temp in [.2,.3,.5]:
     noise=(np.random.default_rng(seed).standard_normal((1,32))*np.sqrt(temp)).astype(np.float32)
     for steps in [1,2,3,5]:
      trace=[]
      def field(s,t,x):
       d=m.flow_lm.flow_net(torch.from_numpy(c),s,t,x)
       onnx=session.run(None,{'c':c,'s':s.numpy(),'t':t.numpy(),'x':x.numpy()})[0]
       trace.append({'s':float(s[0,0]),'t':float(t[0,0]),'current':x.numpy().ravel().tolist(),'direction':d.numpy().ravel().tolist(),'directionParity':difference(d.numpy(),onnx)})
       return d
      expected=lsd_decode(field,torch.from_numpy(noise),steps).numpy()
      current=noise.copy()
      for i in range(steps):
       s=np.full((1,1),i/steps,np.float32);t=np.full((1,1),(i+1)/steps,np.float32)
       d=session.run(None,{'c':c,'s':s,'t':t,'x':current})[0];current=current+d/np.float32(steps)
      row={'conditioningSeed':cs,'seed':seed,'temperature':temp,'steps':steps,'final':difference(expected,current),'directions':[x['directionParity'] for x in trace]};r['cases'].append(row)
      if cs==7 and seed==42 and temp==.3:fixtures.append({'steps':steps,'noise':noise.ravel().tolist(),'trace':[{k:v for k,v in x.items() if k!='directionParity'} for x in trace],'expected':expected.ravel().tolist()})
 r['passed']=all(x['final']['strictParity'] and all(y['strictParity'] for y in x['directions']) for x in r['cases']);r['casesPassed']=sum(x['final']['strictParity'] for x in r['cases'])
 (a.out/'sampler-parity.json').write_text(json.dumps(r,indent=2)+'\n');(a.out/'official-lsd-fixture.json').write_text(json.dumps({'syntheticInput':True,'source':'Kyutai lsd_decode / exact French checkpoint','cases':fixtures},indent=2)+'\n')
 print(json.dumps({'passed':r['passed'],'cases':len(r['cases']),'maxAbs':max(x['final']['maxAbs'] for x in r['cases'])}),flush=True)
 if not r['passed']:raise SystemExit('Strict sampler parity failed')

if __name__=='__main__':main()
