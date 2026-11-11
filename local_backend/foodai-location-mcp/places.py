import math
import os
from pathlib import Path
from typing import Any, Dict, Optional

import httpx

TWOGIS_PLACES_URL = "https://twogis-proxy.zherdevdenis25.workers.dev/3.0/items"
TWOGIS_ROUTE_URL = "https://routing.api.2gis.com/routing/7.0.0/global"
WALK_KMH = 5


def _key() -> str:
    try:
        from dotenv import load_dotenv

        load_dotenv(Path(__file__).resolve().parents[1] / ".env")
    except Exception:
        pass
    return os.getenv("TWO_GIS_API_KEY") or ""


def _same_place(left: str, right: str) -> bool:
    def norm(value: str) -> str:
        return "".join(ch for ch in str(value).lower() if ch.isalnum())

    first = norm(left)
    second = norm(right)
    return bool(first and second and (first in second or second in first))


async def locate_restaurant(name: str, latitude: float, longitude: float) -> Dict[str, Any]:
    key = _key()
    if not name:
        return {"warning": "place_not_found"}
    if not key:
        return {"warning": "location_key"}

    params = {
        "q": name,
        "point": f"{longitude},{latitude}",
        "radius": 50000,
        "key": key,
        "type": "branch",
        "fields": "items.point,items.name",
        "page_size": 5,
        "sort": "distance",
    }

    try:
        async with httpx.AsyncClient() as client:
            response = await client.get(TWOGIS_PLACES_URL, params=params, timeout=8.0)
            data = response.json()
            if data.get("meta", {}).get("code") != 200:
                return {"warning": "location_failed"}
    except Exception as error:
        print(f"Ошибка 2GIS: {error}")
        return {"warning": "location_failed"}

    items = (data.get("result") or {}).get("items") or []
    chosen = next((item for item in items if _same_place(name, item.get("name", ""))), None)
    if chosen is None and items:
        chosen = items[0]
    point = (chosen or {}).get("point") or {}
    lat = point.get("lat")
    lon = point.get("lon")
    if lat is None or lon is None:
        return {"warning": "place_not_found"}
    return {"name": chosen.get("name") or name, "lat": lat, "lon": lon}


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    radius = 6371.0
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    arc = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
    return 2 * radius * math.asin(math.sqrt(arc))


def _walk_summary(meters: float) -> Dict[str, float]:
    # 2ГИС и OSM считают пеший шаг около 4.5 км/ч, карты вроде Яндекса — около 5 км/ч.
    # Минуты считаем по длине пешего пути, иначе 16 минут на карте превращаются в 20.
    return {
        "distance_km": round(meters / 1000, 2),
        "duration_min": round(meters / 1000 / WALK_KMH * 60, 1),
    }


def _osrm_meters(data: Dict[str, Any]) -> Optional[float]:
    if data.get("code") != "Ok" or not data.get("routes"):
        return None
    return float(data["routes"][0]["distance"])


async def route_between(
    start_lat: float,
    start_lon: float,
    end_lat: float,
    end_lon: float,
) -> Optional[Dict[str, float]]:
    key = _key()
    try:
        async with httpx.AsyncClient() as client:
            if key:
                try:
                    response = await client.post(
                        TWOGIS_ROUTE_URL,
                        params={"key": key},
                        json={
                            "points": [
                                {"type": "walking", "lon": start_lon, "lat": start_lat},
                                {"type": "walking", "lon": end_lon, "lat": end_lat},
                            ],
                            "transport": "walking",
                            "output": "summary",
                            "route_mode": "fastest",
                            "locale": "ru",
                        },
                        timeout=8.0,
                    )
                    result = (response.json().get("result") or [{}])[0]
                    if result.get("length") is not None:
                        return _walk_summary(float(result["length"]))
                except Exception as error:
                    print(f"Ошибка пешего маршрута 2ГИС: {error}")
            try:
                valhalla = await client.post(
                    "https://valhalla1.openstreetmap.de/route",
                    json={
                        "locations": [
                            {"lat": start_lat, "lon": start_lon},
                            {"lat": end_lat, "lon": end_lon},
                        ],
                        "costing": "pedestrian",
                        "units": "kilometers",
                    },
                    timeout=8.0,
                )
                trip = (valhalla.json().get("trip") or {}).get("summary") or {}
                if trip.get("length") is not None:
                    return _walk_summary(float(trip["length"]) * 1000)
            except Exception as error:
                print(f"Ошибка запасного маршрута: {error}")
            foot_url = (
                "https://routing.openstreetmap.de/routed-foot/route/v1/foot/"
                f"{start_lon},{start_lat};{end_lon},{end_lat}?overview=false"
            )
            try:
                response = await client.get(foot_url, timeout=8.0)
                meters = _osrm_meters(response.json())
            except Exception as error:
                print(f"Ошибка пешего OSM: {error}")
                return None
            if meters is None:
                return None
            return _walk_summary(meters)
    except Exception as error:
        print(f"Ошибка маршрута: {error}")
        return None
