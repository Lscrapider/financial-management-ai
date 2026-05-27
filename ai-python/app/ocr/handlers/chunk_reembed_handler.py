import logging

from app.messaging.base_handler import MessageHandler
from app.messaging.errors import PermanentMessageError
from app.messaging.models import HandlerResult, IncomingMessage
from app.ocr.engines.embedding_engine import BaseEmbeddingEngine
from app.ocr.services.vector_store import VectorStore

logger = logging.getLogger(__name__)


class ChunkReembedHandler(MessageHandler):
    def __init__(self, engine: BaseEmbeddingEngine, vector_store: VectorStore) -> None:
        self._engine = engine
        self._vector_store = vector_store

    def handle(self, message: IncomingMessage) -> HandlerResult:
        chunk_id = message.body.get("chunkId")
        new_text = message.body.get("newText")
        if not chunk_id or not new_text:
            raise PermanentMessageError("chunkId and newText are required")

        embeddings = self._engine.embed([new_text])
        if not embeddings:
            raise PermanentMessageError("embedding engine returned empty result")

        updated = self._vector_store.update_chunk_embedding(
            chunk_id=chunk_id,
            new_text=new_text,
            embedding=embeddings[0],
        )
        if not updated:
            raise PermanentMessageError(f"chunk not found: {chunk_id}")

        logger.info("chunk re-embedded chunk_id=%s", chunk_id)
        return HandlerResult()
