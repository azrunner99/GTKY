import json
import uuid
from fastapi import APIRouter, Request, Form
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from config import TEMPLATES_DIR, QUIZ_UNLOCK_THRESHOLD
from database import get_db
from services.question_phrasing import category_label

router = APIRouter(prefix="/quiz")
templates = Jinja2Templates(directory=str(TEMPLATES_DIR))

QUIZ_LENGTH = 10


@router.get("", response_class=HTMLResponse)
async def quiz_select(request: Request):
    user_id = request.session.get("user_id")
    if not user_id:
        return RedirectResponse("/")
    lang = request.session.get("lang", "en")

    db = await get_db()
    try:
        async with db.execute(
            "SELECT COUNT(*) as cnt FROM survey_answers WHERE user_id=?", (user_id,)
        ) as cur:
            cnt = (await cur.fetchone())["cnt"]
        if cnt < QUIZ_UNLOCK_THRESHOLD:
            return RedirectResponse("/survey")

        async with db.execute(
            """
            SELECT u.id, u.name, u.photo_filename,
                   COUNT(sa.id) as answered
            FROM users u
            LEFT JOIN survey_answers sa ON sa.user_id = u.id
            WHERE u.id != ?
            GROUP BY u.id
            HAVING answered > 0
            ORDER BY u.name
            """,
            (user_id,),
        ) as cur:
            others = [dict(r) for r in await cur.fetchall()]

        return templates.TemplateResponse(
            "quiz/select.html",
            {"request": request, "lang": lang, "others": others},
        )
    finally:
        await db.close()


@router.post("/start", response_class=HTMLResponse)
async def quiz_start(request: Request, subject_id: int = Form(...)):
    user_id = request.session.get("user_id")
    if not user_id:
        return RedirectResponse("/")

    db = await get_db()
    try:
        async with db.execute(
            """
            SELECT sq.id, sq.template_en, sq.template_es, sq.category, sq.type,
                   sq.options_en, sq.options_es, sa.answer_en
            FROM survey_answers sa
            JOIN survey_questions sq ON sq.id = sa.question_id
            WHERE sa.user_id = ?
            ORDER BY RANDOM()
            LIMIT ?
            """,
            (subject_id, QUIZ_LENGTH),
        ) as cur:
            rows = await cur.fetchall()

        if not rows:
            return RedirectResponse("/quiz")

        questions = [
            {
                "question_id": r["id"],
                "template_en": r["template_en"],
                "template_es": r["template_es"],
                "category": r["category"],
                "type": r["type"],
                "options_en": json.loads(r["options_en"]),
                "options_es": json.loads(r["options_es"]),
                "correct_en": r["answer_en"],
            }
            for r in rows
        ]

        session_id = str(uuid.uuid4())
        await db.execute(
            "INSERT INTO quiz_sessions(session_id, subject_id, questions_json) VALUES(?,?,?)",
            (session_id, subject_id, json.dumps(questions)),
        )
        await db.commit()
    finally:
        await db.close()

    request.session["quiz_session_id"] = session_id
    return RedirectResponse("/quiz/question", status_code=303)


@router.get("/question", response_class=HTMLResponse)
async def quiz_question(request: Request):
    user_id = request.session.get("user_id")
    session_id = request.session.get("quiz_session_id")
    if not user_id or not session_id:
        return RedirectResponse("/quiz")
    lang = request.session.get("lang", "en")

    db = await get_db()
    try:
        async with db.execute(
            "SELECT * FROM quiz_sessions WHERE session_id=?", (session_id,)
        ) as cur:
            sess = await cur.fetchone()
        if not sess:
            return RedirectResponse("/quiz")

        questions = json.loads(sess["questions_json"])
        idx = sess["current_index"]
        score = sess["score"]

        if idx >= len(questions):
            return RedirectResponse("/quiz/results")

        q = questions[idx]
        options_en = q["options_en"]
        options_es = q["options_es"]
        display_options = options_es if lang == "es" else options_en

        async with db.execute(
            "SELECT name, photo_filename FROM users WHERE id=?", (sess["subject_id"],)
        ) as cur:
            subject = await cur.fetchone()

        template = q["template_es"] if lang == "es" else q["template_en"]
        question_text = template.replace("[NAME]", subject["name"])
        cat = category_label(q["category"], lang)

        return templates.TemplateResponse(
            "quiz/question.html",
            {
                "request": request,
                "lang": lang,
                "session_id": session_id,
                "question_text": question_text,
                "category": cat,
                "display_options": display_options,
                "english_options": options_en,
                "subject": dict(subject),
                "question_num": idx + 1,
                "total": len(questions),
                "score": score,
            },
        )
    finally:
        await db.close()


@router.post("/answer", response_class=HTMLResponse)
async def quiz_answer(
    request: Request,
    guessed_en: str = Form(...),
):
    user_id = request.session.get("user_id")
    session_id = request.session.get("quiz_session_id")
    if not user_id or not session_id:
        return RedirectResponse("/quiz")

    db = await get_db()
    try:
        async with db.execute(
            "SELECT * FROM quiz_sessions WHERE session_id=?", (session_id,)
        ) as cur:
            sess = await cur.fetchone()
        if not sess:
            return RedirectResponse("/quiz")

        questions = json.loads(sess["questions_json"])
        idx = sess["current_index"]
        score = sess["score"]
        q = questions[idx]

        is_correct = int(guessed_en == q["correct_en"])
        new_score = score + is_correct

        await db.execute(
            """
            INSERT INTO quiz_results(guesser_id, subject_id, question_id, guessed_en, correct_en, is_correct)
            VALUES(?,?,?,?,?,?)
            """,
            (user_id, sess["subject_id"], q["question_id"], guessed_en, q["correct_en"], is_correct),
        )
        await db.execute(
            "UPDATE quiz_sessions SET current_index=?, score=? WHERE session_id=?",
            (idx + 1, new_score, session_id),
        )
        await db.commit()

        # Show result briefly then auto-advance
        lang = request.session.get("lang", "en")
        options_en = q["options_en"]
        options_es = q["options_es"]

        try:
            correct_idx = options_en.index(q["correct_en"])
            correct_display = options_es[correct_idx] if lang == "es" else q["correct_en"]
            guessed_display_idx = options_en.index(guessed_en) if guessed_en in options_en else -1
            guessed_display = options_es[guessed_display_idx] if (lang == "es" and guessed_display_idx >= 0) else guessed_en
        except (ValueError, IndexError):
            correct_display = q["correct_en"]
            guessed_display = guessed_en

        async with db.execute(
            "SELECT name FROM users WHERE id=?", (sess["subject_id"],)
        ) as cur:
            subject = await cur.fetchone()

        next_idx = idx + 1
        is_last = next_idx >= len(questions)

        return templates.TemplateResponse(
            "quiz/result.html",
            {
                "request": request,
                "lang": lang,
                "is_correct": bool(is_correct),
                "correct_display": correct_display,
                "guessed_display": guessed_display,
                "score": new_score,
                "question_num": idx + 1,
                "total": len(questions),
                "subject_name": subject["name"],
                "is_last": is_last,
            },
        )
    finally:
        await db.close()


@router.get("/results", response_class=HTMLResponse)
async def quiz_results(request: Request):
    user_id = request.session.get("user_id")
    session_id = request.session.get("quiz_session_id")
    if not user_id or not session_id:
        return RedirectResponse("/quiz")
    lang = request.session.get("lang", "en")

    db = await get_db()
    try:
        async with db.execute(
            "SELECT * FROM quiz_sessions WHERE session_id=?", (session_id,)
        ) as cur:
            sess = await cur.fetchone()
        if not sess:
            return RedirectResponse("/quiz")

        questions = json.loads(sess["questions_json"])
        async with db.execute(
            "SELECT name, photo_filename FROM users WHERE id=?", (sess["subject_id"],)
        ) as cur:
            subject = await cur.fetchone()

        request.session.pop("quiz_session_id", None)
        await db.execute("DELETE FROM quiz_sessions WHERE session_id=?", (session_id,))
        await db.commit()

        return templates.TemplateResponse(
            "quiz/results.html",
            {
                "request": request,
                "lang": lang,
                "score": sess["score"],
                "total": len(questions),
                "subject": dict(subject),
            },
        )
    finally:
        await db.close()
