from __future__ import annotations

import logging
from dataclasses import dataclass
from collections.abc import Callable
from typing import Any

from app.agent.graph.graph_runner import AgentGraphRunner
from app.agent.llm.deepseek_chat import DeepSeekChatModelFactory
from app.agent.prompts.prompt_builder import AgentPromptBuilder
from app.agent.runtime.agent_execution_budget import AgentExecutionBudget
from app.agent.runtime.answer_generator import AgentAnswerGenerator
from app.agent.runtime.token_usage import AgentTokenUsageCollector
from app.agent.runtime.tool_call_runner import ToolCallRunner
from app.agent.services.answer_builder import AgentAnswerBuilder
from app.agent.services.data_gateway_client import AgentDataGatewayClient
from app.agent.tools.conversation_history_tool import ConversationHistoryTool
from app.agent.tools.market_quote_tool import MarketQuoteTool
from app.agent.tools.tool_registry import AgentToolContext, AgentToolRegistry

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class AgentRunResult:
    answer: str
    token_usage_events: list[dict[str, Any]]


class BasicAgentExecutor:
    def __init__(
        self,
        market_quote_tool: MarketQuoteTool | None = None,
        conversation_history_tool: ConversationHistoryTool | None = None,
        model_factory: DeepSeekChatModelFactory | None = None,
        answer_builder: AgentAnswerBuilder | None = None,
        prompt_builder: AgentPromptBuilder | None = None,
        tool_registry: AgentToolRegistry | None = None,
        tool_call_runner: ToolCallRunner | None = None,
        answer_generator: AgentAnswerGenerator | None = None,
        data_gateway_client: AgentDataGatewayClient | None = None,
        graph_runner: AgentGraphRunner | None = None,
    ) -> None:
        self._model_factory = model_factory or DeepSeekChatModelFactory()
        self._conversation_history_tool = conversation_history_tool or ConversationHistoryTool()
        self._prompt_builder = prompt_builder or AgentPromptBuilder()
        self._tool_registry = tool_registry or AgentToolRegistry(
            market_quote_tool=market_quote_tool,
        )
        self._tool_call_runner = tool_call_runner or ToolCallRunner()
        self._answer_generator = answer_generator or AgentAnswerGenerator(answer_builder)
        self._data_gateway_client = data_gateway_client or AgentDataGatewayClient()
        self._agent_graph_runner = graph_runner or AgentGraphRunner(
            tool_call_runner=self._tool_call_runner,
            answer_generator=self._answer_generator,
        )

    def run(
        self,
        message_body: dict[str, Any],
        answer_delta_callback: Callable[[str], None] | None = None,
    ) -> AgentRunResult:
        user_message = str(message_body.get("userMessage") or "").strip()
        agent_session_id = str(message_body["agentSessionId"])
        session_secret = str(message_body["sessionSecret"])
        data_gateway_url = str(message_body["dataGatewayUrl"])
        user_id = self._normalized_user_id(message_body.get("userId"))
        execution_budget = AgentExecutionBudget.from_payload(message_body.get("executionBudget"))
        logger.info(
            "agent executor start session_id=%s conversation_id=%s message_id=%s user_message_len=%s max_steps=%s max_tool_calls_total=%s max_tool_calls_per_step=%s timeout_seconds=%s max_final_backtracks=%s",
            agent_session_id,
            message_body.get("conversationId"),
            message_body.get("messageId"),
            len(user_message),
            execution_budget.max_steps,
            execution_budget.max_tool_calls_total,
            execution_budget.max_tool_calls_per_step,
            execution_budget.timeout_seconds,
            execution_budget.max_final_backtracks,
        )
        result = self._tool_calling_answer(
            data_gateway_url=data_gateway_url,
            agent_session_id=agent_session_id,
            session_secret=session_secret,
            user_message=user_message,
            user_id=user_id,
            execution_budget=execution_budget,
            answer_delta_callback=answer_delta_callback,
        )
        if result and result.answer:
            logger.info(
                "agent executor langchain answer done session_id=%s answer_len=%s",
                agent_session_id,
                len(result.answer),
            )
            return result
        logger.warning("agent executor failed without model answer session_id=%s", agent_session_id)
        return AgentRunResult("AI Agent 暂时没有完成工具规划，请稍后重试。", [])

    def _tool_calling_answer(
        self,
        data_gateway_url: str,
        agent_session_id: str,
        session_secret: str,
        user_message: str,
        user_id: str | None = None,
        execution_budget: AgentExecutionBudget | None = None,
        answer_delta_callback: Callable[[str], None] | None = None,
    ) -> AgentRunResult | None:
        try:
            from langchain_core.messages import HumanMessage, SystemMessage, ToolMessage
            from langchain_core.tools import tool
        except ImportError:
            logger.warning("langchain is not installed, model tool planning cannot run")
            return None

        try:
            model = self._model_factory.create(user_id=user_id)
            token_usage_collector = AgentTokenUsageCollector()
            messages = self._prompt_builder.build_messages(
                system_message_type=SystemMessage,
                human_message_type=HumanMessage,
                user_message=user_message,
                history=[],
            )
            tools_by_name = self._tool_registry.build_langchain_tools(
                AgentToolContext(
                    data_gateway_url=data_gateway_url,
                    agent_session_id=agent_session_id,
                    session_secret=session_secret,
                ),
                tool,
            )
            graph_result = self._agent_graph_runner.run(
                model=model,
                messages=messages,
                tools_by_name=tools_by_name,
                tool_message_type=ToolMessage,
                quote_result_provider=lambda: self._tool_registry.last_market_quote_result,
                agent_session_id=agent_session_id,
                budget=execution_budget,
                token_usage_collector=token_usage_collector,
                answer_delta_callback=answer_delta_callback,
                psych_profile_provider=lambda: self._load_psych_profile(
                    data_gateway_url,
                    agent_session_id,
                    session_secret,
                ),
                memory_provider=lambda mode: self._load_memory_context(
                    data_gateway_url,
                    agent_session_id,
                    session_secret,
                    mode,
                ),
            )
            answer = graph_result.answer
            if not answer:
                return None
            return AgentRunResult(answer, token_usage_collector.events())
        except Exception as exc:
            logger.warning("langchain tool calling failed: %s", exc)
            return None

    def _load_psych_profile(
        self,
        data_gateway_url: str,
        agent_session_id: str,
        session_secret: str,
    ) -> dict[str, Any] | None:
        try:
            result = self._data_gateway_client.query(
                data_gateway_url=data_gateway_url,
                agent_session_id=agent_session_id,
                session_secret=session_secret,
                action="investor.psych_profile",
            )
            rows = result.get("data") if isinstance(result, dict) else None
            if not isinstance(rows, list) or not rows:
                return None
            profile = rows[0]
            if not isinstance(profile, dict):
                return None
            return profile
        except Exception as exc:
            logger.warning("agent psych profile query failed session_id=%s error=%s", agent_session_id, exc)
            return None

    def _load_memory_context(
        self,
        data_gateway_url: str,
        agent_session_id: str,
        session_secret: str,
        mode: str,
    ) -> str | None:
        return self._conversation_history_tool.invoke(
            data_gateway_url=data_gateway_url,
            agent_session_id=agent_session_id,
            session_secret=session_secret,
            mode=mode,
            limit=ConversationHistoryTool.MEMORY_WINDOW,
        )

    def _normalized_user_id(self, user_id: Any) -> str | None:
        normalized = str(user_id or "").strip()
        return normalized or None
