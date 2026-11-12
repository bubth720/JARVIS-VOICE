#!/usr/bin/env python3
"""Negative tests: the VOICE-00 successor guard must reject every protected mutation."""
import subprocess, sys, tempfile, unittest
from pathlib import Path

ROOT = Path(subprocess.check_output(['git', 'rev-parse', '--show-toplevel'], text=True).strip())
GUARD = ROOT / 'scripts/ci_guard_scope/voice_v3_successor.py'

class SuccessorGuardTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.wt = Path(self.tmp.name) / 'wt'
        subprocess.run(['git', 'worktree', 'add', '--detach', str(self.wt), 'HEAD'], cwd=ROOT, check=True, capture_output=True)

    def tearDown(self):
        subprocess.run(['git', 'worktree', 'remove', '--force', str(self.wt)], cwd=ROOT, check=True)
        self.tmp.cleanup()

    def run_guard(self, *modes):
        return subprocess.run([sys.executable, str(GUARD), *modes], cwd=self.wt, capture_output=True, text=True)

    def assert_rejected(self, rel, *modes, append=b'\n// tamper\n'):
        p = self.wt / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        with open(p, 'ab') as f:
            f.write(append)
        r = self.run_guard(*modes)
        self.assertNotEqual(r.returncode, 0, rel)
        return r.stderr

    def test_clean_head_passes(self):
        r = self.run_guard()
        self.assertEqual(r.returncode, 0, r.stderr)

    def test_historical_app_file_mutation_rejected(self):
        self.assertIn('beyond V3 allowlist', self.assert_rejected('app/build.gradle.kts'))

    def test_studio_only_path_mutation_rejected(self):
        self.assertIn('beyond V3 allowlist', self.assert_rejected('voiceIdentity/build.gradle.kts', '--studio'))

    def test_pinned_v3_file_drift_rejected(self):
        self.assertIn('drifted', self.assert_rejected('voiceStudio/build.gradle.kts', '--sdk'))

    def test_untracked_file_in_protected_path_rejected(self):
        self.assertIn('beyond V3 allowlist', self.assert_rejected('pocketRuntime/Injected.kt', '--sdk'))

    def test_historical_guard_script_mutation_rejected(self):
        self.assertIn('historical SDK guard modified',
                      self.assert_rejected('scripts/voice_sdk/check_style_preservation.py', '--sdk', append=b'\n# weakened\n'))

if __name__ == '__main__':
    unittest.main(verbosity=2)
