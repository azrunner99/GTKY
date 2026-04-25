import json
import random
import uuid
from fastapi import APIRouter, Request, Form, Query
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from config import TEMPLATES_DIR, QUIZ_UNLOCK_THRESHOLD
from database import get_db
from services.question_phrasing import for_quiz, category_label
from services.quiz_session import build_quiz_session, QUIZ_LENGTH

router = APIRouter(prefix="/quiz")
templates = Jinja2Templates(directory=str(TEMPLATES_DIR))


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
            SELECT u.id, u.name, u.photo_filename, COUNT(sa.id) as answered
            FROM users u
            LEFT JOIN survey_answers sa ON sa.user_id = u.id
            WHERE u.id != ?
            GROUP BY u.id
            HAVING answered >= ?
            ORDER BY u.name
            """,
            (user_id, QUIZ_UNLOCK_THRESHOLD),
        ) as cur:
            eligible = [dict(r) for r in await cur.fetchall()]

        async with db.execute(
            """
            SELECT g.id, g.name,
                   GROUP_CONCAT(ugm.user_id) AS member_ids
            FROM groups g
            LEFT JOIN user_group_memberships ugm ON ugm.group_id = g.id
            GROUP BY g.id
            ORDER BY g.name
            """
        ) as cur:
            group_rows = await cur.fetchall()
        groups = []
        for r in group_rows:
            member_ids = set()
            if r["member_ids"]:
                member_ids = set(int(x) for x in r["member_ids"].split(","))
            groups.append({"id": r["id"], "name": r["name"], "member_ids": list(member_ids)})

        return templates.TemplateResponse(
            "quiz/select.html",
            {"request": request, "lang": lang, "eligible": eligible, "groups": groups},
        )
    finally:
        await db.close()


@router.get("/pool-size")
async def pool_size(request: Request, subject_id: list[int] = Query(default=[])):
    user_id = request.session.get("user_id")
    if not user_id:
        return {"count": 0}

    db = await get_db()
    try:
        if not subject_id:
            return {"count": 0}

        placeholders = ",".join("?" * len(subject_id))
        async with db.execute(
            f"""
            SELECT COUNT(*) AS cnt
            FROM survey_answers sa
            WHERE sa.user_id IN ({placeholders})
              AND sa.question_id NOT IN (
                SELECT question_id FROM quiz_results WHERE guesser_id = ?
              )
            """,
            (*subject_id, user_id),
        ) as cur:
            row = await cur.fetchone()
        return {"count": row["cnt"]}
    finally:
        await db.close()


@router.post("/start", response_class=HTMLResponse)
async def quiz_start(
    request: Request,
    subject_ids: list[int] = Form(default=[]),
    group_ids: list[int] = Form(default=[]),
):
    user_id = request.session.get("user_id")
    if not user_id:
        return RedirectResponse("/")

    db = await get_db()
    try:
        # Resolve eligible subjects.
        if subject_ids:
            placeholders = ",".join("?" * len(subject_ids))
            async with db.execute(
                f"""
                SELECT u.id FROM users u
                JOIN survey_answers sa ON sa.user_id = u.id
                WHERE u.id IN ({placeholders}) AND u.id != ?
                GROUP BY u.id
                HAVING COUNT(sa.id) >= ?
                """,
                (*subject_ids, user_id, QUIZ_UNLOCK_THRESHOLD),
            ) as cur:
                resolved = [r["id"] for r in await cur.fetchall()]
        elif group_ids:
            placeholders = ",".join("?" * len(group_ids))
            async with db.execute(
                f"""
                SELECT u.id
                FROM users u
                JOIN user_group_memberships ugm ON ugm.user_id = u.id
                JOIN survey_answers sa ON sa.user_id = u.id
                WHERE ugm.group_id IN ({placeholders}) AND u.id != ?
                GROUP BY u.id
                HAVING COUNT(sa.id) >= ?
                """,
                (*group_ids, user_id, QUIZ_UNLOCK_THRESHOLD),
            ) as cur:
                resolved = [r["id"] for r in await cur.fetchall()]
        else:
            async with db.execute(
                """
                SELECT u.id
                FROM users u
                JOIN survey_answers sa ON sa.user_id = u.id
                WHERE u.id != ?
                GROUP BY u.id
                HAVING COUNT(sa.id) >= ?
                """,
                (user_id, QUIZ_UNLOCK_THRESHOLD),
            ) as cur:
                resolved = [r["id"] for r in await cur.fetchall()]

        if not resolved:
            return RedirectResponse("/quiz")

        # Build per-subject question pools.
        pools: dict[int, list[dict]] = {}
        for sid in resolved:
            async with db.execute(
                """
                SELECT sq.id AS question_id, sq.template_en, sq.template_es, sq.category, sq.type,
                       sq.options_en, sq.options_es, sa.answer_en
                FROM survey_answers sa
                JOIN survey_questions sq ON sq.id = sa.question_id
                WHERE sa.user_id = ?
                  AND sa.question_id NOT IN (
                    SELECT question_id FROM quiz_results
                    WHERE guesser_id = ? AND subject_id = ?
                  )
                """,
                (sid, user_id, sid),
            ) as cur:
                raw = [dict(r) for r in await cur.fetchall()]
            random.shuffle(raw)
            for r in raw:
                r["options_en"] = json.loads(r["options_en"])
                r["options_es"] = json.loads(r["options_es"])
                r["correct_en"] = r.pop("answer_en")
            if raw:
                pools[sid] = raw

        if not pools:
            return RedirectResponse("/quiz")

        # Per-subject historical quiz count for weighting.
        times_quizzed: dict[int, int] = {}
        for sid in pools.keys():
            async with db.execute(
                "SELECT COUNT(*) AS cnt FROM quiz_results WHERE guesser_id=? AND subject_id=?",
                (user_id, sid),
            ) as cur:
                times_quizzed[sid] = (await cur.fetchone())["cnt"]

        questions = build_quiz_session(pools, times_quizzed, count=QUIZ_LENGTH)
        if not questions:
            return RedirectResponse("/quiz")

        session_id = str(uuid.uuid4())
        placeholder_subject = questions[0]["subject_id"]
        await db.execute(
            "INSERT INTO quiz_sessions(session_id, subject_id, questions_json) VALUES(?,?,?)",
            (session_id, placeholder_subject, json.dumps(questions)),
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

        subject_id = q["subject_id"]
        async with db.execute(
            "SELECT id, name, photo_filename FROM users WHERE id=?", (subject_id,)
        ) as cur:
            subject = await cur.fetchone()
        if not subject:
            # Subject deleted mid-session — skip.
            await db.execute(
                "UPDATE quiz_sessions SET current_index=? WHERE session_id=?",
                (idx + 1, session_id),
            )
            await db.commit()
            return RedirectResponse("/quiz/question", status_code=303)

        template = q["template_es"] if lang == "es" else q["template_en"]
        question_text = for_quiz(template, subject["name"])
        cat = category_label(q["category"], lang)

        return templates.TemplateResponse(
            "quiz/question.html",
            {
                "request": request,
                "lang": lang,
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
async def quiz_answer(request: Request, guessed_en: str = Form(...)):
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
        subject_id = q["subject_id"]

        is_correct = int(guessed_en == q["correct_en"])
        new_score = score + is_correct

        await db.execute(
            """
            INSERT INTO quiz_results(guesser_id, subject_id, question_id, guessed_en, correct_en, is_correct)
            VALUES(?,?,?,?,?,?)
            """,
            (user_id, subject_id, q["question_id"], guessed_en, q["correct_en"], is_correct),
        )
        await db.execute(
            "UPDATE quiz_sessions SET current_index=?, score=? WHERE session_id=?",
            (idx + 1, new_score, session_id),
        )
        await db.commit()

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
            "SELECT name FROM users WHERE id=?", (subject_id,)
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
                "subject_name": subject["name"] if subject else "?",
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
        total = len(questions)
        score = sess["score"]
        subject_ids = list({q["subject_id"] for q in questions})

        placeholders = ",".join("?" * len(subject_ids))
        async with db.execute(
            f"""
            SELECT qr.subject_id, qr.is_correct, u.name, u.photo_filename
            FROM quiz_results qr
            JOIN users u ON u.id = qr.subject_id
            WHERE qr.guesser_id = ? AND qr.subject_id IN ({placeholders})
            ORDER BY qr.id DESC
            LIMIT ?
            """,
            (user_id, *subject_ids, total),
        ) as cur:
            recent_results = await cur.fetchall()

        by_subject: dict[int, dict] = {}
        for r in recent_results:
            sid = r["subject_id"]
            if sid not in by_subject:
                by_subject[sid] = {
                    "subject_id": sid,
                    "name": r["name"],
                    "photo_filename": r["photo_filename"],
                    "correct": 0,
                    "total": 0,
                }
            by_subject[sid]["correct"] += r["is_correct"] or 0
            by_subject[sid]["total"] += 1

        breakdown = sorted(
            by_subject.values(),
            key=lambda s: (-(s["correct"] / s["total"]) if s["total"] else 0, s["name"]),
        )
        for s in breakdown:
            s["pct"] = round(100 * s["correct"] / s["total"]) if s["total"] else 0

        suggest_subject = None
        if breakdown:
            worst = min(breakdown, key=lambda s: (s["correct"] / s["total"]) if s["total"] else 1)
            if worst["total"] > 0 and (worst["correct"] / worst["total"]) < 0.6:
                suggest_subject = worst

        request.session.pop("quiz_session_id", None)
        await db.execute("DELETE FROM quiz_sessions WHERE session_id=?", (session_id,))
        await db.commit()

        return templates.TemplateResponse(
            "quiz/results.html",
            {
                "request": request,
                "lang": lang,
                "score": score,
                "total": total,
                "breakdown": breakdown,
                "suggest_subject": suggest_subject,
            },
        )
    finally:
        await db.close()
