"""Private, component-only study. Never edits a pack or produces an Android-compatible bundle."""
import argparse,hashlib,json,time
from pathlib import Path
import numpy as np
import onnx
import onnxruntime as ort
from onnxruntime.quantization import quantize_dynamic,QuantType

def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()
def quantiles(values):
    v=sorted(values)
    return {'n':len(v),'p50':v[int(np.ceil(len(v)*.5))-1],'p95':v[int(np.ceil(len(v)*.95))-1],'method':'nearest-rank'}
def main():
    p=argparse.ArgumentParser();p.add_argument('--source',required=True,type=Path);p.add_argument('--output',required=True,type=Path);p.add_argument('--report',required=True,type=Path);a=p.parse_args()
    source=a.source.resolve();output=a.output.resolve();a.report=a.report.resolve();assert source.name=='flow_lm_flow_fp32.onnx'
    assert not output.is_relative_to(source.parent) and not a.report.is_relative_to(source.parent),'Never write into the qualified pack'
    assert output!=a.report and output.suffix=='.onnx' and a.report.suffix=='.json'
    assert not output.exists() or not output.samefile(source)
    assert not a.report.exists() or not a.report.samefile(source)
    original_sha=sha(source)
    lock=Path(__file__).resolve().parents[2]/'pocketRuntime/src/main/resources/experimental-fp32-lock.json'
    assert original_sha==json.loads(lock.read_text())['files'][source.name]['sha256'],'Source must match the existing qualified model lock'
    output.parent.mkdir(parents=True,exist_ok=True);started=time.perf_counter()
    quantize_dynamic(str(source),str(output),weight_type=QuantType.QInt8,op_types_to_quantize=['MatMul','Gemm'])
    conversion_ms=(time.perf_counter()-started)*1000
    opts=ort.SessionOptions();opts.intra_op_num_threads=1;opts.inter_op_num_threads=1;opts.execution_mode=ort.ExecutionMode.ORT_SEQUENTIAL
    reference=ort.InferenceSession(str(source),sess_options=opts,providers=['CPUExecutionProvider'])
    candidate=ort.InferenceSession(str(output),sess_options=opts,providers=['CPUExecutionProvider'])
    assert {i.name for i in reference.get_inputs()}=={'c','s','t','x'}
    rng=np.random.default_rng(42);schedule=[(0.,1.),(0.,.5),(.5,1.),(0.,1/3),(1/3,2/3),(2/3,1.)]+[(i/5,(i+1)/5) for i in range(5)]
    maximum=0.;relative=[];exact=True;times={'FP32':[],'DYNAMIC_INT8_COMPONENT':[]}
    for iteration in range(44):
        s,t=schedule[iteration%len(schedule)]
        inputs={'c':rng.standard_normal((1,1024),dtype=np.float32),'x':rng.standard_normal((1,32),dtype=np.float32),
                's':np.array([[s]],dtype=np.float32),'t':np.array([[t]],dtype=np.float32)}
        results={}
        order=[('FP32',reference),('DYNAMIC_INT8_COMPONENT',candidate)]
        if iteration%2: order.reverse()
        for name,session in order:
            before=time.perf_counter();value=session.run(None,inputs)[0];elapsed=(time.perf_counter()-before)*1000
            assert value.shape==(1,32) and np.isfinite(value).all()
            results[name]=value
            if iteration>=4: times[name].append(elapsed)
        x=results['FP32'].astype(np.float64);y=results['DYNAMIC_INT8_COMPONENT'].astype(np.float64)
        maximum=max(maximum,float(np.max(np.abs(x-y))));relative.append(float(np.linalg.norm(x-y)/max(np.linalg.norm(x),1e-12)));exact=exact and np.array_equal(x,y)
    assert sha(source)==original_sha,'Original pack must remain unchanged'
    graph=onnx.load(str(output),load_external_data=False)
    report={'schema':'voice-final01.private-component-quantization/1','sourceSha256':original_sha,'sourceBytes':source.stat().st_size,
        'candidateBytes':output.stat().st_size,'conversionMs':conversion_ms,'ortVersion':ort.__version__,'threads':1,'provider':'CPUExecutionProvider',
        'syntheticCases':44,'warmupCases':4,'schedule':'LSD1/2/3/5 interval samples; synthetic Gaussian conditioning/noise, not a real voice',
        'component':'flow_lm_flow_fp32 / flow_dir only','maxAbsError':maximum,'relativeL2':quantiles(relative),'bitExact':bool(exact),
        'directionRunMs':{name:quantiles(v) for name,v in times.items()},'quantizedMatmulNodes':sum(n.op_type=='MatMulInteger' for n in graph.graph.node),
        'originalUnchanged':True,'androidPackProduced':False,'weightsPublished':False,'humanListening':False,'fullGenerationTested':False,
        'phoneticOrIdentityQualityMeasured':False,'decision':'NOT_QUALIFIED_FOR_INTEGRATION','acceptanceThreshold':'None introduced; component differences do not validate acoustic quality'}
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))
if __name__=='__main__': main()
