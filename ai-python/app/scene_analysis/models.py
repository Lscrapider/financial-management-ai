from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class BaseMetrics:
    values: dict[str, Any] = field(default_factory=dict)
    missing: list[str] = field(default_factory=list)

    def get(self, key: str, default: Any = None) -> Any:
        return self.values.get(key, default)
