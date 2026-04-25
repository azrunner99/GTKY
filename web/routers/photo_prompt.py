from fastapi import APIRouter, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from config import TEMPLATES_DIR
from database import get_db
from routers.auth import PHOTO_PROMPT_CAP

router = APIRouter()
templates = Jinja2Templates(directory=str(TEMPLATES_DIR))


@router.get("/photo-prompt", response_class=HTMLResponse, include_in_schema=False)
async def photo_prompt_get(request: Request):
    user_id = request.session.get("user_id")
    if not user_id:
        return RedirectResponse("/")
    lang = request.session.get("lang", "en")

    db = await get_db()
    try:
        async with db.execute(
            "SELECT photo_filename, photo_prompt_count, photo_prompt_opt_out FROM users WHERE id=?",
            (user_id,),
        ) as cur:
            row = await cur.fetchone()
        if not row:
            request.session.clear()
            return RedirectResponse("/")
        if row["photo_filename"] or row["photo_prompt_opt_out"]:
            return RedirectResponse("/", status_code=303)
        show_opt_out = row["photo_prompt_count"] >= PHOTO_PROMPT_CAP
        upload_error = request.session.pop("photo_upload_error", None)
        return templates.TemplateResponse(
            "profile/photo_prompt.html",
            {
                "request": request,
                "lang": lang,
                "show_opt_out": show_opt_out,
                "upload_error": upload_error,
            },
        )
    finally:
        await db.close()


@router.post("/photo-prompt/skip")
async def photo_prompt_skip(request: Request):
    if not request.session.get("user_id"):
        return RedirectResponse("/")
    return RedirectResponse("/", status_code=303)


@router.post("/photo-prompt/opt-out")
async def photo_prompt_opt_out(request: Request):
    user_id = request.session.get("user_id")
    if not user_id:
        return RedirectResponse("/")
    db = await get_db()
    try:
        await db.execute(
            "UPDATE users SET photo_prompt_opt_out = 1 WHERE id=?", (user_id,)
        )
        await db.commit()
    finally:
        await db.close()
    return RedirectResponse("/", status_code=303)
