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
        budget: float,
        hunger: str,
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

    system_prompt = (
        "Ты эксперт по быстрому питанию. Отвечай ТОЛЬКО чистым JSON-массивом. "
        "Никакого markdown (без ```json), никакого текста до или после JSON."
    )

    prompt = ChatPromptTemplate.from_messages([
        ("system", system_prompt),
        ("human", "{user_prompt}"),
    ])

    user_prompt = (
        f"Блюда: {json.dumps(cleaned_dishes, ensure_ascii=False, default=str)}\n"
        f"Голод: {hunger}\n"
        f"Профиль: {json.dumps(profile, ensure_ascii=False, default=str) if profile else 'нет'}\n"
        "\n⚠️ ВАЖНО: Все блюда в списке УЖЕ ПРОВЕРЕНЫ и ТОЧНО ВПИСЫВАЮТСЯ в бюджет. "
        "НЕ ПЫТАЙСЯ считать бюджет и НИКОГДА не пиши в ответе, что блюдо не входит в бюджет.\n"
        "\n⚠️ КРИТИЧЕСКИ ВАЖНО: В значениях полей 'dish' и 'reason' НЕ ИСПОЛЬЗУЙ ДВОЙНЫЕ КАВЫЧКИ (\"). "
        "Если нужно, используй одинарные кавычки (например, Салат 'Цезарь').\n"
        "\nТвоя задача: Выбери топ-5 блюд по уровню голода и профилю. "
        "Если голод сильный, выбирай блюда с высоким 'satiety' (ближе к 100). "
        "Если голод слабый, выбирай блюда с низким 'satiety' (ближе к 10-30). "
        "Не выбирай блюда и секции из hidden и avoid. "
        "Учитывай liked, history и from_comment.\n"
        "\nВ поле 'reason' напиши ОЧЕНЬ КОРОТКОЕ обоснование (максимум 3-5 слов), например: 'Сытный обед', 'Легкий перекус'.\n"
        "\nВерни JSON-массив объектов строго с полями: "
        "dish (название), price (цена), restaurant (название ресторана), reason (почему выбрали), calories (калории), satiety (насыщение)."
    )

    chain = prompt | get_llm(temperature=0.3)

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