from fastapi import APIRouter, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from config import TEMPLATES_DIR
from database import get_db

router = APIRouter(prefix="/connections")
templates = Jinja2Templates(directory=str(TEMPLATES_DIR))


@router.get("", response_class=HTMLResponse)
async def connections(request: Request, scope: str = "mine"):
    user_id = request.session.get("user_id")
    if not user_id:
        return RedirectResponse("/")
    lang = request.session.get("lang", "en")
    if scope not in ("mine", "everyone"):
        scope = "mine"

    db = await get_db()
    try:
        if scope == "mine":
            async with db.execute(
                """
                SELECT u.id, u.name, u.photo_filename,
                    (SELECT COUNT(*) FROM quiz_results WHERE guesser_id=? AND subject_id=u.id) AS my_total,
                    (SELECT COALESCE(SUM(is_correct),0) FROM quiz_results WHERE guesser_id=? AND subject_id=u.id) AS my_correct,
                    (SELECT COUNT(*) FROM quiz_results WHERE guesser_id=u.id AND subject_id=?) AS their_total,
                    (SELECT COALESCE(SUM(is_correct),0) FROM quiz_results WHERE guesser_id=u.id AND subject_id=?) AS their_correct
                FROM users u
                WHERE u.id != ?
                """,
                (user_id, user_id, user_id, user_id, user_id),
            ) as cur:
                rows = [dict(r) for r in await cur.fetchall()]

            connections_list = [r for r in rows if r["my_total"] > 0 or r["their_total"] > 0]
            for c in connections_list:
                c["my_pct"] = round(100 * c["my_correct"] / c["my_total"]) if c["my_total"] else None
                c["their_pct"] = round(100 * c["their_correct"] / c["their_total"]) if c["their_total"] else None
                pcts = [p for p in (c["my_pct"], c["their_pct"]) if p is not None]
                c["mutual_avg"] = sum(pcts) / len(pcts) if pcts else 0
            connections_list.sort(key=lambda c: c["mutual_avg"], reverse=True)
        else:
            async with db.execute(
                """
                SELECT
                    MIN(qr.guesser_id, qr.subject_id) AS user_a_id,
                    MAX(qr.guesser_id, qr.subject_id) AS user_b_id,
                    SUM(CASE WHEN qr.guesser_id = MIN(qr.guesser_id, qr.subject_id) THEN qr.is_correct ELSE 0 END) AS a_correct,
                    SUM(CASE WHEN qr.guesser_id = MIN(qr.guesser_id, qr.subject_id) THEN 1 ELSE 0 END) AS a_total,
                    SUM(CASE WHEN qr.guesser_id = MAX(qr.guesser_id, qr.subject_id) THEN qr.is_correct ELSE 0 END) AS b_correct,
                    SUM(CASE WHEN qr.guesser_id = MAX(qr.guesser_id, qr.subject_id) THEN 1 ELSE 0 END) AS b_total
                FROM quiz_results qr
                GROUP BY user_a_id, user_b_id
                """
            ) as cur:
                pair_rows = [dict(r) for r in await cur.fetchall()]

            user_ids = set()
            for r in pair_rows:
                user_ids.add(r["user_a_id"])
                user_ids.add(r["user_b_id"])
            if user_ids:
                placeholders = ",".join("?" * len(user_ids))
                async with db.execute(
                    f"SELECT id, name, photo_filename FROM users WHERE id IN ({placeholders})",
                    tuple(user_ids),
                ) as cur:
                    lookup = {r["id"]: dict(r) for r in await cur.fetchall()}
            else:
                lookup = {}

            connections_list = []
            for r in pair_rows:
                a = lookup.get(r["user_a_id"])
                b = lookup.get(r["user_b_id"])
                if not a or not b:
                    continue
                a_pct = round(100 * r["a_correct"] / r["a_total"]) if r["a_total"] else None
                b_pct = round(100 * r["b_correct"] / r["b_total"]) if r["b_total"] else None
                pcts = [p for p in (a_pct, b_pct) if p is not None]
                mutual_avg = sum(pcts) / len(pcts) if pcts else 0
                connections_list.append({
                    "user_a": a, "user_b": b,
                    "a_correct": r["a_correct"], "a_total": r["a_total"], "a_pct": a_pct,
                    "b_correct": r["b_correct"], "b_total": r["b_total"], "b_pct": b_pct,
                    "mutual_avg": mutual_avg,
                })
            connections_list.sort(key=lambda c: c["mutual_avg"], reverse=True)

        return templates.TemplateResponse(
            "connections/connections.html",
            {
                "request": request,
                "lang": lang,
                "scope": scope,
                "connections": connections_list,
            },
        )
    finally:
        await db.close()
