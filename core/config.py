"""Server-only configuration. Never log this object's credential fields."""

from dataclasses import dataclass, field
from pathlib import Path
import os

from dotenv import load_dotenv

PROJECT_ROOT = Path(__file__).resolve().parent.parent
load_dotenv(PROJECT_ROOT / ".env", override=False)


def project_path(name: str, default: str) -> Path:
    value = Path(os.getenv(name) or default).expanduser()
    return value if value.is_absolute() else PROJECT_ROOT / value


@dataclass(frozen=True)
class Settings:
    environment: str = field(default_factory=lambda: os.getenv("NEO_ENV", "development"))
    api_host: str = field(default_factory=lambda: os.getenv("NEO_API_HOST", "127.0.0.1"))
    api_port: int = field(default_factory=lambda: int(os.getenv("NEO_API_PORT", "8765")))
    api_token: str = field(default_factory=lambda: os.getenv("NEO_API_TOKEN", ""), repr=False)
    llm_api_key: str = field(default_factory=lambda: os.getenv("NEO_LLM_API_KEY", ""), repr=False)
    telephony_api_key: str = field(default_factory=lambda: os.getenv("NEO_TELEPHONY_API_KEY", ""), repr=False)
    webhook_secret: str = field(default_factory=lambda: os.getenv("NEO_TELEPHONY_WEBHOOK_SECRET", ""), repr=False)
    reports_path: Path = field(default_factory=lambda: project_path("NEO_REPORTS_PATH", "data/mobile_reports.sqlite3"))
    vosk_model_path: Path = field(default_factory=lambda: project_path("NEO_VOSK_MODEL_PATH", "models/vosk_model"))
    piper_model_path: Path = field(default_factory=lambda: project_path("NEO_PIPER_MODEL_PATH", "models/piper_model/en_US-lessac-medium.onnx"))

    voice_path: Path = field(default_factory=lambda: project_path("NEO_VOICE_PATH", "data/my_voice"))
    voice_python: Path = field(default_factory=lambda: project_path("NEO_VOICE_PYTHON", ".venv-voice/Scripts/python.exe" if os.name == "nt" else ".venv-voice/bin/python"))
    voice_device: str = field(default_factory=lambda: os.getenv("NEO_VOICE_DEVICE", "cpu"))

    def __post_init__(self) -> None:
        if not 1 <= self.api_port <= 65535:
            raise ValueError("NEO_API_PORT must be between 1 and 65535")


settings = Settings()
