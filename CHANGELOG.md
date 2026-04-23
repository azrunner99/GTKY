# Changelog

## UX Fix Pack

- **Fix 2 — Connections defaults to "My Connections"** — Renamed `ConnectionMode` → `ConnectionScope` (MINE/EVERYONE) + `ConnectionDirection` (MUTUAL/ONE_WAY). Connections screen now defaults to MINE scope, showing "You & $name" rows and "You know / knows you" one-way labels. Everyone view still accessible via scope toggle. MINE empty state explains how connections are earned.
- **Fix 1 — Quiz unlock threshold lowered to 8** — Introduced `Constants.QUIZ_UNLOCK_THRESHOLD = 8` and `QUIZ_MIN_QUESTIONS_BEFORE_FINISH = 5` as single sources of truth. Home screen now shows a 3-state subtitle ("Answer N more", "wait for others", or nothing). Survey now shows a `LinearProgressIndicator` and category label. Milestone toast fires when the threshold is first crossed. All six hardcoded `15` references replaced.
