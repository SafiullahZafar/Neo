"""Run the APK build and Kotlin-to-Python test without putting secrets in argv."""
import argparse
import os
from pathlib import Path
import subprocess

from core.config import PROJECT_ROOT, settings

parser = argparse.ArgumentParser()
parser.add_argument("--gradle-jar", type=Path, help="Optional installed Gradle CLI jar; otherwise use the project wrapper")
parser.add_argument("--lint", action="store_true", help="Also check Android API and permission usage")
args = parser.parse_args()
env = os.environ.copy()
env["NEO_TEST_URL"] = f"http://127.0.0.1:{settings.api_port}"
env["NEO_TEST_TOKEN"] = settings.api_token
project = PROJECT_ROOT / "_ktlion" / "_app"
tasks = [":app:assembleDebug", ":app:testDebugUnitTest", "--no-daemon", "--console=plain", "--rerun-tasks"]
if args.lint:
    tasks.insert(2, ":app:lintDebug")
if args.gradle_jar:
    java = Path(env["JAVA_HOME"]) / "bin" / "java.exe"
    command = [str(java), "-Dorg.gradle.appname=gradle", "-jar", str(args.gradle_jar), *tasks]
else:
    command = [str(project / ("gradlew.bat" if os.name == "nt" else "gradlew")), *tasks]
raise SystemExit(subprocess.call(command, cwd=project, env=env))
