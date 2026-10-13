import threading
import uuid
from concurrent.futures import ThreadPoolExecutor

import pytest
from fastapi.testclient import TestClient

from jarvis_pocket.server import create_app
from jarvis_pocket.storage import Store

TOKEN = "test-only-" + "a" * 40
HEADERS = {"Authorization": "Bearer " + TOKEN}
PROFILE = str(uuid.uuid4())


class FakeEngine:
    def state(self, store, identifier):
        return {"test": identifier}

    def synthesize(self, state, text):
        return b"test-wav", {}


@pytest.fixture
def client(tmp_path):
    return TestClient(create_app(Store(tmp_path / "private"), FakeEngine(), TOKEN))


def test_auth_required(client):
    assert client.get("/health").status_code == 401
    assert (
        client.post("/v1/speech", json={"profile_id": PROFILE, "text": "Bonjour"}).status_code
        == 401
    )


def test_wrong_token(client):
    assert client.get("/health", headers={"Authorization": "Bearer invalid"}).status_code == 401


def test_health_and_private_speech_contract(client):
    assert client.get("/health", headers=HEADERS).json()["device"] == "cpu"
    response = client.post(
        "/v1/speech", headers=HEADERS, json={"profile_id": PROFILE, "text": "Bonjour"}
    )
    assert response.status_code == 200
    assert response.headers["content-type"] == "audio/wav"
    assert response.headers["cache-control"] == "no-store"


def test_request_limits(client):
    response = client.post(
        "/v1/speech", headers=HEADERS, json={"profile_id": PROFILE, "text": "x" * 501}
    )
    assert response.status_code == 422
    response = client.post(
        "/v1/speech", headers=HEADERS, json={"profile_id": "../../other", "text": "Bonjour"}
    )
    assert response.status_code == 400


def test_docs_not_exposed(client):
    assert client.get("/docs").status_code == 404
    assert client.get("/openapi.json").status_code == 404


def test_model_calls_are_serialized(tmp_path):
    started = threading.Event()
    finish = threading.Event()

    class BlockingEngine(FakeEngine):
        def synthesize(self, state, text):
            started.set()
            assert finish.wait(5)
            return super().synthesize(state, text)

    client = TestClient(create_app(Store(tmp_path / "private"), BlockingEngine(), TOKEN))
    body = {"profile_id": PROFILE, "text": "Bonjour"}
    with ThreadPoolExecutor(max_workers=1) as pool:
        first = pool.submit(client.post, "/v1/speech", headers=HEADERS, json=body)
        try:
            assert started.wait(5)
            assert client.post("/v1/speech", headers=HEADERS, json=body).status_code == 429
        finally:
            finish.set()
        assert first.result().status_code == 200


def test_weak_token_refused(tmp_path):
    with pytest.raises(ValueError):
        create_app(Store(tmp_path / "private"), FakeEngine(), "weak")


def test_unicode_wrong_token_is_unauthorized(client):
    # ASGI headers can contain non-ASCII octets; compare them without a TypeError.
    response = client.get("/health", headers={"Authorization": b"Bearer \xc3\xa9"})
    assert response.status_code == 401


def test_blank_text_is_rejected(client):
    response = client.post(
        "/v1/speech", headers=HEADERS, json={"profile_id": PROFILE, "text": "   "}
    )
    assert response.status_code == 400
