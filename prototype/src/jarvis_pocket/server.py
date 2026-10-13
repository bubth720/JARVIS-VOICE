import secrets
import threading

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel, Field

from jarvis_pocket.storage import Store, validate_id


class SpeechBody(BaseModel):
    profile_id: str
    text: str = Field(min_length=1, max_length=500)


def create_app(store: Store, engine, token: str) -> FastAPI:
    if len(token) < 32:
        raise ValueError("Server token must contain at least 32 characters")
    app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)
    lock = threading.Lock()

    def authenticated(authorization: str | None = Header(default=None)):
        expected = "Bearer " + token
        if authorization is None or not secrets.compare_digest(
            authorization.encode("utf-8"), expected.encode("utf-8")
        ):
            raise HTTPException(401, "Authentication required")

    @app.get("/health", dependencies=[Depends(authenticated)])
    def health():
        return {"status": "ready", "language": "french", "device": "cpu"}

    @app.post("/v1/speech", dependencies=[Depends(authenticated)])
    def speech(body: SpeechBody):
        if not lock.acquire(blocking=False):
            raise HTTPException(429, "Speech engine busy; retry later")
        try:
            if not body.text.strip():
                raise ValueError("Empty text")
            validate_id(body.profile_id)
            state = engine.state(store, body.profile_id)
            audio, _ = engine.synthesize(state, body.text)
            return Response(audio, media_type="audio/wav", headers={"Cache-Control": "no-store"})
        except FileNotFoundError:
            raise HTTPException(404, "Private profile not found") from None
        except ValueError:
            raise HTTPException(400, "Invalid profile or speech request") from None
        finally:
            lock.release()

    return app
