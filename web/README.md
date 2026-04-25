# GTKY Web App

A browser-based version of the GTKY icebreaker app. Runs on a local server and is
accessible by any device on the same WiFi network.

## Setup

```bash
cd web
pip install -r requirements.txt
python main.py
```

The app prints both a local URL and a network URL:

```
Local:   http://localhost:8000
Network: http://192.168.x.x:8000
```

Share the Network URL with people on the same WiFi.

## Configuration

Environment variables:

- **`SESSION_SECRET`** — Used to sign session cookies. If unset, a value is generated
  on first launch and persisted to `web/.session_secret`. Setting this explicitly is
  recommended for production deployments and lets you rotate sessions by changing it.
- **`PORT`** — HTTP port (default 8000).

## Stack

- **FastAPI** — Python web framework
- **SQLite** (WAL mode) — embedded database, no server needed
- **Jinja2** — HTML templates
- **PicoCSS** — minimal CSS framework (CDN)
- **Starlette sessions** — signed cookie sessions
- **Pillow** — photo resize/crop

## Features

- **Sign in by name.** Two paths: "I'm new here" (first/last name fields, normalized to title case) and "I'm already here" (searchable picker of existing users). Similar-name detection prompts ("Is this you?") to prevent duplicate signups.
- **Survey** — 700+ bilingual EN/ES questions across categories. Skip a question to defer it; submit an answer to clear the skip queue. A milestone banner fires when you cross the quiz-unlock threshold.
- **Quiz** — Multi-subject sessions weighted by historical familiarity (people you've quizzed less come up more often). Filter by group, by specific people, or both, with a live pool-size preview. Each question shows a 96px hero avatar of the subject.
- **Connections** — Two scopes: "Mine" (your bidirectional scores with each other user) and "Everyone" (aggregated pair scores across the whole group).
- **Profile** — Public stats card with avatar, name, total answer count, and bidirectional quiz scores. No answer list (admin-only).
- **Photos** — Upload from phone or laptop. Photo prompt appears on signin up to 3 times; on the third it offers "Never ask again."
- **Groups** — Admins create and delete groups; users join/leave themselves. Admins can also pre-assign membership in bulk.
- **Admin panel** — PIN-protected. Default PIN is 1234 — change it on first use. Admins manage users, groups, photos, and can view any user's full answer list.
- **About** — Tap the GTKY wordmark to read what the app is, who it's for, and how it works.
- **English/Spanish toggle** — On every page.

## Operations

- **Idle timeout** — Sessions expire after 5 minutes of inactivity (server-side). Closing a tab doesn't sign out, but a returning user past the idle window will land on the welcome screen.
- **CSRF protection** — Cross-origin POSTs are rejected via an Origin/Referer check. Combined with `SameSite=lax` cookies.
- **Photo limits** — 10 MB max upload size. Photos are center-cropped to square and re-encoded as JPEG at 400×400.
- **Health check** — `GET /health` returns `{"ok": true, "uptime_seconds": N}`.
