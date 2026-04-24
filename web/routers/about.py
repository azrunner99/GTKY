from fastapi import APIRouter, Request
from fastapi.responses import HTMLResponse
from fastapi.templating import Jinja2Templates
from config import TEMPLATES_DIR

router = APIRouter(prefix="/about")
templates = Jinja2Templates(directory=str(TEMPLATES_DIR))


@router.get("", response_class=HTMLResponse)
async def about(request: Request):
    lang = request.session.get("lang", "en")
    return templates.TemplateResponse(
        "about/about.html", {"request": request, "lang": lang}
    )
