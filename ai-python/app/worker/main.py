from app.core.config import settings
from app.core.logging import configure_logging
from app.agent.bootstrap import register_agent_handlers
from app.messaging.rabbit_worker import RabbitMqWorker
from app.messaging.registry import HandlerRegistry
from app.ocr.bootstrap import register_ocr_handlers
from app.ocr.engines.embedding_engine import SentenceTransformersEngine
from app.scene_analysis.bootstrap import register_scene_analysis_handlers


def build_worker() -> RabbitMqWorker:
    registry = HandlerRegistry()
    embedding_engine = SentenceTransformersEngine(
        settings.embedding.model_name,
        settings.embedding.device,
        settings.embedding.batch_size,
    )
    ocr_routes, _ocr_repository = register_ocr_handlers(settings, registry, embedding_engine)
    scene_analysis_routes = register_scene_analysis_handlers(settings, registry, embedding_engine)
    agent_routes = register_agent_handlers(registry)
    routes = [*ocr_routes, *scene_analysis_routes, *agent_routes]
    return RabbitMqWorker(settings.rabbitmq, routes, registry)


def main() -> None:
    configure_logging()
    build_worker().run()


if __name__ == "__main__":
    main()
