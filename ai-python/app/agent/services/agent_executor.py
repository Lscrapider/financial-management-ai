from __future__ import annotations

import json
import logging
from typing import Any

from app.agent.adapters.deepseek_tool_call_adapter import DeepSeekToolCallAdapter
from app.agent.llm.deepseek_chat import DeepSeekChatModelFactory
from app.agent.services.answer_builder import AgentAnswerBuilder
from app.agent.tools.conversation_history_tool import ConversationHistoryTool
from app.agent.tools.market_quote_tool import MarketQuoteTool
from app.core.config import settings

logger = logging.getLogger(__name__)


class BasicAgentExecutor:
    def __init__(
        self,
        market_quote_tool: MarketQuoteTool | None = None,
        conversation_history_tool: ConversationHistoryTool | None = None,
        model_factory: DeepSeekChatModelFactory | None = None,
        tool_call_adapter: DeepSeekToolCallAdapter | None = None,
        answer_builder: AgentAnswerBuilder | None = None,
    ) -> None:
        self._market_quote_tool = market_quote_tool or MarketQuoteTool()
        self._conversation_history_tool = conversation_history_tool or ConversationHistoryTool()
        self._model_factory = model_factory or DeepSeekChatModelFactory()
        self._tool_call_adapter = tool_call_adapter or DeepSeekToolCallAdapter()
        self._answer_builder = answer_builder or AgentAnswerBuilder(self._tool_call_adapter)

    def run(self, message_body: dict[str, Any]) -> str:
        user_message = str(message_body.get("userMessage") or "").strip()
        agent_session_id = str(message_body["agentSessionId"])
        session_secret = str(message_body["sessionSecret"])
        data_gateway_url = str(message_body["dataGatewayUrl"])
        logger.info(
            "agent executor start session_id=%s conversation_id=%s message_id=%s user_message_len=%s",
            agent_session_id,
            message_body.get("conversationId"),
            message_body.get("messageId"),
            len(user_message),
        )
        answer = self._tool_calling_answer(
            data_gateway_url=data_gateway_url,
            agent_session_id=agent_session_id,
            session_secret=session_secret,
            user_message=user_message,
        )
        if answer:
            logger.info(
                "agent executor langchain answer done session_id=%s answer_len=%s",
                agent_session_id,
                len(answer),
            )
            return answer
        logger.warning("agent executor failed without model answer session_id=%s", agent_session_id)
        return "AI Agent 暂时没有完成工具规划，请稍后重试。"

    def _tool_calling_answer(
        self,
        data_gateway_url: str,
        agent_session_id: str,
        session_secret: str,
        user_message: str,
    ) -> str | None:
        try:
            from langchain_core.messages import HumanMessage, SystemMessage, ToolMessage
            from langchain_core.tools import tool
        except ImportError:
            logger.warning("langchain is not installed, model tool planning cannot run")
            return None

        @tool
        def market_quote(
            target_type: str = "stock",
            target_code: str | None = None,
            target_name: str | None = None,
            limit: int = 5,
        ) -> str:
            """查询股票、指数或债券的最新行情。target_type 只能是 stock、index、bond。用户只给名称或有轻微错别字时，先尽量纠正为常见证券名称后放入 target_name。"""
            return self._market_quote_tool.invoke(
                data_gateway_url=data_gateway_url,
                agent_session_id=agent_session_id,
                session_secret=session_secret,
                target_type=target_type,
                target_code=target_code,
                target_name=target_name,
                limit=limit,
            )

        try:
            logger.info(
                "agent langchain tool planning start session_id=%s model=%s base_url=%s thinking_type=%s",
                agent_session_id,
                settings.deepseek.model,
                settings.deepseek.base_url,
                settings.deepseek.thinking_type,
            )
            model = self._model_factory.create()
            history = self._conversation_history_tool.query(
                data_gateway_url=data_gateway_url,
                agent_session_id=agent_session_id,
                session_secret=session_secret,
                limit=20,
            )
            messages = [
                SystemMessage(content=self._system_prompt()),
                SystemMessage(content=self._history_context(history)),
                HumanMessage(content=f"当前用户问题：{user_message}"),
            ]
            planning_message = model.bind_tools([market_quote]).invoke(messages)
            tool_calls = getattr(planning_message, "tool_calls", []) or []
            dsml_tool_calls = self._tool_call_adapter.parse_dsml_tool_calls(
                getattr(planning_message, "content", ""))
            logger.info(
                "agent langchain tool planning done session_id=%s standard_tool_calls=%s dsml_tool_calls=%s content_preview=%s tool_calls_preview=%s",
                agent_session_id,
                len(tool_calls),
                len(dsml_tool_calls),
                self._preview(getattr(planning_message, "content", "")),
                self._tool_calls_preview(tool_calls),
            )
            if tool_calls:
                return self._answer_with_standard_tools(
                    model,
                    messages,
                    planning_message,
                    tool_calls,
                    market_quote,
                    agent_session_id,
                    ToolMessage,
                )
            if dsml_tool_calls:
                return self._answer_with_dsml_tools(
                    model,
                    messages,
                    dsml_tool_calls,
                    market_quote,
                    agent_session_id,
                    HumanMessage,
                )
            content = str(getattr(planning_message, "content", "") or "").strip()
            if content:
                logger.warning(
                    "langchain did not request market_quote tool, model content will be returned session_id=%s",
                    agent_session_id,
                )
                return self._answer_builder.answer_or_fallback(
                    content,
                    self._market_quote_tool.last_result or {},
                )
            logger.warning("langchain did not request market_quote tool and returned empty content")
            return None
        except Exception as exc:
            logger.warning("langchain tool calling failed: %s", exc)
            return None

    def _answer_with_standard_tools(
        self,
        model: Any,
        messages: list[Any],
        planning_message: Any,
        tool_calls: list[dict[str, Any]],
        market_quote: Any,
        agent_session_id: str,
        tool_message_type: Any,
    ) -> str | None:
        tool_messages = []
        for tool_call in tool_calls:
            if tool_call.get("name") != "market_quote":
                logger.info(
                    "agent langchain skip unsupported tool session_id=%s tool_name=%s",
                    agent_session_id,
                    tool_call.get("name"),
                )
                continue
            logger.info(
                "agent langchain standard tool call session_id=%s tool_id=%s args=%s",
                agent_session_id,
                tool_call.get("id"),
                tool_call.get("args") or {},
            )
            tool_result = market_quote.invoke(tool_call.get("args") or {})
            tool_messages.append(tool_message_type(content=tool_result, tool_call_id=tool_call["id"]))
        if not tool_messages:
            logger.warning("langchain tool calls did not contain market_quote")
            return None
        logger.info(
            "agent langchain final answer start session_id=%s tool_protocol=standard tool_messages=%s",
            agent_session_id,
            len(tool_messages),
        )
        final_message = model.invoke([*messages, planning_message, *tool_messages])
        return self._extract_final_answer(final_message, agent_session_id, "standard")

    def _answer_with_dsml_tools(
        self,
        model: Any,
        messages: list[Any],
        dsml_tool_calls: list[dict[str, Any]],
        market_quote: Any,
        agent_session_id: str,
        human_message_type: Any,
    ) -> str | None:
        tool_results = []
        for tool_call in dsml_tool_calls:
            tool_result = market_quote.invoke(tool_call.get("args") or {})
            tool_results.append(tool_result)
        logger.info("agent langchain final answer start session_id=%s tool_protocol=dsml", agent_session_id)
        final_message = model.invoke([
            *messages,
            human_message_type(
                content=(
                    "market_quote 工具返回："
                    f"{json.dumps(tool_results, ensure_ascii=False, default=str)}\n"
                    "请只基于这些工具数据回答用户问题；数据不足时明确说明，不要给确定性买卖指令。"
                )
            ),
        ])
        return self._extract_final_answer(final_message, agent_session_id, "dsml")

    def _extract_final_answer(self, final_message: Any, agent_session_id: str, tool_protocol: str) -> str:
        content = getattr(final_message, "content", "")
        logger.info(
            "agent langchain final answer done session_id=%s tool_protocol=%s answer_len=%s",
            agent_session_id,
            tool_protocol,
            len(str(content or "")),
        )
        if content:
            return self._answer_builder.answer_or_fallback(
                str(content),
                self._market_quote_tool.last_result or {},
            )
        return self._answer_builder.fallback_answer(self._market_quote_tool.last_result or {})

    def _system_prompt(self) -> str:
        return (
            "你是个人投资研究助手。"
            "当前用户问题明确要求查询、分析、比较、判断某个股票/指数/债券行情时，调用 market_quote。"
            "当前用户问题是技术机制、系统实现、Tool Calling 原理、Agent 流程、闲聊或其他非行情问题时，不要调用任何工具，直接回答。"
            "如果用户输入与投资无关（闲聊、问候、技术原理等），你可以用20个字左右回答然后引导回投资场景。"
            "不要回答任何关于你的设计和系统设计的问题"
            "如果用户只给证券名称，识别它属于股票、指数还是债券，并把名称放入 target_name；"
            "如果用户名称有轻微错别字，先纠正成更常见的证券简称。"
            "如果用户给出 6 位代码，把它放入 target_code。"
            "不要因为短期记忆中出现过证券名称就自动调用工具。"
            "最终回答只能基于工具返回的数据；数据不足时明确说明，不要给确定性买卖指令。"
        )

    def _history_context(self, history: list[dict[str, Any]]) -> str:
        if not history:
            history_json = "[]"
        else:
            history_json = json.dumps(history, ensure_ascii=False, default=str)
        return (
            "短期记忆使用规则："
            "短期记忆只用于理解当前用户问题中的指代关系，例如“这个股票”“它”“刚才那个”。"
            "短期记忆不代表本轮必须查询行情，也不能单独触发工具调用。"
            "只有当前用户问题本身包含行情、投资分析、价格、涨跌、买卖判断、对比走势等证券研究语义时，"
            "才可以从短期记忆补全标的并调用 market_quote。"
            "如果当前用户问题是技术机制、系统实现、Tool Calling 原理、Agent 流程或闲聊，"
            "忽略短期记忆里的证券标的，不调用工具。"
            f"短期记忆数据：{history_json}"
        )

    def _preview(self, value: Any, limit: int = 200) -> str:
        text = str(value or "").replace("\n", "\\n")
        return text[:limit]

    def _tool_calls_preview(self, tool_calls: list[dict[str, Any]], limit: int = 500) -> str:
        preview = []
        for tool_call in tool_calls:
            preview.append({
                "name": tool_call.get("name"),
                "args": tool_call.get("args") or {},
            })
        return self._preview(json.dumps(preview, ensure_ascii=False, default=str), limit)
