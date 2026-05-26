from datetime import datetime
import logging
import uuid

from app.messaging.base_handler import MessageHandler
from app.messaging.errors import PermanentMessageError, RetryableMessageError
from app.messaging.models import HandlerResult, IncomingMessage, OutgoingMessage
from app.ocr.constants import (
    OCR_TOPIC_EXCHANGE,
    ROUTING_KEY_OCR_RECOGNIZE,
    STAGE_DOCUMENT_NORMALIZE,
    STAGE_OCR_RECOGNIZE,
)
from app.ocr.repository import OcrTaskRepository
from app.ocr.services.document_normalizer import DocumentNormalizer

logger = logging.getLogger(__name__)


class DocumentNormalizeHandler(MessageHandler):
    def __init__(self, repository: OcrTaskRepository, normalizer: DocumentNormalizer, max_attempts: int) -> None:
        self._repository = repository
        self._normalizer = normalizer
        self._max_attempts = max_attempts

    def handle(self, message: IncomingMessage) -> HandlerResult:
        if not message.task_no:
            raise PermanentMessageError("taskNo is required")
        if message.stage != STAGE_DOCUMENT_NORMALIZE:
            raise PermanentMessageError(f"unexpected stage: {message.stage}")

        task = self._repository.find_by_task_no(message.task_no)
        if task is None:
            raise RetryableMessageError(f"ocr_task not found: {message.task_no}")
        if task.status in {"finished", "failed", "manual_review_required"}:
            logger.info("skip terminal task task_no=%s status=%s", task.task_no, task.status)
            return HandlerResult()

        input_ref = self._input_ref(message.body)
        self._repository.mark_running(message.task_no, STAGE_DOCUMENT_NORMALIZE, progress=10)
        self._repository.start_stage(
            task_no=message.task_no,
            stage=STAGE_DOCUMENT_NORMALIZE,
            attempt=message.attempt,
            max_attempts=self._max_attempts,
            input_message=message.body,
            input_ref=input_ref,
        )
        logger.info(
            "document normalize started task_no=%s input_ref=%s",
            message.task_no,
            input_ref,
        )

        try:
            normalize_result = self._normalizer.normalize(message.body)
        except Exception as exc:
            self._repository.fail_stage(
                message.task_no,
                STAGE_DOCUMENT_NORMALIZE,
                str(exc),
                mark_task_failed=message.attempt >= self._max_attempts,
            )
            raise

        output_ref = normalize_result["manifestRef"]
        next_message = self._build_next_message(message, output_ref)
        self._repository.finish_document_normalize(
            task_no=message.task_no,
            stage=STAGE_DOCUMENT_NORMALIZE,
            next_stage=STAGE_OCR_RECOGNIZE,
            page_count=int(normalize_result["pageCount"]),
            output_ref=output_ref,
            output_message=next_message,
            metrics=normalize_result["metrics"],
        )
        logger.info(
            "document normalize finished task_no=%s page_count=%s output_ref=%s",
            message.task_no,
            normalize_result["pageCount"],
            output_ref,
        )
        return HandlerResult(
            outgoing_messages=[
                OutgoingMessage(
                    exchange=OCR_TOPIC_EXCHANGE,
                    routing_key=ROUTING_KEY_OCR_RECOGNIZE,
                    body=next_message,
                )
            ]
        )

    def _input_ref(self, payload: dict) -> dict:
        return {
            "storageType": payload.get("storageType", "minio"),
            "bucket": payload.get("bucket"),
            "objectKey": payload.get("objectKey"),
        }

    def _build_next_message(self, message: IncomingMessage, output_ref: dict) -> dict:
        return {
            "eventId": str(uuid.uuid4()),
            "taskNo": message.task_no,
            "stage": STAGE_OCR_RECOGNIZE,
            "attempt": 1,
            "inputRef": output_ref,
            "outputPrefix": {
                "storageType": output_ref.get("storageType", "minio"),
                "bucket": output_ref.get("bucket"),
                "objectKey": f"{message.task_no}/ocr/raw/",
            },
            "createdAt": datetime.now().isoformat(timespec="seconds"),
        }
