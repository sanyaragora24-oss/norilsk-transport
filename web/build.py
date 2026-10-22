#!/usr/bin/env python3
"""Собирает веб-версию (PWA) в каталог dist/.

Данные берутся из тех же файлов, что и в Android-приложении
(android/app/src/main/assets), — маршруты и расписание не дублируются вручную.
Координаты округляются до 5 знаков (~1 м), чтобы файл был меньше.

    python3 web/build.py            # -> web/dist
"""
import hashlib
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WEB = ROOT / "web"
ASSETS = ROOT / "android" / "app" / "src" / "main" / "assets"
DIST = WEB / "dist"

STATIC = ["index.html", "app.css", "app.js", "manifest.webmanifest", "sw.js"]
STATIC_DIRS = ["vendor", "icons"]


def color(argb: int) -> str:
    return "#%06x" % (argb & 0xFFFFFF)


import re

_ABBR = [
    ("административно-деловой центр", "адц"),
    ("торгово-бытовой комплекс", "тбк"),
    ("торговый центр", "тц"),
    ("5-й м-н", "5 микрорайон"),
    ("5-й микрорайон", "5 микрорайон"),
    ("№", " "),
]
_STOP_WORDS = {"по", "улица", "норильск", "кайеркан", "талнах", "д", "дом"}


def _tokens(s: str) -> set:
    s = s.lower()
    for a, b in _ABBR:
        s = s.replace(a, b)
    return {t for t in re.split(r"[^0-9a-zа-яё]+", s) if t and t not in _STOP_WORDS}


def departure_terminal(origin: str, timetable: list):
    """Индекс конечной в расписании, откуда уходит это направление, или None.

    В расписании НПОПАТ конечные названы иначе, чем в маршрутах
    («Кайеркан (ТБК)» против «Торгово-бытовой комплекс»), поэтому сравниваем
    значимые слова. Если совпадения нет — честно не угадываем.
    """
    want = _tokens(origin)
    best, best_score = None, 0
    for i, t in enumerate(timetable):
        score = len(want & _tokens(t["t"]))
        if score > best_score:
            best, best_score = i, score
    return best


def build_data():
    routes = json.loads((ASSETS / "norilsk_routes.json").read_text("utf-8"))["routes"]
    schedule = json.loads((ASSETS / "norilsk_schedule.json").read_text("utf-8"))
    out_routes = []
    for r in routes:
        sch = schedule["routes"].get(r["id"])
        out_routes.append({
            "id": r["id"],
            "n": r["number"],
            "from": r["origin"],
            "to": r["destination"],
            "c": color(r["colorArgb"]),
            "stops": [[s["id"], s["name"], round(s["lat"], 5), round(s["lon"], 5)] for s in r["stops"]],
            "line": [[round(p["lat"], 5), round(p["lon"], 5)] for p in r["polyline"]],
            "sch": None if not sch or not sch.get("hasSchedule") else [
                {"t": t["terminal"], "wd": t["weekday"], "we": t["weekend"]} for t in sch.get("timetable", [])
            ] or [{"t": sch.get("terminal", ""), "wd": sch.get("weekday", []), "we": sch.get("weekend", [])}],
            "src": sch.get("sourceUrl") if sch else None,
        })
        last = out_routes[-1]
        last["dep"] = departure_terminal(r["origin"], last["sch"]) if last["sch"] else None
    return {"dataDate": schedule.get("dataDate", ""), "routes": out_routes}


def main():
    if DIST.exists():
        shutil.rmtree(DIST)
    DIST.mkdir(parents=True)
    data = build_data()
    raw = json.dumps(data, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    (DIST / "data.json").write_bytes(raw)
    for f in STATIC:
        shutil.copy2(WEB / f, DIST / f)
    for d in STATIC_DIRS:
        shutil.copytree(WEB / d, DIST / d)
    # Версия кэша зависит от содержимого: после обновления телефон сам подтянет новое.
    h = hashlib.sha256(raw)
    for f in STATIC:
        h.update((WEB / f).read_bytes())
    version = h.hexdigest()[:10]
    sw = (DIST / "sw.js").read_text("utf-8").replace("__BUILD__", version)
    (DIST / "sw.js").write_text(sw, "utf-8")
    html = (DIST / "index.html").read_text("utf-8").replace("__BUILD__", version)
    (DIST / "index.html").write_text(html, "utf-8")
    (DIST / ".nojekyll").write_text("")
    n31 = [r for r in data["routes"] if r["n"] in ("31", "31Э")]
    print(f"dist ready: {len(data['routes'])} routes ({len(n31)} variants of 31/31Э), "
          f"data {len(raw)//1024} KB, build {version}, schedule date {data['dataDate']}")
    if len(n31) != 12:
        sys.exit("route 31/31Э variants missing")


if __name__ == "__main__":
    main()
