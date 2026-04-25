from urllib.parse import urlparse
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import PlainTextResponse


class OriginCheckMiddleware(BaseHTTPMiddleware):
    """
    Reject state-changing requests whose Origin or Referer header doesn't match the
    request's host. Combined with SameSite=lax cookies, this blocks the realistic
    CSRF vectors without the complexity of a synchronizer-token pattern.

    GET/HEAD/OPTIONS are not state-changing and are not checked.
    """

    UNSAFE_METHODS = {"POST", "PUT", "PATCH", "DELETE"}

    async def dispatch(self, request: Request, call_next):
        if request.method not in self.UNSAFE_METHODS:
            return await call_next(request)

        host_header = request.headers.get("host", "")
        if not host_header:
            return PlainTextResponse("Bad Request: missing Host header", status_code=400)

        allowed = {f"http://{host_header}", f"https://{host_header}"}

        origin = request.headers.get("origin")
        if origin:
            if origin not in allowed:
                return PlainTextResponse("Forbidden: cross-origin request", status_code=403)
            return await call_next(request)

        referer = request.headers.get("referer")
        if referer:
            parsed = urlparse(referer)
            referer_origin = f"{parsed.scheme}://{parsed.netloc}"
            if referer_origin not in allowed:
                return PlainTextResponse("Forbidden: cross-origin request", status_code=403)
            return await call_next(request)

        # No Origin and no Referer on a state-changing request — refuse.
        return PlainTextResponse("Forbidden: missing Origin/Referer", status_code=403)
