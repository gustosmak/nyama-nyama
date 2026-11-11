# agents/ranking.py
import json
import logging
from langchain_core.prompts import ChatPromptTemplate
from utils.llm import get_llm, parse_llm_json

logger = logging.getLogger(__name__)


def _clean_restaurant_name(name: str) -> str:
    if not isinstance(name, str):
        return str(name)
    name = name.lstrip('#').strip()
    name = name.split(',')[0].strip()
    return name


async def rank_dishes(
        dishes: list[dict],
        budgets: list[float],
        hunger_level: int,
        profile: dict | None,
) -> list[dict]:
    cleaned_dishes = []
    for d in dishes:
        if not isinstance(d, dict):
            continue
        d_copy = d.copy()
        if "restaurant" in d_copy:
            d_copy["restaurant"] = _clean_restaurant_name(d_copy["restaurant"])
        cleaned_dishes.append(d_copy)

    if not cleaned_dishes:
        return []

    brief = []
    for dish in cleaned_dishes:
        brief.append({
            "dish": dish.get("dish"),
            "restaurant": dish.get("restaurant"),
            "section": dish.get("section") or "",
            "description": str(dish.get("description") or "")[:120],
            "price": dish.get("price"),
            "satiety": dish.get("satiety"),
            "travel_min": dish.get("travel_min"),
            "place_found": bool(dish.get("place_found", True)),
            "liked": bool(dish.get("liked")),
            "in_history": bool(dish.get("in_history")),
        })
    ceilings = sorted({int(amount) for amount in budgets if amount})
    satiety_slack = 15
    budget_slack = 100

    system_prompt = (
        "Ты ранжируешь блюда. Отвечай ТОЛЬКО чистым JSON-массивом. "
        "Никакого markdown и текста вокруг JSON. "
        "Не выдумывай блюда и рестораны: копируй dish и restaurant из списка."
    )

    prompt = ChatPromptTemplate.from_messages([
        ("system", system_prompt),
        ("human", "{user_prompt}"),
    ])

    user_prompt = (
        f"Блюда: {json.dumps(brief, ensure_ascii=False, default=str)}\n"
        f"Голод, число сытости 0-100: {int(hunger_level)}\n"
        f"Потолки бюджета в рублях: {ceilings}. Погрешность бюджета {budget_slack} руб, "
        f"погрешность сытости {satiety_slack}. Список уже отфильтрован по бюджету, сытости "
        "и времени в пути до 60 минут.\n"
        f"Профиль: {json.dumps(profile, ensure_ascii=False, default=str) if profile else 'нет'}\n"
        "\nВерни КАЖДОЕ блюдо из списка ровно один раз. Ничего не выкидывай.\n"
        "Комментарий, лайки, история и avoid не причина убирать блюдо. Они только меняют порядок.\n"
        "Порядок ресторанов:\n"
        "1. Сначала меньший travel_min. Разница в 5 минут и больше всегда важнее вкуса: "
        "ближний ресторан выше дальнего, даже если у дальнего лайк или он лучше подходит к комментарию.\n"
        "2. Если разница travel_min меньше 5 минут, выше тот, кто лучше совпадает с комментарием.\n"
        "3. При той же близости и том же комментарии выше liked, затем in_history.\n"
        "Ресторан без лайка остаётся в ленте на своём месте по расстоянию.\n"
        "В reason не используй двойные кавычки. Reason — 3-5 слов.\n"
        "Верни JSON-массив в итоговом порядке. Поля: dish, price, restaurant, reason, satiety."
    )

    chain = prompt | get_llm(temperature=0.1)

    try:
        result_text = await chain.ainvoke({"user_prompt": user_prompt})
        raw_content = result_text.content if hasattr(result_text, 'content') else str(result_text)

        dishes_result = parse_llm_json(raw_content)
        if dishes_result is None:
            logger.error(f"Не удалось распарсить JSON от LLM. Текст: {raw_content[:300]}")
            return []

        if not isinstance(dishes_result, list):
            dishes_result = [dishes_result] if isinstance(dishes_result, dict) else []

        safe_result = []
        for item in dishes_result:
            if isinstance(item, dict):
                if "restaurant" in item:
                    item["restaurant"] = _clean_restaurant_name(item["restaurant"])
                safe_result.append(item)
            elif isinstance(item, str):
                found_dish = next((d for d in cleaned_dishes if d.get("dish") == item or d.get("name") == item), None)
                if found_dish:
                    safe_result.append(found_dish)

        return safe_result
    except Exception as e:
        logger.error(f"LLM ranking failed: {e}")
        return []