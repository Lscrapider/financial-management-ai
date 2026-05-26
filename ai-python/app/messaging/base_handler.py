from abc import ABC, abstractmethod

from app.messaging.models import HandlerResult, IncomingMessage


class MessageHandler(ABC):
    @abstractmethod
    def handle(self, message: IncomingMessage) -> HandlerResult:
        raise NotImplementedError
