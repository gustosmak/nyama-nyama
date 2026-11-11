import importlib.util
import json
import sqlite3
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from agents.preference import build_profile
from agents.ranking import rank_dishes
import asyncio

_LOOP = asyncio.new_event_loop()


def _run_loop():
    asyncio.set_event_loop(_LOOP)
    _LOOP.run_forever()


threading.Thread(target=_run_loop, daemon=True).start()


def run_async(coro):
    return asyncio.run_coroutine_threadsafe(coro, _LOOP).result()


DATABASES = Path(__file__).resolve().parent / "databases"
MENU_DB = DATABASES / "foodai_menu.db"
HOST = "0.0.0.0"
PORT = 8765


def ensure_generated_column(connection):
    columns = [row[1] for row in connection.execute("PRAGMA table_info(dishes)")]
    if "generated" not in columns:
        try:
            connection.execute(
                "ALTER TABLE dishes ADD COLUMN generated INTEGER NOT NULL DEFAULT 0"
            )
        except sqlite3.OperationalError:
            return
        connection.execute(
            """
            UPDATE dishes
            SET generated = 1
            WHERE lower(COALESCE(section, '')) LIKE '%сгенер%'
            """
        )
        connection.commit()


def menu_rows():
    if not MENU_DB.is_file():
        return []
    connection = sqlite3.connect(MENU_DB)
    try:
        ensure_generated_column(connection)
        cursor = connection.execute(
            """
            SELECT restaurants.name, dishes.name, dishes.description, dishes.satiety,
                   dishes.price, dishes.calories, COALESCE(sections.name, dishes.section, ''),
                   COALESCE(dishes.generated, 0)
            FROM dishes
            JOIN restaurants ON restaurants.id = dishes.restaurant_id
            LEFT JOIN sections ON sections.id = dishes.section_id
            ORDER BY restaurants.name, dishes.id
            """
        )
        return cursor.fetchall()
    finally:
        connection.close()


def offers_from_rows(rows):
    offers = []
    current = None
    for restaurant, name, description, satiety, price, calories, _section, generated in rows:
        if current is None or current["name"] != restaurant:
            current = {"name": restaurant, "travelTime": "", "dishes": []}
            offers.append(current)
        current["dishes"].append(dish_payload(
            name,
            description or "",
            satiety,
            price,
            calories,
            bool(generated),
        ))
    return offers


def dish_payload(name, satiety, level, price, calories, generated=False):
    calorie_text = calories if isinstance(calories, str) else f"{calories} ккал"
    return {
        "name": name,
        "satiety": satiety or "",
        "satietyLevel": int(level or 0),
        "price": int(round(float(price or 0))),
        "calories": calorie_text,
        "generated": bool(generated),
    }


def ranked_offers(budget_recs, budgets):
    grouped = []
    by_name = {}
    seen = set()
    lists = []
    for budget in budgets:
        lists.append(budget_recs.get(budget) or budget_recs.get(str(budget)) or [])
    if not any(lists):
        lists = list(budget_recs.values())
    for dishes in lists:
        for dish in dishes or []:
            if not isinstance(dish, dict):
                continue
            restaurant = str(dish.get("restaurant") or "")
            name = str(dish.get("dish") or dish.get("name") or "")
            if not restaurant or not name:
                continue
            key = (restaurant.lower(), name.lower())
            if key in seen:
                continue
            seen.add(key)
            card = by_name.get(restaurant)
            if card is None:
                card = {"name": restaurant, "travelTime": "", "dishes": []}
                by_name[restaurant] = card
                grouped.append(card)
            reason = dish.get("reason") or dish.get("description") or ""
            card["dishes"].append(dish_payload(
                name,
                reason,
                dish.get("satiety") or 0,
                dish.get("price") or 0,
                dish.get("calories") or 0,
                dish.get("generated"),
            ))
            if not card.get("travelTime") and dish.get("travel_min") is not None:
                card["travelTime"] = f"{int(dish['travel_min'])} min"
                if dish.get("distance_m") is not None:
                    card["distanceMeters"] = int(dish["distance_m"])
    return grouped


def pipeline_args(session):
    coordinates = session.get("coordinates")
    if not isinstance(coordinates, dict):
        coordinates = {}
    categories = session.get("categories") or session.get("frames") or []
    budgets = []
    for item in categories:
        if isinstance(item, dict) and item.get("amount"):
            budgets.append(float(item["amount"]))
    if not budgets:
        budgets = [300.0, 500.0, 1000.0]
    hunger = session.get("phrase") or session.get("hunger") or "средний"
    return {
        "lat": float(coordinates.get("latitude") or 55.7905),
        "lon": float(coordinates.get("longitude") or 37.5315),
        "budgets": budgets,
        "hunger": str(hunger),
        "user_id": int(session.get("user_id") or 1),
        "comment": session.get("comment") or "",
        "liked": session.get("liked") or [],
        "history": session.get("history") or [],
        "hidden": session.get("hidden") or [],
    }


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/menu":
            self.respond({"offers": offers_from_rows(menu_rows())})
            return
        if parsed.path == "/locate":
            query = parse_qs(parsed.query)
            name = (query.get("restaurant") or [""])[0]
            try:
                lat = float((query.get("latitude") or ["55.7905"])[0])
                lon = float((query.get("longitude") or ["37.5315"])[0])
            except ValueError:
                lat, lon = 55.7905, 37.5315
            found = {}
            try:
                found = run_async(locate_place(name, lat, lon))
            except Exception as error:
                print("locate failed:", error)
                found = {"warning": "location_failed"}
            payload = {"lat": found.get("lat"), "lon": found.get("lon")}
            if found.get("warning"):
                payload["warning"] = found["warning"]
            self.respond(payload)
            return
        if parsed.path == "/sections":
            query = parse_qs(parsed.query)
            restaurant = (query.get("restaurant") or [""])[0]
            dish = (query.get("dish") or [""])[0]
            self.respond({"sections": sections(restaurant, dish)})
            return
        self.respond({"error": "not found"}, 404)

    def do_POST(self):
        if urlparse(self.path).path != "/recommend":
            self.respond({"error": "not found"}, 404)
            return
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length).decode("utf-8")
        try:
            session = json.loads(raw or "{}")
        except json.JSONDecodeError:
            self.respond({"error": "bad json"}, 400)
            return
        from_database = True
        warnings = []
        args = {"lat": 55.7905, "lon": 37.5315}
        try:
            args = pipeline_args(session)
            offers, from_database, warnings = run_async(local_recommend(session, args))
        except Exception as error:
            print("recommend failed:", error)
            offers = []
            warnings = ["model_failed"]
        if not offers and "nothing_nearby" not in warnings:
            offers = offers_from_rows(menu_rows())
            from_database = True
            if not offers:
                warnings = ["no_menu"]
            elif not warnings:
                warnings = ["model_empty"]
        try:
            if any(not str(card.get("travelTime") or "").strip() for card in offers):
                offers = run_async(with_travel(offers, args["lat"], args["lon"]))
        except Exception as error:
            print("travel failed:", error)
            for card in offers:
                card["travelTime"] = card.get("travelTime") or "0 min"
        self.respond({
            "offers": offers,
            "fromDatabase": from_database,
            "warnings": warnings,
        })

    def respond(self, payload, status=200):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print(fmt % args)


def location_agent():
    from dotenv import load_dotenv

    load_dotenv(Path(__file__).resolve().parent / ".env")
    path = Path(__file__).resolve().parent / "foodai-location-mcp" / "places.py"
    spec = importlib.util.spec_from_file_location("location_agent", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


async def locate_place(name, latitude, longitude):
    agent = location_agent()
    found = await agent.locate_restaurant(name, latitude, longitude)
    return found if isinstance(found, dict) else {}


async def with_travel(offers, latitude, longitude):
    agent = location_agent()
    gate = asyncio.Semaphore(4)

    async def locate(card):
        async with gate:
            try:
                found = await asyncio.wait_for(
                    agent.locate_restaurant(card.get("name") or "", latitude, longitude),
                    8,
                )
            except Exception:
                found = {}
            point_lat = found.get("lat") if isinstance(found, dict) else None
            point_lon = found.get("lon") if isinstance(found, dict) else None
            card["_missing"] = isinstance(found, dict) and found.get("warning") == "place_not_found"
            if point_lat is None or point_lon is None:
                card["_meters"] = None
                card["_point"] = None
                return
            meters = int(round(agent.haversine_km(latitude, longitude, point_lat, point_lon) * 1000))
            card["_meters"] = meters
            card["_point"] = (point_lat, point_lon)

    await asyncio.gather(*(locate(card) for card in offers))

    async def route(card):
        meters = card.get("_meters")
        point = card.get("_point")
        missing = card.pop("_missing", False)
        card.pop("_point", None)
        card.pop("_meters", None)
        if meters is None or point is None:
            card["travelTime"] = "-1 min" if missing else "0 min"
            return
        card["distanceMeters"] = meters
        async with gate:
            try:
                path = await asyncio.wait_for(
                    agent.route_between(latitude, longitude, point[0], point[1]),
                    8,
                )
            except Exception:
                path = None
        if not path or path.get("duration_min") is None:
            card["travelTime"] = "0 min"
            return
        if path.get("distance_km") is not None:
            card["distanceMeters"] = int(round(path["distance_km"] * 1000))
        minutes = max(1, int(round(path["duration_min"])))
        card["travelTime"] = f"{minutes} min"

    await asyncio.gather(*(route(card) for card in offers))

    def order(card):
        label = str(card.get("travelTime") or "")
        if label == "-1 min":
            return 10 ** 9
        parts = label.split()
        if not parts or parts[0] in {"0", ""}:
            return 10 ** 8
        try:
            return int(parts[0])
        except ValueError:
            return 10 ** 8

    return sorted(offers, key=order)


def dish_records():
    records = []
    for restaurant, name, description, satiety, price, calories, section, generated in menu_rows():
        records.append({
            "dish": name,
            "restaurant": restaurant,
            "description": description or "",
            "section": section or "",
            "generated": bool(generated),
            "price": int(round(float(price or 0))),
            "calories": calories or 0,
            "satiety": int(satiety or 0),
        })
    return records


def marked_keys(items):
    keys = set()
    for item in items or []:
        if not isinstance(item, dict):
            continue
        restaurant = str(item.get("restaurant") or "").lower()
        name = str(item.get("name") or item.get("dish") or "").lower()
        if restaurant and name:
            keys.add((restaurant, name))
    return keys


def hunger_target(session, hunger_text):
    try:
        return int(session.get("hunger"))
    except (TypeError, ValueError):
        text = str(hunger_text or "").lower()
        if "сильн" in text or "очен" in text:
            return 80
        if "слаб" in text or "легк" in text:
            return 25
        return 50


def repair_ranked(ranked, known):
    by_pair = {}
    by_dish = {}
    for dish in known:
        by_pair[(dish["restaurant"].lower(), dish["dish"].lower())] = dish
        by_dish.setdefault(dish["dish"].lower(), dish)
    repaired = []
    for item in ranked or []:
        if not isinstance(item, dict):
            continue
        restaurant = str(item.get("restaurant") or "")
        name = str(item.get("dish") or item.get("name") or "")
        match = by_pair.get((restaurant.lower(), name.lower()))
        if match is None and name:
            match = by_dish.get(name.lower())
        if match is None:
            continue
        item = dict(item)
        item["dish"] = match["dish"]
        item["restaurant"] = match["restaurant"]
        item["price"] = match["price"]
        item["calories"] = match["calories"]
        item["satiety"] = match["satiety"]
        item["generated"] = match.get("generated")
        item["travel_min"] = match.get("travel_min")
        item["distance_m"] = match.get("distance_m")
        repaired.append(item)
    return repaired


async def travel_by_restaurant(names, latitude, longitude):
    agent = location_agent()
    gate = asyncio.Semaphore(4)
    found_points = {}

    async def locate(name):
        async with gate:
            try:
                found = await asyncio.wait_for(
                    agent.locate_restaurant(name, latitude, longitude),
                    8,
                )
            except Exception:
                found = {}
        point_lat = found.get("lat") if isinstance(found, dict) else None
        point_lon = found.get("lon") if isinstance(found, dict) else None
        missing = isinstance(found, dict) and found.get("warning") == "place_not_found"
        if point_lat is None or point_lon is None:
            found_points[name.lower()] = {"place_found": not missing, "missing": missing}
            return
        found_points[name.lower()] = {
            "place_found": True,
            "missing": False,
            "point": (point_lat, point_lon),
            "distance_m": int(round(agent.haversine_km(latitude, longitude, point_lat, point_lon) * 1000)),
        }

    await asyncio.gather(*(locate(name) for name in names))

    async def route(name, info):
        point = info.get("point")
        if not point:
            info["travel_min"] = -1 if info.get("missing") else None
            return
        async with gate:
            try:
                path = await asyncio.wait_for(
                    agent.route_between(latitude, longitude, point[0], point[1]),
                    8,
                )
            except Exception:
                path = None
        if not path or path.get("duration_min") is None:
            info["travel_min"] = None
            return
        if path.get("distance_km") is not None:
            info["distance_m"] = int(round(path["distance_km"] * 1000))
        info["travel_min"] = max(1, int(round(path["duration_min"])))

    await asyncio.gather(*(
        route(name, info)
        for name, info in list(found_points.items())
    ))
    return found_points


MAX_WALK_MIN = 60
SATIETY_SLACK = 15
BUDGET_SLACK = 100


def within_budget(dish, budgets):
    if not budgets:
        return True
    return any(dish["price"] <= float(amount) + BUDGET_SLACK for amount in budgets)


def eligible_dishes(dishes, budgets, hunger):
    picked = []
    for dish in dishes:
        travel = dish.get("travel_min")
        if not dish.get("place_found") or travel is None or travel < 0 or travel > MAX_WALK_MIN:
            continue
        if not within_budget(dish, budgets):
            continue
        if abs(int(dish.get("satiety") or 0) - int(hunger)) > SATIETY_SLACK:
            continue
        picked.append(dish)
    return picked


def order_offers(ranked, eligible):
    ranked = repair_ranked(ranked, eligible)
    groups = {}
    hint = {}
    for item in ranked:
        key = item["restaurant"].lower()
        groups.setdefault(key, []).append(item)
        hint.setdefault(key, len(hint))
    for dish in eligible:
        key = dish["restaurant"].lower()
        bucket = groups.setdefault(key, [])
        if not any(row["dish"].lower() == dish["dish"].lower() for row in bucket):
            bucket.append(dish)
        hint.setdefault(key, len(hint))

    def travel(key):
        return int(groups[key][0].get("travel_min") or 0)

    names = sorted(groups, key=lambda key: (travel(key) // 5, hint[key], travel(key)))
    ordered = []
    for key in names:
        ordered.extend(groups[key])
    return ranked_offers({0: ordered}, [0])


async def local_recommend(session, args):
    dishes = dish_records()
    if not dishes:
        return [], True, ["no_menu"]
    hidden = marked_keys(args["hidden"])
    liked = marked_keys(args["liked"])
    history = marked_keys(args["history"])
    visible = [
        dish for dish in dishes
        if (dish["restaurant"].lower(), dish["dish"].lower()) not in hidden
    ]
    times = await travel_by_restaurant(
        {dish["restaurant"] for dish in visible},
        args["lat"],
        args["lon"],
    )
    for dish in visible:
        info = times.get(dish["restaurant"].lower(), {})
        dish["travel_min"] = info.get("travel_min")
        dish["distance_m"] = info.get("distance_m")
        dish["place_found"] = bool(info.get("place_found"))
        key = (dish["restaurant"].lower(), dish["dish"].lower())
        dish["liked"] = key in liked
        dish["in_history"] = key in history
    hunger = hunger_target(session, args["hunger"])
    eligible = eligible_dishes(visible, args["budgets"], hunger)
    if not eligible:
        return [], True, ["nothing_nearby"]
    ranked = []
    warnings = []
    try:
        profile = await asyncio.wait_for(build_profile(
            comment=args["comment"],
            liked=args["liked"],
            history=args["history"],
            hidden=args["hidden"],
            menu_db=str(MENU_DB),
        ), 18)
        ranked = await asyncio.wait_for(
            rank_dishes(eligible, args["budgets"], hunger, profile),
            45,
        )
    except asyncio.TimeoutError:
        warnings.append("model_timeout")
    except Exception as error:
        print("agents skipped:", error)
        warnings.append("model_failed")
    offers = order_offers(ranked, eligible)
    if offers:
        return offers, bool(warnings), warnings
    if not warnings:
        warnings.append("model_empty")
    return [], True, warnings


def sections(restaurant, dish):
    if not MENU_DB.is_file():
        return []
    connection = sqlite3.connect(MENU_DB)
    try:
        cursor = connection.execute(
            """
            SELECT DISTINCT sections.name
            FROM dishes
            JOIN restaurants ON restaurants.id = dishes.restaurant_id
            JOIN sections ON sections.id = dishes.section_id
            WHERE lower(restaurants.name) = lower(?) AND lower(dishes.name) = lower(?)
            ORDER BY sections.name
            """,
            (restaurant, dish),
        )
        return [row[0] for row in cursor.fetchall() if row[0]]
    finally:
        connection.close()


if __name__ == "__main__":
    print(f"Базы: {DATABASES}")
    print(f"Слушаю {HOST}:{PORT}")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
