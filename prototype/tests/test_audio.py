import subprocess

import numpy as np
import pytest
from scipy.io import wavfile

from jarvis_pocket.audio import SAMPLE_RATE, decode_mp3, prepare, quality, select_reference
from jarvis_pocket.storage import Store


@pytest.fixture
def mp3(tmp_path):
    seconds = 18
    t = np.arange(seconds * SAMPLE_RATE) / SAMPLE_RATE
    # Synthetic signal tests file handling, not speech or model quality.
    signal = 0.2 * np.sin(2 * np.pi * 220 * t)
    signal[: 4 * SAMPLE_RATE] = 0
    source = tmp_path / "signal.wav"
    wavfile.write(source, SAMPLE_RATE, (signal * 32767).astype(np.int16))
    result = tmp_path / "signal.mp3"
    subprocess.run(
        [
            "ffmpeg",
            "-v",
            "error",
            "-nostdin",
            "-i",
            str(source),
            "-codec:a",
            "libmp3lame",
            str(result),
        ],
        check=True,
    )
    return result


def test_real_mp3_decode_and_extract(mp3, tmp_path):
    store = Store(tmp_path / "private")
    result = prepare(store, mp3, seconds=8)
    assert result["source"]["source_sample_rate"] == SAMPLE_RATE
    assert result["reference"]["duration_seconds"] == 8
    assert result["start_seconds"] >= 4
    rate, samples = wavfile.read(
        store.directory("references", result["reference_id"]) / "reference.wav"
    )
    assert rate == 24000 and samples.ndim == 1 and samples.dtype == np.int16


def test_extension_is_not_a_format_check(tmp_path):
    fake = tmp_path / "fake.mp3"
    fake.write_text("not audio")
    with pytest.raises(ValueError):
        decode_mp3(fake)


def test_wav_renamed_mp3_is_rejected(tmp_path):
    path = tmp_path / "not-really.mp3"
    wavfile.write(path, SAMPLE_RATE, np.zeros(SAMPLE_RATE * 5, dtype=np.int16))
    with pytest.raises(ValueError, match="MP3"):
        decode_mp3(path)


def test_manual_reference(mp3):
    audio, _ = decode_mp3(mp3)
    excerpt, start = select_reference(audio, seconds=5, start=5.25)
    assert start == 5.25 and len(excerpt) == 5 * SAMPLE_RATE


@pytest.mark.parametrize(
    "seconds,start", [(31, None), (2, None), (8, -1), (8, 20), (8, float("nan"))]
)
def test_interval_limits(mp3, seconds, start):
    audio, _ = decode_mp3(mp3)
    with pytest.raises(ValueError):
        select_reference(audio, seconds, start)


def test_silent_reference_is_rejected():
    with pytest.raises(ValueError, match="silent"):
        select_reference(np.zeros(10 * SAMPLE_RATE), 5, None)


def test_heavy_clipping_is_rejected():
    with pytest.raises(ValueError, match="clipped"):
        select_reference(np.ones(10 * SAMPLE_RATE), 5, 0)


def test_quality_flags_are_not_speaker_recognition():
    report = quality(np.full(5 * SAMPLE_RATE, 0.1))
    assert "speaker count" in report["limitations"]
    assert report["clipped_sample_fraction"] == 0
