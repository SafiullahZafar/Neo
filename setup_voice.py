"""Install a local, separate voice engine; explicitly download its public model weights."""
import argparse
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--python", help="Path to Python 3.11; otherwise Windows py -3.11 or python3.11")
    args = parser.parse_args()
    managed = sorted((ROOT / ".voice-runtime").glob("cpython-3.11*-windows-x86_64-none/python.exe"))
    command = [args.python] if args.python else ([str(managed[-1])] if managed else
        (["py", "-3.11"] if os.name == "nt" else ["python3.11"]))
    try:
        version = subprocess.check_output([*command, "-c", "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')"], text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        print("Python 3.11 is required for the isolated voice engine. Install Python 3.11, then rerun this script. Your API environment is unchanged.")
        return 1
    if version != "3.11":
        print("Select Python 3.11 using --python. The API can keep using Python 3.14.")
        return 1
    envdir = ROOT / ".venv-voice"
    subprocess.run([*command, "-m", "venv", str(envdir)], check=True)
    python = envdir / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
    subprocess.run([str(python), "-m", "pip", "install", "-r", str(ROOT / "requirements-voice.txt")], check=True)
    env = os.environ.copy()
    env["HF_HOME"] = str(ROOT / "models" / "voice_cache")
    env["HF_HUB_DISABLE_TELEMETRY"] = "1"
    env.pop("HF_HUB_OFFLINE", None)
    print("Downloading and loading public Chatterbox weights. This can use several GB of disk and RAM. No voice recording is uploaded.", flush=True)
    subprocess.run([str(python), "-c", "from chatterbox.tts import ChatterboxTTS; ChatterboxTTS.from_pretrained(device='cpu'); print('Voice engine loaded successfully')"], env=env, check=True)
    print("Setup complete. Restart the Neo Python API, then generate a preview from My voice in the app.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
