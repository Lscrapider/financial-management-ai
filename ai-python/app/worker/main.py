from app.core.config import settings
from app.core.logging import configure_logging
from app.messaging.models import HandlerRoute
from app.messaging.rabbit_worker import RabbitMqWorker
from app.messaging.registry import HandlerRegistry
from app.ocr.constants import (
    QUEUE_CHUNK_REEMBED,
    QUEUE_CHUNK_TAG_CORRECT,
    QUEUE_CHUNK_TAG_LLM,
    QUEUE_CHUNK_TAG_RULE,
    QUEUE_DOCUMENT_NORMALIZE,
    QUEUE_EMBEDDING_INDEX,
    QUEUE_OCR_RECOGNIZE,
    QUEUE_TEXT_CLEAN,
    ROUTING_KEY_CHUNK_REEMBED,
    ROUTING_KEY_CHUNK_TAG_CORRECT,
    ROUTING_KEY_CHUNK_TAG_LLM,
    ROUTING_KEY_CHUNK_TAG_RULE,
    ROUTING_KEY_DOCUMENT_NORMALIZE,
    ROUTING_KEY_EMBEDDING_INDEX,
    ROUTING_KEY_OCR_RECOGNIZE,
    ROUTING_KEY_TEXT_CLEAN,
)
from app.ocr.engines.deepseek_chat_engine import DeepSeekChatEngine
from app.ocr.engines.embedding_engine import SentenceTransformersEngine
from app.ocr.engines.qwen_vl_ocr_engine import QwenVlOcrEngine
from app.ocr.handlers.chunk_llm_tag_handler import ChunkLlmTagHandler
from app.ocr.handlers.chunk_reembed_handler import ChunkReembedHandler
from app.ocr.handlers.chunk_rule_tag_handler import ChunkRuleTagHandler
from app.ocr.handlers.chunk_tag_correct_handler import ChunkTagCorrectHandler
from app.ocr.handlers.document_normalize_handler import DocumentNormalizeHandler
from app.ocr.handlers.embedding_index_handler import EmbeddingIndexHandler
from app.ocr.handlers.ocr_recognize_handler import OcrRecognizeHandler
from app.ocr.handlers.text_clean_handler import TextCleanHandler
from app.ocr.services.chunk_rule_tagger import ChunkRuleTagger
from app.ocr.services.chunk_tag_aggregation import InMemoryChunkTagAggregator
from app.ocr.services.chunk_tag_corrector import ChunkTagCorrector
from app.ocr.repository import OcrTaskRepository
from app.ocr.services.document_normalizer import DocumentNormalizer
from app.ocr.services.embedding_service import EmbeddingService
from app.ocr.services.ocr_recognizer import OcrRecognizer
from app.ocr.services.text_cleaner import TextCleaner
from app.ocr.services.vector_store import VectorStore
from app.ocr.storage import OcrArtifactStorage


def build_worker() -> RabbitMqWorker:
    registry = HandlerRegistry()
    repository = OcrTaskRepository(settings.postgres)
    storage = OcrArtifactStorage(settings.minio)
    normalizer = DocumentNormalizer(storage)
    qwen_engine = QwenVlOcrEngine(settings.qwen_ocr)
    recognizer = OcrRecognizer(storage, qwen_engine)
    cleaner = TextCleaner(storage)
    registry.register(
        ROUTING_KEY_DOCUMENT_NORMALIZE,
        DocumentNormalizeHandler(repository, normalizer, settings.rabbitmq.max_attempts),
    )
    registry.register(
        ROUTING_KEY_OCR_RECOGNIZE,
        OcrRecognizeHandler(repository, recognizer, settings.rabbitmq.max_attempts),
    )
    registry.register(
        ROUTING_KEY_TEXT_CLEAN,
        TextCleanHandler(repository, cleaner, settings.rabbitmq.max_attempts),
    )
    rule_tagger = ChunkRuleTagger()
    registry.register(
        ROUTING_KEY_CHUNK_TAG_RULE,
        ChunkRuleTagHandler(repository, rule_tagger, storage, settings.rabbitmq.max_attempts),
    )
    deepseek_engine = DeepSeekChatEngine(settings.deepseek)
    registry.register(
        ROUTING_KEY_CHUNK_TAG_LLM,
        ChunkLlmTagHandler(
            repository,
            deepseek_engine,
            settings.deepseek,
            settings.rabbitmq.max_attempts,
        ),
    )
    registry.register(
        ROUTING_KEY_CHUNK_TAG_CORRECT,
        ChunkTagCorrectHandler(
            repository,
            ChunkTagCorrector(),
            InMemoryChunkTagAggregator(),
            storage,
            settings.minio.artifact_bucket,
            settings.rabbitmq.max_attempts,
        ),
    )
    embedding_engine = SentenceTransformersEngine(
        settings.embedding.model_name,
        settings.embedding.device,
        settings.embedding.batch_size,
    )
    embedding_service = EmbeddingService(embedding_engine, settings.embedding.model_name)
    vector_store = VectorStore(settings.postgres)
    registry.register(
        ROUTING_KEY_EMBEDDING_INDEX,
        EmbeddingIndexHandler(
            repository,
            embedding_service,
            vector_store,
            storage,
            settings.rabbitmq.max_attempts,
        ),
    )
    registry.register(
        ROUTING_KEY_CHUNK_REEMBED,
        ChunkReembedHandler(embedding_engine, vector_store),
    )
    routes = [
        HandlerRoute(
            queue=QUEUE_DOCUMENT_NORMALIZE,
            routing_key=ROUTING_KEY_DOCUMENT_NORMALIZE,
            handler_key=ROUTING_KEY_DOCUMENT_NORMALIZE,
        ),
        HandlerRoute(
            queue=QUEUE_OCR_RECOGNIZE,
            routing_key=ROUTING_KEY_OCR_RECOGNIZE,
            handler_key=ROUTING_KEY_OCR_RECOGNIZE,
        ),
        HandlerRoute(
            queue=QUEUE_TEXT_CLEAN,
            routing_key=ROUTING_KEY_TEXT_CLEAN,
            handler_key=ROUTING_KEY_TEXT_CLEAN,
        ),
        HandlerRoute(
            queue=QUEUE_CHUNK_TAG_RULE,
            routing_key=ROUTING_KEY_CHUNK_TAG_RULE,
            handler_key=ROUTING_KEY_CHUNK_TAG_RULE,
        ),
        HandlerRoute(
            queue=QUEUE_CHUNK_TAG_LLM,
            routing_key=ROUTING_KEY_CHUNK_TAG_LLM,
            handler_key=ROUTING_KEY_CHUNK_TAG_LLM,
        ),
        HandlerRoute(
            queue=QUEUE_CHUNK_TAG_CORRECT,
            routing_key=ROUTING_KEY_CHUNK_TAG_CORRECT,
            handler_key=ROUTING_KEY_CHUNK_TAG_CORRECT,
        ),
        HandlerRoute(
            queue=QUEUE_EMBEDDING_INDEX,
            routing_key=ROUTING_KEY_EMBEDDING_INDEX,
            handler_key=ROUTING_KEY_EMBEDDING_INDEX,
        ),
        HandlerRoute(
            queue=QUEUE_CHUNK_REEMBED,
            routing_key=ROUTING_KEY_CHUNK_REEMBED,
            handler_key=ROUTING_KEY_CHUNK_REEMBED,
        ),
    ]
    return RabbitMqWorker(settings.rabbitmq, routes, registry, repository.is_deleted)


def main() -> None:
    configure_logging()
    build_worker().run()


if __name__ == "__main__":
    main()
