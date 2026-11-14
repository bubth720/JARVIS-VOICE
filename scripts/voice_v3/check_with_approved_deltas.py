#!/usr/bin/env python3
"""Historical VOICE guard with EXACT approved deltas (owner approval 14/11/2026, AUDIT-GLOBAL-01 A-003).
Same base and protected paths as the original guard; every changed path must be listed in approved_deltas.json
with the same status and the same git blob. Anything else (new change, other content) fails like before."""
import json, pathlib, subprocess, sys
root = pathlib.Path(__file__).resolve().parents[2]
spec = json.loads((pathlib.Path(__file__).parent / 'approved_deltas.json').read_text())['guards'][sys.argv[1]]
git = lambda *a: subprocess.check_output(['git', *a], cwd=root, text=True)
approved = {e['path']: e for e in spec['approved']}
bad = []
for line in git('diff', '--name-status', spec['base'], '--', *spec['paths']).splitlines():
    status, path = line.split('\t', 1)
    e = approved.get(path)
    blob = git('rev-parse', f'HEAD:{path}').strip() if status != 'D' else None
    if e is None or e['status'] != status or e['blob'] != blob:
        bad.append(f'{status} {path}')
if bad:
    sys.exit('UNAPPROVED CHANGE to protected paths since ' + spec['base'][:7] + ': ' + ', '.join(bad))
print(json.dumps({'guard': sys.argv[1], 'base': spec['base'], 'approved_deltas_verified': len(approved), 'other_protected_paths_unchanged': True}))
