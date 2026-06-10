import logging
from collections.abc import Callable
from typing import Any

from app.agent.services.callback_client import AgentCallbackClient
from app.agent.services.agent_executor import BasicAgentExecutor
from app.messaging.base_handler import MessageHandler
from app.messaging.errors import PermanentMessageError
from app.messaging.models import HandlerResult, IncomingMessage

logger = logging.getLogger(__name__)


class AgentRunHandler(MessageHandler):
    def __init__(
        self,
        callback_client: AgentCallbackClient | None = None,
        agent_executor: BasicAgentExecutor | None = None,
    ) -> None:
        self._callback_client = callback_client or AgentCallbackClient()
        self._agent_executor = agent_executor or BasicAgentExecutor()

    def handle(self, message: IncomingMessage) -> HandlerResult:
        body = message.body
        self._require(body, "agentSessionId")
        self._require(body, "sessionSecret")
        self._require(body, "conversationId")
        self._require(body, "messageId")
        self._require(body, "callbackUrl")
        self._require(body, "dataGatewayUrl")

        agent_session_id = str(body["agentSessionId"])
        user_id = body.get("userId")
        username = body.get("username")
        logger.info(
            "agent run start consumed session_id=%s conversation_id=%s message_id=%s user_id=%s username=%s",
            agent_session_id,
            body.get("conversationId"),
            body.get("messageId"),
            user_id,
            username,
        )
        run_result = self._agent_executor.run(
            body,
            answer_delta_callback=self._answer_delta_callback(body, agent_session_id),
        )
        logger.info(
            "agent run answer generated session_id=%s conversation_id=%s message_id=%s answer_len=%s",
            agent_session_id,
            body.get("conversationId"),
            body.get("messageId"),
            len(run_result.answer),
        )
        self._callback_client.send_final_answer(
            callback_url=str(body["callbackUrl"]),
            agent_session_id=agent_session_id,
            session_secret=str(body["sessionSecret"]),
            conversation_id=str(body["conversationId"]),
            message_id=str(body["messageId"]),
            answer=run_result.answer,
            token_usage_events=run_result.token_usage_events,
        )
        logger.info(
            "agent run final answer callback sent session_id=%s conversation_id=%s message_id=%s",
            agent_session_id,
            body.get("conversationId"),
            body.get("messageId"),
        )
        return HandlerResult()

    def _answer_delta_callback(self, body: dict[str, Any], agent_session_id: str) -> Callable[[str], None]:
        def callback(delta: str) -> None:
            if not delta:
                return
            try:
                self._callback_client.send_answer_delta(
                    callback_url=str(body["callbackUrl"]),
                    agent_session_id=agent_session_id,
                    session_secret=str(body["sessionSecret"]),
                    conversation_id=str(body["conversationId"]),
                    message_id=str(body["messageId"]),
                    delta=delta,
                )
            except Exception as exc:
                logger.warning(
                    "agent run answer delta callback failed session_id=%s conversation_id=%s message_id=%s error=%s",
                    agent_session_id,
                    body.get("conversationId"),
                    body.get("messageId"),
                    exc,
                )

        return callback

    def _require(self, body: dict[str, Any], key: str) -> None:
        value = body.get(key)
        if value is None or str(value).strip() == "":
            raise PermanentMessageError(f"agent run start message {key} is required")
