import json
import random
from fastapi import APIRouter, Request, Form
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from config import TEMPLATES_DIR, QUIZ_UNLOCK_THRESHOLD
from database import get_db
from services.question_phrasing import for_survey_en, for_survey_es, category_label

router = APIRouter(prefix="/survey")
templates = Jinja2Templates(directory=str(TEMPLATES_DIR))


async def next_question(db, user_id: int, skipped_ids: list[int] | None = None):
    skipped_ids = skipped_ids or []
    skipped_clause = ""
    params = [user_id]
    if skipped_ids:
        placeholders = ",".join("?" * len(skipped_ids))
        skipped_clause = f"AND sq.id NOT IN ({placeholders})"
        params.extend(skipped_ids)

    async with db.execute(
        f"""
        SELECT sq.id, sq.template_en, sq.template_es, sq.category, sq.type,
               sq.options_en, sq.options_es
        FROM survey_questions sq
        WHERE sq.id NOT IN (
            SELECT question_id FROM survey_answers WHERE user_id = ?
        )
        {skipped_clause}
        ORDER BY sq.id
        """,
        params,
    ) as cur:
        rows = await cur.fetchall()

    if not rows:
        return None

    # Per-user seed so two users don't see identical order.
    rng = random.Random(0x474B5946 + user_id)
    rows_list = list(rows)
    rng.shuffle(rows_list)
    return rows_list[0]


@router.get("", response_class=HTMLResponse)
async def survey_get(request: Request):
    user_id = request.session.get("user_id")
    if not user_id:
        return RedirectResponse("/")
    lang = request.session.get("lang", "en")

    db = await get_db()
    try:
        async with db.execute(
            "SELECT COUNT(*) as cnt FROM survey_answers WHERE user_id=?", (user_id,)
        ) as cur:
            total_answered = (await cur.fetchone())["cnt"]

        skipped = request.session.get("skipped_question_ids", [])
        q = await next_question(db, user_id, skipped)
        if not q:
            return templates.TemplateResponse(
                "survey/done.html",
                {"request": request, "lang": lang, "total_answered": total_answered},
            )

        options_en = json.loads(q["options_en"])
        options_es = json.loads(q["options_es"])

        if lang == "es":
            question_text = for_survey_es(q["template_es"])
            display_options = options_es
        else:
            question_text = for_survey_en(q["template_en"])
            display_options = options_en

        can_quit = total_answered >= QUIZ_UNLOCK_THRESHOLD
        just_unlocked = total_answered == QUIZ_UNLOCK_THRESHOLD

        return templates.TemplateResponse(
            "survey/survey.html",
            {
                "request": request,
                "lang": lang,
                "question_id": q["id"],
                "question_text": question_text,
                "category": category_label(q["category"], lang),
                "display_options": display_options,
                "english_options": options_en,
                "total_answered": total_answered,
                "can_quit": can_quit,
                "just_unlocked": just_unlocked,
                "quiz_threshold": QUIZ_UNLOCK_THRESHOLD,
            },
        )
    finally:
        await db.close()


@router.post("/answer", response_class=HTMLResponse)
async def survey_answer(
    request: Request,
    question_id: int = Form(...),
    answer_en: str = Form(...),
):
    user_id = request.session.get("user_id")
    if not user_id:
        return RedirectResponse("/")

    db = await get_db()
    try:
        await db.execute(
            "INSERT OR IGNORE INTO survey_answers(user_id, question_id, answer_en) VALUES(?,?,?)",
            (user_id, question_id, answer_en),
        )
        await db.commit()
    finally:
        await db.close()

    # Clear skip list — skipped questions can resurface in the next cycle.
    request.session.pop("skipped_question_ids", None)
    return RedirectResponse("/survey", status_code=303)


@router.post("/skip", response_class=HTMLResponse)
async def survey_skip(request: Request, question_id: int = Form(...)):
    user_id = request.session.get("user_id")
    if not user_id:
        return RedirectResponse("/")
    skipped = request.session.get("skipped_question_ids", [])
    if question_id not in skipped:
        skipped.append(question_id)
        # Cap at 50 to prevent cookie bloat.
        if len(skipped) > 50:
            skipped = skipped[-50:]
        request.session["skipped_question_ids"] = skipped
    return RedirectResponse("/survey", status_code=303)
