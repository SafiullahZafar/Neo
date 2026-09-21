# Use your own voice in Neo

## On the phone

Open **Settings > Choose delay and preview voices > My voice: record and clone**.

1. Tap **Record my voice**, allow microphone access, and speak normally for **10?20 seconds**.
   The screen includes text to read. Use a quiet room; do not include music or other speakers.
   Neo stops at 20 seconds. Leaving the screen cancels a recording in progress.
2. Tap **Listen to my recording**. Record again if needed.
3. Confirm this is your own voice and tap **Upload my voice**. Upload is explicit.
4. Enter a short English message and tap **Generate in my voice**.
5. Wait for **ready**, then tap **Listen to generated preview**. Check server status
   after returning to the screen, losing connectivity, or restarting the app.

The recording is a reference for generation, not permanent model training. Results
may resemble your voice but are not guaranteed identical. The introduction says
Neo is an automated assistant. Selecting a voice does not connect SIM/WhatsApp
caller audio. This feature generates in-app previews, not real-time call replies.

## Python server setup

Run commands from the Neo root, using PowerShell:

```powershell
cd D:\development\python-related\Neo
.\.venv\Scripts\python.exe setup_voice.py
.\.venv\Scripts\python.exe main.py
```

The setup script needs Python 3.11 and makes a separate `.venv-voice`. It first
checks a project-local `.voice-runtime` installation, otherwise uses `py -3.11`.
For another installation, pass `--python C:\path\to\python.exe`.
The existing API environment stays on Python 3.14. Public Chatterbox model weights
are downloaded during setup to `models/voice_cache`, requiring several GB of disk
and RAM. CPU generation may take minutes; no real-time performance is claimed.
Phone requests run with model downloads disabled and a five-minute timeout.

Optional server-only `.env` settings (defaults already work on Windows):

```dotenv
NEO_VOICE_PATH=data/my_voice
NEO_VOICE_PYTHON=.venv-voice/Scripts/python.exe
NEO_VOICE_DEVICE=cpu
```

A compatible GPU/runtime can use `cuda`; a GPU has not been assumed or configured.
No paid voice API key is used. The engine uses the original English Chatterbox
model and retains its built-in watermark. Upstream documentation:
https://github.com/resemble-ai/chatterbox

## Storage and deletion

The API is for **one phone owner per paired server/token**, not a multi-user hosted
service. Anyone with the pairing token has access to this server's voice profile.
Use HTTPS when hosting; debug USB HTTP is limited to localhost.

The original sample remains in private app files; delete it with **Delete phone
recording**. Upload stores a reference in `data/my_voice/reference.wav`. Server
voice deletion removes the reference and generated preview; phone deletion is
separate. No original-reference download route is exposed. Only the latest
preview is retained. Replacing the reference invalidates that preview.
Generated previews are cached privately on the phone for playback. The phone
recording/delete action removes this cache too. Android backups are disabled.

Generation, replacement and deletion are serialized: wait for an active job to
finish before replacing/deleting its reference. Use a single API process/worker.
Model files are shared public weights; deleting a personal voice does not delete
those weights. The server does not train, fine-tune or upload your reference.

## Developer checks and limits

Tests cover authorization, consent flag, WAV bounds, quiet/clipped samples,
replacement, deletion, missing engine, and job response/audio contracts. The job
unit test substitutes a fake worker and is **not** an inference/voice-quality test.
Actual recording quality, Android microphone behavior and similarity to the owner
must be checked with their own recording on the device.
