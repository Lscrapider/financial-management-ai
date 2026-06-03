from app.core.config import Settings
from app.messaging.models import HandlerRoute
from app.messaging.registry import HandlerRegistry
from app.scene_analysis.constants import (
    QUEUE_CURRENT_SCENE_GENERATE,
    QUEUE_RETRIEVAL_EMBEDDING,
    ROUTING_KEY_CURRENT_SCENE_GENERATE,
    ROUTING_KEY_RETRIEVAL_EMBEDDING,
    SCENE_ANALYSIS_RETRY_EXCHANGE,
)
from app.scene_analysis.handlers.current_scene_handler import CurrentSceneHandler
from app.scene_analysis.handlers.retrieval_embedding_handler import RetrievalEmbeddingHandler
from app.ocr.engines.embedding_engine import BaseEmbeddingEngine


def register_scene_analysis_handlers(
    settings: Settings,
    registry: HandlerRegistry,
    embedding_engine: BaseEmbeddingEngine,
) -> list[HandlerRoute]:
    registry.register(
        ROUTING_KEY_CURRENT_SCENE_GENERATE,
        CurrentSceneHandler(settings.rabbitmq.max_attempts),
    )
    registry.register(
        ROUTING_KEY_RETRIEVAL_EMBEDDING,
        RetrievalEmbeddingHandler(embedding_engine),
    )
    return [
        HandlerRoute(
            queue=QUEUE_CURRENT_SCENE_GENERATE,
            routing_key=ROUTING_KEY_CURRENT_SCENE_GENERATE,
            handler_key=ROUTING_KEY_CURRENT_SCENE_GENERATE,
            retry_exchange=SCENE_ANALYSIS_RETRY_EXCHANGE,
        ),
        HandlerRoute(
            queue=QUEUE_RETRIEVAL_EMBEDDING,
            routing_key=ROUTING_KEY_RETRIEVAL_EMBEDDING,
            handler_key=ROUTING_KEY_RETRIEVAL_EMBEDDING,
            retry_exchange=SCENE_ANALYSIS_RETRY_EXCHANGE,
        ),
    ]
