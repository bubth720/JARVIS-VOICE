"""Shared, fail-closed loader for private validation exports."""
import hashlib
import importlib.metadata
import tempfile
from pathlib import Path
import pocket_tts
import yaml
from pocket_tts import TTSModel
from download_official_checkpoint import SHA256, SIZE, REVISION, FILE


def load_verified_model(checkpoint: Path, temporary_directory: Path):
    if importlib.metadata.version('pocket-tts') != '3.3.0':
        raise RuntimeError('pocket-tts==3.3.0 required')
    if not checkpoint.is_file() or checkpoint.stat().st_size != SIZE:
        raise RuntimeError('exact_official_checkpoint_missing_or_wrong_size')
    with checkpoint.open('rb') as source:
        if hashlib.file_digest(source, 'sha256').hexdigest() != SHA256:
            raise RuntimeError('official_checkpoint_sha256_mismatch')
    config = yaml.safe_load((Path(pocket_tts.__file__).parent / 'config/french.yaml').read_text())
    if config['weights_path'] != f'hf://kyutai/pocket-tts/{FILE}@{REVISION}' or config['flow_lm']['transformer']['num_layers'] != 6:
        raise RuntimeError('official_french_config_mismatch')
    config['weights_path'] = str(checkpoint.resolve())
    config['weights_path_without_voice_cloning'] = None
    with tempfile.NamedTemporaryFile(mode='w', suffix='.yaml', prefix='private-verified-', dir=temporary_directory, delete=False) as output:
        output.write(yaml.safe_dump(config))
        temporary = Path(output.name)
    try:
        model = TTSModel.load_model(config=temporary).eval()
    finally:
        temporary.unlink(missing_ok=True)
    if not model.has_voice_cloning or not model.flow_lm.insert_bos_before_voice or len(model.flow_lm.transformer.layers) != 6:
        raise RuntimeError('full_french_checkpoint_with_bos_required')
    return model
