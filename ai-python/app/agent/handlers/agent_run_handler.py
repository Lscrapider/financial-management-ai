import logging
from typing import Any

from app.agent.services.callback_client import AgentCallbackClient
from app.messaging.base_handler import MessageHandler
from app.messaging.errors import PermanentMessageError
from app.messaging.models import HandlerResult, IncomingMessage

logger = logging.getLogger(__name__)


class AgentRunHandler(MessageHandler):
    def __init__(self, callback_client: AgentCallbackClient | None = None) -> None:
        self._callback_client = callback_client or AgentCallbackClient()

    def handle(self, message: IncomingMessage) -> HandlerResult:
        body = message.body
        self._require(body, "agentSessionId")
        self._require(body, "sessionSecret")
        self._require(body, "conversationId")
        self._require(body, "messageId")
        self._require(body, "callbackUrl")

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
        self._callback_client.send_final_answer(
            callback_url=str(body["callbackUrl"]),
            agent_session_id=agent_session_id,
            session_secret=str(body["sessionSecret"]),
            conversation_id=str(body["conversationId"]),
            message_id=str(body["messageId"]),
            answer="Agent 链路已接通，后续将接入 LangChain 分析能力。",
        )
        return HandlerResult()

    def _require(self, body: dict[str, Any], key: str) -> None:
        value = body.get(key)
        if value is None or str(value).strip() == "":
            raise PermanentMessageError(f"agent run start message {key} is required")
