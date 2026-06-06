import json
import logging
import uuid
from datetime import datetime
from typing import Any

from app.core.config import DeepSeekSettings
from app.messaging.base_handler import MessageHandler
from app.messaging.errors import PermanentMessageError, RetryableMessageError
from app.messaging.models import HandlerResult, IncomingMessage, OutgoingMessage
from app.ocr.constants import (
    OCR_TOPIC_EXCHANGE,
    ROUTING_KEY_CHUNK_TAG_CORRECT,
    STAGE_CHUNK_TAG_CORRECT,
    STAGE_CHUNK_TAG_LLM,
)
from app.ocr.engines.deepseek_chat_engine import DeepSeekChatEngine
from app.ocr.repository import OcrTaskRepository
from app.ocr.services.chunk_tag_schema import LLM_TAG_SYSTEM_PROMPT, SCENE_CATEGORIES, VALID_TAGS

logger = logging.getLogger(__name__)


class ChunkLlmTagHandler(MessageHandler):
    def __init__(
        self,
        repository: OcrTaskRepository,
        deepseek_engine: DeepSeekChatEngine,
        deepseek_settings: DeepSeekSettings,
        max_attempts: int,
    ) -> None:
        self._repository = repository
        self._engine = deepseek_engine
        self._settings = deepseek_settings
        self._max_attempts = max_attempts

    def handle(self, message: IncomingMessage) -> HandlerResult:
        if not message.task_no:
            raise PermanentMessageError("taskNo is required")
        if message.stage != STAGE_CHUNK_TAG_LLM:
            raise PermanentMessageError(f"unexpected stage: {message.stage}")

        task = self._repository.find_by_task_no(message.task_no)
        if task is None:
            raise RetryableMessageError(f"ocr_task not found: {message.task_no}")
        if task.status in {"finished", "failed"}:
            logger.info("skip terminal task task_no=%s status=%s", task.task_no, task.status)
            return HandlerResult()

        self._repository.mark_running(message.task_no, STAGE_CHUNK_TAG_LLM, progress=80)
        self._repository.start_stage(
            task_no=message.task_no,
            stage=STAGE_CHUNK_TAG_LLM,
            attempt=message.attempt,
            max_attempts=self._max_attempts,
            input_message=message.body,
            input_ref={},
        )

        chunk = message.body.get("chunk") or {}
        chunk_id = chunk.get("chunkId", "")
        chunk_index = int(chunk.get("chunkIndex") or 0)
        rule_tagging = message.body.get("ruleTagging") or {}
        self._repository.start_chunk_stage(
            task_no=message.task_no,
            stage=STAGE_CHUNK_TAG_LLM,
            chunk_id=chunk_id,
            chunk_index=chunk_index,
            attempt=message.attempt,
            max_attempts=self._max_attempts,
            input_message=message.body,
            input_ref={},
        )
        logger.info(
            "chunk llm tag started task_no=%s chunk_id=%s",
            message.task_no,
            chunk_id,
        )

        try:
            user_message = self._build_user_message(chunk, rule_tagging)
            result = self._engine.chat(LLM_TAG_SYSTEM_PROMPT, user_message)
            llm_scenes = self._parse_and_validate(result["content"])
        except Exception as exc:
            self._repository.fail_stage(
                message.task_no,
                STAGE_CHUNK_TAG_LLM,
                str(exc),
                mark_task_failed=message.attempt >= self._max_attempts,
            )
            self._repository.fail_chunk_stage(
                message.task_no,
                STAGE_CHUNK_TAG_LLM,
                chunk_id,
                str(exc),
            )
            raise

        outgoing_message = OutgoingMessage(
            exchange=OCR_TOPIC_EXCHANGE,
            routing_key=ROUTING_KEY_CHUNK_TAG_CORRECT,
            body=self._build_output_body(message.body, llm_scenes, result["usage"]),
        )
        output_message = {
            "eventId": str(uuid.uuid4()),
            "taskNo": message.task_no,
            "stage": STAGE_CHUNK_TAG_LLM,
            "chunkId": chunk_id,
            "createdAt": datetime.now().isoformat(timespec="seconds"),
        }
        self._repository.finish_stage(
            task_no=message.task_no,
            stage=STAGE_CHUNK_TAG_LLM,
            next_stage=STAGE_CHUNK_TAG_CORRECT,
            progress=82,
            page_count=int(message.body.get("pageCount") or 0),
            segment_count=0,
            output_ref={},
            output_message=output_message,
            metrics={
                "chunkId": chunk_id,
                "llmModel": self._settings.model,
                "tokenUsage": result["usage"],
            },
        )
        self._repository.finish_chunk_stage(
            task_no=message.task_no,
            stage=STAGE_CHUNK_TAG_LLM,
            chunk_id=chunk_id,
            output_ref={},
            output_message=outgoing_message.body,
            metrics={
                "llmModel": self._settings.model,
                "tokenUsage": result["usage"],
            },
        )
        logger.info(
            "chunk llm tag finished task_no=%s chunk_id=%s",
            message.task_no,
            chunk_id,
        )
        return HandlerResult(outgoing_messages=[outgoing_message])

    def _build_user_message(self, chunk: dict, rule_tagging: dict) -> str:
        text = chunk.get("text", "")
        rule_scenes = rule_tagging.get("ruleScenes") or {}
        quality_gate = rule_tagging.get("qualityGate") or {}
        parts = [
            f"## Chunk 文本\n\n{text}",
            f"## 规则标签结果（仅供参考）\n\n{json.dumps(rule_scenes, ensure_ascii=False, indent=2)}",
        ]
        if quality_gate:
            parts.append(
                f"## 规则标签质量门\n\n{json.dumps(quality_gate, ensure_ascii=False, indent=2)}"
            )
        parts.append("请输出 JSON。")
        return "\n\n".join(parts)

    def _build_output_body(
        self, input_body: dict, llm_scenes: dict, usage: Any
    ) -> dict:
        return {
            "eventId": str(uuid.uuid4()),
            "taskNo": input_body["taskNo"],
            "stage": STAGE_CHUNK_TAG_CORRECT,
            "attempt": 1,
            "tagPipeline": {
                "version": "v1.0",
                "sourceStage": STAGE_CHUNK_TAG_LLM,
                "targetStage": STAGE_CHUNK_TAG_CORRECT,
            },
            "taskChunkSummary": input_body.get("taskChunkSummary") or {},
            "outputPrefix": input_body.get("outputPrefix") or {},
            "chunk": input_body.get("chunk") or {},
            "ruleTagging": input_body.get("ruleTagging") or {},
            "llmTagging": {
                "llmScenes": llm_scenes,
                "model": self._settings.model,
                "usage": usage,
            },
            "createdAt": datetime.now().isoformat(timespec="seconds"),
        }

    def _parse_and_validate(self, content: str) -> dict[str, list[str]]:
        parsed = self._extract_json(content)
        scenes = parsed.get("scenes")
        if not isinstance(scenes, dict):
            raise PermanentMessageError("LLM response missing 'scenes' object")
        result: dict[str, list[str]] = {}
        for category in SCENE_CATEGORIES:
            tags = scenes.get(category)
            if tags is None:
                result[category] = []
                continue
            if not isinstance(tags, list):
                result[category] = []
                continue
            valid_tags = VALID_TAGS.get(category, frozenset())
            result[category] = [t for t in tags if isinstance(t, str) and t in valid_tags]
        return result

    def _extract_json(self, content: str) -> dict[str, Any]:
        stripped = content.strip()
        if stripped.startswith("```"):
            lines = stripped.splitlines()
            if lines and lines[0].startswith("```"):
                lines = lines[1:]
            if lines and lines[-1].strip() == "```":
                lines = lines[:-1]
            stripped = "\n".join(lines).strip()
        try:
            payload = json.loads(stripped)
        except json.JSONDecodeError:
            raise PermanentMessageError(f"LLM response is not valid JSON: {content[:500]}")
        if not isinstance(payload, dict):
            raise PermanentMessageError("LLM response is not a JSON object")
        return payload
