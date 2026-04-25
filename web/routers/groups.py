from fastapi import APIRouter, Request, Form
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from config import TEMPLATES_DIR
from database import get_db
from routers.admin import is_admin

router = APIRouter(prefix="/groups")
templates = Jinja2Templates(directory=str(TEMPLATES_DIR))


@router.get("", response_class=HTMLResponse)
async def groups_list(request: Request):
    user_id = request.session.get("user_id")
    if not user_id:
        return RedirectResponse("/")
    lang = request.session.get("lang", "en")

    db = await get_db()
    try:
        async with db.execute(
            """
            SELECT g.id, g.name,
                   COUNT(ugm.user_id) as member_count,
                   MAX(CASE WHEN ugm.user_id=? THEN 1 ELSE 0 END) as is_member
            FROM groups g
            LEFT JOIN user_group_memberships ugm ON ugm.group_id = g.id
            GROUP BY g.id
            ORDER BY g.name
            """,
            (user_id,),
        ) as cur:
            groups = [dict(r) for r in await cur.fetchall()]

        return templates.TemplateResponse(
            "groups/groups.html",
            {"request": request, "lang": lang, "groups": groups},
        )
    finally:
        await db.close()


@router.post("/create", response_class=HTMLResponse)
async def create_group(request: Request, name: str = Form(...)):
    user_id = request.session.get("user_id")
    if not user_id:
        return RedirectResponse("/")
    if not is_admin(request):
        return RedirectResponse("/groups", status_code=303)
    name = name.strip()
    if not name:
        return RedirectResponse("/admin/groups", status_code=303)

    db = await get_db()
    try:
        try:
            await db.execute(
                "INSERT INTO groups(name, created_by) VALUES(?,?)", (name, user_id)
            )
            await db.commit()
        except Exception:
            pass
    finally:
        await db.close()

    return RedirectResponse("/admin/groups", status_code=303)


@router.post("/{group_id:int}/join")
async def join_group(request: Request, group_id: int):
    user_id = request.session.get("user_id")
    if not user_id:
        return RedirectResponse("/")
    db = await get_db()
    try:
        await db.execute(
            "INSERT OR IGNORE INTO user_group_memberships(user_id, group_id) VALUES(?,?)",
            (user_id, group_id),
        )
        await db.commit()
    finally:
        await db.close()
    return RedirectResponse("/groups", status_code=303)


@router.post("/{group_id:int}/leave")
async def leave_group(request: Request, group_id: int):
    user_id = request.session.get("user_id")
    if not user_id:
        return RedirectResponse("/")
    db = await get_db()
    try:
        await db.execute(
            "DELETE FROM user_group_memberships WHERE user_id=? AND group_id=?",
            (user_id, group_id),
        )
        await db.commit()
    finally:
        await db.close()
    return RedirectResponse("/groups", status_code=303)
