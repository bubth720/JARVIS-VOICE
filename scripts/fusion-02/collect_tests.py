"""Collect actual XML results, never infer emulator/device success from JVM."""
import argparse,json,subprocess,xml.etree.ElementTree as ET,hashlib
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--output',required=True);a=p.parse_args()
modules=['voiceFusion02','voiceFinalSdk','voiceFinalRuntime','voice04Runtime','voiceSdk','studioRuntime','expressionRuntime','identityRuntime','pocketRuntime','voiceBridgeContract','voiceBridgeHarness','app','voiceBridgePilot']
rows=[]
for module in modules:
 files=sorted(Path(module,'build/test-results').rglob('TEST-*.xml'))
 assert files,module
 count={k:0 for k in ('tests','failures','errors','skipped')}
 suites=[]
 for f in files:
  root=ET.parse(f).getroot()
  for k in count: count[k]+=int(root.get(k,0))
  suites.append({'file':str(f),'sha256':hashlib.sha256(f.read_bytes()).hexdigest(),'tests':int(root.get('tests',0))})
 assert count['tests']>0 and count['failures']==count['errors']==0,(module,count)
 rows.append({'module':module,**count,'suites':suites})
new=next(r for r in rows if r['module']=='voiceFusion02')
assert new['tests']>=17 and new['skipped']<=1,new
result={'platform':'Linux JVM/Robolectric; not Android instrumentation','gitHeadAtRun':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),'dirtyWorktree':bool(subprocess.check_output(['git','status','--porcelain'])),'modules':rows,'totals':{k:sum(r[k] for r in rows) for k in ('tests','failures','errors','skipped')},'emulatorResult':'NOT_INFERRED','deviceResult':'NOT_RUN'}
Path(a.output).parent.mkdir(parents=True,exist_ok=True);Path(a.output).write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({'totals':result['totals'],'modules':[{k:v for k,v in r.items() if k!='suites'} for r in rows]},indent=2))
