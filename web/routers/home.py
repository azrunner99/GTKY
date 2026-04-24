import json
import random
from fastapi import APIRouter, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from config import TEMPLATES_DIR, QUIZ_UNLOCK_THRESHOLD
from database import get_db
from services.question_phrasing import category_label

router = APIRouter()
templates = Jinja2Templates(directory=str(TEMPLATES_DIR))


async def get_icebreaker(db, lang: str):
    """Return a random answered icebreaker question from the pool."""
    async with db.execute(
        """
        SELECT sq.id, sq.template_en, sq.template_es, sq.category,
               sq.options_en, sq.options_es,
               u.name, sa.answer_en,
               sq.options_es
        FROM survey_answers sa
        JOIN survey_questions sq ON sq.id = sa.question_id
        JOIN users u ON u.id = sa.user_id
        ORDER BY RANDOM()
        LIMIT 1
        """
    ) as cur:
        row = await cur.fetchone()
    if not row:
        return None

    options_en = json.loads(row["options_en"])
    options_es = json.loads(row["options_es"])
    answer_en = row["answer_en"]
    try:
        idx = options_en.index(answer_en)
        answer_display = options_es[idx] if lang == "es" and idx < len(options_es) else answer_en
    except ValueError:
        answer_display = answer_en

    template = row["template_es"] if lang == "es" else row["template_en"]
    question_text = template.replace("[NAME]", row["name"])
    cat = category_label(row["category"], lang)

    return {
        "user_name": row["name"],
        "question": question_text,
        "answer": answer_display,
        "category": cat,
    }


@router.get("/", response_class=HTMLResponse)
async def home(request: Request):
    user_id = request.session.get("user_id")
    lang = request.session.get("lang", "en")

    db = await get_db()
    try:
        icebreaker = await get_icebreaker(db, lang)

        if not user_id:
            return templates.TemplateResponse(
                "home/index.html",
                {"request": request, "icebreaker": icebreaker, "lang": lang},
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

        return templates.TemplateResponse(
            "home/dashboard.html",
            {
                "request": request,
                "user": dict(user),
                "user_id": user_id,
                "lang": lang,
                "icebreaker": icebreaker,
                "answered_count": answered_count,
                "quiz_unlocked": quiz_unlocked,
                "quiz_threshold": QUIZ_UNLOCK_THRESHOLD,
            },
        )
    finally:
        await db.close()
