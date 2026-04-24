import io
from fastapi import APIRouter, Request, UploadFile, File
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from config import TEMPLATES_DIR, PHOTOS_DIR, PHOTO_MAX_SIZE, PHOTO_QUALITY
from database import get_db

router = APIRouter(prefix="/profile")
templates = Jinja2Templates(directory=str(TEMPLATES_DIR))


@router.get("/{user_id:int}", response_class=HTMLResponse)
async def profile(request: Request, user_id: int):
    viewer_id = request.session.get("user_id")
    if not viewer_id:
        return RedirectResponse("/")
    lang = request.session.get("lang", "en")

    db = await get_db()
    try:
        async with db.execute(
            "SELECT id, name, photo_filename FROM users WHERE id=?", (user_id,)
        ) as cur:
            subject = await cur.fetchone()
        if not subject:
            return RedirectResponse("/users")

        async with db.execute(
            "SELECT COUNT(*) AS cnt FROM survey_answers WHERE user_id=?", (user_id,)
        ) as cur:
            answer_count = (await cur.fetchone())["cnt"]

        async with db.execute(
            """
            SELECT COUNT(*) AS total, COALESCE(SUM(is_correct), 0) AS correct
            FROM quiz_results
            WHERE guesser_id=? AND subject_id=?
            """,
            (viewer_id, user_id),
        ) as cur:
            row = await cur.fetchone()
            you_total = row["total"]
            you_correct = row["correct"]

        async with db.execute(
            """
            SELECT COUNT(*) AS total, COALESCE(SUM(is_correct), 0) AS correct
            FROM quiz_results
            WHERE guesser_id=? AND subject_id=?
            """,
            (user_id, viewer_id),
        ) as cur:
            row = await cur.fetchone()
            them_total = row["total"]
            them_correct = row["correct"]

        is_own = viewer_id == user_id

        return templates.TemplateResponse(
            "profile/profile.html",
            {
                "request": request,
                "lang": lang,
                "subject": dict(subject),
                "answer_count": answer_count,
                "you_correct": you_correct,
                "you_total": you_total,
                "them_correct": them_correct,
                "them_total": them_total,
                "is_own": is_own,
            },
        )
    finally:
        await db.close()


@router.post("/photo", response_class=HTMLResponse)
async def upload_photo(request: Request, photo: UploadFile = File(...)):
    user_id = request.session.get("user_id")
    if not user_id:
        return RedirectResponse("/")

    try:
        from PIL import Image
        data = await photo.read()
        img = Image.open(io.BytesIO(data)).convert("RGB")
        img.thumbnail(PHOTO_MAX_SIZE)

        filename = f"user_{user_id}.jpg"
        save_path = PHOTOS_DIR / filename
        img.save(save_path, "JPEG", quality=PHOTO_QUALITY)

        db = await get_db()
        try:
            await db.execute(
                "UPDATE users SET photo_filename=? WHERE id=?", (filename, user_id)
            )
            await db.commit()
        finally:
            await db.close()
    except Exception:
        pass

    return RedirectResponse(f"/profile/{user_id}", status_code=303)


@router.post("/photo/delete", response_class=HTMLResponse)
async def delete_photo(request: Request):
    user_id = request.session.get("user_id")
    if not user_id:
        return RedirectResponse("/")

    db = await get_db()
    try:
        async with db.execute(
            "SELECT photo_filename FROM users WHERE id=?", (user_id,)
        ) as cur:
            row = await cur.fetchone()
        if row and row["photo_filename"]:
            path = PHOTOS_DIR / row["photo_filename"]
            if path.exists():
                path.unlink(missing_ok=True)
        await db.execute(
            "UPDATE users SET photo_filename=NULL WHERE id=?", (user_id,)
        )
        await db.commit()
    finally:
        await db.close()

    return RedirectResponse(f"/profile/{user_id}", status_code=303)
