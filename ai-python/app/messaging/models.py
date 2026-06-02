from dataclasses import dataclass, field
from collections.abc import Callable
from typing import Any


@dataclass(frozen=True)
class IncomingMessage:
    body: dict[str, Any]
    routing_key: str
    delivery_tag: int
    attempt: int

    @property
    def task_no(self) -> str:
        return str(self.body.get("taskNo") or "")

    @property
    def stage(self) -> str:
        return str(self.body.get("stage") or "")


@dataclass(frozen=True)
class OutgoingMessage:
    exchange: str
    routing_key: str
    body: dict[str, Any]


@dataclass(frozen=True)
class HandlerResult:
    outgoing_messages: list[OutgoingMessage] = field(default_factory=list)


@dataclass(frozen=True)
class HandlerRoute:
    queue: str
    routing_key: str
    handler_key: str
    retry_exchange: str | None = None
    task_deleted_checker: Callable[[str], bool] | None = None
