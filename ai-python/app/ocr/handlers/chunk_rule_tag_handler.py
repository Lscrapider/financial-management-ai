from datetime import datetime
import logging
import uuid

from app.messaging.base_handler import MessageHandler
from app.messaging.errors import PermanentMessageError, RetryableMessageError
from app.messaging.models import HandlerResult, IncomingMessage, OutgoingMessage
from app.ocr.constants import (
    OCR_TOPIC_EXCHANGE,
    ROUTING_KEY_CHUNK_TAG_CORRECT,
    ROUTING_KEY_CHUNK_TAG_LLM,
    STAGE_CHUNK_TAG_CORRECT,
    STAGE_CHUNK_TAG_LLM,
    STAGE_CHUNK_TAG_RULE,
)
from app.ocr.repository import OcrTaskRepository
from app.ocr.services.chunk_rule_tagger import ChunkRuleTagger
from app.ocr.storage import OcrArtifactStorage

logger = logging.getLogger(__name__)


class ChunkRuleTagHandler(MessageHandler):
    def __init__(
        self,
        repository: OcrTaskRepository,
        tagger: ChunkRuleTagger,
        storage: OcrArtifactStorage,
        max_attempts: int,
    ) -> None:
        self._repository = repository
        self._tagger = tagger
        self._storage = storage
        self._max_attempts = max_attempts

    def handle(self, message: IncomingMessage) -> HandlerResult:
        if not message.task_no:
            raise PermanentMessageError("taskNo is required")
        if message.stage != STAGE_CHUNK_TAG_RULE:
            raise PermanentMessageError(f"unexpected stage: {message.stage}")

        task = self._repository.find_by_task_no(message.task_no)
        if task is None:
            raise RetryableMessageError(f"ocr_task not found: {message.task_no}")
        if task.status in {"finished", "failed"}:
            logger.info("skip terminal task task_no=%s status=%s", task.task_no, task.status)
            return HandlerResult()

        input_ref = message.body.get("inputRef") or {}
        self._repository.mark_running(message.task_no, STAGE_CHUNK_TAG_RULE, progress=75)
        self._repository.start_stage(
            task_no=message.task_no,
            stage=STAGE_CHUNK_TAG_RULE,
            attempt=message.attempt,
            max_attempts=self._max_attempts,
            input_message=message.body,
            input_ref=input_ref,
        )
        logger.info("chunk rule tag started task_no=%s input_ref=%s", message.task_no, input_ref)

        bucket = input_ref.get("bucket") or ""
        object_key = input_ref.get("objectKey") or ""
        try:
            reviewed_json = self._storage.get_json(bucket, object_key)
            rule_result = self._tagger.tag(reviewed_json)
            if not rule_result["chunks"]:
                raise PermanentMessageError("no paragraphs found in reviewed content")
        except Exception as exc:
            self._repository.fail_stage(
                message.task_no,
                STAGE_CHUNK_TAG_RULE,
                str(exc),
                mark_task_failed=message.attempt >= self._max_attempts,
            )
            raise

        output_ref = self._write_result(message, bucket, rule_result)
        next_stage = STAGE_CHUNK_TAG_LLM if rule_result["needLlm"] else STAGE_CHUNK_TAG_CORRECT
        next_routing_key = ROUTING_KEY_CHUNK_TAG_LLM if rule_result["needLlm"] else ROUTING_KEY_CHUNK_TAG_CORRECT
        next_message = self._build_next_message(message, output_ref, next_stage)
        self._repository.finish_stage(
            task_no=message.task_no,
            stage=STAGE_CHUNK_TAG_RULE,
            next_stage=next_stage,
            progress=78,
            page_count=int(message.body.get("pageCount") or 0),
            segment_count=int(rule_result["chunkCount"]),
            output_ref=output_ref,
            output_message=next_message,
            metrics={
                "chunkCount": rule_result["chunkCount"],
                "needLlm": rule_result["needLlm"],
                "tagVersion": rule_result["tagVersion"],
            },
        )
        logger.info(
            "chunk rule tag finished task_no=%s chunk_count=%s need_llm=%s output_ref=%s",
            message.task_no,
            rule_result["chunkCount"],
            rule_result["needLlm"],
            output_ref,
        )
        return HandlerResult(
            outgoing_messages=[
                OutgoingMessage(
                    exchange=OCR_TOPIC_EXCHANGE,
                    routing_key=next_routing_key,
                    body=next_message,
                )
            ]
        )

    def _write_result(self, message: IncomingMessage, fallback_bucket: str, rule_result: dict) -> dict:
        output_prefix = message.body.get("outputPrefix") or {}
        output_bucket = output_prefix.get("bucket") or fallback_bucket
        output_key = output_prefix.get("objectKey") or ""
        result_key = f"{output_key.rstrip('/')}/rule_tag_result.json" if output_key else "rule_tag_result.json"
        payload = {
            "taskNo": message.task_no,
            "stage": STAGE_CHUNK_TAG_RULE,
            "inputRef": message.body.get("inputRef") or {},
            "finishedAt": datetime.now().isoformat(timespec="seconds"),
            "result": rule_result,
        }
        self._storage.put_json(output_bucket, result_key, payload)
        return {"storageType": "minio", "bucket": output_bucket, "objectKey": result_key}

    def _build_next_message(self, message: IncomingMessage, output_ref: dict, next_stage: str) -> dict:
        return {
            "eventId": str(uuid.uuid4()),
            "taskNo": message.task_no,
            "stage": next_stage,
            "attempt": 1,
            "inputRef": output_ref,
            "outputPrefix": message.body.get("outputPrefix") or {},
            "createdAt": datetime.now().isoformat(timespec="seconds"),
        }
