"""Synthetic HTTP responses: no real token, audio, model or remote call."""
import hashlib
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import download_official_checkpoint as downloader

class Response:
    def __init__(self, status, data=b'', location=None):
        self.status_code = status
        self.headers = {'Location': location} if location else {}
        self.data = data
    def __enter__(self): return self
    def __exit__(self, *args): pass
    def iter_content(self, size): yield self.data

class ProtectedDownloadTests(unittest.TestCase):
    def test_signed_storage_request_has_no_authorization_and_exact_digest(self):
        payload = b'synthetic-checkpoint'
        with tempfile.TemporaryDirectory() as root, patch.dict('os.environ', {'HF_TOKEN': 'synthetic-placeholder'}), \
             patch.object(downloader, 'SIZE', len(payload)), patch.object(downloader, 'SHA256', hashlib.sha256(payload).hexdigest()), \
             patch.object(downloader.requests, 'get', side_effect=[Response(302, location='https://us.aws.cdn.hf.co/test?signed=synthetic'), Response(200, payload)]) as get:
            destination = Path(root) / 'model.safetensors'
            report = downloader.download(destination)
            self.assertEqual(payload, destination.read_bytes())
            self.assertEqual('Bearer synthetic-placeholder', get.call_args_list[0].kwargs['headers']['Authorization'])
            self.assertNotIn('headers', get.call_args_list[1].kwargs)
            self.assertFalse(get.call_args_list[1].kwargs['allow_redirects'])
            self.assertFalse(report['hfAuthorizationForwarded'])
    def test_unreviewed_host_is_never_contacted(self):
        with tempfile.TemporaryDirectory() as root, patch.dict('os.environ', {'HF_TOKEN': 'synthetic-placeholder'}), \
             patch.object(downloader.requests, 'get', return_value=Response(302, location='https://unreviewed.invalid/secret')) as get:
            with self.assertRaisesRegex(RuntimeError, 'unreviewed_storage_host'):
                downloader.download(Path(root) / 'model')
            self.assertEqual(1, get.call_count)
    def test_corrupt_download_preserves_previous_and_cleans_temporary(self):
        with tempfile.TemporaryDirectory() as root, patch.dict('os.environ', {'HF_TOKEN': 'synthetic-placeholder'}), \
             patch.object(downloader, 'SIZE', 3), patch.object(downloader, 'SHA256', '0' * 64), \
             patch.object(downloader.requests, 'get', side_effect=[Response(302, location='https://us.aws.cdn.hf.co/test'), Response(200,b'bad')]):
            destination = Path(root) / 'model.safetensors'; destination.write_bytes(b'previous')
            with self.assertRaisesRegex(RuntimeError, 'checkpoint_integrity_mismatch'): downloader.download(destination)
            self.assertEqual(b'previous',destination.read_bytes())
            self.assertFalse(destination.with_suffix('.pending').exists())
    def test_authentication_failure_does_not_contact_storage(self):
        with tempfile.TemporaryDirectory() as root, patch.dict('os.environ', {'HF_TOKEN': 'synthetic-placeholder'}), \
             patch.object(downloader.requests, 'get', return_value=Response(401)) as get:
            with self.assertRaisesRegex(RuntimeError,'checkpoint_http_401'): downloader.download(Path(root) / 'model')
            self.assertEqual(1,get.call_count)

if __name__ == '__main__': unittest.main()
