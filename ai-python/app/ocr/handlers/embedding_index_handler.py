from datetime import datetime
import logging
import uuid

from app.messaging.base_handler import MessageHandler
from app.messaging.errors import PermanentMessageError, RetryableMessageError
from app.messaging.models import HandlerResult, IncomingMessage
from app.ocr.constants import STAGE_EMBEDDING_INDEX
from app.ocr.repository import OcrTaskRepository
from app.ocr.services.embedding_service import EmbeddingService
from app.ocr.services.vector_store import VectorStore
from app.ocr.storage import OcrArtifactStorage

logger = logging.getLogger(__name__)


class EmbeddingIndexHandler(MessageHandler):
    def __init__(
        self,
        repository: OcrTaskRepository,
        embedding_service: EmbeddingService,
        vector_store: VectorStore,
        storage: OcrArtifactStorage,
        max_attempts: int,
    ) -> None:
        self._repository = repository
        self._embedding_service = embedding_service
        self._vector_store = vector_store
        self._storage = storage
        self._max_attempts = max_attempts

    def handle(self, message: IncomingMessage) -> HandlerResult:
        if not message.task_no:
            raise PermanentMessageError("taskNo is required")
        if message.stage != STAGE_EMBEDDING_INDEX:
            raise PermanentMessageError(f"unexpected stage: {message.stage}")

        task = self._repository.find_by_task_no(message.task_no)
        if task is None:
            raise RetryableMessageError(f"ocr_task not found: {message.task_no}")
        if task.status in {"finished", "failed"}:
            logger.info("skip terminal task task_no=%s status=%s", task.task_no, task.status)
            return HandlerResult()

        input_ref = message.body.get("inputRef") or {}
        self._repository.mark_running(message.task_no, STAGE_EMBEDDING_INDEX, progress=80)
        self._repository.start_stage(
            task_no=message.task_no,
            stage=STAGE_EMBEDDING_INDEX,
            attempt=message.attempt,
            max_attempts=self._max_attempts,
            input_message=message.body,
            input_ref=input_ref,
        )
        logger.info(
            "embedding index started task_no=%s input_ref=%s",
            message.task_no,
            input_ref,
        )

        bucket = input_ref.get("bucket") or ""
        object_key = input_ref.get("objectKey") or ""
        try:
            reviewed_json = self._storage.get_json(bucket, object_key)
        except Exception as exc:
            self._repository.fail_stage(
                message.task_no,
                STAGE_EMBEDDING_INDEX,
                str(exc),
                mark_task_failed=message.attempt >= self._max_attempts,
            )
            raise

        try:
            chunks = self._embedding_service.embed(reviewed_json)
            if not chunks:
                raise PermanentMessageError("no paragraphs found in reviewed content")
            written = self._vector_store.rebuild_task_vectors(message.task_no, chunks)
        except Exception as exc:
            self._repository.fail_stage(
                message.task_no,
                STAGE_EMBEDDING_INDEX,
                str(exc),
                mark_task_failed=message.attempt >= self._max_attempts,
            )
            raise

        output_prefix = message.body.get("outputPrefix") or {}
        output_bucket = output_prefix.get("bucket") or bucket
        output_key = output_prefix.get("objectKey") or ""
        result_key = f"{output_key.rstrip('/')}/embedding_result.json"
        output_result = {
            "taskNo": message.task_no,
            "stage": STAGE_EMBEDDING_INDEX,
            "chunkCount": written,
            "embeddingModel": self._embedding_service.model_name,
            "finishedAt": datetime.now().isoformat(timespec="seconds"),
        }
        output_ref = {"storageType": "minio", "bucket": output_bucket, "objectKey": result_key}
        self._storage.put_json(output_bucket, result_key, output_result)

        output_message = {
            "eventId": str(uuid.uuid4()),
            "taskNo": message.task_no,
            "stage": STAGE_EMBEDDING_INDEX,
            "chunkCount": written,
            "finishedAt": output_result["finishedAt"],
        }
        content = reviewed_json.get("content") or {}
        page_count = self._compute_page_count(content)
        self._repository.finish_task(
            task_no=message.task_no,
            stage=STAGE_EMBEDDING_INDEX,
            progress=100,
            page_count=page_count,
            segment_count=content.get("paragraphCount") or written,
            output_ref=output_ref,
            output_message=output_message,
            metrics={
                "chunkCount": written,
                "embeddingModel": self._embedding_service.model_name,
            },
        )
        logger.info(
            "embedding index finished task_no=%s chunk_count=%s",
            message.task_no,
            written,
        )
        return HandlerResult()

    @staticmethod
    def _compute_page_count(content: dict) -> int:
        paragraphs = content.get("paragraphs") or []
        pages: set[int] = set()
        for paragraph in paragraphs:
            for page in paragraph.get("sourcePages") or []:
                if isinstance(page, int):
                    pages.add(page)
        return len(pages) or 0
