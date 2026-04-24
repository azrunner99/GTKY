from fastapi import APIRouter, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from config import TEMPLATES_DIR
from database import get_db

router = APIRouter(prefix="/connections")
templates = Jinja2Templates(directory=str(TEMPLATES_DIR))


@router.get("", response_class=HTMLResponse)
async def connections(request: Request):
    user_id = request.session.get("user_id")
    if not user_id:
        return RedirectResponse("/")
    lang = request.session.get("lang", "en")

    db = await get_db()
    try:
        # Get quiz scores with each subject
        async with db.execute(
            """
            SELECT u.id, u.name, u.photo_filename,
                   COUNT(qr.id) as quizzed,
                   SUM(qr.is_correct) as correct
            FROM quiz_results qr
            JOIN users u ON u.id = qr.subject_id
            WHERE qr.guesser_id = ?
            GROUP BY u.id
            ORDER BY (CAST(SUM(qr.is_correct) AS REAL) / COUNT(qr.id)) DESC
            """,
            (user_id,),
        ) as cur:
            my_scores = [dict(r) for r in await cur.fetchall()]

        for s in my_scores:
            s["pct"] = round(100 * s["correct"] / s["quizzed"]) if s["quizzed"] else 0

        # Get scores others have on me
        async with db.execute(
            """
            SELECT u.id, u.name, u.photo_filename,
                   COUNT(qr.id) as quizzed,
                   SUM(qr.is_correct) as correct
            FROM quiz_results qr
            JOIN users u ON u.id = qr.guesser_id
            WHERE qr.subject_id = ?
            GROUP BY u.id
            ORDER BY (CAST(SUM(qr.is_correct) AS REAL) / COUNT(qr.id)) DESC
            """,
            (user_id,),
        ) as cur:
            their_scores = [dict(r) for r in await cur.fetchall()]

        for s in their_scores:
            s["pct"] = round(100 * s["correct"] / s["quizzed"]) if s["quizzed"] else 0

        return templates.TemplateResponse(
            "connections/connections.html",
            {
                "request": request,
                "lang": lang,
                "my_scores": my_scores,
                "their_scores": their_scores,
            },
        )
    finally:
        await db.close()
