import logging

from app.messaging.base_handler import MessageHandler
from app.messaging.errors import PermanentMessageError
from app.messaging.models import HandlerResult, IncomingMessage
from app.scene_analysis.services.base_metrics import BaseMetricsCalculator

logger = logging.getLogger(__name__)


class CurrentSceneHandler(MessageHandler):
    def __init__(self, max_attempts: int, base_metrics_calculator: BaseMetricsCalculator | None = None) -> None:
        self._max_attempts = max_attempts
        self._base_metrics_calculator = base_metrics_calculator or BaseMetricsCalculator()

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
        base_metrics = self._base_metrics_calculator.calculate(message.body)
        logger.info(
            "scene analysis base metrics calculated task_no=%s target_type=%s target_code=%s report_type=%s "
            "metric_count=%s missing_metrics=%s attempt=%s/%s",
            task_no,
            target.get("type"),
            target.get("code"),
            message.body.get("reportType"),
            len(base_metrics.values),
            base_metrics.missing,
            message.attempt,
            self._max_attempts,
        )
        return HandlerResult()
