from app.messaging.base_handler import MessageHandler


class HandlerRegistry:
    def __init__(self) -> None:
        self._handlers: dict[str, MessageHandler] = {}

    def register(self, key: str, handler: MessageHandler) -> None:
        self._handlers[key] = handler

    def get(self, key: str) -> MessageHandler | None:
        return self._handlers.get(key)
