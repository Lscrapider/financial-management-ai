from datetime import date, datetime
import logging
import uuid

from app.messaging.base_handler import MessageHandler
from app.messaging.errors import PermanentMessageError, RetryableMessageError
from app.messaging.models import HandlerResult, IncomingMessage, OutgoingMessage
from app.ocr.constants import (
    OCR_TOPIC_EXCHANGE,
    ROUTING_KEY_QUALITY_VALIDATE,
    STAGE_QUALITY_VALIDATE,
    STAGE_TEXT_CLEAN,
)
from app.ocr.repository import OcrTaskRepository
from app.ocr.services.text_cleaner import TextCleaner

logger = logging.getLogger(__name__)


class TextCleanHandler(MessageHandler):
    def __init__(self, repository: OcrTaskRepository, cleaner: TextCleaner, max_attempts: int) -> None:
        self._repository = repository
        self._cleaner = cleaner
        self._max_attempts = max_attempts

    def handle(self, message: IncomingMessage) -> HandlerResult:
        if not message.task_no:
            raise PermanentMessageError("taskNo is required")
        if message.stage != STAGE_TEXT_CLEAN:
            raise PermanentMessageError(f"unexpected stage: {message.stage}")

        task = self._repository.find_by_task_no(message.task_no)
        if task is None:
            raise RetryableMessageError(f"ocr_task not found: {message.task_no}")
        if task.status in {"finished", "failed", "manual_review_required"}:
            logger.info("skip terminal task task_no=%s status=%s", task.task_no, task.status)
            return HandlerResult()

        input_ref = self._input_ref(message.body)
        self._repository.mark_running(message.task_no, STAGE_TEXT_CLEAN, progress=50)
        self._repository.start_stage(
            task_no=message.task_no,
            stage=STAGE_TEXT_CLEAN,
            attempt=message.attempt,
            max_attempts=self._max_attempts,
            input_message=message.body,
            input_ref=input_ref,
        )
        logger.info("text clean started task_no=%s input_ref=%s", message.task_no, input_ref)

        try:
            clean_result = self._cleaner.clean(message.body)
        except Exception as exc:
            self._repository.fail_stage(
                message.task_no,
                STAGE_TEXT_CLEAN,
                str(exc),
                mark_task_failed=message.attempt >= self._max_attempts,
            )
            raise

        next_message = self._build_next_message(message, clean_result)
        self._repository.finish_stage(
            task_no=message.task_no,
            stage=STAGE_TEXT_CLEAN,
            next_stage=STAGE_QUALITY_VALIDATE,
            progress=60,
            page_count=int(message.body.get("pageCount") or 0),
            segment_count=int(clean_result["paragraphCount"]),
            output_ref=clean_result["outputRef"],
            output_message=next_message,
            metrics=clean_result["metrics"],
        )
        logger.info(
            "text clean finished task_no=%s paragraph_count=%s output_ref=%s",
            message.task_no,
            clean_result["paragraphCount"],
            clean_result["outputRef"],
        )
        return HandlerResult(
            outgoing_messages=[
                OutgoingMessage(
                    exchange=OCR_TOPIC_EXCHANGE,
                    routing_key=ROUTING_KEY_QUALITY_VALIDATE,
                    body=next_message,
                )
            ]
        )

    def _input_ref(self, payload: dict) -> dict:
        return payload.get("inputRef") or {}

    def _build_next_message(self, message: IncomingMessage, clean_result: dict) -> dict:
        return {
            "eventId": str(uuid.uuid4()),
            "taskNo": message.task_no,
            "stage": STAGE_QUALITY_VALIDATE,
            "attempt": 1,
            "inputRef": clean_result["outputRef"],
            "paragraphCount": clean_result["paragraphCount"],
            "metrics": clean_result["metrics"],
            "outputPrefix": {
                "storageType": "minio",
                "bucket": clean_result["outputRef"].get("bucket"),
                "objectKey": f"{self._stage_output_prefix(message.task_no)}/quality/",
            },
            "createdAt": datetime.now().isoformat(timespec="seconds"),
        }

    def _stage_output_prefix(self, task_no: str) -> str:
        today = date.today()
        return f"stage-4-output/{today:%Y/%m/%d}/{task_no}"
