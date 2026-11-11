"""Текущие метрики агентной ленты: доступность, задержка, Precision@3, nDCG@5.

Запуск из корня репозитория:
    .venv\\Scripts\\python.exe local_backend\\metrics\\agent_metrics.py

Лайков в users.db нет, поэтому релевантность — рестораны, которые проходят
обязательные рамки (бюджет, сытость, не дальше 60 минут пешком).
Чем меньше время в пути, тем выше оценка для nDCG.
"""

import json
import math
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND))

import serve  # noqa: E402

HOST = "http://127.0.0.1:8765"
PROBE_LAT = 55.649083 # широта и долгота мифи
PROBE_LON = 37.663454
PROBE_HUNGER = 50
PROBE_BUDGETS = [1000.0]
UPTIME_PROBES = 5


def percentile(values, fraction):
    if not values:
        return None
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, math.ceil(fraction * len(ordered)) - 1))
    return ordered[index]


def probe_once(path, payload=None, timeout=180):
    url = HOST + path
    data = None
    headers = {}
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=headers)
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read()
            ok = 200 <= response.status < 300
            parsed = json.loads(body.decode("utf-8")) if body else {}
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError):
        return False, (time.perf_counter() - started) * 1000, {}
    return ok, (time.perf_counter() - started) * 1000, parsed


def uptime(samples):
    if not samples:
        return {"up": False, "uptime": 0.0, "probes": 0, "ok": 0}
    ok = sum(1 for item in samples if item)
    return {
        "up": ok == len(samples),
        "uptime": round(ok / len(samples), 4),
        "probes": len(samples),
        "ok": ok,
    }


def precision_at_k(ranked, relevant, k):
    if k <= 0:
        return 0.0
    hits = sum(1 for name in ranked[:k] if name in relevant)
    return round(hits / k, 4)


def dcg(gains):
    total = 0.0
    for index, gain in enumerate(gains):
        total += gain / math.log2(index + 2)
    return total


def ndcg_at_k(ranked, grades, k):
    ideal = sorted(grades.values(), reverse=True)[:k]
    ideal_dcg = dcg(ideal)
    if ideal_dcg == 0:
        return None
    actual = [grades.get(name, 0.0) for name in ranked[:k]]
    return round(dcg(actual) / ideal_dcg, 4)


def recall_at_k(ranked, targets, k):
    if not targets:
        return None
    found = sum(1 for name in targets if name in set(ranked[:k]))
    return round(found / len(targets), 4)


def reciprocal_rank(ranked, target):
    for index, name in enumerate(ranked, start=1):
        if name == target:
            return round(1 / index, 4)
    return 0.0


def parse_minutes(label):
    parts = str(label or "").split()
    if not parts:
        return None
    try:
        return int(parts[0])
    except ValueError:
        return None


def session_body():
    return {
        "user_id": 1,
        "hunger": PROBE_HUNGER,
        "phrase": "средний",
        "comment": "",
        "liked": [],
        "history": [],
        "hidden": [],
        "categories": [
            {"name": "проверка", "amount": int(PROBE_BUDGETS[0]), "color": 0, "custom": False}
        ],
        "coordinates": {"latitude": PROBE_LAT, "longitude": PROBE_LON},
    }


def eligible_restaurants():
    dishes = serve.dish_records()
    times = serve.run_async(
        serve.travel_by_restaurant(
            {dish["restaurant"] for dish in dishes},
            PROBE_LAT,
            PROBE_LON,
        )
    )
    for dish in dishes:
        info = times.get(dish["restaurant"].lower(), {})
        dish["travel_min"] = info.get("travel_min")
        dish["place_found"] = bool(info.get("place_found"))
    eligible = serve.eligible_dishes(dishes, PROBE_BUDGETS, PROBE_HUNGER)
    by_name = {}
    for dish in eligible:
        key = dish["restaurant"].lower()
        current = by_name.get(key)
        if current is None or dish["travel_min"] < current["travel_min"]:
            by_name[key] = {
                "key": key,
                "travel_min": int(dish["travel_min"]),
            }
    return sorted(by_name.values(), key=lambda row: (row["travel_min"], row["key"]))


def returned_restaurants(payload):
    ranked = []
    seen = set()
    for card in payload.get("offers") or []:
        name = str(card.get("name") or "").strip().lower()
        if not name or name in seen:
            continue
        seen.add(name)
        ranked.append({"key": name, "travel_min": parse_minutes(card.get("travelTime"))})
    return ranked


def ranking_metrics(returned, eligible):
    ranked_keys = [row["key"] for row in returned]
    eligible_keys = [row["key"] for row in eligible]
    grades = {
        row["key"]: float(serve.MAX_WALK_MIN + 1 - row["travel_min"])
        for row in eligible
    }
    nearest = eligible[:3]
    if nearest:
        cutoff = nearest[-1]["travel_min"]
        relevant = {row["key"] for row in eligible if row["travel_min"] <= cutoff}
    else:
        relevant = set()
    nearest_five = eligible_keys[:5]
    violations = 0
    eligible_set = set(eligible_keys)
    for row in returned:
        travel = row["travel_min"]
        if row["key"] not in eligible_set or travel is None or travel > serve.MAX_WALK_MIN:
            violations += 1
    total = len(returned)
    return {
        "precision_at_3": precision_at_k(ranked_keys, relevant, 3),
        "ndcg_at_5": ndcg_at_k(ranked_keys, grades, 5),
        "recall_at_5": recall_at_k(ranked_keys, nearest_five, 5),
        "mrr_nearest": reciprocal_rank(ranked_keys, eligible_keys[0]) if eligible_keys else None,
        "constraint_violation_rate": round(violations / total, 4) if total else None,
        "returned": total,
        "eligible": len(eligible),
        "relevant_at_3": len(relevant),
    }


def collect():
    probes = []
    menu_latency = []
    for _ in range(UPTIME_PROBES):
        ok, elapsed, _payload = probe_once("/menu", timeout=8)
        probes.append(ok)
        if ok:
            menu_latency.append(elapsed)
    availability = uptime(probes)
    started = time.perf_counter()
    ok, recommend_ms, payload = probe_once("/recommend", session_body(), timeout=180)
    if not ok:
        recommend_ms = (time.perf_counter() - started) * 1000
    quality = None
    warnings = payload.get("warnings") or []
    if ok:
        quality = ranking_metrics(returned_restaurants(payload), eligible_restaurants())
    report = {
        "uptime": availability["uptime"],
        "up": availability["up"],
        "probes_ok": availability["ok"],
        "probes": availability["probes"],
        "latency_ms": {
            "menu_mean": round(sum(menu_latency) / len(menu_latency), 1) if menu_latency else None,
            "menu_p50": round(percentile(menu_latency, 0.5), 1) if menu_latency else None,
            "recommend": round(recommend_ms, 1) if ok else None,
        },
        "recommend_ok": ok,
        "from_database": payload.get("fromDatabase") if ok else None,
        "warnings": warnings,
        "quality": quality,
        "probe": {
            "latitude": PROBE_LAT,
            "longitude": PROBE_LON,
            "hunger": PROBE_HUNGER,
            "budget": PROBE_BUDGETS[0],
            "relevance": "eligible restaurants within 60 walking minutes; nearer is better",
        },
    }
    return report


def main():
    report = collect()
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
