import logging

from app.messaging.base_handler import MessageHandler
from app.messaging.errors import PermanentMessageError
from app.messaging.models import HandlerResult, IncomingMessage

logger = logging.getLogger(__name__)


class CurrentSceneHandler(MessageHandler):
    def __init__(self, max_attempts: int) -> None:
        self._max_attempts = max_attempts

    def handle(self, message: IncomingMessage) -> HandlerResult:
        task_no = message.task_no
        target = message.body.get("target") or {}
        config = message.body.get("config") or {}
        if not task_no:
            raise PermanentMessageError("scene analysis message taskNo is required")
        if not isinstance(target, dict) or not target.get("code"):
            raise PermanentMessageError(f"scene analysis target.code is required task_no={task_no}")
        if not isinstance(config, dict) or not isinstance(config.get("parameters"), dict):
            raise PermanentMessageError(f"scene analysis config.parameters is required task_no={task_no}")
        print(message)
        logger.info(
            "scene analysis message received task_no=%s target_type=%s target_code=%s report_type=%s attempt=%s/%s",
            task_no,
            target.get("type"),
            target.get("code"),
            message.body.get("reportType"),
            message.attempt,
            self._max_attempts,
        )
        return HandlerResult()
