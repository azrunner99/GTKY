import json

from fastapi import APIRouter, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from config import TEMPLATES_DIR, QUIZ_UNLOCK_THRESHOLD
from database import get_db
from services.question_phrasing import for_survey_en, for_survey_es, category_label

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
            async with db.execute(
                "SELECT id, name, photo_filename FROM users ORDER BY name"
            ) as cur:
                existing_users = [dict(r) for r in await cur.fetchall()]

            icebreaker = None
            async with db.execute(
                "SELECT * FROM survey_questions ORDER BY RANDOM() LIMIT 1"
            ) as cur:
                q_row = await cur.fetchone()
            if q_row:
                q = dict(q_row)
                icebreaker = {
                    "category": category_label(q["category"], "en"),
                    "question_en": for_survey_en(q["template_en"]),
                    "question_es": for_survey_es(q["template_es"]),
                    "options_en": json.loads(q["options_en"]),
                    "options_es": json.loads(q["options_es"]),
                }

            return templates.TemplateResponse(
                "home/index.html",
                {"request": request, "lang": lang, "existing_users": existing_users, "icebreaker": icebreaker},
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

        if quiz_unlocked:
            async with db.execute(
                """
                SELECT COUNT(*) AS cnt FROM (
                    SELECT u.id
                    FROM users u
                    JOIN survey_answers sa ON sa.user_id = u.id
                    WHERE u.id != ?
                    GROUP BY u.id
                    HAVING COUNT(sa.id) >= ?
                )
                """,
                (user_id, QUIZ_UNLOCK_THRESHOLD),
            ) as cur:
                ready_count = (await cur.fetchone())["cnt"]
        else:
            ready_count = 0

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
                "ready_count": ready_count,
                "activity": activity,
            },
        )
    finally:
        await db.close()
