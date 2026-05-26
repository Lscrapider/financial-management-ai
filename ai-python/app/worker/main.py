from app.core.config import settings
from app.core.logging import configure_logging
from app.messaging.models import HandlerRoute
from app.messaging.rabbit_worker import RabbitMqWorker
from app.messaging.registry import HandlerRegistry
from app.ocr.constants import QUEUE_DOCUMENT_NORMALIZE, ROUTING_KEY_DOCUMENT_NORMALIZE
from app.ocr.handlers.document_normalize_handler import DocumentNormalizeHandler
from app.ocr.repository import OcrTaskRepository
from app.ocr.services.document_normalizer import DocumentNormalizer
from app.ocr.storage import OcrArtifactStorage


def build_worker() -> RabbitMqWorker:
    registry = HandlerRegistry()
    repository = OcrTaskRepository(settings.postgres)
    storage = OcrArtifactStorage(settings.minio)
    normalizer = DocumentNormalizer(storage)
    registry.register(
        ROUTING_KEY_DOCUMENT_NORMALIZE,
        DocumentNormalizeHandler(repository, normalizer, settings.rabbitmq.max_attempts),
    )
    routes = [
        HandlerRoute(
            queue=QUEUE_DOCUMENT_NORMALIZE,
            routing_key=ROUTING_KEY_DOCUMENT_NORMALIZE,
            handler_key=ROUTING_KEY_DOCUMENT_NORMALIZE,
        )
    ]
    return RabbitMqWorker(settings.rabbitmq, routes, registry)


def main() -> None:
    configure_logging()
    build_worker().run()


if __name__ == "__main__":
    main()
