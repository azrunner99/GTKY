from fastapi import APIRouter, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from config import TEMPLATES_DIR, QUIZ_UNLOCK_THRESHOLD
from database import get_db

router = APIRouter(prefix="/users")
templates = Jinja2Templates(directory=str(TEMPLATES_DIR))


@router.get("", response_class=HTMLResponse)
async def active_users(request: Request):
    user_id = request.session.get("user_id")
    if not user_id:
        return RedirectResponse("/")
    lang = request.session.get("lang", "en")

    db = await get_db()
    try:
        async with db.execute(
            """
            SELECT u.id, u.name, u.photo_filename,
                   COUNT(sa.id) as answered
            FROM users u
            LEFT JOIN survey_answers sa ON sa.user_id = u.id
            GROUP BY u.id
            ORDER BY u.name
            """
        ) as cur:
            users = [dict(r) for r in await cur.fetchall()]

        for u in users:
            u["is_eligible"] = u["answered"] >= QUIZ_UNLOCK_THRESHOLD

        return templates.TemplateResponse(
            "active_users/list.html",
            {
                "request": request,
                "lang": lang,
                "users": users,
                "current_user_id": user_id,
                "quiz_threshold": QUIZ_UNLOCK_THRESHOLD,
            },
        )
    finally:
        await db.close()
