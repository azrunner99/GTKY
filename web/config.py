import os
import secrets
from pathlib import Path

BASE_DIR = Path(__file__).parent
DB_PATH = BASE_DIR / "gtky.db"
PHOTOS_DIR = BASE_DIR / "static" / "photos"
TEMPLATES_DIR = BASE_DIR / "templates"
STATIC_DIR = BASE_DIR / "static"

SESSION_SECRET_FILE = BASE_DIR / ".session_secret"


def load_session_secret() -> str:
    env = os.environ.get("SESSION_SECRET")
    if env:
        return env
    if SESSION_SECRET_FILE.exists():
        text = SESSION_SECRET_FILE.read_text(encoding="utf-8").strip()
        if text:
            return text
    # First launch with no env and no file — generate, persist, return.
    new_secret = secrets.token_urlsafe(48)
    SESSION_SECRET_FILE.write_text(new_secret, encoding="utf-8")
    try:
        # Restrict to owner-read on POSIX. Best-effort; silently no-ops on Windows.
        os.chmod(SESSION_SECRET_FILE, 0o600)
    except OSError:
        pass
    return new_secret


SESSION_SECRET = load_session_secret()

ADMIN_PIN_DEFAULT = "1234"
QUIZ_UNLOCK_THRESHOLD = 10
SESSION_COOKIE = "gtky_session"
PHOTO_MAX_SIZE = (400, 400)
PHOTO_QUALITY = 85
PHOTO_MAX_UPLOAD_BYTES = 10 * 1024 * 1024  # 10 MB
IDLE_TIMEOUT_SECONDS = 5 * 60  # 5 minutes
