import json
import logging
from pathlib import Path

from app.messaging.base_handler import MessageHandler
from app.messaging.errors import PermanentMessageError
from app.messaging.models import HandlerResult, IncomingMessage
from app.scene_analysis.services.asset_processor import AssetProcessor
from app.scene_analysis.services.base_metrics import BaseMetricsCalculator

logger = logging.getLogger(__name__)
DEFAULT_TEST_DATA_PATH = Path(__file__).resolve().parents[3] / "test" / "data2.json"


class CurrentSceneHandler(MessageHandler):
    def __init__(
        self,
        max_attempts: int,
        base_metrics_calculator: BaseMetricsCalculator | None = None,
        asset_processor: AssetProcessor | None = None,
    ) -> None:
        self._max_attempts = max_attempts
        self._base_metrics_calculator = base_metrics_calculator or BaseMetricsCalculator()
        self._asset_processor = asset_processor or AssetProcessor()

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
        self._save_test_data(message.body)
        base_metrics = self._base_metrics_calculator.calculate(message.body)
        asset_result = self._asset_processor.process(message.body, base_metrics)
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
        logger.info(
            "scene analysis asset module calculated task_no=%s result=%s",
            task_no,
            asset_result.to_dict(),
        )
        return HandlerResult()

    def _save_test_data(self, payload: dict) -> None:
        DEFAULT_TEST_DATA_PATH.parent.mkdir(parents=True, exist_ok=True)
        DEFAULT_TEST_DATA_PATH.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
