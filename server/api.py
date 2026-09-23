"""Authenticated local API for the Android preview; no real-call audio transport."""

from contextlib import asynccontextmanager, contextmanager
import json
from pathlib import Path
import secrets
import sqlite3
from typing import Annotated, Literal
from uuid import UUID

from fastapi import Depends, FastAPI, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, ConfigDict, Field

from core.config import Settings, settings
from server.voice import VoiceService, voice_router
from server.errors import install_error_handlers

GREETING = "Hello, this is Neo, an assistant. They are unavailable right now. Please leave a message."


class Report(BaseModel):
    model_config = ConfigDict(extra="forbid")
    id: UUID
    caller: str = Field(min_length=1, max_length=160)
    outcome: str = Field(min_length=1, max_length=1000)
    reply: str = Field(default="", max_length=4000)
    message: str = Field(default="", max_length=4000)
    time: int = Field(ge=0)
    demo: Literal[True] = True


def create_app(config: Settings = settings) -> FastAPI:
    database = config.reports_path
    voice = VoiceService(config)

    @contextmanager
    def connection():
        db = sqlite3.connect(database, timeout=10)
        try:
            with db:
                yield db
        finally:
            db.close()

    @asynccontextmanager
    async def lifespan(app):
        if len(config.api_token) < 32:
            raise RuntimeError("Set NEO_API_TOKEN to at least 32 random characters in .env; run setup_local.py first.")
        Path(database).parent.mkdir(parents=True, exist_ok=True)
        with connection() as db:
            db.execute("CREATE TABLE IF NOT EXISTS reports (id TEXT PRIMARY KEY, time INTEGER NOT NULL, payload TEXT NOT NULL)")
        try:
            yield
        finally:
            voice.close()

    app = FastAPI(title="Neo mobile API", version="0.2.0", lifespan=lifespan,
                  docs_url=None, redoc_url=None, openapi_url=None)
    bearer = HTTPBearer(auto_error=False)
    install_error_handlers(app)

    def authorize(credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)]):
        if credentials is None or not secrets.compare_digest(credentials.credentials.encode(), config.api_token.encode()):
            raise HTTPException(401, "Invalid or missing API token", headers={"WWW-Authenticate": "Bearer"})

    protected = [Depends(authorize)]
    app.include_router(voice_router(voice, protected))

    @app.get("/")
    def index():
        return {"service": "Neo", "status": "running", "health_endpoint": "/health",
                "message": "Pair from the Neo app using this base URL. Private API routes require your pairing token.",
                "mode": "demo"}

    @app.get("/health")
    def health():
        return {"status": "ok", "service": "neo", "mode": "demo", "api_version": 1}

    @app.get("/v1/policy", dependencies=protected)
    def policy():
        return {"answer_delay_ms": 6000, "contacts_only": True, "greeting": GREETING, "real_calls_enabled": False}

    @app.get("/v1/sim/readiness", dependencies=protected)
    def sim_readiness():
        # Report the actual server boundary, not the phone's untested capabilities.
        # Do not import desktop speech_io: that loads models and opens PC audio.
        return {"sim_audio_transport": "NOT_CONNECTED", "conversation": "UNAVAILABLE",
                "vosk_files_present": (config.vosk_model_path / "am" / "final.mdl").is_file(),
                "piper_files_present": config.piper_model_path.is_file(),
                "models_loaded": False, "paid_services_required": False,
                "phone_audio_verification": "REQUIRED_ON_DEVICE"}

    @app.post("/v1/reports", dependencies=protected)
    def save_report(report: Report):
        payload = report.model_dump(mode="json")
        encoded = json.dumps(payload, sort_keys=True)
        with connection() as db:
            db.execute("BEGIN IMMEDIATE")
            existing = db.execute("SELECT payload FROM reports WHERE id = ?", (str(report.id),)).fetchone()
            if existing and existing[0] != encoded:
                raise HTTPException(409, "Report ID already exists with different content")
            if not existing:
                db.execute("INSERT INTO reports VALUES (?, ?, ?)", (str(report.id), report.time, encoded))
        return {"id": str(report.id), "saved": True}

    @app.get("/v1/reports", dependencies=protected)
    def list_reports():
        with connection() as db:
            rows = db.execute("SELECT payload FROM reports ORDER BY time DESC, id DESC LIMIT 100").fetchall()
        return {"reports": [json.loads(row[0]) for row in rows]}

    @app.delete("/v1/reports/{report_id}", dependencies=protected)
    def delete_report(report_id: UUID):
        with connection() as db:
            db.execute("DELETE FROM reports WHERE id = ?", (str(report_id),))
        return {"deleted": str(report_id)}

    return app


app = create_app()
