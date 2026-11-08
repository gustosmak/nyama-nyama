from fastmcp import FastMCP
from pydantic import BaseModel, Field, ValidationError
from typing import Dict, Any

mcp = FastMCP("FoodAI-Validator")


class DishInput(BaseModel):
    name: str = Field(..., max_length=100, description="Название блюда")
    price: float = Field(..., ge=0, description="Цена должна быть положительной")


@mcp.tool()
def validate_recommendation(name: str, price: float, user_budget: float) -> Dict[str, Any]:
    """
    Проверяет рекомендацию на безопасность и соответствие бюджету.
    Использует Pydantic для строгой валидации входных данных.
    """
    errors = []

    # 1. Валидация типов и форматов через Pydantic
    try:
        dish = DishInput(name=name, price=price)
    except ValidationError as e:
        return {"is_valid": False, "errors": [f"Ошибка формата данных: {e.errors()[0]['msg']}"]}

    # 2. Проверка бюджета
    if dish.price > user_budget:
        errors.append(f"Превышен бюджет: цена {dish.price} > {user_budget}")

    # 3. Эвристическая проверка на явные аномалии (Защита от атак)
    suspicious_patterns = [
        "system", "prompt", "ignore", "instructions", "exec", "eval",
        "систем", "промпт", "игнорир", "инструкц", "выведи", "забудь"
    ]
    name_lower = dish.name.lower()

    if any(pattern in name_lower for pattern in suspicious_patterns):
        if len(dish.name.split()) > 3:
            errors.append("Обнаружена попытка Prompt Injection (подозрительный паттерн в названии)")

    if errors:
        return {"is_valid": False, "errors": errors}

    return {"is_valid": True, "errors": []}


if __name__ == "__main__":
    mcp.run(transport="streamable-http")