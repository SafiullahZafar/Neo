"""Create local configuration without displaying or overwriting existing secrets."""
from pathlib import Path
import secrets
from dotenv import dotenv_values, set_key

root = Path(__file__).resolve().parent
env_file = root / ".env"
if not env_file.exists():
    env_file.write_text((root / ".env.example").read_text(encoding="utf-8"), encoding="utf-8")
values = dotenv_values(env_file)
if not values.get("NEO_API_TOKEN"):
    set_key(str(env_file), "NEO_API_TOKEN", secrets.token_urlsafe(32))
    print("Generated a private pairing token in .env (not displayed).")
else:
    print("Existing API token preserved.")
print("Start: .venv\\Scripts\\python.exe main.py")
