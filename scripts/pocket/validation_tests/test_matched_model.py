"""Loader must reject invalid checkpoints before any remote model loading."""
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import matched_model


class MatchedModelTests(unittest.TestCase):
    def test_missing_or_wrong_size_never_loads_a_fallback(self):
        with tempfile.TemporaryDirectory() as root, patch.object(matched_model.TTSModel, 'load_model') as load:
            with self.assertRaisesRegex(RuntimeError, 'missing_or_wrong_size'):
                matched_model.load_verified_model(Path(root) / 'absent', Path(root))
            load.assert_not_called()

    def test_wrong_digest_never_loads_a_fallback(self):
        with tempfile.TemporaryDirectory() as root, patch.object(matched_model, 'SIZE', 3), patch.object(matched_model.TTSModel, 'load_model') as load:
            checkpoint = Path(root) / 'synthetic'; checkpoint.write_bytes(b'bad')
            with self.assertRaisesRegex(RuntimeError, 'sha256_mismatch'):
                matched_model.load_verified_model(checkpoint, Path(root))
            load.assert_not_called()


if __name__ == '__main__': unittest.main()
