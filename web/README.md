# GTKY Web App

A browser-based version of the GTKY icebreaker app. Runs on a local server and is accessible by any device on the same WiFi network.

## Setup

```bash
cd web
pip install -r requirements.txt
python main.py
```

The app will print both a local URL and a network URL:
```
Local:   http://localhost:8000
Network: http://192.168.x.x:8000
```

Share the Network URL with others on your WiFi.

## Stack

- **FastAPI** — Python web framework
- **SQLite** (WAL mode) — embedded database, no server needed
- **Jinja2** — HTML templates
- **PicoCSS** — minimal CSS framework (CDN)
- **Starlette sessions** — signed cookie sessions
- **Pillow** — photo resize/crop

## Features

- Sign in by name (no password)
- Survey questions about yourself (700+ bilingual EN/ES questions)
- Quiz: guess how others answered
- Connections: see your scores with each person
- Active Users list with profiles
- Groups: create and join subgroups
- Admin panel (PIN protected) — manage users, photos, change PIN
- About page
- English/Spanish language toggle
- Photo upload for profile avatars
