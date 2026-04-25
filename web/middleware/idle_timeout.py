import time
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from config import IDLE_TIMEOUT_SECONDS


class IdleTimeoutMiddleware(BaseHTTPMiddleware):
    """
    Tracks last-activity timestamp per session. If the gap exceeds IDLE_TIMEOUT_SECONDS,
    clears the session before the request proceeds. The user lands on a normal handler,
    which (because the session is now empty) treats them as signed-out.

    Static asset requests (anything under /static/) don't count as activity — otherwise
    a phone with a stale tab loading photos in the background would never time out.
    """

    EXEMPT_PATH_PREFIXES = ("/static/",)

    async def dispatch(self, request: Request, call_next):
        if "session" in request.scope:
            session = request.session
            now = int(time.time())
            user_id = session.get("user_id")
            last_active = session.get("last_active")

            is_exempt = any(request.url.path.startswith(p) for p in self.EXEMPT_PATH_PREFIXES)

            if user_id and last_active is not None:
                if now - last_active > IDLE_TIMEOUT_SECONDS:
                    session.clear()

            if not is_exempt:
                session["last_active"] = now

        return await call_next(request)
