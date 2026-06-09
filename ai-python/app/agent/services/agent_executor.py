from __future__ import annotations

import logging
from typing import Any

from app.agent.llm.deepseek_chat import DeepSeekChatModelFactory
from app.agent.memory.memory_loader import AgentMemoryLoader
from app.agent.planning.agent_planner import AgentPlanner
from app.agent.prompts.prompt_builder import AgentPromptBuilder
from app.agent.runtime.answer_generator import AgentAnswerGenerator
from app.agent.runtime.tool_call_runner import ToolCallRunner
from app.agent.services.answer_builder import AgentAnswerBuilder
from app.agent.tools.conversation_history_tool import ConversationHistoryTool
from app.agent.tools.market_quote_tool import MarketQuoteTool
from app.agent.tools.tool_registry import AgentToolContext, AgentToolRegistry

logger = logging.getLogger(__name__)


class BasicAgentExecutor:
    def __init__(
        self,
        market_quote_tool: MarketQuoteTool | None = None,
        conversation_history_tool: ConversationHistoryTool | None = None,
        model_factory: DeepSeekChatModelFactory | None = None,
        answer_builder: AgentAnswerBuilder | None = None,
        memory_loader: AgentMemoryLoader | None = None,
        prompt_builder: AgentPromptBuilder | None = None,
        tool_registry: AgentToolRegistry | None = None,
        planner: AgentPlanner | None = None,
        tool_call_runner: ToolCallRunner | None = None,
        answer_generator: AgentAnswerGenerator | None = None,
    ) -> None:
        self._model_factory = model_factory or DeepSeekChatModelFactory()
        self._memory_loader = memory_loader or AgentMemoryLoader(conversation_history_tool)
        self._prompt_builder = prompt_builder or AgentPromptBuilder()
        self._tool_registry = tool_registry or AgentToolRegistry(market_quote_tool)
        self._planner = planner or AgentPlanner()
        self._tool_call_runner = tool_call_runner or ToolCallRunner()
        self._answer_generator = answer_generator or AgentAnswerGenerator(answer_builder)

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

        try:
            model = self._model_factory.create()
            history = self._memory_loader.load_short_term_memory(
                data_gateway_url=data_gateway_url,
                agent_session_id=agent_session_id,
                session_secret=session_secret,
                limit=20,
            )
            messages = self._prompt_builder.build_messages(
                system_message_type=SystemMessage,
                human_message_type=HumanMessage,
                user_message=user_message,
                history=history,
            )
            tools_by_name = self._tool_registry.build_langchain_tools(
                AgentToolContext(
                    data_gateway_url=data_gateway_url,
                    agent_session_id=agent_session_id,
                    session_secret=session_secret,
                ),
                tool,
            )
            plan = self._planner.plan(
                model=model,
                messages=messages,
                tools=list(tools_by_name.values()),
                agent_session_id=agent_session_id,
            )
            if plan.standard_tool_calls:
                tool_messages = self._tool_call_runner.run_standard_tools(
                    tool_calls=plan.standard_tool_calls,
                    tools_by_name=tools_by_name,
                    agent_session_id=agent_session_id,
                    tool_message_type=ToolMessage,
                )
                return self._answer_generator.answer_with_standard_tools(
                    model=model,
                    messages=messages,
                    planning_message=plan.planning_message,
                    tool_messages=tool_messages,
                    quote_result=self._tool_registry.last_market_quote_result,
                    agent_session_id=agent_session_id,
                )
            return self._answer_generator.answer_without_tools(
                content=plan.content,
                quote_result=self._tool_registry.last_market_quote_result,
                agent_session_id=agent_session_id,
            )
        except Exception as exc:
            logger.warning("langchain tool calling failed: %s", exc)
            return None
