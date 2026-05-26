from datetime import date, datetime
import logging
import uuid

from app.messaging.base_handler import MessageHandler
from app.messaging.errors import PermanentMessageError, RetryableMessageError
from app.messaging.models import HandlerResult, IncomingMessage, OutgoingMessage
from app.ocr.constants import (
    OCR_TOPIC_EXCHANGE,
    ROUTING_KEY_TEXT_CLEAN,
    STAGE_OCR_RECOGNIZE,
    STAGE_TEXT_CLEAN,
)
from app.ocr.repository import OcrTaskRepository
from app.ocr.services.ocr_recognizer import OcrRecognizer

logger = logging.getLogger(__name__)


class OcrRecognizeHandler(MessageHandler):
    def __init__(self, repository: OcrTaskRepository, recognizer: OcrRecognizer, max_attempts: int) -> None:
        self._repository = repository
        self._recognizer = recognizer
        self._max_attempts = max_attempts

    def handle(self, message: IncomingMessage) -> HandlerResult:
        if not message.task_no:
            raise PermanentMessageError("taskNo is required")
        if message.stage != STAGE_OCR_RECOGNIZE:
            raise PermanentMessageError(f"unexpected stage: {message.stage}")

        task = self._repository.find_by_task_no(message.task_no)
        if task is None:
            raise RetryableMessageError(f"ocr_task not found: {message.task_no}")
        if task.status in {"finished", "failed", "manual_review_required"}:
            logger.info("skip terminal task task_no=%s status=%s", task.task_no, task.status)
            return HandlerResult()

        input_ref = self._input_ref(message.body)
        self._repository.mark_running(message.task_no, STAGE_OCR_RECOGNIZE, progress=30)
        self._repository.start_stage(
            task_no=message.task_no,
            stage=STAGE_OCR_RECOGNIZE,
            attempt=message.attempt,
            max_attempts=self._max_attempts,
            input_message=message.body,
            input_ref=input_ref,
        )
        logger.info("ocr recognize started task_no=%s page_count=%s", message.task_no, len(message.body.get("pages") or []))

        try:
            recognize_result = self._recognizer.recognize(message.body)
        except Exception as exc:
            self._repository.fail_stage(
                message.task_no,
                STAGE_OCR_RECOGNIZE,
                str(exc),
                mark_task_failed=message.attempt >= self._max_attempts,
            )
            raise

        next_message = self._build_next_message(message, recognize_result)
        self._repository.finish_stage(
            task_no=message.task_no,
            stage=STAGE_OCR_RECOGNIZE,
            next_stage=STAGE_TEXT_CLEAN,
            progress=40,
            page_count=int(recognize_result["pageCount"]),
            segment_count=int(recognize_result["segmentCount"]),
            output_ref=recognize_result["outputRef"],
            output_message=next_message,
            metrics=recognize_result["metrics"],
        )
        logger.info(
            "ocr recognize finished task_no=%s segment_count=%s output_ref=%s",
            message.task_no,
            recognize_result["segmentCount"],
            recognize_result["outputRef"],
        )
        return HandlerResult(
            outgoing_messages=[
                OutgoingMessage(
                    exchange=OCR_TOPIC_EXCHANGE,
                    routing_key=ROUTING_KEY_TEXT_CLEAN,
                    body=next_message,
                )
            ]
        )

    def _input_ref(self, payload: dict) -> dict:
        return {
            "sourceRef": payload.get("sourceRef"),
            "pages": payload.get("pages"),
        }

    def _build_next_message(self, message: IncomingMessage, recognize_result: dict) -> dict:
        return {
            "eventId": str(uuid.uuid4()),
            "taskNo": message.task_no,
            "stage": STAGE_TEXT_CLEAN,
            "attempt": 1,
            "inputRef": recognize_result["outputRef"],
            "pageCount": recognize_result["pageCount"],
            "segmentCount": recognize_result["segmentCount"],
            "metrics": recognize_result["metrics"],
            "outputPrefix": {
                "storageType": "minio",
                "bucket": recognize_result["outputRef"].get("bucket"),
                "objectKey": f"{self._stage_output_prefix(message.task_no)}/text/clean/",
            },
            "createdAt": datetime.now().isoformat(timespec="seconds"),
        }

    def _stage_output_prefix(self, task_no: str) -> str:
        today = date.today()
        return f"stage-3-output/{today:%Y/%m/%d}/{task_no}"
