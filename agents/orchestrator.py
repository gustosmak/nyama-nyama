# agents/orchestrator.py
import asyncio
import functools
import logging
import time
import json
import ast
import re
import operator
from typing import Annotated, TypedDict, Any

from langgraph.graph import StateGraph, START, END
from sqlalchemy.orm import Session
from langchain_core.prompts import ChatPromptTemplate

from agents.preference import build_profile
from agents.ranking import rank_dishes
from utils.llm import get_llm

logger = logging.getLogger(__name__)


class RecommendState(TypedDict, total=False):
    user_id: int
    budgets: list[float]
    hunger: str
    lat: float
    lon: float
    restaurants: list[dict[str, Any]]
    profile: dict[str, Any] | None
    all_dishes: list[dict[str, Any]]
    budget_recommendations: dict[float, list[dict[str, Any]]]
    fallback: bool
    error: str | None
    timings: Annotated[dict[str, float], operator.or_]


def _parse_mcp_response(result: Any) -> Any:
    if hasattr(result, 'content') and result.content:
        text = result.content[0].text if hasattr(result.content[0], 'text') else str(result.content[0])
        try:
            return json.loads(text)
        except Exception:
            try:
                return ast.literal_eval(text)
            except Exception:
                return []

    if isinstance(result, dict):
        if "result" in result:
            return result["result"]
        elif "text" in result:
            text = result["text"]
            try:
                return json.loads(text)
            except Exception:
                try:
                    return ast.literal_eval(text)
                except Exception:
                    return []
        else:
            return result

    if isinstance(result, list):
        if len(result) > 0 and isinstance(result[0], dict) and result[0].get("type") == "text":
            text = result[0].get("text", "")
            try:
                return json.loads(text)
            except Exception:
                try:
                    return ast.literal_eval(text)
                except Exception:
                    return []
        return result

    if isinstance(result, str):
        try:
            return json.loads(result)
        except Exception:
            try:
                return ast.literal_eval(result)
            except Exception:
                return []

    return result


def _timed_node(name: str, fn):
    async def wrapper(state: RecommendState, tools: dict):
        start = time.perf_counter()
        try:
            update = await fn(state, tools)
        except Exception as exc:
            logger.exception("Node %s failed", name)
            update = {"error": f"{name}: {exc}"}

        elapsed = time.perf_counter() - start
        node_timing = {name: round(elapsed, 4)}

        if "timings" in update:
            update["timings"].update(node_timing)
        else:
            update["timings"] = node_timing

        if "error" in update:
            update["timings"][f"{name}_error"] = 1.0

        return update

    return wrapper


async def generate_fallback_menu(restaurant_name: str) -> list[dict]:
    logger.info(f"Generating fallback menu for '{restaurant_name}' via LLM")
    prompt = ChatPromptTemplate.from_messages([
        ("system", "Ты эксперт по ресторанам. Отвечай ТОЛЬКО чистым JSON-массивом без markdown."),
        ("human",
         f"Сгенерируй реалистичное меню из 3 блюд для ресторана '{restaurant_name}'. "
         "⚠️ ВАЖНО: Цены указывай ТОЛЬКО В РУБЛЯХ (от 150 до 1500). Не используй копейки или доллары. "
         "Верни JSON-массив объектов строго с полями: "
         "dish (название), section (категория: например 'закуски', 'горячее', 'десерты'), "
         "price (целое число в рублях), calories (число), "
         "satiety (число от 0 до 100 — насколько блюдо сытное).")
    ])

    chain = prompt | get_llm(temperature=0.7)

    try:
        result_text = await chain.ainvoke({})
        raw_content = result_text.content if hasattr(result_text, 'content') else str(result_text)
        raw_content = raw_content.replace("```json", "").replace("```", "").strip()

        match = re.search(r'\[.*\]', raw_content, re.DOTALL)
        if match:
            raw_content = match.group(0)

        try:
            dishes = json.loads(raw_content)
        except Exception:
            try:
                dishes = ast.literal_eval(raw_content)
            except Exception:
                logger.error(f"Не удалось распарсить ответ LLM: {raw_content[:100]}...")
                return []

        if not isinstance(dishes, list):
            dishes = [dishes] if isinstance(dishes, dict) else []

        for dish in dishes:
            if isinstance(dish, dict):
                dish["restaurant"] = restaurant_name
                if "price" in dish:
                    try:
                        dish["price"] = int(float(dish["price"]))
                    except:
                        pass

        return dishes
    except Exception as e:
        logger.error(f"LLM fallback menu generation failed: {e}")
        return []


async def location_node(state: RecommendState, tools: dict) -> dict:
    tool = tools.get("find_restaurants")
    if not tool:
        return {"error": "Location MCP tool not found"}

    budgets = state.get("budgets") or [0]
    max_budget = max(budgets)

    result = await tool.ainvoke({
        "latitude": state["lat"],
        "longitude": state["lon"],
        "budget": max_budget
    })

    logger.info(f"RAW LOCATION MCP RESULT: {result}")

    restaurants = _parse_mcp_response(result)

    if not isinstance(restaurants, list):
        restaurants = []

    restaurants = [r for r in restaurants if isinstance(r, dict) and "name" in r]

    if not restaurants:
        logger.warning("2GIS вернул пустоту! Включаю RICH MOCK FALLBACK.")
        return {
            "restaurants": [
                {"name": "Вкусно и точка", "lat": 55.75, "lon": 37.61, "address": "Тест", "cuisine": "Фастфуд"},
                {"name": "KFC", "lat": 55.76, "lon": 37.62, "address": "Тест", "cuisine": "Фастфуд"},
                {"name": "Му-му", "lat": 55.74, "lon": 37.60, "address": "Тест", "cuisine": "Русская"},
                {"name": "Додо Пицца", "lat": 55.77, "lon": 37.63, "address": "Тест", "cuisine": "Пицца"},
                {"name": "Сыроварня", "lat": 55.76, "lon": 37.60, "address": "Тест", "cuisine": "Европейская"}
            ],
            "fallback": True
        }

    return {"restaurants": restaurants}


async def preference_node(state: RecommendState, tools: dict) -> dict:
    db = tools.get("_db")
    if db is None:
        logger.warning("No DB provided. Skipping preference build.")
        return {"profile": None}
    try:
        profile = build_profile(db, state["user_id"])
        return {"profile": profile}
    except Exception as e:
        logger.error(f"Failed to build profile: {e}")
        return {"profile": None}


async def menu_node(state: RecommendState, tools: dict) -> dict:
    restaurants = state.get("restaurants") or []
    if not restaurants:
        return {"all_dishes": [], "error": "No restaurants found"}

    top_restaurants = restaurants[:10]
    menu_tool = tools.get("get_menu")
    save_tool = tools.get("save_generated_menu")

    if not menu_tool:
        logger.warning("Menu MCP не подключен. Генерирую меню через LLM.")
        all_dishes = []
        for r in top_restaurants:
            if "name" in r:
                dishes = await generate_fallback_menu(r["name"])
                all_dishes.extend(dishes)
        return {"all_dishes": all_dishes}

    async def fetch_menu(restaurant: dict):
        if "name" not in restaurant:
            return []

        res = await menu_tool.ainvoke({"restaurant_name": restaurant["name"]})
        parsed = _parse_mcp_response(res)

        # 1. Ресторана нет в базе — генерируем всё меню
        if isinstance(parsed, dict) and parsed.get("status") == "not_found":
            logger.info(f"Ресторан '{restaurant['name']}' не найден в БД. Генерирую меню.")
            fallback_dishes = await generate_fallback_menu(restaurant["name"])

            if save_tool and fallback_dishes:
                try:
                    await save_tool.ainvoke({
                        "restaurant_name": restaurant["name"],
                        "dishes": json.dumps(fallback_dishes, ensure_ascii=False)
                    })
                except Exception as e:
                    logger.warning(f"Не удалось сохранить меню: {e}")
            return fallback_dishes

        # 2. Ресторан есть в базе
        if isinstance(parsed, list):
            for dish in parsed:
                if isinstance(dish, dict):
                    dish["restaurant"] = restaurant["name"]

            # Если в базе только дешевые блюда (макс. цена < 500), генерируем дорогие
            max_price = max([d.get("price", 0) for d in parsed if isinstance(d, dict)] or [0])
            if max_price < 500:
                logger.info(f"В меню '{restaurant['name']}' нет дорогих блюд. Генерирую дополнительные.")
                generated = await generate_fallback_menu(restaurant["name"])
                parsed.extend(generated)

            return parsed

        return []

    tasks = [fetch_menu(r) for r in top_restaurants]
    results = await asyncio.gather(*tasks, return_exceptions=True)

    all_dishes: list[dict] = []
    for res in results:
        if isinstance(res, Exception):
            logger.warning("Menu fetch failed: %s", res)
            continue
        if isinstance(res, list):
            all_dishes.extend([r for r in res if isinstance(r, dict)])

    return {"all_dishes": all_dishes}


async def ranking_node(state: RecommendState, tools: dict) -> dict:
    dishes = state.get("all_dishes") or []
    budgets = state.get("budgets") or []

    budget_recommendations = {}
    sorted_budgets = sorted(list(set(budgets)))

    for i, current_budget in enumerate(sorted_budgets):
        prev_budget = sorted_budgets[i - 1] if i > 0 else 0

        premium_dishes = [
            d for d in dishes
            if isinstance(d, dict) and prev_budget < d.get("price", 0) <= current_budget
        ]

        if len(premium_dishes) < 3:
            all_affordable = [
                d for d in dishes
                if isinstance(d, dict) and d.get("price", 0) <= current_budget
            ]
            all_affordable = sorted(all_affordable, key=lambda x: x.get("price", 0), reverse=True)
            for d in all_affordable:
                if d not in premium_dishes:
                    premium_dishes.append(d)
                if len(premium_dishes) >= 5:
                    break

        affordable_dishes = premium_dishes

        if not affordable_dishes:
            budget_recommendations[current_budget] = []
            continue

        top = await rank_dishes(
            dishes=affordable_dishes,
            budget=current_budget,
            hunger=state["hunger"],
            profile=state.get("profile"),
        )

        if not top:
            top = affordable_dishes[:5]

        budget_recommendations[current_budget] = [item for item in top if isinstance(item, dict)]

    return {"budget_recommendations": budget_recommendations}


async def validator_node(state: RecommendState, tools: dict) -> dict:
    budget_recs = state.get("budget_recommendations") or {}
    validate_tool = tools.get("validate_recommendation")

    if not validate_tool:
        logger.warning("Validator MCP не подключен! Пропускаю валидацию.")
        return {"budget_recommendations": budget_recs, "fallback": True}

    validated_recs = {}
    sem = asyncio.Semaphore(5)

    async def validate_single(dish: dict, budget: float) -> bool:
        if not isinstance(dish, dict):
            return False

        async with sem:
            try:
                res = await validate_tool.ainvoke({
                    "name": dish.get("dish", dish.get("name", "Unknown")),
                    "price": dish.get("price", 0),
                    "user_budget": budget
                })
                parsed = _parse_mcp_response(res)
                if isinstance(parsed, dict) and parsed.get("is_valid") is True:
                    return True
            except Exception as e:
                logger.warning(f"Validation failed for {dish.get('dish', 'Unknown')}: {e}")
            return False

    for budget, dishes in budget_recs.items():
        if not dishes:
            validated_recs[budget] = []
            continue

        safe_dishes = [d for d in dishes if isinstance(d, dict)]
        tasks = [validate_single(d, budget) for d in safe_dishes]
        results = await asyncio.gather(*tasks)

        validated_recs[budget] = [dish for dish, is_valid in zip(safe_dishes, results) if is_valid]

    is_all_empty = all(len(d) == 0 for d in validated_recs.values())
    fallback = state.get("fallback", False) or is_all_empty

    return {"budget_recommendations": validated_recs, "fallback": fallback}


def build_graph(db: Session | None, mcp_tools: list):
    builder = StateGraph(RecommendState)

    tools_dict = {t.name: t for t in mcp_tools}
    if db is not None:
        tools_dict["_db"] = db

    location = functools.partial(_timed_node("location", location_node), tools=tools_dict)
    preference = functools.partial(_timed_node("preference", preference_node), tools=tools_dict)
    menu = functools.partial(_timed_node("menu", menu_node), tools=tools_dict)
    ranking = functools.partial(_timed_node("ranking", ranking_node), tools=tools_dict)
    validator = functools.partial(_timed_node("validator", validator_node), tools=tools_dict)

    builder.add_node("location", location)
    builder.add_node("preference", preference)
    builder.add_node("menu", menu)
    builder.add_node("ranking", ranking)
    builder.add_node("validator", validator)

    builder.add_edge(START, "location")
    builder.add_edge(START, "preference")
    builder.add_edge("location", "menu")
    builder.add_edge("preference", "menu")
    builder.add_edge("menu", "ranking")
    builder.add_edge("ranking", "validator")
    builder.add_edge("validator", END)

    return builder.compile()