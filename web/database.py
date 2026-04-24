import aiosqlite
from config import DB_PATH

DDL = [
    """
    CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL UNIQUE,
        photo_filename TEXT,
        created_at TEXT NOT NULL DEFAULT (datetime('now'))
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS survey_questions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        template_en TEXT NOT NULL,
        template_es TEXT NOT NULL,
        category TEXT NOT NULL,
        type TEXT NOT NULL CHECK(type IN ('tf', 'mc')),
        options_en TEXT NOT NULL,
        options_es TEXT NOT NULL
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS survey_answers (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        question_id INTEGER NOT NULL REFERENCES survey_questions(id),
        answer_en TEXT NOT NULL,
        answered_at TEXT NOT NULL DEFAULT (datetime('now')),
        UNIQUE(user_id, question_id)
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS quiz_results (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        guesser_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        subject_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        question_id INTEGER NOT NULL REFERENCES survey_questions(id),
        guessed_en TEXT NOT NULL,
        correct_en TEXT,
        is_correct INTEGER,
        quizzed_at TEXT NOT NULL DEFAULT (datetime('now'))
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS quiz_sessions (
        session_id TEXT PRIMARY KEY,
        subject_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        questions_json TEXT NOT NULL,
        current_index INTEGER NOT NULL DEFAULT 0,
        score INTEGER NOT NULL DEFAULT 0,
        created_at TEXT NOT NULL DEFAULT (datetime('now'))
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS groups (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL UNIQUE,
        created_by INTEGER REFERENCES users(id) ON DELETE SET NULL,
        created_at TEXT NOT NULL DEFAULT (datetime('now'))
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS user_group_memberships (
        user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        group_id INTEGER NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
        PRIMARY KEY (user_id, group_id)
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS app_config (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
    )
    """,
]


async def init_db():
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute("PRAGMA journal_mode=WAL")
        await db.execute("PRAGMA foreign_keys=ON")
        for stmt in DDL:
            await db.execute(stmt)
        await db.commit()


async def get_db() -> aiosqlite.Connection:
    db = await aiosqlite.connect(DB_PATH)
    db.row_factory = aiosqlite.Row
    await db.execute("PRAGMA journal_mode=WAL")
    await db.execute("PRAGMA foreign_keys=ON")
    return db


async def config_get(db: aiosqlite.Connection, key: str) -> str | None:
    async with db.execute("SELECT value FROM app_config WHERE key = ?", (key,)) as cur:
        row = await cur.fetchone()
        return row["value"] if row else None


async def config_set(db: aiosqlite.Connection, key: str, value: str):
    await db.execute(
        "INSERT INTO app_config(key, value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
        (key, value),
    )
