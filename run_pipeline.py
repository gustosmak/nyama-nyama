import os
import asyncio
import json
import time
from contextlib import AsyncExitStack
from typing import Any, Type, List, Dict
from pydantic import BaseModel, Field, create_model
from dotenv import load_dotenv
import httpx

from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client
from langchain_core.tools import BaseTool

from agents.orchestrator import build_graph

load_dotenv()

FMCP_KEY = os.getenv("FASTMCP_CLOUD_API_KEY")

MCP_SERVERS = {
    "location": "https://foodai-location.fastmcp.app/mcp",
    "validator": "https://foodai-validator.fastmcp.app/mcp",
    "menu": "https://foodai-menu-mcp.fastmcp.app/mcp",
}


class MCPToolWrapper(BaseTool):
    name: str
    description: str
    session: ClientSession
    args_schema: Type[BaseModel]

    def _run(self, *args, **kwargs):
        raise NotImplementedError("Используй _arun")

    async def _arun(self, **kwargs):
        try:
            result = await self.session.call_tool(self.name, arguments=kwargs)
            if result.content and len(result.content) > 0:
                return result.content[0].text
            return json.dumps({"status": "ok", "result": "empty"})
        except Exception as e:
            return json.dumps({"status": "error", "message": str(e)})


async def load_tools(stack: AsyncExitStack, name: str, url: str) -> List[BaseTool]:
    print(f"🔌 Подключение к {name}...")
    try:
        http_client = await stack.enter_async_context(
            httpx.AsyncClient(
                headers={"Authorization": f"Bearer {FMCP_KEY}"},
                timeout=httpx.Timeout(30.0, read=300.0),
                follow_redirects=True,
            )
        )

        read, write = await stack.enter_async_context(
            streamable_http_client(url, http_client=http_client)
        )
        session = await stack.enter_async_context(ClientSession(read, write))
        await session.initialize()

        response = await session.list_tools()
        tools = []
        for tool in response.tools:
            schema = getattr(tool, 'input_schema', getattr(tool, 'inputSchema', {}))
            props = schema.get("properties", {})
            required = schema.get("required", [])

            fields = {}
            for p_name, p_info in props.items():
                p_type = str
                if p_info.get("type") == "number":
                    p_type = float
                elif p_info.get("type") == "integer":
                    p_type = int
                elif p_info.get("type") == "boolean":
                    p_type = bool
                fields[p_name] = (p_type, Field(default=... if p_name in required else None,
                                                description=p_info.get("description", "")))

            ArgsModel = create_model(f"{tool.name}_args", **fields)

            tools.append(MCPToolWrapper(
                name=tool.name,
                description=tool.description or "",
                session=session,
                args_schema=ArgsModel
            ))
        print(f"✅ {name}: загружено {len(tools)} инструментов")
        return tools
    except Exception as e:
        print(f"❌ Ошибка при подключении к {name}: {e}")
        return []


async def run_foodai_pipeline(
        lat: float,
        lon: float,
        budgets: list[float],
        hunger: str,
        user_id: int = 1,
        comment: str | None = None,
        liked: list | None = None,
        history: list | None = None,
        hidden: list | None = None,
        session_path: str | None = None,
):
    print(f"\n{'=' * 60}")
    print(f"🚀 ЗАПУСК ПАЙПЛАЙНА")
    print(f"   Бюджеты: {budgets}")
    print(f"   Голод: {hunger}")
    print(f"   Координаты: ({lat}, {lon})")
    print(f"{'=' * 60}\n")

    async with AsyncExitStack() as stack:
        all_tools = []
        for name, url in MCP_SERVERS.items():
            server_tools = await load_tools(stack, name, url)
            all_tools.extend(server_tools)

        if not all_tools:
            print("❌ Не удалось загрузить инструменты ни с одного сервера.")
            return {}

        print(f"\n✅ Всего загружено инструментов: {len(all_tools)}")

        print("🏗️ Компиляция LangGraph...")
        graph = build_graph(db=None, mcp_tools=all_tools)
        print("✅ Граф скомпилирован\n")

        initial_state = {
            "user_id": user_id,
            "lat": lat,
            "lon": lon,
            "budgets": budgets,
            "hunger": hunger,
            "comment": comment,
            "liked": liked,
            "history": history,
            "hidden": hidden,
            "session_path": session_path,
        }

        print("🏃 Запуск графа...\n")
        start_time = time.perf_counter()

        try:
            final_state = await graph.ainvoke(initial_state)
        except Exception as e:
            print(f"❌ Ошибка при выполнении графа: {e}")
            import traceback
            traceback.print_exc()
            return {}

        elapsed = time.perf_counter() - start_time

        print(f"\n{'=' * 60}")
        print(f"✨ РЕЗУЛЬТАТ (общее время: {elapsed:.2f} сек)")
        print(f"{'=' * 60}\n")

        timings = final_state.get("timings", {})
        if timings:
            print("📊 Тайминги узлов:")
            for node, t in timings.items():
                print(f"   - {node}: {t} сек")
            print()

        budget_recs = final_state.get("budget_recommendations", {})

        print(f"{'=' * 60}")
        print("🎯 ФИНАЛЬНЫЙ ОТВЕТ ДЛЯ МОБИЛЬНОГО ПРИЛОЖЕНИЯ:")
        print(f"{'=' * 60}")

        for budget in budgets:
            dishes_for_budget = budget_recs.get(budget, [])
            print(f"\n💵 Бюджет {budget} ₽ → {len(dishes_for_budget)} блюд:")

            if not dishes_for_budget:
                print("   (нет подходящих блюд)")
                continue

            for i, dish in enumerate(dishes_for_budget, 1):
                if not isinstance(dish, dict):
                    continue
                name = dish.get("dish") or dish.get("name", "?")
                price = dish.get("price", "?")
                calories = dish.get("calories", "?")
                restaurant = dish.get("restaurant", "?")
                reason = dish.get("reason", "")
                print(f"   {i}. [{restaurant}] {name} — {price} ₽, {calories} ккал")
                if reason:
                    print(f"      └─ {reason}")

        return budget_recs


if __name__ == "__main__":
    asyncio.run(run_foodai_pipeline(
        lat=55.7905,
        lon=37.5315,
        budgets=[300, 500, 1000],
        hunger="средний",
        user_id=1,
        comment="Хочу плотно поесть, без острого и без десертов",
        liked=[
            {"restaurant": "kfc", "name": "Чизбургер"},
            {"restaurant": "burger king", "name": "Двойной Воппер"},
            {"restaurant": "додо пицца", "name": "Пицца Пепперони"},
        ],
        history=[
            {"restaurant": "вкусно и точка", "name": "Биг Спешиал"},
            {"restaurant": "kfc", "name": "Картофель фри (средний)"},
            {"restaurant": "теремок", "name": "Блин с ветчиной и сыром"},
        ],
        hidden=[
            {"restaurant": "kfc", "name": "Баскет Рост (острый)"},
            {"restaurant": "cofix", "name": "Донат шоколадный"},
            {"restaurant": "subway", "name": "Печенье с шоколадом"},
        ],
    ))