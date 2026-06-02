from app.core.config import Settings
from app.messaging.models import HandlerRoute
from app.messaging.registry import HandlerRegistry
from app.scene_analysis.constants import (
    QUEUE_CURRENT_SCENE_GENERATE,
    ROUTING_KEY_CURRENT_SCENE_GENERATE,
    SCENE_ANALYSIS_RETRY_EXCHANGE,
)
from app.scene_analysis.handlers.current_scene_handler import CurrentSceneHandler


def register_scene_analysis_handlers(settings: Settings, registry: HandlerRegistry) -> list[HandlerRoute]:
    registry.register(
        ROUTING_KEY_CURRENT_SCENE_GENERATE,
        CurrentSceneHandler(settings.rabbitmq.max_attempts),
    )
    return [
        HandlerRoute(
            queue=QUEUE_CURRENT_SCENE_GENERATE,
            routing_key=ROUTING_KEY_CURRENT_SCENE_GENERATE,
            handler_key=ROUTING_KEY_CURRENT_SCENE_GENERATE,
            retry_exchange=SCENE_ANALYSIS_RETRY_EXCHANGE,
        )
    ]
