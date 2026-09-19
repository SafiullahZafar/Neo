# Run Neo locally

Python lives in the Neo root. All Android files live in `_ktlion/_app`.

## Python

The project uses a private `.venv` (Python 3.14). From the Neo root:

```powershell
# Only needed when recreating the environment:
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements-lock.txt
.venv\Scripts\python.exe setup_local.py

# Start the mobile API:
.venv\Scripts\python.exe main.py
```

The service listens on `http://127.0.0.1:8765` by default. Keep it running during
phone tests. Do not launch a second copy on the same port. `GET /health` reports
readiness without exposing secrets.

`main.py` now starts the API without Vosk, Piper, a camera, or desktop audio
drivers. The old experiment is preserved in `desktop_main.py`, selectable with
`main.py --desktop`; its optional packages/models and simulation limitations
still apply.

## Phone over USB

1. Enable USB debugging on the Oppo, connect it, and accept its debugging prompt.
2. Run `_ktlion\_app\connect-phone.ps1`. It requires one authorized device,
   installs the APK, forwards port 8765, and opens Neo.
3. In Home, tap **Connect to Python**. Keep `http://127.0.0.1:8765` as the URL.
   Copy `NEO_API_TOKEN` from the private root `.env` (without surrounding quotes)
   into the pairing field.
4. Connect. The app fetches Python's contact-only, six-second policy and greeting.
5. Run a contact test and save a message. Its status should become **Saved to Python**.
6. Stop Python and run another test. The report remains on the phone. Restart
   Python and tap **Sync pending reports** to upload it once.

`adb reverse` forwards phone localhost to the computer. Repeat after USB
disconnects/reboots. No LAN firewall change is needed. See the official
[Android local-server guide](https://developer.android.com/develop/ui/views/layout/webapps/access-local-server).

## What is connected

- `GET /v1/policy`: authenticated greeting and call rules.
- `POST /v1/reports`: validated demo reports with stable UUIDs.
- `GET /v1/reports`: latest 100 server reports.
- `DELETE /v1/reports/{id}`: delete one server copy.

Report endpoints require the pairing token. Upload retries do not create
duplicates. Changing the paired server marks local reports for re-upload.
Completed calls upload automatically when paired; manual sync retries failures.
The app caches Python's greeting for offline practice. This is upload sync,
not server-to-phone restoration. The phone keeps up to 100 reports. The server
stores copies in `data/mobile_reports.sqlite3` until explicitly deleted.
Clearing local reports also clears pending uploads, but not existing server copies.

All calls are **demonstrations**. Real SIM audio, AI conversations and automatic phone-call answering remain
unconnected. Optional contacts access now selects a practice caller; it does not
monitor incoming calls. No external API
subscription is required for this local milestone.

## Verify

```powershell
.venv\Scripts\python.exe -m unittest discover -s tests -v
# Requires Python running and JAVA_HOME / ANDROID_HOME configured:
.venv\Scripts\python.exe verify_android.py
```

The second command builds the APK and tests its actual Kotlin HTTP transport
against Python. The token is passed through a child process environment, never
command-line arguments. The test deletes its own report afterward. Ordinary
Gradle tests skip the integration test unless its test URL/token environment
variables are set. Physical-phone testing remains separate.

APK: `_ktlion/_app/app/build/outputs/apk/debug/app-debug.apk`.

## Optional permissions (0.3)

Open **Settings** to allow/manage Contacts, Camera and Microphone individually.
Android's system dialog controls the grant. Use **Open Android app permissions**
to deny or re-enable an existing grant. The demo still works when access is denied.

Try **Choose a phone contact** in Test, **Check presence now** in Settings, or
**Dictate a practice message** after the greeting. These run locally on screen;
real SIM answering is still not enabled. Voice recognition may use the phone's
online recognition service if on-device recognition is unavailable.

For tomorrow's phone checks: test denial before allowance, revoke permissions
and return, test the pause switch, and verify takeover and timeout behavior.

## Notifications (0.4)

Install the new APK before testing. Open Settings > Allow notifications and
approve Android's prompt (Android 13+), then use Send a test notification.
Keep the Reports category enabled for that test. Separate switches control
practice-call events, reports, and sync results. Tap a notification to return to
its relevant screen. Opening other apps still ends an active practice call.
These notifications do not monitor your SIM or receive server push while Neo
is closed; they describe local demo and sync events only.
