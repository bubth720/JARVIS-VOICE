"""Download pinned authorized weights privately with no HF token on the CDN.
Never use this script in public Actions; it handles gated model weights.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
from urllib.parse import urlparse
import requests

REVISION = 'd0ebc5deb50bc6b225f686782f124dd37e2dca0a'
SHA256 = 'dd5fe98f5577ecfd56db9af8057cd05fe20cbc91560042aec8a56cfb01c34995'
SIZE = 219029196
FILE = 'languages/french/model.safetensors'


def download(destination):
    token = os.environ.get('HF_TOKEN')
    if not token:
        raise RuntimeError('binding_absent')
    headers = {'Authorization': 'Bearer ' + token}
    destination.parent.mkdir(parents=True, exist_ok=True)
    with requests.get(f'https://huggingface.co/kyutai/pocket-tts/resolve/{REVISION}/{FILE}',
                      headers=headers, allow_redirects=False, stream=True, timeout=30) as response:
        if response.status_code not in (301, 302, 303, 307, 308):
            raise RuntimeError(f'checkpoint_http_{response.status_code}')
        url = response.headers['Location']
    target = urlparse(url)
    if target.scheme != 'https' or target.hostname != 'us.aws.cdn.hf.co':
        raise RuntimeError('unreviewed_storage_host')
    temporary = destination.with_suffix('.pending')
    digest = hashlib.sha256()
    count = 0
    try:
        # Fresh request without headers, session auth or redirects. Signed URL only.
        with requests.get(url, allow_redirects=False, timeout=(30, 120), stream=True) as response:
            if response.status_code != 200:
                raise RuntimeError(f'storage_http_{response.status_code}')
            with temporary.open('wb') as output:
                for block in response.iter_content(1 << 20):
                    count += len(block)
                    if count > SIZE:
                        raise RuntimeError('unexpected_checkpoint_size')
                    digest.update(block); output.write(block)
        if count != SIZE or digest.hexdigest() != SHA256:
            raise RuntimeError('checkpoint_integrity_mismatch')
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)
    return {'repository': 'kyutai/pocket-tts', 'revision': REVISION,
            'file': FILE, 'bytes': count, 'sha256': digest.hexdigest(),
            'hfAuthorizationForwarded': False, 'redistributionApproved': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--destination', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    try:
        result = download(args.destination)
    except requests.exceptions.ProxyError:
        raise SystemExit('BLOCKED: storage domain denied by proxy; no Authorization forwarded') from None
    except requests.exceptions.RequestException as error:
        raise SystemExit(f'BLOCKED: {type(error).__name__}; remote URL and headers withheld') from None
    except RuntimeError as error:
        raise SystemExit(str(error)) from None
    args.report.write_text(json.dumps(result, indent=2) + '\n')
    print('Pinned official checkpoint downloaded and SHA256 verified privately.')
