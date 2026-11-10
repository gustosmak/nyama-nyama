# utils/llm.py
import os
from langchain_openai import ChatOpenAI

def get_llm(temperature: float = 0.3) -> ChatOpenAI:
    """
    Клиент Cloud.ru Evolution AI Factory.
    Увеличили max_tokens, чтобы LLM не обрезала длинные JSON-ответы.
    """
    return ChatOpenAI(
        model="ai-sage/GigaChat3-10B-A1.8B",
        temperature=temperature,
        base_url="https://foundation-models.api.cloud.ru/v1",
        api_key=os.getenv("CLOUD_RU_API_KEY"),
        max_tokens=4000,  # 👈 УВЕЛИЧИЛИ ЛИМИТ
    )