# Python configuration

`.env` is private and Git-ignored; `.env.example` is the shareable template.
Run `.venv/Scripts/python.exe setup_local.py` to create a random pairing token.
The script preserves existing tokens and never prints them.

- `NEO_API_HOST`: localhost (`127.0.0.1`) for USB tests.
- `NEO_API_PORT`: `8765` by default.
- `NEO_API_TOKEN`: at least 32 random characters; authenticates the local API.
  Paste this into the phone pairing dialog. Never paste provider API keys there.
- `NEO_REPORTS_PATH`: defaults to `data/mobile_reports.sqlite3`.
- Vosk/Piper model paths apply only to the optional legacy desktop experiment.
- LLM/telephony keys are reserved; no external provider is connected yet.

Existing environment variables override `.env`. Relative paths resolve from the
Neo root. Credential fields are excluded from the Settings representation.
The phone encrypts its pairing token with an Android Keystore key. No token is
compiled into the APK. Release builds require HTTPS. The debug build permits
HTTP only on localhost and the emulator host alias for local testing.

Ignore rules exclude private configuration, runtime data, models and `.venv`.
They do not untrack files committed before the rules were added. See
[RUN_LOCAL.md](RUN_LOCAL.md) for setup, running, and testing.
