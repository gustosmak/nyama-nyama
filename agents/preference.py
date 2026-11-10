import json
import logging
import os
import sqlite3
from pathlib import Path

logger = logging.getLogger(__name__)

_ROOT = Path(__file__).resolve().parents[1]


def load_session(path: str) -> dict:
    """Читает JSON сессии. comment — строка, списки — liked, history, hidden."""
    raw = Path(path).read_text(encoding="utf-8")
    data = json.loads(raw)
    if not isinstance(data, dict):
        return {"comment": "", "liked": [], "history": [], "hidden": []}
    return {
        "comment": data.get("comment") or "",
        "liked": data.get("liked") or [],
        "history": data.get("history") or [],
        "hidden": data.get("hidden") or [],
    }


def menu_db_path(explicit: str | None = None) -> str | None:
    if explicit and os.path.isfile(explicit):
        return explicit
    candidates = [
        _ROOT / "app" / "src" / "main" / "assets" / "foodai_menu.db",
        _ROOT / "foodai-menu-mcp" / "foodai_menu.db",
        Path("foodai_menu.db"),
    ]
    for path in candidates:
        if path.is_file():
            return str(path)
    return None


def _pair(item) -> tuple[str, str] | None:
    if isinstance(item, dict):
        restaurant = item.get("restaurant") or item.get("restaurant_name") or ""
        dish = item.get("name") or item.get("dish") or ""
    elif isinstance(item, (list, tuple)) and len(item) >= 2:
        restaurant, dish = item[0], item[1]
    elif isinstance(item, str) and "|" in item:
        restaurant, dish = item.split("|", 1)
    else:
        return None
    restaurant = str(restaurant).strip()
    dish = str(dish).strip()
    if not restaurant or not dish:
        return None
    return restaurant, dish


def _lookup(connection: sqlite3.Connection, restaurant: str, dish: str) -> dict | None:
    row = connection.execute(
        """
        SELECT restaurants.name, dishes.name,
               COALESCE(sections.name, dishes.section, ''),
               dishes.price, dishes.calories, dishes.satiety, dishes.description
        FROM dishes
        JOIN restaurants ON restaurants.id = dishes.restaurant_id
        LEFT JOIN sections ON sections.id = dishes.section_id
        WHERE lower(restaurants.name) = lower(?) AND lower(dishes.name) = lower(?)
        LIMIT 1
        """,
        (restaurant, dish),
    ).fetchone()
    if row is None:
        row = connection.execute(
            """
            SELECT restaurants.name, dishes.name,
                   COALESCE(sections.name, dishes.section, ''),
                   dishes.price, dishes.calories, dishes.satiety, dishes.description
            FROM dishes
            JOIN restaurants ON restaurants.id = dishes.restaurant_id
            LEFT JOIN sections ON sections.id = dishes.section_id
            WHERE lower(restaurants.name) LIKE '%' || lower(?) || '%'
              AND lower(dishes.name) LIKE '%' || lower(?) || '%'
            LIMIT 1
            """,
            (restaurant, dish),
        ).fetchone()
    if row is None:
        return None
    return {
        "restaurant": row[0],
        "dish": row[1],
        "section": row[2] or "",
        "price": row[3],
        "calories": row[4],
        "satiety": row[5],
        "description": row[6] or "",
    }


def enrich(items, db_path: str | None) -> list[dict]:
    pairs = []
    for item in items or []:
        pair = _pair(item)
        if pair is not None:
            pairs.append(pair)
    if not pairs:
        return []
    if not db_path:
        return [{"restaurant": restaurant, "dish": dish} for restaurant, dish in pairs]

    connection = sqlite3.connect(db_path)
    try:
        found = []
        for restaurant, dish in pairs:
            row = _lookup(connection, restaurant, dish)
            found.append(row or {"restaurant": restaurant, "dish": dish})
        return found
    finally:
        connection.close()


def _parse_profile(text: str) -> dict:
    from utils.llm import parse_llm_json

    parsed = parse_llm_json(text)
    return parsed if isinstance(parsed, dict) else {}


async def build_profile(
    comment: str | None = None,
    liked: list | None = None,
    history: list | None = None,
    hidden: list | None = None,
    menu_db: str | None = None,
    session_path: str | None = None,
) -> dict:
    """
    Профиль вкуса: комментарий из JSON, блюда из foodai_menu.db
    и списки liked / history / hidden.
    """
    from dotenv import load_dotenv
    from langchain_core.prompts import ChatPromptTemplate

    from utils.llm import get_llm

    load_dotenv()
    if session_path:
        session = load_session(session_path)
        if comment is None:
            comment = session["comment"]
        if liked is None:
            liked = session["liked"]
        if history is None:
            history = session["history"]
        if hidden is None:
            hidden = session["hidden"]

    comment = comment or ""
    db_path = menu_db_path(menu_db)
    liked_dishes = enrich(liked, db_path)
    history_dishes = enrich(history, db_path)
    hidden_dishes = enrich(hidden, db_path)

    profile = {
        "comment": comment,
        "liked": liked_dishes,
        "history": history_dishes,
        "hidden": hidden_dishes,
        "summary": "",
        "prefer": [],
        "avoid": [],
        "from_comment": "",
        "satiety_bias": "any",
    }
    if not comment and not liked_dishes and not history_dishes and not hidden_dishes:
        return profile
    if not os.getenv("CLOUD_RU_API_KEY"):
        logger.warning("CLOUD_RU_API_KEY не задан, профиль без текста модели")
        return profile

    prompt = ChatPromptTemplate.from_messages([
        (
            "system",
            "Ты собираешь вкусовой профиль для рекомендаций еды. "
            "Отвечай только JSON-объектом без markdown. "
            "Скрытые блюда и секции из них нельзя рекомендовать. "
            "История — то, что человек уже заказывал. Лайки — то, что ему зашло. "
            "Комментарий важнее общих догадок.",
        ),
        (
            "human",
            "Комментарий: {comment}\n"
            "Лайки: {liked}\n"
            "История: {history}\n"
            "Скрытые: {hidden}\n"
            "Верни JSON с полями: "
            "summary (одно предложение), "
            "prefer (массив секций или типов еды), "
            "avoid (массив того, что не предлагать), "
            "from_comment (что именно просит комментарий), "
            "satiety_bias (low, medium, high или any).",
        ),
    ])
    chain = prompt | get_llm(temperature=0.2)
    try:
        result = await chain.ainvoke({
            "comment": comment or "нет",
            "liked": json.dumps(liked_dishes, ensure_ascii=False),
            "history": json.dumps(history_dishes, ensure_ascii=False),
            "hidden": json.dumps(hidden_dishes, ensure_ascii=False),
        })
        text = result.content if hasattr(result, "content") else str(result)
        parsed = _parse_profile(text)
    except Exception:
        logger.exception("Не удалось собрать профиль через Cloud.ru")
        return profile

    profile["summary"] = str(parsed.get("summary") or "")
    profile["prefer"] = parsed.get("prefer") if isinstance(parsed.get("prefer"), list) else []
    profile["avoid"] = parsed.get("avoid") if isinstance(parsed.get("avoid"), list) else []
    profile["from_comment"] = str(parsed.get("from_comment") or "")
    bias = str(parsed.get("satiety_bias") or "any").lower()
    profile["satiety_bias"] = bias if bias in {"low", "medium", "high", "any"} else "any"
    return profile
