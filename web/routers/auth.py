import re
from enum import Enum
from typing import Optional
from urllib.parse import urlencode
from fastapi import APIRouter, Request, Form
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from config import TEMPLATES_DIR
from database import get_db

PHOTO_PROMPT_CAP = 3


async def should_show_photo_prompt(db, user_id: int) -> bool:
    async with db.execute(
        "SELECT photo_filename, photo_prompt_count, photo_prompt_opt_out FROM users WHERE id=?",
        (user_id,),
    ) as cur:
        row = await cur.fetchone()
    if not row:
        return False
    if row["photo_filename"]:
        return False
    if row["photo_prompt_opt_out"]:
        return False
    return row["photo_prompt_count"] < PHOTO_PROMPT_CAP

router = APIRouter()
templates = Jinja2Templates(directory=str(TEMPLATES_DIR))


class MatchKind(str, Enum):
    EXACT = "exact"
    PREFIX_LONGER = "prefix_longer"    # typed "Alex S", existing "Alex Smith"
    PREFIX_SHORTER = "prefix_shorter"  # typed "Alex Smith", existing "Alex S"
    SAME_INITIAL = "same_initial"      # typed "Alex Smith", existing "Alex Smyth"


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


def classify_name_match(
    typed_first: str, typed_last: str,
    existing_first: str, existing_last: str,
) -> Optional[MatchKind]:
    tf, tl = typed_first.lower(), typed_last.lower()
    ef, el = existing_first.lower(), existing_last.lower()
    if tf != ef:
        return None
    if el == tl:
        return MatchKind.EXACT
    if tl and el and el.startswith(tl):
        return MatchKind.PREFIX_LONGER
    if tl and el and tl.startswith(el):
        return MatchKind.PREFIX_SHORTER
    if tl and el and tl[0] == el[0]:
        return MatchKind.SAME_INITIAL
    return None


async def find_similar_names(db, typed_name: str):
    typed = normalize_name(typed_name)
    if not typed:
        return []
    parts = typed.split(" ", 1)
    typed_first = parts[0] if parts else ""
    typed_last = parts[1] if len(parts) > 1 else ""
    if not typed_first:
        return []

    async with db.execute("SELECT id, name, photo_filename FROM users") as cur:
        users = await cur.fetchall()

    matches = []
    for u in users:
        u_parts = u["name"].split(" ", 1)
        u_first = u_parts[0] if u_parts else ""
        u_last = u_parts[1] if len(u_parts) > 1 else ""
        kind = classify_name_match(typed_first, typed_last, u_first, u_last)
        if kind:
            matches.append({"user": dict(u), "kind": kind})
    order = {
        MatchKind.EXACT: 0,
        MatchKind.PREFIX_LONGER: 1,
        MatchKind.PREFIX_SHORTER: 1,
        MatchKind.SAME_INITIAL: 2,
    }
    matches.sort(key=lambda m: order[m["kind"]])
    return matches


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
            user_id = row["id"]
            request.session["user_id"] = user_id
            request.session["user_name"] = row["name"]
            request.session.pop("lang", None)
            request.session.pop("skip_similar_for_name", None)
            if await should_show_photo_prompt(db, user_id):
                await db.execute(
                    "UPDATE users SET photo_prompt_count = photo_prompt_count + 1 WHERE id=?",
                    (user_id,),
                )
                await db.commit()
                return RedirectResponse("/photo-prompt", status_code=303)
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

        lang = request.session.get("lang", "en")
        matches = await find_similar_names(db, name)
        skip_similar_for = request.session.get("skip_similar_for_name")
        bypass_fuzzy = skip_similar_for and skip_similar_for == name

        if matches:
            only_exact = len(matches) == 1 and matches[0]["kind"] == MatchKind.EXACT
            if only_exact:
                return templates.TemplateResponse(
                    "home/duplicate_name.html",
                    {
                        "request": request,
                        "lang": lang,
                        "colliding_user": matches[0]["user"],
                        "typed_first": first_name,
                        "typed_last": last_name,
                        "typed_full": name,
                    },
                )
            if not bypass_fuzzy:
                return templates.TemplateResponse(
                    "home/similar_name.html",
                    {
                        "request": request,
                        "lang": lang,
                        "matches": matches,
                        "typed_first": first_name,
                        "typed_last": last_name,
                        "typed_full": name,
                    },
                )

        # Clear bypass flag now that we're past the fuzzy check.
        request.session.pop("skip_similar_for_name", None)

        try:
            async with db.execute("INSERT INTO users(name) VALUES(?)", (name,)) as cur:
                user_id = cur.lastrowid
            await db.commit()
        except Exception:
            return templates.TemplateResponse(
                "home/index.html",
                {
                    "request": request,
                    "lang": lang,
                    "error": "That exact name is already taken. Add more letters to your last name.",
                    "existing_users": [],
                    "prefill_first": first_name,
                    "prefill_last": last_name,
                },
            )

        request.session["user_id"] = user_id
        request.session["user_name"] = name
        request.session.pop("lang", None)
        if await should_show_photo_prompt(db, user_id):
            await db.execute(
                "UPDATE users SET photo_prompt_count = photo_prompt_count + 1 WHERE id=?",
                (user_id,),
            )
            await db.commit()
            return RedirectResponse("/photo-prompt", status_code=303)
        return RedirectResponse("/", status_code=303)
    finally:
        await db.close()


@router.post("/signin-different", response_class=HTMLResponse)
async def signin_different(
    request: Request,
    typed_first: str = Form(...),
    typed_last: str = Form(...),
    typed_full: str = Form(...),
):
    request.session["skip_similar_for_name"] = typed_full
    params = urlencode({"mode": "new", "first": typed_first, "last": typed_last})
    return RedirectResponse(f"/?{params}", status_code=303)


@router.get("/rename", response_class=HTMLResponse)
async def rename_form(request: Request):
    user_id = request.session.get("user_id")
    if not user_id:
        return RedirectResponse("/")
    lang = request.session.get("lang", "en")
    db = await get_db()
    try:
        async with db.execute("SELECT name FROM users WHERE id=?", (user_id,)) as cur:
            row = await cur.fetchone()
    finally:
        await db.close()
    if not row:
        request.session.clear()
        return RedirectResponse("/")
    return templates.TemplateResponse(
        "home/rename.html",
        {"request": request, "lang": lang, "current_name": row["name"]},
    )


@router.post("/rename")
async def rename_submit(request: Request, new_name: str = Form(...)):
    user_id = request.session.get("user_id")
    if not user_id:
        return RedirectResponse("/")
    name = normalize_name(new_name)
    if not name or len(name) < 2:
        return RedirectResponse("/rename")

    db = await get_db()
    try:
        async with db.execute(
            "SELECT id FROM users WHERE name=? AND id != ?", (name, user_id)
        ) as cur:
            existing = await cur.fetchone()
        if existing:
            lang = request.session.get("lang", "en")
            async with db.execute("SELECT name FROM users WHERE id=?", (user_id,)) as cur:
                cur_row = await cur.fetchone()
            return templates.TemplateResponse(
                "home/rename.html",
                {
                    "request": request,
                    "lang": lang,
                    "current_name": cur_row["name"] if cur_row else "",
                    "error": "That name is already taken.",
                    "attempted": name,
                },
            )
        await db.execute("UPDATE users SET name=? WHERE id=?", (name, user_id))
        await db.commit()
        request.session["user_name"] = name
    finally:
        await db.close()
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
