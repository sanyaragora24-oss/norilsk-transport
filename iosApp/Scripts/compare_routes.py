#!/usr/bin/env python3
"""Сравнение старого и нового norilsk_routes.json.

Инструмент для приёмки актуальных геометрий из Android-проекта.
НИЧЕГО не меняет: только читает два файла и печатает отчёт.

Запуск из корня репозитория:

    python3 iosApp/Scripts/compare_routes.py --new ~/Downloads/norilsk_routes.json

По умолчанию «старый» файл — iosApp/Resources/norilsk_routes.json.
Если --old не задан явно, сводка по新旧 печатается для одного файла
(режим «только новый»), что удобно для первичного осмотра.

Отчёт содержит ровно те разделы, которые нужны для приёмки:
  1. количество направлений и схема файла
  2. добавленные / удалённые / изменённые ID
  3. маршрут 31
  4. все варианты 31Э
  5. точки геометрии и длины
  6. остановки
  7. подозрительные прямые линии
  8. сверка с norilsk_schedule.json (у кого нет геометрии / нет расписания)
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
from pathlib import Path

EARTH_RADIUS = 6_371_000.0


# --------------------------------------------------------------------------- чтение

def first_key(obj: dict, *names, default=None):
    for name in names:
        if isinstance(obj, dict) and name in obj and obj[name] is not None:
            return obj[name]
    return default


NORILSK_BBOX = (67.0, 71.0, 84.0, 94.0)  # lat_min, lat_max, lon_min, lon_max


def decode_polyline(value: str, precision: int):
    """Раскодировка «свёрнутой» полилинии (Google / MapKit стиль)."""
    points, index, lat, lon, factor = [], 0, 0, 0, float(10 ** precision)
    length = len(value)
    while index < length:
        for is_lat in (True, False):
            result, shift = 0, 0
            while index < length:
                byte = ord(value[index]) - 63
                index += 1
                result |= (byte & 0x1F) << shift
                shift += 5
                if byte < 0x20:
                    break
            else:
                return []
            delta = ~(result >> 1) if (result & 1) else (result >> 1)
            if is_lat:
                lat += delta
            else:
                lon += delta
        points.append((lat / factor, lon / factor))
    return points


def in_norilsk(points):
    lat_min, lat_max, lon_min, lon_max = NORILSK_BBOX
    return bool(points) and all(lat_min <= lat <= lat_max and lon_min <= lon <= lon_max
                                for lat, lon in points[:5])


def decode_polyline_auto(value: str):
    """Пробуем точность 1e-6 и 1e-5, выбираем ту, что попадает в район Норильска."""
    variants = [decode_polyline(value, precision) for precision in (6, 5)]
    return next((v for v in variants if in_norilsk(v)), variants[0])


def load_points(raw):
    """Точки полилинии: поддерживаем {lat,lon}, {latitude,longitude}, [lat, lon] и строку."""
    if isinstance(raw, str) and raw.strip():
        return decode_polyline_auto(raw.strip())
    points = []
    for item in raw or []:
        if isinstance(item, dict):
            lat = first_key(item, "lat", "latitude")
            lon = first_key(item, "lon", "lng", "longitude")
        elif isinstance(item, (list, tuple)) and len(item) >= 2:
            lat, lon = item[0], item[1]
        else:
            continue
        if lat is None or lon is None:
            continue
        try:
            points.append((float(lat), float(lon)))
        except (TypeError, ValueError):
            continue
    return points


def load_stops(raw):
    stops = []
    for item in raw or []:
        if not isinstance(item, dict):
            continue
        stop_id = first_key(item, "id", "stopId")
        name = first_key(item, "name", "title", default="")
        lat = first_key(item, "lat", "latitude", default=0.0)
        lon = first_key(item, "lon", "lng", "longitude", default=0.0)
        try:
            stops.append({
                "id": int(stop_id) if stop_id is not None else None,
                "name": str(name),
                "lat": float(lat),
                "lon": float(lon),
            })
        except (TypeError, ValueError):
            continue
    return stops


def load_routes(path: Path):
    """Возвращает (routes, top_level_keys, schema_keys)."""
    data = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(data, dict):
        top_keys = sorted(data.keys())
        raw_routes = first_key(data, "routes", "data", default=[])
    elif isinstance(data, list):
        top_keys = ["<массив>"]
        raw_routes = data
    else:
        raise SystemExit(f"неожиданная структура файла: {type(data)}")

    if isinstance(raw_routes, dict):
        raw_routes = list(raw_routes.values())

    routes = []
    schema_keys = []
    for raw in raw_routes:
        if not isinstance(raw, dict):
            continue
        if not schema_keys:
            schema_keys = sorted(raw.keys())
        raw_line = first_key(raw, "polyline", "geometry", "points", default=[])
        if isinstance(raw_line, dict):  # {"points": [...]} или {"encoded": "..."}
            raw_line = first_key(raw_line, "points", "polyline", "encoded",
                                 "coordinates", default=[])
        points = load_points(raw_line)
        routes.append({
            "id": str(first_key(raw, "id", "routeId", default="")),
            "busId": first_key(raw, "busId", "bus_id"),
            "number": str(first_key(raw, "number", "routeNumber", "name", default="")),
            "direction": first_key(raw, "direction", default=None),
            "origin": str(first_key(raw, "origin", "from", "start", default="")),
            "destination": str(first_key(raw, "destination", "to", "end", default="")),
            "colorArgb": first_key(raw, "colorArgb", "color"),
            "points": points,
            "stops": load_stops(first_key(raw, "stops", default=[])),
        })
    return routes, top_keys, schema_keys


def load_schedules(path: Path):
    """Расписания: id -> номер, число терминалов и число рейсов."""
    data = json.loads(path.read_text(encoding="utf-8"))
    raw = first_key(data, "routes", "data", default=data) if isinstance(data, dict) else data
    if isinstance(raw, list):
        raw = {str(first_key(r, "id", "routeId", default=i)): r for i, r in enumerate(raw)}

    result = {}
    for rid, entry in (raw or {}).items():
        if not isinstance(entry, dict):
            continue
        terminals = entry.get("timetable") or []
        names, departures = [], 0
        if isinstance(terminals, list) and terminals:
            for term in terminals:
                if not isinstance(term, dict):
                    continue
                names.append(str(first_key(term, "terminal", "name", default="")))
                departures += len(term.get("weekday") or []) + len(term.get("weekend") or [])
        else:  # «плоский» вариант: terminal/weekday/weekend лежат в самой записи
            names.append(str(first_key(entry, "terminal", default="")))
            departures += len(entry.get("weekday") or []) + len(entry.get("weekend") or [])
        result[str(rid)] = {
            "number": str(first_key(entry, "number", "routeNumber", default="")),
            "hasSchedule": bool(entry.get("hasSchedule", departures > 0)),
            "terminals": len([n for n in names if n]),
            "terminal_names": [n for n in names if n],
            "departures": departures,
        }
    data_date = data.get("dataDate") if isinstance(data, dict) else None
    return result, data_date


# --------------------------------------------------------------------------- геометрия

def haversine(a, b):
    lat1, lon1 = math.radians(a[0]), math.radians(a[1])
    lat2, lon2 = math.radians(b[0]), math.radians(b[1])
    dlat = lat2 - lat1
    dlon = lon2 - lon1
    h = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 2 * EARTH_RADIUS * math.asin(math.sqrt(h))


def length_m(points):
    return sum(haversine(points[i - 1], points[i]) for i in range(1, len(points)))


def max_segment_m(points):
    return max((haversine(points[i - 1], points[i]) for i in range(1, len(points))), default=0.0)


def detour_ratio(route):
    """Длина по треку / расстояние между концами: 1.0 = идеально прямая."""
    points = route["points"]
    if len(points) < 2:
        return 0.0
    straight = haversine(points[0], points[-1])
    if straight < 100:
        return 0.0
    return length_m(points) / straight


def consecutive_duplicates(route):
    """Повторы подряд: нормальны на остановках, подозрительны в большом количестве."""
    points = route["points"]
    return sum(1 for i in range(1, len(points))
               if round(points[i][0], 6) == round(points[i - 1][0], 6)
               and round(points[i][1], 6) == round(points[i - 1][1], 6))


def geometry_flags(route):
    """Эвристики подозрительной геометрии (прямые линии, разрывы, нули)."""
    points = route["points"]
    flags = []
    if not points:
        return ["НЕТ_ГЕОМЕТРИИ"]
    length = length_m(points)
    detour = detour_ratio(route)

    if length > 1000 and detour and detour < 1.02:
        flags.append(f"ПРЯМАЯ(detour={detour:.3f})")
    elif length > 1000 and detour and detour < 1.10:
        flags.append(f"ПОЧТИ_ПРЯМАЯ(detour={detour:.3f})")
    segment = max_segment_m(points)
    if segment > 800:
        flags.append(f"РАЗРЫВ({segment/1000:.1f} км)")
    if len(points) < 10 and length > 2000:
        flags.append(f"МАЛО_ТОЧЕК({len(points)})")
    if any(abs(p[0]) < 0.01 and abs(p[1]) < 0.01 for p in points):
        flags.append("НУЛЕВЫЕ_КООРДИНАТЫ")
    dupes = consecutive_duplicates(route)
    if dupes > max(3, 0.02 * len(points)):
        flags.append(f"ДУБЛИ_ПОДРЯД({dupes})")
    return flags


def route_signature(route):
    """Подпись содержимого: что именно сравниваем при поиске изменённых ID."""
    payload = json.dumps({
        "number": route["number"],
        "origin": route["origin"],
        "destination": route["destination"],
        "colorArgb": route["colorArgb"],
        "points": [round(p[0], 5) for p in route["points"]] + [round(p[1], 5) for p in route["points"]],
        "stops": [(s["id"], s["name"], round(s["lat"], 5), round(s["lon"], 5)) for s in route["stops"]],
    }, sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:16]


# --------------------------------------------------------------------------- отчёт

def print_table(rows, headers):
    widths = [max(len(str(h)), *(len(str(r[i])) for r in rows)) if rows else len(str(h))
              for i, h in enumerate(headers)]
    line = " | ".join(str(h).ljust(w) for h, w in zip(headers, widths))
    print(line)
    print("-" * len(line))
    for row in rows:
        print(" | ".join(str(v).ljust(w) for v, w in zip(row, widths)))


def describe(route):
    return [
        route["id"],
        route["number"],
        route["direction"] if route["direction"] is not None else "-",
        f"{route['origin']} -> {route['destination']}"[:58],
        len(route["points"]),
        f"{length_m(route['points'])/1000:.1f}",
        f"{detour_ratio(route):.2f}",
        len(route["stops"]),
    ]


def report(old_routes, new_routes, old_path, new_path, old_keys, new_keys,
           schedules=None, schedule_path=None, schedule_date=None):
    print("=" * 100)
    print("1. ФАЙЛЫ И КОЛИЧЕСТВО НАПРАВЛЕНИЙ")
    print("=" * 100)
    print(f"старый: {old_path} ({old_path.stat().st_size if old_path.exists() else 0} байт), "
          f"направлений: {len(old_routes)}")
    print(f"новый:  {new_path} ({new_path.stat().st_size} байт), направлений: {len(new_routes)}")
    print(f"ключи верхнего уровня нового файла: {new_keys}")
    delta = len(new_routes) - len(old_routes)
    print(f"разница: {delta:+d}")

    old_by_id = {r["id"]: r for r in old_routes}
    new_by_id = {r["id"]: r for r in new_routes}

    added = sorted(set(new_by_id) - set(old_by_id))
    removed = sorted(set(old_by_id) - set(new_by_id))
    changed, changed_fields = [], {}
    for rid in sorted(set(old_by_id) & set(new_by_id)):
        if route_signature(old_by_id[rid]) != route_signature(new_by_id[rid]):
            changed.append(rid)
            fields = []
            o, n = old_by_id[rid], new_by_id[rid]
            if o["number"] != n["number"]:
                fields.append("number")
            if (o["origin"], o["destination"]) != (n["origin"], n["destination"]):
                fields.append("конечные")
            if len(o["points"]) != len(n["points"]):
                fields.append(f"точки {len(o['points'])}->{len(n['points'])}")
            elif o["points"] != n["points"]:
                fields.append("геометрия")
            if [s["id"] for s in o["stops"]] != [s["id"] for s in n["stops"]]:
                fields.append(f"остановки {len(o['stops'])}->{len(n['stops'])}")
            if o["colorArgb"] != n["colorArgb"]:
                fields.append("цвет")
            changed_fields[rid] = ", ".join(fields) or "без явных различий полей"

    print()
    print("=" * 100)
    print("2. ID: ДОБАВЛЕНЫ / УДАЛЕНЫ / ИЗМЕНЕНЫ")
    print("=" * 100)
    print(f"добавлены ({len(added)}): {added}")
    print(f"удалены  ({len(removed)}): {removed}")
    print(f"изменены ({len(changed)}):")
    for rid in changed:
        print(f"   {rid}: {changed_fields[rid]}")

    print()
    print("=" * 100)
    print("3. МАРШРУТ 31")
    print("=" * 100)
    rows = [describe(r) for r in new_routes if r["number"].startswith("31")]
    headers = ["id", "номер", "напр.", "конечные", "точек", "км", "изгиб", "ост."]
    print_table(rows, headers) if rows else print("в новом файле нет маршрутов 31*")

    print()
    print("=" * 100)
    print("4. ВСЕ ВАРИАНТЫ 31Э")
    print("=" * 100)
    rows = [describe(r) for r in new_routes if r["number"] == "31Э"]
    print_table(rows, headers) if rows else print("в новом файле нет 31Э")
    for r in new_routes:
        if r["number"] == "31Э":
            flags = geometry_flags(r)
            print(f"   {r['id']}: точек={len(r['points'])}, длина={length_m(r['points'])/1000:.2f} км, "
                  f"остановок={len(r['stops'])}, флаги={flags or 'ок'}")

    print()
    print("=" * 100)
    print("5. ТОЧКИ ГЕОМЕТРИИ И ДЛИНЫ (все направления нового файла)")
    print("=" * 100)
    print_table([describe(r) for r in new_routes], headers)

    print()
    print("=" * 100)
    print("6. ОСТАНОВКИ")
    print("=" * 100)
    all_stops = {}
    for r in new_routes:
        for s in r["stops"]:
            all_stops.setdefault(s["id"], []).append((s["name"], r["id"]))
    print(f"уникальных остановок: {len(all_stops)}")
    print(f"всего упоминаний:     {sum(len(v) for v in all_stops.values())}")
    no_geom = [r["id"] for r in new_routes if not r["points"]]
    print(f"направлений без геометрии: {len(no_geom)} {no_geom}")
    no_stops = [r["id"] for r in new_routes if not r["stops"]]
    print(f"направлений без остановок: {len(no_stops)} {no_stops}")
    zero = [s["id"] for r in new_routes for s in r["stops"] if abs(s["lat"]) < 0.01 and abs(s["lon"]) < 0.01]
    print(f"остановок с нулевыми координатами: {len(set(zero))} {sorted(set(zero))[:10]}")
    inconsistent = {sid: {name for name, _ in entries}
                    for sid, entries in all_stops.items() if len({name for name, _ in entries}) > 1}
    print(f"id с разными названиями: {len(inconsistent)} "
          f"{sorted(inconsistent)[:10]}")

    print()
    print("=" * 100)
    print("7. ПОДОЗРИТЕЛЬНЫЕ ПРЯМЫЕ ЛИНИИ И РАЗРЫВЫ")
    print("=" * 100)
    suspicious = []
    for r in new_routes:
        flags = geometry_flags(r)
        if flags:
            suspicious.append((r["id"], r["number"], len(r["points"]),
                               round(length_m(r["points"]) / 1000, 2), "; ".join(flags)))
    if suspicious:
        print_table([[a, b, c, d, e] for a, b, c, d, e in suspicious],
                    ["id", "номер", "точек", "км", "флаги"])
    else:
        print("подозрительных геометрий не найдено")
    print()
    print("легенда: ПРЯМАЯ(detour<1.02) / ПОЧТИ_ПРЯМАЯ(detour<1.10) — линия почти без изгибов "
          "(detour = длина по треку / расстояние между концами);")
    print("         РАЗРЫВ(>800 м) — слишком длинный сегмент; МАЛО_ТОЧЕК(<10 при длине >2 км);")
    print("         НУЛЕВЫЕ_КООРДИНАТЫ; ДУБЛИ_ПОДРЯД(>2% точек или >3) — повторы координат подряд")
    print("         НЕТ_ГЕОМЕТРИИ — у направления есть расписание, но нет polyline")

    if schedules is None:
        return

    print()
    print("=" * 100)
    print("8. СВЕРКА С РАСПИСАНИЯМИ (norilsk_schedule.json)")
    print("=" * 100)
    print(f"файл расписаний: {schedule_path} (dataDate: {schedule_date})")
    print(f"записей расписаний: {len(schedules)} | направлений с геометрией: {len(new_routes)}")

    geo_by_id = {r["id"]: r for r in new_routes}
    no_geometry = sorted(set(schedules) - set(geo_by_id))
    no_schedule = sorted(set(geo_by_id) - set(schedules))

    print(f"\nесть расписание, НЕТ геометрии ({len(no_geometry)}):")
    if no_geometry:
        print_table([[i, schedules[i]["number"], schedules[i]["terminals"],
                      schedules[i]["departures"],
                      "; ".join(schedules[i]["terminal_names"])[:58]]
                     for i in no_geometry],
                    ["id", "номер", "терм.", "рейсов", "терминалы"])
    else:
        print("   нет — у каждого направления с расписанием есть polyline")

    print(f"\nесть геометрия, НЕТ расписания ({len(no_schedule)}): {no_schedule}")

    clash = sorted(i for i in set(schedules) & set(geo_by_id)
                   if schedules[i]["number"] and geo_by_id[i]["number"]
                   and schedules[i]["number"] != geo_by_id[i]["number"])
    print(f"\nномер в расписании и в геометриях различается ({len(clash)}):")
    for i in clash:
        print(f"   {i}: расписание={schedules[i]['number']!r} геометрия={geo_by_id[i]['number']!r}")

    empty = sorted(i for i, s in schedules.items() if s["departures"] == 0)
    empty_declared = [i for i in empty if not schedules[i]["hasSchedule"]]
    empty_suspicious = [i for i in empty if schedules[i]["hasSchedule"]]
    print(f"\nрасписаний без единого рейса ({len(empty)}): {empty}")
    print(f"   из них hasSchedule=false (расписание официально не публикуется): {len(empty_declared)}")
    print(f"   hasSchedule=true, но рейсов нет (подозрительно): {len(empty_suspicious)} {empty_suspicious}")
    print(f"всего рейсов в расписаниях: {sum(s['departures'] for s in schedules.values())}")


def main():
    parser = argparse.ArgumentParser(description="Сравнение norilsk_routes.json")
    parser.add_argument("--new", required=True, type=Path, help="путь к новому (Android) файлу")
    parser.add_argument("--old", type=Path,
                        default=Path(__file__).resolve().parents[1] / "Resources" / "norilsk_routes.json",
                        help="путь к текущему файлу в репозитории")
    parser.add_argument("--schedule", type=Path,
                        default=Path(__file__).resolve().parents[1] / "Resources" / "norilsk_schedule.json",
                        help="путь к norilsk_schedule.json для раздела 8")
    parser.add_argument("--no-schedule", action="store_true",
                        help="не печатать раздел 8 (сверку с расписаниями)")
    args = parser.parse_args()

    if not args.new.exists():
        raise SystemExit(f"нет файла: {args.new}")

    new_routes, new_top, new_schema = load_routes(args.new)
    print(f"схема нового файла (ключи первого маршрута): {new_schema}")
    print()

    if args.old.exists():
        old_routes, old_top, old_schema = load_routes(args.old)
        print(f"схема старого файла (ключи первого маршрута): {old_schema}")
    else:
        print("старый файл не найден — печатаю только новый")
        old_routes, old_top = [], []
    schedules = schedule_date = None
    if not args.no_schedule and args.schedule.exists():
        schedules, schedule_date = load_schedules(args.schedule)
    elif not args.no_schedule:
        print(f"расписания не найдены: {args.schedule} — раздел 8 пропущен")

    print()

    report(old_routes, new_routes, args.old, args.new, old_top, new_top,
           schedules, args.schedule, schedule_date)


if __name__ == "__main__":
    sys.exit(main())
