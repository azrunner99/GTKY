import re
from fastapi import APIRouter, Request, Form
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from config import TEMPLATES_DIR
from database import get_db

router = APIRouter()
templates = Jinja2Templates(directory=str(TEMPLATES_DIR))


def normalize_name(name: str) -> str:
    name = name.strip()
    name = re.sub(r"\s+", " ", name)
    return " ".join(w.capitalize() for w in name.split())


@router.post("/signin", response_class=HTMLResponse)
async def signin(request: Request, name: str = Form(...)):
    name = normalize_name(name)
    if not name or len(name) < 2:
        return templates.TemplateResponse(
            "home/index.html",
            {"request": request, "error": "Please enter your name (at least 2 characters)."},
        )

    db = await get_db()
    try:
        async with db.execute("SELECT id FROM users WHERE name = ?", (name,)) as cur:
            row = await cur.fetchone()
        if row:
            user_id = row["id"]
        else:
            async with db.execute(
                "INSERT INTO users(name) VALUES(?)", (name,)
            ) as cur:
                user_id = cur.lastrowid
            await db.commit()
    finally:
        await db.close()

    request.session["user_id"] = user_id
    request.session["user_name"] = name
    request.session.pop("lang", None)
    return RedirectResponse("/", status_code=303)


@router.post("/signout")
async def signout(request: Request):
    request.session.clear()
    return RedirectResponse("/", status_code=303)


@router.post("/set-lang")
async def set_lang(request: Request, lang: str = Form(...)):
    if lang in ("en", "es"):
        request.session["lang"] = lang
    referer = request.headers.get("referer", "/")
    return RedirectResponse(referer, status_code=303)
