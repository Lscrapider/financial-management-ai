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
        output_prefix = self._message_output_prefix(message, output_ref)
        outgoing_messages = self._build_chunk_messages(rule_result, output_prefix)
        output_message = {
            "eventId": str(uuid.uuid4()),
            "taskNo": message.task_no,
            "stage": STAGE_CHUNK_TAG_RULE,
            "inputRef": output_ref,
            "totalChunkCount": rule_result["totalChunkCount"],
            "llmChunkCount": rule_result["llmChunkCount"],
            "ruleOnlyChunkCount": rule_result["ruleOnlyChunkCount"],
            "publishedMessageCount": len(outgoing_messages),
            "createdAt": datetime.now().isoformat(timespec="seconds"),
        }
        self._repository.finish_stage(
            task_no=message.task_no,
            stage=STAGE_CHUNK_TAG_RULE,
            next_stage=STAGE_CHUNK_TAG_LLM if rule_result["needLlm"] else STAGE_CHUNK_TAG_CORRECT,
            progress=78,
            page_count=int(message.body.get("pageCount") or 0),
            segment_count=int(rule_result["chunkCount"]),
            output_ref=output_ref,
            output_message=output_message,
            metrics={
                "chunkCount": rule_result["chunkCount"],
                "totalChunkCount": rule_result["totalChunkCount"],
                "needLlm": rule_result["needLlm"],
                "llmChunkCount": rule_result["llmChunkCount"],
                "ruleOnlyChunkCount": rule_result["ruleOnlyChunkCount"],
                "tagVersion": rule_result["tagVersion"],
            },
        )
        logger.info(
            "chunk rule tag finished task_no=%s chunk_count=%s llm_chunk_count=%s rule_only_chunk_count=%s output_ref=%s",
            message.task_no,
            rule_result["chunkCount"],
            rule_result["llmChunkCount"],
            rule_result["ruleOnlyChunkCount"],
            output_ref,
        )
        return HandlerResult(
            outgoing_messages=outgoing_messages
        )

    def _write_result(self, message: IncomingMessage, fallback_bucket: str, rule_result: dict) -> dict:
        output_prefix = message.body.get("outputPrefix") or {}
        output_bucket = output_prefix.get("bucket") or fallback_bucket
        output_key = output_prefix.get("objectKey") or self._default_output_prefix(message.task_no)
        result_key = f"{output_key.rstrip('/')}/rule_tag_result.json"
        payload = {
            "taskNo": message.task_no,
            "stage": STAGE_CHUNK_TAG_RULE,
            "inputRef": message.body.get("inputRef") or {},
            "finishedAt": datetime.now().isoformat(timespec="seconds"),
            "result": rule_result,
        }
        self._storage.put_json(output_bucket, result_key, payload)
        return {"storageType": "minio", "bucket": output_bucket, "objectKey": result_key}

    def _build_chunk_messages(self, rule_result: dict, output_prefix: dict) -> list[OutgoingMessage]:
        messages = []
        for chunk in rule_result["chunks"]:
            need_llm = chunk["qualityGate"]["needLlm"]
            stage = STAGE_CHUNK_TAG_LLM if need_llm else STAGE_CHUNK_TAG_CORRECT
            routing_key = ROUTING_KEY_CHUNK_TAG_LLM if need_llm else ROUTING_KEY_CHUNK_TAG_CORRECT
            messages.append(
                OutgoingMessage(
                    exchange=OCR_TOPIC_EXCHANGE,
                    routing_key=routing_key,
                    body=self._build_chunk_message_body(rule_result, chunk, stage, output_prefix),
                )
            )
        return messages

    def _build_chunk_message_body(self, rule_result: dict, chunk: dict, stage: str, output_prefix: dict) -> dict:
        return {
            "eventId": str(uuid.uuid4()),
            "taskNo": rule_result["taskNo"],
            "stage": stage,
            "attempt": 1,
            "tagPipeline": {
                "version": "v1.0",
                "sourceStage": STAGE_CHUNK_TAG_RULE,
                "targetStage": stage,
            },
            "taskChunkSummary": {
                "totalChunkCount": rule_result["totalChunkCount"],
                "llmChunkCount": rule_result["llmChunkCount"],
                "ruleOnlyChunkCount": rule_result["ruleOnlyChunkCount"],
            },
            "outputPrefix": output_prefix,
            "chunk": {
                "chunkId": chunk["chunkId"],
                "chunkIndex": chunk["chunkIndex"],
                "text": chunk["text"],
                "pageNos": chunk["pageNos"],
                "paragraphNos": chunk["paragraphNos"],
            },
            "ruleTagging": {
                "ruleScenes": chunk["ruleScenes"],
                "ruleScenesWithConfidence": chunk["ruleScenesWithConfidence"],
                "qualityGate": chunk["qualityGate"],
                "tagVersion": rule_result["tagVersion"],
            },
            "createdAt": datetime.now().isoformat(timespec="seconds"),
        }

    def _message_output_prefix(self, message: IncomingMessage, output_ref: dict) -> dict:
        output_prefix = message.body.get("outputPrefix") or {}
        return {
            "storageType": "minio",
            "bucket": output_prefix.get("bucket") or output_ref.get("bucket") or "",
            "objectKey": output_prefix.get("objectKey") or self._default_output_prefix(message.task_no),
        }

    @staticmethod
    def _default_output_prefix(task_no: str) -> str:
        today = datetime.now()
        return (
            f"stage-5-output/{today.year}/{today.month:02d}/{today.day:02d}/"
            f"{task_no}/chunk-tag/"
        )
