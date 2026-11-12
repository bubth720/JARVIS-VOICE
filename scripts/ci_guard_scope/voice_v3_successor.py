#!/usr/bin/env python3
"""VOICE-00 successor guard for the Expressive V3 branch.

Never edits or relaxes a historical guard. It:
1. proves the historical guards are unchanged and still PASS, byte-for-byte,
   on their own frozen mission commits (detached worktrees);
2. enforces the V3 invariant on HEAD: every path protected by either
   historical guard is identical to the V3 base 642d9c1, except an exact,
   hash-pinned list of files legitimately added/changed by V3.

Usage: voice_v3_successor.py [--sdk] [--studio]   (default: both)
"""
import hashlib, json, subprocess, sys, tempfile
from pathlib import Path

SDK_GUARD = 'scripts/voice_sdk/check_style_preservation.py'
SDK_COMMIT = '3ffb28011506abcc94b5d4ee71d6c79fc278333b'          # VOICE-02 commit that introduced SDK_GUARD
STUDIO_COMMIT = 'cce9a4549c35e721ee37daff014d427ef3656b2a'   # 005J tip
STUDIO_STEP = 'Verify preservation of functional applications and model bindings'
V3_BASE = '642d9c19bcba35e5ffe686403d50c65aa9dc992a'      # M50 tip, V3 fork point
SDK_PROTECTED = ['app', 'pocketRuntime', 'identityRuntime', 'studioRuntime', 'voiceBridgeContract',
    'voiceBridgeHarness', 'voiceBridgePilot', 'voiceBridgePilotClient', 'deliveries',
    'voiceSdk/src/main/kotlin/com/jarvis/voice/sdk/SpeechSession.kt',
    'voiceSdk/src/main/kotlin/com/jarvis/voice/sdk/VaultQualitySpeechEngine.kt',
    'voiceSdk/src/main/kotlin/com/jarvis/voice/sdk/PcmFrameQueue.kt',
    'voiceStudio/src/main/AndroidManifest.xml', 'voiceStudio/build.gradle.kts',
    'voiceStudio/src/main/java/com/jarvis/voice/voicestudio/PcmPlayer.kt',
    'voiceStudio/src/main/java/com/jarvis/voice/voicestudio/DeviceMetrics.kt',
    'voiceStudio/src/main/java/com/jarvis/voice/voicestudio/OperationMetrics.kt',
    'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'expressionRuntime']
STUDIO_PROTECTED = ['app', 'pocketProbe', 'voicePrototype', 'voiceQuality', 'voiceIdentity', 'voiceExpression',
    'pocketRuntime', 'identityRuntime', 'expressionRuntime', 'deliveries/mission005f',
    'deliveries/mission005g', 'deliveries/mission005h', 'deliveries/mission005i']
# Exact V3 changes inside protected paths (ad27149..92d0745), pinned by git blob id.
V3_ALLOWED = {
    'studioRuntime/src/main/kotlin/com/jarvis/voice/studio/QuickReactionTiming.kt': '89dafc49297336c76392956683b578dc2312df83',
    'studioRuntime/src/test/kotlin/com/jarvis/voice/studio/QuickReactionTimingTest.kt': '148eb49cf79c50cbd907232352db90fd0f89363e',
    'voiceStudio/build.gradle.kts': '1c613c126c17a407a9b756fe3c569631d722e9e2',
}

def git(root, *args, **kw):
    return subprocess.run(['git', *args], cwd=root, check=True, capture_output=True, text=True, **kw).stdout

def blob(path):
    data = path.read_bytes()
    return hashlib.sha1(b'blob %d\0' % len(data) + data).hexdigest()

def studio_command(root):
    """Extract the original 005J guard command from its own commit, verbatim."""
    lines = git(root, 'show', f'{STUDIO_COMMIT}:.github/workflows/voice-studio.yml').splitlines()
    for i, line in enumerate(lines):
        if line.strip() == f'- name: {STUDIO_STEP}':
            run = lines[i + 1].strip()
            assert run.startswith('run: git diff --exit-code '), run
            return run[len('run: '):]
    raise AssertionError('historical 005J guard step not found')

def frozen(root, commit, argv):
    with tempfile.TemporaryDirectory() as tmp:
        wt = Path(tmp) / 'frozen'
        git(root, 'worktree', 'add', '--detach', str(wt), commit)
        try:
            r = subprocess.run(argv, cwd=wt, capture_output=True, text=True)
        finally:
            git(root, 'worktree', 'remove', '--force', str(wt))
    assert r.returncode == 0, f'historical guard FAILED on its own commit {commit}: {r.stderr[-400:]}'
    return r.stdout.strip()

def main():
    root = Path(git(Path.cwd(), 'rev-parse', '--show-toplevel').strip())
    modes = [a for a in sys.argv[1:] if a in ('--sdk', '--studio')] or ['--sdk', '--studio']
    for c in (SDK_COMMIT, STUDIO_COMMIT, V3_BASE):
        subprocess.run(['git', 'merge-base', '--is-ancestor', c, 'HEAD'], cwd=root, check=True)
    report = {'mission': 'VOICE-00', 'head': git(root, 'rev-parse', 'HEAD').strip(), 'v3Base': V3_BASE}
    protected = []
    if '--sdk' in modes:
        assert (root / SDK_GUARD).read_bytes() == subprocess.run(['git', 'show', f'{SDK_COMMIT}:{SDK_GUARD}'],
            cwd=root, check=True, capture_output=True).stdout, 'historical SDK guard modified'
        report['sdkHistoricalGuard'] = {'commit': git(root, 'rev-parse', SDK_COMMIT).strip(),
            'script': SDK_GUARD, 'unchanged': True, 'frozenRun': 'PASS',
            'output': frozen(root, SDK_COMMIT, [sys.executable, SDK_GUARD])}
        protected += SDK_PROTECTED
    if '--studio' in modes:
        cmd = studio_command(root)
        frozen(root, STUDIO_COMMIT, ['bash', '-ec', cmd])
        report['studioHistoricalGuard'] = {'commit': STUDIO_COMMIT, 'command': cmd, 'frozenRun': 'PASS'}
        protected += STUDIO_PROTECTED
    protected = sorted(set(protected))
    changed = set(git(root, 'diff', '--name-only', V3_BASE, '--', *protected).split())
    changed |= set(git(root, 'ls-files', '--others', '--exclude-standard', '--', *protected).split())
    allowed = {p: h for p, h in V3_ALLOWED.items() if any(p == q or p.startswith(q + '/') for q in protected)}
    unexpected = sorted(changed - set(allowed))
    assert not unexpected, 'protected path changed beyond V3 allowlist: ' + ', '.join(unexpected)
    for p, h in allowed.items():
        assert (root / p).exists() and blob(root / p) == h, 'V3 pinned file drifted: ' + p
    report['v3Invariant'] = {'protectedPaths': len(protected), 'allowedV3Changes': allowed, 'result': 'PASS'}
    out = root / 'build' / 'ci-guard-scope'
    out.mkdir(parents=True, exist_ok=True)
    (out / ('voice-v3-successor' + ''.join(m.replace('--', '-') for m in modes) + '.json')).write_text(json.dumps(report, indent=2) + '\n')
    print('PASS VOICE-00 successor', ' '.join(modes))

if __name__ == '__main__':
    main()
