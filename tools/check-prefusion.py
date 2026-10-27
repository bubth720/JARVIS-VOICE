"""PRE-FUSION lot 1: exact source freeze, historical VOICE FUSION-02 checked separately.

No Android qualification, dependency replacement or receipt rewriting occurs here.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
MISSION = '697110fa5a8dc87c4ed2a7e8df81a2e644a9559b'
HISTORICAL = '95f873226a6b80e2517266a943dfbead94432b86'
WORKFLOW = '.github/workflows/fusion-02-voice.yml'
ADDITIONS = {
    'tools/check-prefusion.py',
    'tests/prefusion/test_preservation.py',
    'docs/prefusion/ASSESSMENT.md',
}


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def verify_files(root, frozen, actual, additions, replacements):
    """Reject deletion, symlinks, changed baseline bytes and undeclared additions."""
    extra = set(actual) - set(frozen) - additions
    if extra:
        raise ValueError('Undeclared files: ' + ', '.join(sorted(extra)))
    for name in set(actual) | set(frozen):
        path = root / name
        if path.is_symlink() or not path.is_file():
            raise ValueError('Missing/non-regular file: ' + name)
        if name in frozen and path.read_bytes() != replacements.get(name, frozen[name]):
            raise ValueError('Frozen source changed: ' + name)


def main():
    subprocess.run(['git', 'merge-base', '--is-ancestor', MISSION, 'HEAD'], cwd=ROOT, check=True)
    # Respect the repository's reviewed checkout attributes (notably .bat CRLF).
    frozen = {p: git('cat-file', '--filters', MISSION + ':' + p) for p in
              git('ls-tree', '-r', '--name-only', MISSION).decode().splitlines()}
    original = frozen[WORKFLOW].decode()
    replacement = original.replace("branches: ['feat/fusion-02-voice-timeline']", "branches: ['feat/fusion-02-voice-timeline', 'codex/prefusion-20261026-lot1']")
    replacement = replacement.replace("branches: ['feat/voice-final-01']", "branches: ['feat/voice-final-01', 'mission/prefusion-gates-20261026']")
    replacement = replacement.replace("paths: ['voiceFusion02/**'", "paths: ['docs/prefusion/**', 'tools/check-prefusion.py', 'tests/prefusion/**', 'voiceFusion02/**'")
    replacement = replacement.replace('python3 scripts/fusion-02/check_scope.py', 'python3 tools/check-prefusion.py')
    replacement = replacement.replace('      - run: python3 tools/check-prefusion.py\n', '      - run: python3 tools/check-prefusion.py\n      - run: python3 -m unittest discover -s tests/prefusion -v\n')
    replacement = replacement.replace('docs/fusion-02/evidence/CI-JVM.json', 'build/prefusion/CI-JVM.json')
    replacement = replacement.replace('name: fusion02-voice-', 'name: prefusion-voice-')
    actual = git('ls-files', '--cached', '--others', '--exclude-standard').decode().splitlines()
    verify_files(ROOT, frozen, actual, ADDITIONS, {WORKFLOW: replacement.encode()})
    # The old guard keeps its original scope and HEAD semantics. Check it at its
    # own exact SHA in a disposable local clone, never against a rewritten receipt.
    with tempfile.TemporaryDirectory(prefix='voice-preservation-') as temp:
        subprocess.run(['git', 'clone', '--quiet', '--shared', '--no-checkout', str(ROOT), temp], check=True)
        subprocess.run(['git', 'checkout', '--quiet', '--detach', HISTORICAL], cwd=temp, check=True)
        subprocess.run(['python3', 'scripts/fusion-02/check_scope.py'], cwd=temp, check=True)
    print('PASS PRE-FUSION source freeze at mission ' + MISSION)
    print('PASS historical VOICE FUSION-02 only at ' + HISTORICAL + '; native authority remains BLOCKED')


if __name__ == '__main__':
    main()
