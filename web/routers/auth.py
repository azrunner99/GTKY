import re
from typing import Optional
from fastapi import APIRouter, Request, Form
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from config import TEMPLATES_DIR
from database import get_db

router = APIRouter()
templates = Jinja2Templates(directory=str(TEMPLATES_DIR))


def _title_case_segment(seg: str) -> str:
    if not seg:
        return seg
    return seg[0].upper() + seg[1:].lower()


def normalize_name(name: str) -> str:
    """
    Strict title case. Matches Android util/NameFormat.kt:normalizeName.
    "alex smith"    -> "Alex Smith"
    "ALEX SMITH"    -> "Alex Smith"
    "mary-jane LEE" -> "Mary-Jane Lee"
    """
    collapsed = re.sub(r"\s+", " ", name.strip())
    if not collapsed:
        return ""
    words = collapsed.split(" ")
    out = []
    for word in words:
        segments = word.split("-")
        out.append("-".join(_title_case_segment(s) for s in segments))
    return " ".join(out)


@router.post("/signin", response_class=HTMLResponse)
async def signin(
    request: Request,
    first_name: Optional[str] = Form(None),
    last_name: Optional[str] = Form(None),
    existing_user_id: Optional[int] = Form(None),
    is_new: Optional[str] = Form(None),
):
    db = await get_db()
    try:
        if existing_user_id:
            async with db.execute("SELECT id, name FROM users WHERE id=?", (existing_user_id,)) as cur:
                row = await cur.fetchone()
            if not row:
                return RedirectResponse("/?mode=returning", status_code=303)
            request.session["user_id"] = row["id"]
            request.session["user_name"] = row["name"]
            request.session.pop("lang", None)
            return RedirectResponse("/", status_code=303)

        # New user flow
        if not first_name or not last_name:
            return RedirectResponse("/?mode=new", status_code=303)
        combined = f"{first_name} {last_name}"
        name = normalize_name(combined)
        if not name or len(name) < 2:
            return templates.TemplateResponse(
                "home/index.html",
                {
                    "request": request,
                    "error": "Please enter your first and last name.",
                    "prefill_first": first_name,
                    "prefill_last": last_name,
                    "existing_users": [],
                },
            )

        # Hard exact-match check (similar-name detection comes in W1.7).
        async with db.execute("SELECT id, name FROM users WHERE name=?", (name,)) as cur:
            existing = await cur.fetchone()
        if existing:
            # Exact collision — sign them in as that user.
            # W1.7 will upgrade this to a confirmation prompt.
            request.session["user_id"] = existing["id"]
            request.session["user_name"] = existing["name"]
            request.session.pop("lang", None)
            return RedirectResponse("/", status_code=303)

        async with db.execute("INSERT INTO users(name) VALUES(?)", (name,)) as cur:
            user_id = cur.lastrowid
        await db.commit()
        request.session["user_id"] = user_id
        request.session["user_name"] = name
        request.session.pop("lang", None)
        return RedirectResponse("/", status_code=303)
    finally:
        await db.close()


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
