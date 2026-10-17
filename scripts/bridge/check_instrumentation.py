#!/usr/bin/env python3
"""am instrument can exit zero even for failed tests. Require every status success and exact count."""
import argparse,json,re
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('log');p.add_argument('--expected',required=True,type=int);p.add_argument('--skipped',type=int,default=0);a=p.parse_args()
s=Path(a.log).read_text()
statuses=[int(x) for x in re.findall(r'^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$',s,re.M)]
success=statuses.count(0)
fail=[x for x in statuses if x not in (0,1,2,-3,-4)]
assert not fail,(fail,s[-6000:])
skipped=sum(statuses.count(x) for x in [-3,-4])
assert skipped==a.skipped,(skipped,a.skipped,s[-6000:])
assert success+skipped==a.expected,(success,a.expected,s[-6000:])
assert re.search(r'^OK \('+str(a.expected)+r' tests?\)',s,re.M),s[-6000:]
assert 'INSTRUMENTATION_FAILED' not in s and 'FAILURES!!!' not in s and 'Process crashed' not in s
print(json.dumps({'tests_passed':success,'failed':0,'skipped':skipped}))
