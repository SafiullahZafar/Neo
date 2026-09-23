"""Stable, sanitized API errors; never echo submitted values or exception text."""
import logging
from uuid import uuid4

from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException
from starlette.responses import JSONResponse

# Only explicitly reviewed server-authored text can be returned as a diagnosis.
SAFE_DETAILS = frozenset({
    "Record mono 16-bit PCM WAV at 16 kHz using Neo.",
    "Record between 10 and 20 seconds.",
    "Recording is incomplete. Record again.",
    "Recording is too quiet. Move closer and try again.",
    "Recording is distorted. Move farther from the microphone.",
    "Invalid WAV recording.",
    "Record and upload your own voice first",
    "Confirm that this is your own voice before uploading",
    "A preview is already generating; wait for it to finish",
    "Preview is generating; retry deletion when it finishes",
    "Wait for the current preview before replacing your voice",
    "Report ID already exists with different content",
    "Preview expired or server restarted; generate another",
    "Preview is not ready",
    "Voice engine is not installed. Run setup_voice.py on the Python server.",
})


def install_error_handlers(app):
    actions = {
        401: ("Pairing rejected", "Pair again using the token from your local .env."),
        403: ("Access denied", "Check pairing and permission for this operation."),
        404: ("Resource not found", "Check the endpoint and matching app/server versions; refresh expired previews."),
        409: ("Operation conflicts with current state", "Refresh status before retrying; keep any unsynced local reports."),
        413: ("Upload too large", "Record a new 10–20 second voice sample in Neo."),
        415: ("Unsupported audio format", "Upload mono 16 kHz PCM16 WAV from Neo's recorder."),
        422: ("Input validation failed", "Check required fields, own-voice consent, sample quality and duration."),
        429: ("Too many requests", "Wait before retrying."),
        503: ("Service unavailable", "Check Python and voice engine setup, then retry."),
    }

    def response(status, headers=None, code=None, detail=None):
        reference = uuid4().hex
        title, action = actions.get(status, ("Request could not complete", "Check the Python terminal with this error reference and retry."))
        if isinstance(detail, str) and detail in SAFE_DETAILS:
            title = detail
        return JSONResponse(status_code=status, headers={**(headers or {}), "Cache-Control": "no-store"},
                            content={"detail": title, "error": {"code": code or f"API_HTTP_{status}",
                                     "message": title, "action": action, "reference": reference}})

    @app.exception_handler(HTTPException)
    async def http_error(request, error):
        return response(error.status_code, error.headers, detail=error.detail)

    @app.exception_handler(RequestValidationError)
    async def validation_error(request, error):
        return response(422)

    @app.exception_handler(Exception)
    async def unexpected_error(request, error):
        result = response(500, code="API_INTERNAL")
        # Neither request URL/body nor exception message belongs in diagnostics.
        import json
        reference = json.loads(result.body)["error"]["reference"]
        logging.getLogger("neo.errors").error("API_INTERNAL reference=%s exception_type=%s", reference, type(error).__name__)
        return result
