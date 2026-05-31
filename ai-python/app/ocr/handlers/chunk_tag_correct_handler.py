from datetime import datetime
import logging
import uuid

from app.messaging.base_handler import MessageHandler
from app.messaging.errors import PermanentMessageError, RetryableMessageError
from app.messaging.models import HandlerResult, IncomingMessage, OutgoingMessage
from app.ocr.constants import (
    OCR_TOPIC_EXCHANGE,
    ROUTING_KEY_EMBEDDING_INDEX,
    STAGE_CHUNK_TAG_CORRECT,
    STAGE_EMBEDDING_INDEX,
)
from app.ocr.repository import OcrTaskRepository
from app.ocr.services.chunk_tag_aggregation import InMemoryChunkTagAggregator
from app.ocr.services.chunk_tag_corrector import ChunkTagCorrector
from app.ocr.storage import OcrArtifactStorage

logger = logging.getLogger(__name__)


class ChunkTagCorrectHandler(MessageHandler):
    def __init__(
        self,
        repository: OcrTaskRepository,
        corrector: ChunkTagCorrector,
        aggregator: InMemoryChunkTagAggregator,
        storage: OcrArtifactStorage,
        default_bucket: str,
        max_attempts: int,
    ) -> None:
        self._repository = repository
        self._corrector = corrector
        self._aggregator = aggregator
        self._storage = storage
        self._default_bucket = default_bucket
        self._max_attempts = max_attempts

    def handle(self, message: IncomingMessage) -> HandlerResult:
        if not message.task_no:
            raise PermanentMessageError("taskNo is required")
        if message.stage != STAGE_CHUNK_TAG_CORRECT:
            raise PermanentMessageError(f"unexpected stage: {message.stage}")

        task = self._repository.find_by_task_no(message.task_no)
        if task is None:
            raise RetryableMessageError(f"ocr_task not found: {message.task_no}")
        if task.status in {"finished", "failed"}:
            logger.info("skip terminal task task_no=%s status=%s", task.task_no, task.status)
            return HandlerResult()

        self._repository.mark_running(message.task_no, STAGE_CHUNK_TAG_CORRECT, progress=85)
        self._repository.start_stage(
            task_no=message.task_no,
            stage=STAGE_CHUNK_TAG_CORRECT,
            attempt=message.attempt,
            max_attempts=self._max_attempts,
            input_message=message.body,
            input_ref={},
        )

        try:
            chunk_result = self._corrector.correct(message.body)
            total_chunk_count = self._total_chunk_count(message.body)
            aggregation = self._aggregator.add(message.task_no, total_chunk_count, chunk_result)
        except Exception as exc:
            self._repository.fail_stage(
                message.task_no,
                STAGE_CHUNK_TAG_CORRECT,
                str(exc),
                mark_task_failed=message.attempt >= self._max_attempts,
            )
            raise

        chunk_id = chunk_result.get("chunkId") or ""
        if not aggregation.ready:
            logger.info("chunk tag correct pending task_no=%s chunk_id=%s", message.task_no, chunk_id)
            return HandlerResult()

        try:
            output_ref = self._write_tagged_reviewed(message.body, aggregation.chunks)
            outgoing_message = self._build_embedding_message(message.body, output_ref)
            self._repository.finish_stage(
                task_no=message.task_no,
                stage=STAGE_CHUNK_TAG_CORRECT,
                next_stage=STAGE_EMBEDDING_INDEX,
                progress=88,
                page_count=self._page_count(aggregation.chunks),
                segment_count=len(aggregation.chunks),
                output_ref=output_ref,
                output_message=outgoing_message.body,
                metrics={
                    "chunkCount": len(aggregation.chunks),
                    "tagVersion": self._tag_version(message.body),
                },
            )
        except Exception as exc:
            self._repository.fail_stage(
                message.task_no,
                STAGE_CHUNK_TAG_CORRECT,
                str(exc),
                mark_task_failed=message.attempt >= self._max_attempts,
            )
            raise RetryableMessageError(str(exc)) from exc
        self._aggregator.complete(message.task_no)
        logger.info(
            "chunk tag correct finished task_no=%s chunk_count=%s output_ref=%s",
            message.task_no,
            len(aggregation.chunks),
            output_ref,
        )
        return HandlerResult(outgoing_messages=[outgoing_message])

    def _write_tagged_reviewed(self, message_body: dict, chunks: list[dict]) -> dict:
        output_prefix = self._output_prefix(message_body)
        bucket = output_prefix["bucket"]
        output_key = output_prefix["objectKey"]
        result_key = f"{output_key.rstrip('/')}/tagged_reviewed.json"
        payload = {
            "taskNo": message_body.get("taskNo") or "",
            "stage": STAGE_CHUNK_TAG_CORRECT,
            "taggedAt": datetime.now().isoformat(timespec="seconds"),
            "content": {
                "paragraphCount": len(chunks),
                "paragraphs": [self._to_paragraph(chunk) for chunk in chunks],
            },
        }
        self._storage.put_json(bucket, result_key, payload)
        return {"storageType": "minio", "bucket": bucket, "objectKey": result_key}

    def _build_embedding_message(self, message_body: dict, input_ref: dict) -> OutgoingMessage:
        output_prefix = self._output_prefix(message_body)
        output_key = output_prefix["objectKey"]
        embedding_output_prefix = {
            "storageType": "minio",
            "bucket": input_ref["bucket"],
            "objectKey": f"{output_key.rstrip('/')}/embedding",
        }
        return OutgoingMessage(
            exchange=OCR_TOPIC_EXCHANGE,
            routing_key=ROUTING_KEY_EMBEDDING_INDEX,
            body={
                "eventId": str(uuid.uuid4()),
                "taskNo": message_body.get("taskNo") or "",
                "stage": STAGE_EMBEDDING_INDEX,
                "attempt": 1,
                "inputRef": input_ref,
                "outputPrefix": embedding_output_prefix,
                "createdAt": datetime.now().isoformat(timespec="seconds"),
            },
        )

    @staticmethod
    def _to_paragraph(chunk: dict) -> dict:
        return {
            "paragraphNo": int(chunk.get("chunkIndex") or 0),
            "text": chunk.get("text") or "",
            "sourcePages": chunk.get("pageNos") or [],
            "avgConfidence": 1,
            "warnings": [],
            "metadata": chunk.get("metadata") or {},
        }

    @staticmethod
    def _total_chunk_count(message_body: dict) -> int:
        summary = message_body.get("taskChunkSummary") or {}
        total = int(summary.get("totalChunkCount") or 0)
        if total <= 0:
            raise PermanentMessageError("taskChunkSummary.totalChunkCount is required")
        return total

    @staticmethod
    def _page_count(chunks: list[dict]) -> int:
        pages: set[int] = set()
        for chunk in chunks:
            for page_no in chunk.get("pageNos") or []:
                if isinstance(page_no, int):
                    pages.add(page_no)
        return len(pages)

    @staticmethod
    def _tag_version(message_body: dict) -> str:
        rule_tagging = message_body.get("ruleTagging") or {}
        return str(rule_tagging.get("tagVersion") or "")

    def _output_prefix(self, message_body: dict) -> dict:
        output_prefix = message_body.get("outputPrefix") or {}
        bucket = output_prefix.get("bucket") or self._default_bucket
        if not bucket:
            raise PermanentMessageError("outputPrefix.bucket is required")
        return {
            "storageType": "minio",
            "bucket": bucket,
            "objectKey": output_prefix.get("objectKey") or self._default_output_prefix(message_body),
        }

    @staticmethod
    def _default_output_prefix(message_body: dict) -> str:
        today = datetime.now()
        task_no = message_body.get("taskNo") or "unknown-task"
        return (
            f"stage-5-output/{today.year}/{today.month:02d}/{today.day:02d}/"
            f"{task_no}/chunk-tag/"
        )
