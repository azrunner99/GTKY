import json
from fastapi import APIRouter, Request, Form
from services.question_phrasing import for_quiz
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from config import TEMPLATES_DIR, PHOTOS_DIR
from database import get_db, config_get, config_set

router = APIRouter(prefix="/admin")
templates = Jinja2Templates(directory=str(TEMPLATES_DIR))


def is_admin(request: Request) -> bool:
    return bool(request.session.get("admin_authenticated"))


@router.get("", response_class=HTMLResponse)
async def admin_home(request: Request):
    if not request.session.get("user_id"):
        return RedirectResponse("/")
    lang = request.session.get("lang", "en")

    if not is_admin(request):
        return templates.TemplateResponse(
            "admin/login.html", {"request": request, "lang": lang, "error": None}
        )

    db = await get_db()
    try:
        async with db.execute(
            """
            SELECT u.id, u.name, u.photo_filename, u.created_at,
                   COUNT(sa.id) as answered
            FROM users u
            LEFT JOIN survey_answers sa ON sa.user_id = u.id
            GROUP BY u.id
            ORDER BY u.created_at DESC
            """
        ) as cur:
            users = [dict(r) for r in await cur.fetchall()]

        pin_is_default = await config_get(db, "admin_pin_is_default") == "true"

        return templates.TemplateResponse(
            "admin/admin.html",
            {
                "request": request,
                "lang": lang,
                "users": users,
                "pin_is_default": pin_is_default,
            },
        )
    finally:
        await db.close()


@router.post("/login", response_class=HTMLResponse)
async def admin_login(request: Request, pin: str = Form(...)):
    lang = request.session.get("lang", "en")
    db = await get_db()
    try:
        stored_pin = await config_get(db, "admin_pin") or "1234"
    finally:
        await db.close()

    if pin == stored_pin:
        request.session["admin_authenticated"] = True
        return RedirectResponse("/admin", status_code=303)

    return templates.TemplateResponse(
        "admin/login.html",
        {"request": request, "lang": lang, "error": "Incorrect PIN."},
    )


@router.post("/logout")
async def admin_logout(request: Request):
    request.session.pop("admin_authenticated", None)
    return RedirectResponse("/admin", status_code=303)


@router.post("/pin", response_class=HTMLResponse)
async def change_pin(request: Request, new_pin: str = Form(...)):
    if not is_admin(request):
        return RedirectResponse("/admin")
    new_pin = new_pin.strip()
    if len(new_pin) < 4 or not new_pin.isdigit():
        return RedirectResponse("/admin")

    db = await get_db()
    try:
        await config_set(db, "admin_pin", new_pin)
        await config_set(db, "admin_pin_is_default", "false")
        await db.commit()
    finally:
        await db.close()

    return RedirectResponse("/admin", status_code=303)


@router.get("/users/{uid:int}/answers", response_class=HTMLResponse)
async def admin_user_answers(request: Request, uid: int):
    if not is_admin(request):
        return RedirectResponse("/admin")
    lang = request.session.get("lang", "en")

    db = await get_db()
    try:
        async with db.execute(
            "SELECT id, name, photo_filename FROM users WHERE id=?", (uid,)
        ) as cur:
            user = await cur.fetchone()
        if not user:
            return RedirectResponse("/admin")

        async with db.execute(
            """
            SELECT sq.template_en, sq.template_es, sq.category,
                   sq.options_en, sq.options_es, sa.answer_en, sa.answered_at
            FROM survey_answers sa
            JOIN survey_questions sq ON sq.id = sa.question_id
            WHERE sa.user_id = ?
            ORDER BY sq.category, sq.id
            """,
            (uid,),
        ) as cur:
            rows = await cur.fetchall()

        answers = []
        for r in rows:
            opts_en = json.loads(r["options_en"])
            opts_es = json.loads(r["options_es"])
            try:
                idx = opts_en.index(r["answer_en"])
                answer_display = opts_es[idx] if lang == "es" and idx < len(opts_es) else r["answer_en"]
            except ValueError:
                answer_display = r["answer_en"]

            template = r["template_es"] if lang == "es" else r["template_en"]
            question_text = for_quiz(template, user["name"])
            answers.append({
                "question": question_text,
                "answer": answer_display,
                "category": r["category"],
            })

        return templates.TemplateResponse(
            "admin/user_answers.html",
            {
                "request": request,
                "lang": lang,
                "user": dict(user),
                "answers": answers,
            },
        )
    finally:
        await db.close()


@router.post("/groups/{gid:int}/delete")
async def admin_delete_group(request: Request, gid: int):
    if not is_admin(request):
        return RedirectResponse("/admin")
    db = await get_db()
    try:
        await db.execute("DELETE FROM groups WHERE id=?", (gid,))
        await db.commit()
    finally:
        await db.close()
    return RedirectResponse("/admin/groups", status_code=303)


@router.post("/users/{uid:int}/delete")
async def delete_user(request: Request, uid: int):
    if not is_admin(request):
        return RedirectResponse("/admin")

    db = await get_db()
    try:
        async with db.execute(
            "SELECT photo_filename FROM users WHERE id=?", (uid,)
        ) as cur:
            row = await cur.fetchone()
        if row and row["photo_filename"]:
            path = PHOTOS_DIR / row["photo_filename"]
            path.unlink(missing_ok=True)
        await db.execute("DELETE FROM users WHERE id=?", (uid,))
        await db.commit()
    finally:
        await db.close()

    # If admin deleted themselves, clear session
    if request.session.get("user_id") == uid:
        request.session.clear()
        return RedirectResponse("/", status_code=303)

    return RedirectResponse("/admin", status_code=303)


@router.post("/users/{uid:int}/photo/delete")
async def admin_delete_photo(request: Request, uid: int):
    if not is_admin(request):
        return RedirectResponse("/admin")

    db = await get_db()
    try:
        async with db.execute(
            "SELECT photo_filename FROM users WHERE id=?", (uid,)
        ) as cur:
            row = await cur.fetchone()
        if row and row["photo_filename"]:
            path = PHOTOS_DIR / row["photo_filename"]
            path.unlink(missing_ok=True)
        await db.execute("UPDATE users SET photo_filename=NULL WHERE id=?", (uid,))
        await db.commit()
    finally:
        await db.close()

    return RedirectResponse("/admin", status_code=303)
