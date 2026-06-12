import logging
from typing import Any

from app.core.config import settings
from app.messaging.base_handler import MessageHandler
from app.messaging.errors import PermanentMessageError
from app.messaging.models import HandlerResult, IncomingMessage
from app.ocr.engines.embedding_engine import BaseEmbeddingEngine
from app.scene_analysis.services.callback_client import SceneAnalysisCallbackClient

logger = logging.getLogger(__name__)


class RetrievalEmbeddingHandler(MessageHandler):
    def __init__(
        self,
        embedding_engine: BaseEmbeddingEngine,
        callback_client: SceneAnalysisCallbackClient | None = None,
    ) -> None:
        self._embedding_engine = embedding_engine
        self._callback_client = callback_client or SceneAnalysisCallbackClient(settings.finance_api)

    def handle(self, message: IncomingMessage) -> HandlerResult:
        task_no = message.task_no
        if not task_no:
            raise PermanentMessageError("scene retrieval embedding message taskNo is required")
        retrieval_tasks = message.body.get("retrievalTasks")
        if not isinstance(retrieval_tasks, list) or not retrieval_tasks:
            raise PermanentMessageError(f"scene retrievalTasks is required task_no={task_no}")
        callback_token = self._callback_token(message.body, task_no)
        query_texts = [self._query_text(task, task_no) for task in retrieval_tasks]
        embeddings = self._embedding_engine.embed(query_texts)
        if len(embeddings) != len(retrieval_tasks):
            raise PermanentMessageError(
                f"scene retrieval embedding count mismatch task_no={task_no} "
                f"task_count={len(retrieval_tasks)} embedding_count={len(embeddings)}"
            )
        retrieval_embeddings = [
            self._embedding_payload(task, embeddings[index], task_no)
            for index, task in enumerate(retrieval_tasks)
        ]
        self._callback_client.submit_retrieval_embeddings(task_no, callback_token, retrieval_embeddings)
        logger.info(
            "scene retrieval embeddings callback success task_no=%s retrieval_task_count=%s",
            task_no,
            len(retrieval_embeddings),
        )
        return HandlerResult()

    def _callback_token(self, payload: dict, task_no: str) -> str:
        callback_token = payload.get("callbackToken")
        if not isinstance(callback_token, str) or not callback_token.strip():
            raise PermanentMessageError(f"scene retrieval callbackToken is required task_no={task_no}")
        return callback_token.strip()

    def _query_text(self, task: Any, task_no: str) -> str:
        if not isinstance(task, dict):
            raise PermanentMessageError(f"scene retrievalTask must be object task_no={task_no}")
        query_text = task.get("queryText")
        if not isinstance(query_text, str) or not query_text.strip():
            raise PermanentMessageError(f"scene retrievalTask.queryText is required task_no={task_no}")
        return query_text.strip()

    def _embedding_payload(self, task: dict, embedding: list[float], task_no: str) -> dict:
        scene = task.get("scene")
        chunk_count = task.get("chunkCount")
        current_tags = task.get("currentTags") or {}
        if not isinstance(scene, str) or not scene.strip():
            raise PermanentMessageError(f"scene retrievalTask.scene is required task_no={task_no}")
        if not isinstance(chunk_count, int) or isinstance(chunk_count, bool) or chunk_count <= 0:
            raise PermanentMessageError(f"scene retrievalTask.chunkCount is invalid task_no={task_no}")
        if not isinstance(current_tags, dict):
            raise PermanentMessageError(f"scene retrievalTask.currentTags must be object task_no={task_no}")
        return {
            "scene": scene.strip(),
            "chunkCount": chunk_count,
            "currentTags": current_tags,
            "queryText": self._query_text(task, task_no),
            "queryEmbedding": embedding,
        }
