import logging
import os
import socket
import time
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.middleware.sessions import SessionMiddleware
from middleware.idle_timeout import IdleTimeoutMiddleware
from middleware.csrf import OriginCheckMiddleware

from config import SESSION_SECRET, SESSION_SECRET_FILE, PHOTOS_DIR, STATIC_DIR
from database import init_db
from seed import seed_questions

log = logging.getLogger("gtky")
if "SESSION_SECRET" in os.environ:
    log.info("Session secret loaded from environment.")
else:
    log.info(f"Session secret loaded from {SESSION_SECRET_FILE}. Set SESSION_SECRET env var to override.")

from routers import auth, home, survey, quiz, connections, active_users, profile, groups, admin, about, photo_prompt


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    await seed_questions()
    PHOTOS_DIR.mkdir(parents=True, exist_ok=True)
    yield


class SessionUserContextMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request, call_next):
        user_id = request.session.get("user_id") if "session" in request.scope else None
        request.state.session_user_photo = None
        request.state.session_user_name = None
        if user_id:
            from database import get_db
            db = await get_db()
            try:
                async with db.execute(
                    "SELECT name, photo_filename FROM users WHERE id=?", (user_id,)
                ) as cur:
                    row = await cur.fetchone()
                if row:
                    request.state.session_user_photo = row["photo_filename"]
                    request.state.session_user_name = row["name"]
            finally:
                await db.close()
        return await call_next(request)


_START_TIME = time.time()

app = FastAPI(lifespan=lifespan, title="GTKY")
app.add_middleware(
    SessionMiddleware,
    secret_key=SESSION_SECRET,
    max_age=60 * 60 * 24 * 30,  # 30 days
    session_cookie="gtky_session",
    same_site="lax",
    https_only=False,
)
app.add_middleware(IdleTimeoutMiddleware)
app.add_middleware(SessionUserContextMiddleware)
app.add_middleware(OriginCheckMiddleware)

app.mount("/static", StaticFiles(directory=str(STATIC_DIR)), name="static")

app.include_router(auth.router)
app.include_router(photo_prompt.router)
app.include_router(home.router)
app.include_router(survey.router)
app.include_router(quiz.router)
app.include_router(connections.router)
app.include_router(active_users.router)
app.include_router(profile.router)
app.include_router(groups.router)
app.include_router(admin.router)
app.include_router(about.router)


@app.get("/health", include_in_schema=False)
async def health():
    return JSONResponse({"ok": True, "uptime_seconds": int(time.time() - _START_TIME)})


if __name__ == "__main__":
    import uvicorn

    try:
        import subprocess
        result = subprocess.run(
            ["powershell.exe", "-Command",
             "(Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '172.*' -and $_.IPAddress -notlike '169.*' } | Select-Object -First 1).IPAddress"],
            capture_output=True, text=True, timeout=5
        )
        lan_ip = result.stdout.strip() or "localhost"
    except Exception:
        lan_ip = "localhost"

    network_url = f"http://{lan_ip}:8000"
    print("\n=== GTKY Web App ===")
    print(f"Local:   http://localhost:8000")
    print(f"Network: {network_url}")
    print("Share the Network URL with others on your WiFi\n")
    try:
        import qrcode
        qr = qrcode.QRCode(border=1)
        qr.add_data(network_url)
        qr.make(fit=True)
        qr.print_ascii(invert=True)
        print()
    except ImportError:
        print("(install qrcode for a scannable QR code: pip3 install qrcode)\n")

    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
