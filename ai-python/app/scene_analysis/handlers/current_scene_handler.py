import json
import logging
from pathlib import Path

from app.core.config import settings
from app.messaging.base_handler import MessageHandler
from app.messaging.errors import PermanentMessageError
from app.messaging.models import HandlerResult, IncomingMessage
from app.scene_analysis.context import SceneAnalysisContext
from app.scene_analysis.services.asset_processor import AssetProcessor
from app.scene_analysis.services.base_metrics import BaseMetricsCalculator
from app.scene_analysis.services.callback_client import SceneAnalysisCallbackClient
from app.scene_analysis.services.current_scene_result import build_current_scenes_payload
from app.scene_analysis.services.market_context import MarketContextBuilder
from app.scene_analysis.services.price_processor import PriceProcessor
from app.scene_analysis.services.risk_strategy_processor import RiskStrategyProcessor
from app.scene_analysis.services.sentiment_processor import SentimentProcessor
from app.scene_analysis.services.trend_processor import TrendProcessor
from app.scene_analysis.services.valuation_processor import ValuationProcessor
from app.scene_analysis.services.volume_processor import VolumeProcessor

logger = logging.getLogger(__name__)
DEFAULT_TEST_DATA_PATH = Path(__file__).resolve().parents[3] / "test" / "data3.json"
class CurrentSceneHandler(MessageHandler):
    def __init__(
        self,
        max_attempts: int,
        base_metrics_calculator: BaseMetricsCalculator | None = None,
        asset_processor: AssetProcessor | None = None,
        price_processor: PriceProcessor | None = None,
        volume_processor: VolumeProcessor | None = None,
        trend_processor: TrendProcessor | None = None,
        valuation_processor: ValuationProcessor | None = None,
        sentiment_processor: SentimentProcessor | None = None,
        risk_strategy_processor: RiskStrategyProcessor | None = None,
        market_context_builder: MarketContextBuilder | None = None,
        callback_client: SceneAnalysisCallbackClient | None = None,
    ) -> None:
        self._max_attempts = max_attempts
        self._base_metrics_calculator = base_metrics_calculator or BaseMetricsCalculator()
        self._asset_processor = asset_processor or AssetProcessor()
        self._price_processor = price_processor or PriceProcessor()
        self._volume_processor = volume_processor or VolumeProcessor()
        self._trend_processor = trend_processor or TrendProcessor()
        self._valuation_processor = valuation_processor or ValuationProcessor()
        self._sentiment_processor = sentiment_processor or SentimentProcessor()
        self._risk_strategy_processor = risk_strategy_processor or RiskStrategyProcessor()
        self._market_context_builder = market_context_builder or MarketContextBuilder()
        self._callback_client = callback_client or SceneAnalysisCallbackClient(settings.finance_api)

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
        callback_token = self._callback_token(message.body, task_no)
        total_chunks = self._total_chunks(message.body)
        # self._save_test_data(message.body)
        base_metrics = self._base_metrics_calculator.calculate(message.body)
        context = SceneAnalysisContext.from_message(message.body, base_metrics)
        asset_result = self._asset_processor.process(context)
        price_result = self._price_processor.process(context)
        volume_result = self._volume_processor.process(context)
        trend_result = self._trend_processor.process(context)
        valuation_result = self._valuation_processor.process(context, trend_result.tags)
        sentiment_result = self._sentiment_processor.process(context)
        risk_strategy_result = self._risk_strategy_processor.process(
            context,
            trend_result.tags,
            valuation_result.tags,
            sentiment_result.tags,
        )
        module_results = [
            asset_result,
            price_result,
            volume_result,
            trend_result,
            valuation_result,
            sentiment_result,
            risk_strategy_result,
        ]
        current_scenes_payload = build_current_scenes_payload(
            target=target,
            report_type=message.body.get("reportType"),
            total_chunks=total_chunks,
            market_context=self._market_context_builder.build(message.body),
            module_results=module_results,
        )
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
            "scene analysis currentScenes calculated task_no=%s result=%s",
            task_no,
            current_scenes_payload,
        )
        self._callback_client.mark_success(task_no, callback_token, current_scenes_payload)
        logger.info("scene analysis callback success task_no=%s", task_no)
        return HandlerResult()

    def _callback_token(self, payload: dict, task_no: str) -> str:
        callback_token = payload.get("callbackToken")
        if not isinstance(callback_token, str) or not callback_token.strip():
            raise PermanentMessageError(f"scene analysis callbackToken is required task_no={task_no}")
        return callback_token.strip()

    def _total_chunks(self, payload: dict) -> int:
        value = payload.get("totalChunks")
        if not isinstance(value, int) or isinstance(value, bool) or value <= 0:
            raise PermanentMessageError("scene analysis totalChunks is required and must be greater than 0")
        return value

    def _save_test_data(self, payload: dict) -> None:
        DEFAULT_TEST_DATA_PATH.parent.mkdir(parents=True, exist_ok=True)
        DEFAULT_TEST_DATA_PATH.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
