# utils/llm.py
import ast
import json
import os
import re

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


def loosen_quotes(text: str) -> str:
    """Меняет кавычки внутри строк JSON на одинарные: Салат "Квиноа"."""
    out = []
    in_string = False
    escaped = False
    i = 0
    length = len(text)
    while i < length:
        char = text[i]
        if not in_string:
            out.append(char)
            if char == '"':
                in_string = True
            i += 1
            continue
        if escaped:
            out.append(char)
            escaped = False
            i += 1
            continue
        if char == "\\":
            out.append(char)
            escaped = True
            i += 1
            continue
        if char == '"':
            j = i + 1
            while j < length and text[j] in " \t\r\n":
                j += 1
            if j >= length or text[j] in ",}]:":
                out.append(char)
                in_string = False
            else:
                out.append("'")
            i += 1
            continue
        out.append(char)
        i += 1
    return "".join(out)


def parse_llm_json(text: str):
    cleaned = text.replace("```json", "").replace("```", "").strip()
    match = re.search(r"(\[.*\]|\{.*\})", cleaned, re.DOTALL)
    raw = match.group(0) if match else cleaned
    for candidate in (raw, loosen_quotes(raw)):
        for loader in (json.loads, ast.literal_eval):
            try:
                return loader(candidate)
            except (json.JSONDecodeError, SyntaxError, ValueError):
                continue
    return None