from fastapi import APIRouter, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from config import TEMPLATES_DIR, QUIZ_UNLOCK_THRESHOLD
from database import get_db

router = APIRouter()
templates = Jinja2Templates(directory=str(TEMPLATES_DIR))


async def get_activity_stat(db):
    async with db.execute("SELECT COUNT(DISTINCT user_id) AS users FROM survey_answers") as cur:
        row = await cur.fetchone()
        users_with_answers = row["users"] if row else 0
    async with db.execute("SELECT COUNT(*) AS total FROM survey_answers") as cur:
        row = await cur.fetchone()
        total_answers = row["total"] if row else 0
    return {"users_with_answers": users_with_answers, "total_answers": total_answers}


@router.get("/", response_class=HTMLResponse)
async def home(request: Request):
    user_id = request.session.get("user_id")
    lang = request.session.get("lang", "en")

    db = await get_db()
    try:
        if not user_id:
            return templates.TemplateResponse(
                "home/index.html",
                {"request": request, "lang": lang},
            )

        async with db.execute("SELECT name, photo_filename FROM users WHERE id=?", (user_id,)) as cur:
            user = await cur.fetchone()
        if not user:
            request.session.clear()
            return RedirectResponse("/")

        async with db.execute(
            "SELECT COUNT(*) as cnt FROM survey_answers WHERE user_id=?", (user_id,)
        ) as cur:
            answered_count = (await cur.fetchone())["cnt"]

        quiz_unlocked = answered_count >= QUIZ_UNLOCK_THRESHOLD
        activity = await get_activity_stat(db)

        return templates.TemplateResponse(
            "home/dashboard.html",
            {
                "request": request,
                "user": dict(user),
                "user_id": user_id,
                "lang": lang,
                "answered_count": answered_count,
                "quiz_unlocked": quiz_unlocked,
                "quiz_threshold": QUIZ_UNLOCK_THRESHOLD,
                "activity": activity,
            },
        )
    finally:
        await db.close()
