from array import array
from io import BytesIO
import json
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch
from types import SimpleNamespace
import wave
from fastapi.testclient import TestClient
from core.config import Settings
from server.api import create_app
from server.voice import validate_reference


def wav(seconds=10, amplitude=1500):
    output = BytesIO()
    with wave.open(output, "wb") as audio:
        audio.setnchannels(1); audio.setsampwidth(2); audio.setframerate(16000)
        audio.writeframes(array("h", [amplitude, -amplitude] * (8000 * seconds)).tobytes())
    return output.getvalue()


class VoiceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        root = Path(self.temp.name)
        self.config = Settings(api_token="voice-test-token-" * 3, reports_path=root / "reports.sqlite3",
                               voice_path=root / "voice", voice_python=root / "missing.exe")
        self.client = TestClient(create_app(self.config)); self.client.__enter__()
        self.headers = {"Authorization": f"Bearer {self.config.api_token}"}
        self.upload_headers = {**self.headers, "Content-Type": "audio/wav", "X-Neo-Voice-Consent": "own-voice"}

    def tearDown(self):
        self.client.__exit__(None, None, None); self.temp.cleanup()

    def upload(self, content=None):
        return self.client.put("/v1/voice/reference", headers=self.upload_headers, content=content or wav())

    def test_voice_endpoints_require_auth(self):
        for method, path in [("GET", "/v1/voice"), ("PUT", "/v1/voice/reference"),
                             ("DELETE", "/v1/voice/reference"), ("POST", "/v1/voice/preview"),
                             ("GET", "/v1/voice/jobs/00000000-0000-0000-0000-000000000000/audio")]:
            self.assertEqual(self.client.request(method, path).status_code, 401)

    def test_consent_format_duration_and_size_checked(self):
        self.assertEqual(self.client.put("/v1/voice/reference", headers=self.headers, content=wav()).status_code, 422)
        self.assertEqual(self.upload(b"bad").status_code, 422)
        self.assertEqual(self.upload(wav(9)).status_code, 422)
        self.assertEqual(self.upload(wav(21)).status_code, 422)
        self.assertEqual(self.upload(b"x" * 700001).status_code, 413)
        self.assertEqual(self.upload(wav(10, 0)).status_code, 422)
        self.assertEqual(self.upload(wav(10, 32767)).status_code, 422)
        self.assertFalse((self.config.voice_path / "reference.wav").exists())

    def test_saved_reference_survives_invalid_replacement_and_can_be_deleted(self):
        self.assertEqual(self.upload().status_code, 200)
        self.assertEqual(self.upload(b"bad").status_code, 422)
        self.assertEqual(validate_reference((self.config.voice_path / "reference.wav").read_bytes()), 10)
        status = self.client.get("/v1/voice", headers=self.headers).json()
        self.assertTrue(status["reference_saved"]); self.assertFalse(status["engine_configured"])
        self.assertNotIn(str(self.config.voice_path), str(status))
        self.assertEqual(self.client.delete("/v1/voice/reference", headers=self.headers).status_code, 200)
        self.assertFalse((self.config.voice_path / "reference.wav").exists())

    def test_missing_engine_never_reports_cloned_voice_ready(self):
        self.upload()
        result = self.client.post("/v1/voice/preview", headers=self.headers, json={"text": "A preview"})
        self.assertEqual(result.status_code, 503)
        self.assertEqual(self.client.get("/v1/voice", headers=self.headers).json()["job"], None)

    def test_job_audio_and_delete_with_injected_worker_not_real_model(self):
        self.upload(); self.config.voice_python.touch()
        def worker(*args, **kwargs):
            payload = json.loads(kwargs["input"])
            self.assertTrue(payload["text"].startswith("Hello, I'm Neo, an automated assistant."))
            self.assertEqual(kwargs["env"]["HF_HUB_OFFLINE"], "1")
            Path(payload["output"]).write_bytes(wav(1))
            return SimpleNamespace(returncode=0)
        with patch("server.voice.subprocess.run", side_effect=worker):
            result = self.client.post("/v1/voice/preview", headers=self.headers, json={"text": "My test"})
            self.assertEqual(result.status_code, 202)
            job_id = result.json()["id"]
            for _ in range(100):
                status = self.client.get(f"/v1/voice/jobs/{job_id}", headers=self.headers).json()
                if status["state"] != "generating": break
                time.sleep(0.01)
            self.assertEqual(status["state"], "ready")
            audio = self.client.get(f"/v1/voice/jobs/{job_id}/audio", headers=self.headers)
            self.assertEqual(audio.content, wav(1)); self.assertEqual(audio.headers["cache-control"], "no-store")
            self.client.delete("/v1/voice/reference", headers=self.headers)
            self.assertFalse((self.config.voice_path / "preview.wav").exists())
            self.assertEqual(self.client.get(f"/v1/voice/jobs/{job_id}", headers=self.headers).status_code, 404)
