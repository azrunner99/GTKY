import json, os, shutil

p = os.path.expanduser("~/.claude/settings.json")
shutil.copy(p, p + ".bak")

with open(p) as f:
    s = json.load(f)

s.get("hooks", {}).pop("SessionStart", None)

with open(p, "w") as f:
    json.dump(s, f, indent=2)

print("SessionStart hooks removed. Backup saved to settings.json.bak")
