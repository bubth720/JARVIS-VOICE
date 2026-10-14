"""Safe preflight for the protected HF binding; never logs token, URLs or identities.
Storage URLs are signed; Authorization is sent exclusively to huggingface.co.
Uses requests so cross-domain headers are explicitly absent. huggingface_hub may
also use the binding directly for lightweight files (verified in Mission 005D).
"""
import argparse
import json
import os
from pathlib import Path
from urllib.parse import urlparse
import requests

REVISION = 'd0ebc5deb50bc6b225f686782f124dd37e2dca0a'
BASE = 'https://huggingface.co'


def check():
    token = os.environ.get('HF_TOKEN')
    if not token:
        return {'status': 'binding_absent', 'checks': []}
    session = requests.Session()
    headers = {'Authorization': 'Bearer ' + token}
    checks = []
    for name, path in [
        ('authentication', '/api/whoami-v2'),
        ('metadata', f'/api/models/kyutai/pocket-tts/revision/{REVISION}'),
        ('checkpoint', f'/kyutai/pocket-tts/resolve/{REVISION}/languages/french/model.safetensors'),
    ]:
        try:
            with session.get(BASE + path, headers=headers, allow_redirects=False,
                             timeout=30, stream=True) as response:
                record = {'check': name, 'httpStatus': response.status_code,
                          'errorCode': response.headers.get('X-Error-Code')}
                if name == 'metadata' and response.status_code == 200:
                    metadata = response.json()
                    record.update(revision=metadata.get('sha'), gated=metadata.get('gated'),
                                  license=metadata.get('cardData', {}).get('license'))
                if name == 'checkpoint' and response.is_redirect:
                    location = response.headers['Location']
                    parsed = urlparse(location)
                    record['redirectHost'] = parsed.hostname
                    # Fixed known storage host, HTTPS only. No HF header or session auth.
                    if parsed.scheme == 'https' and parsed.hostname == 'us.aws.cdn.hf.co':
                        try:
                            with session.get(location, headers={'Range': 'bytes=0-15'},
                                             allow_redirects=False, timeout=30, stream=True) as storage:
                                record['storageHttpStatus'] = storage.status_code
                        except requests.exceptions.ProxyError:
                            record['storageFailure'] = 'proxy_destination_denied'
                        except requests.exceptions.RequestException as error:
                            record['storageFailure'] = type(error).__name__
                    else:
                        record['storageFailure'] = 'unreviewed_redirect_host'
                    record['hfAuthorizationForwarded'] = False
                checks.append(record)
        except requests.exceptions.RequestException as error:
            checks.append({'check': name, 'failure': type(error).__name__})
    return {'revision': REVISION, 'protectedBindingPresent': True, 'checks': checks}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    report = check()
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))
