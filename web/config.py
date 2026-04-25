import os
from pathlib import Path

BASE_DIR = Path(__file__).parent
DB_PATH = BASE_DIR / "gtky.db"
PHOTOS_DIR = BASE_DIR / "static" / "photos"
TEMPLATES_DIR = BASE_DIR / "templates"
STATIC_DIR = BASE_DIR / "static"

SECRET_KEY = os.environ.get("GTKY_SECRET_KEY", "gtky-dev-secret-change-in-prod-please")

ADMIN_PIN_DEFAULT = "1234"
QUIZ_UNLOCK_THRESHOLD = 10
SESSION_COOKIE = "gtky_session"
PHOTO_MAX_SIZE = (400, 400)
PHOTO_QUALITY = 85
PHOTO_MAX_UPLOAD_BYTES = 10 * 1024 * 1024  # 10 MB
IDLE_TIMEOUT_SECONDS = 5 * 60  # 5 minutes
