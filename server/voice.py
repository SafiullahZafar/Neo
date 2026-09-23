"""Single-owner local voice enrollment and bounded asynchronous preview generation."""
from array import array
from concurrent.futures import ThreadPoolExecutor
from io import BytesIO
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
from uuid import uuid4, UUID
import wave

from fastapi import APIRouter, HTTPException, Request, Response
from pydantic import BaseModel, Field, ConfigDict
from core.config import PROJECT_ROOT

MAX_REFERENCE_BYTES = 700_000
MAX_OUTPUT_BYTES = 8_000_000


def validate_reference(data: bytes) -> float:
    try:
        with wave.open(BytesIO(data), "rb") as audio:
            if (audio.getnchannels(), audio.getsampwidth(), audio.getframerate(), audio.getcomptype()) != (1, 2, 16000, "NONE"):
                raise ValueError("Record mono 16-bit PCM WAV at 16 kHz using Neo.")
            frames = audio.getnframes()
            duration = frames / 16000
            if not 10 <= duration <= 20:
                raise ValueError("Record between 10 and 20 seconds.")
            raw = audio.readframes(frames)
            if len(raw) != frames * 2:
                raise ValueError("Recording is incomplete. Record again.")
        samples = array("h", raw)
        if sys.byteorder != "little":
            samples.byteswap()
        # Basic signal checks, not a claim to detect identity or speech.
        if sum(value * value for value in samples) / len(samples) < 100 ** 2:
            raise ValueError("Recording is too quiet. Move closer and try again.")
        if sum(abs(value) >= 32700 for value in samples) / len(samples) > 0.02:
            raise ValueError("Recording is distorted. Move farther from the microphone.")
        return duration
    except (wave.Error, EOFError) as error:
        raise ValueError("Invalid WAV recording.") from error


class PreviewText(BaseModel):
    model_config = ConfigDict(extra="forbid")
    text: str = Field(min_length=1, max_length=240)


class VoiceService:
    def __init__(self, config):
        self.config = config
        self.root = Path(config.voice_path)
        self.lock = threading.RLock()
        self.pool = ThreadPoolExecutor(max_workers=1, thread_name_prefix="neo-voice")
        self.job = None
        self.closed = False

    @property
    def busy(self):
        return self.job is not None and self.job["state"] == "generating"

    def status(self):
        with self.lock:
            duration = None
            try:
                with wave.open(str(self.root / "reference.wav"), "rb") as wav:
                    duration = wav.getnframes() / wav.getframerate()
            except (OSError, wave.Error, EOFError):
                pass
            return {"reference_saved": duration is not None, "seconds": duration,
                    "engine_configured": Path(self.config.voice_python).is_file(),
                    "engine_note": "Model readiness is checked during generation. Run setup_voice.py on the Python server first.",
                    "job": dict(self.job) if self.job else None, "live_call_audio": False}

    def save(self, data):
        if len(data) > MAX_REFERENCE_BYTES:
            raise HTTPException(413, "Recording exceeds 20 seconds or upload size limit")
        try:
            duration = validate_reference(data)
        except ValueError as error:
            raise HTTPException(422, str(error)) from error
        with self.lock:
            if self.busy:
                raise HTTPException(409, "Wait for the current preview before replacing your voice")
            self.root.mkdir(parents=True, exist_ok=True)
            temp = self.root / "reference.tmp"
            temp.write_bytes(data)
            os.replace(temp, self.root / "reference.wav")
            (self.root / "preview.wav").unlink(missing_ok=True)
            self.job = None
        return {"saved": True, "seconds": duration}

    def delete(self):
        with self.lock:
            if self.busy:
                raise HTTPException(409, "Preview is generating; retry deletion when it finishes")
            for name in ("reference.wav", "reference.tmp", "preview.wav"):
                (self.root / name).unlink(missing_ok=True)
            self.job = None
        return {"deleted": True}

    def start(self, text):
        with self.lock:
            if self.busy or self.closed:
                raise HTTPException(409, "A preview is already generating; wait for it to finish")
            if not (self.root / "reference.wav").is_file():
                raise HTTPException(409, "Record and upload your own voice first")
            if not Path(self.config.voice_python).is_file():
                raise HTTPException(503, "Voice engine is not installed. Run setup_voice.py on the Python server.")
            if not text.strip():
                raise HTTPException(422, "Enter preview text")
            self.job = {"id": str(uuid4()), "state": "generating", "message": "Generating locally; this may take several minutes."}
            (self.root / "preview.wav").unlink(missing_ok=True)
            self.pool.submit(self._generate, self.job["id"], text.strip())
            return dict(self.job)

    def _generate(self, job_id, text):
        state, message = "failed", "[VOICE_WORKER_FAILED] The worker failed or produced no valid output. Next: check voice setup and try a short preview; no substitute voice was used."
        try:
            with tempfile.TemporaryDirectory(prefix="job-", dir=self.root) as directory:
                output = Path(directory) / "preview.wav"
                payload = {"reference": str((self.root / "reference.wav").resolve()), "output": str(output.resolve()),
                           "text": "Hello, I'm Neo, an automated assistant. " + text,
                           "device": self.config.voice_device}
                env = os.environ.copy()
                env["HF_HOME"] = str(PROJECT_ROOT / "models" / "voice_cache")
                env["HF_HUB_OFFLINE"] = "1"  # Downloads happen explicitly during setup, never from a phone request.
                env["HF_HUB_DISABLE_TELEMETRY"] = "1"
                result = subprocess.run([str(self.config.voice_python), str(PROJECT_ROOT / "voice_worker.py")],
                    input=json.dumps(payload), text=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                    timeout=300, env=env, cwd=PROJECT_ROOT,
                    creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
                if result.returncode == 0 and output.is_file() and 44 < output.stat().st_size <= MAX_OUTPUT_BYTES:
                    with wave.open(str(output), "rb") as wav:
                        if wav.getnchannels() != 1 or wav.getsampwidth() != 2 or wav.getnframes() == 0:
                            raise ValueError("Invalid generated audio")
                    os.replace(output, self.root / "preview.wav")
                    state, message = "ready", "Generated preview is ready. Voice similarity must be checked by listening."
        except subprocess.TimeoutExpired:
            message = "[VOICE_TIMEOUT] Generation exceeded five minutes. Next: try shorter text or configure a supported GPU on the server."
        except FileNotFoundError:
            message = "[VOICE_FILE_MISSING] A required engine or audio file was missing. Next: run setup_voice.py, check the reference sample, and retry."
        except (OSError, wave.Error, ValueError):
            message = "[VOICE_FILE_FAILED] An audio file could not be read, validated or saved. Next: check free storage and voice setup, then regenerate the preview."
        except Exception:
            message = "[VOICE_UNKNOWN] Preview generation failed for an unknown reason. Next: restart the server and retry a short preview; no substitute voice was used."
        with self.lock:
            if self.job and self.job["id"] == job_id:
                self.job.update(state=state, message=message)

    def job_status(self, job_id):
        with self.lock:
            if not self.job or self.job["id"] != str(job_id):
                raise HTTPException(404, "Preview expired or server restarted; generate another")
            return dict(self.job)

    def audio(self, job_id):
        with self.lock:
            if self.job_status(job_id)["state"] != "ready":
                raise HTTPException(409, "Preview is not ready")
            path = self.root / "preview.wav"
            if not path.is_file() or path.stat().st_size > MAX_OUTPUT_BYTES:
                raise HTTPException(404, "Preview unavailable")
            return path.read_bytes()

    def close(self):
        with self.lock:
            self.closed = True
        self.pool.shutdown(wait=True, cancel_futures=True)


def voice_router(service, protected):
    router = APIRouter(prefix="/v1/voice", dependencies=protected)

    @router.get("")
    def status():
        return service.status()

    @router.put("/reference")
    async def upload(request: Request):
        if request.headers.get("X-Neo-Voice-Consent") != "own-voice":
            raise HTTPException(422, "Confirm that this is your own voice before uploading")
        if request.headers.get("content-type", "").split(";")[0] not in ("audio/wav", "audio/x-wav"):
            raise HTTPException(415, "Send a WAV recording")
        data = bytearray()
        async for chunk in request.stream():
            if len(data) + len(chunk) > MAX_REFERENCE_BYTES:
                raise HTTPException(413, "Recording too large")
            data.extend(chunk)
        return service.save(bytes(data))

    @router.delete("/reference")
    def delete():
        return service.delete()

    @router.post("/preview", status_code=202)
    def generate(body: PreviewText):
        return service.start(body.text)

    @router.get("/jobs/{job_id}")
    def job(job_id: UUID):
        return service.job_status(job_id)

    @router.get("/jobs/{job_id}/audio")
    def audio(job_id: UUID):
        return Response(service.audio(job_id), media_type="audio/wav", headers={"Cache-Control": "no-store"})

    return router
