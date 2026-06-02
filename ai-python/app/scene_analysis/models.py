from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class BaseMetrics:
    values: dict[str, Any] = field(default_factory=dict)
    missing: list[str] = field(default_factory=list)

    def get(self, key: str, default: Any = None) -> Any:
        return self.values.get(key, default)


@dataclass(frozen=True)
class SceneModuleResult:
    module: str
    score: float
    level: str
    direction: str
    tags: dict[str, float] = field(default_factory=dict)
    evidence: list[str] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return {
            "module": self.module,
            "score": self.score,
            "level": self.level,
            "direction": self.direction,
            "tags": self.tags,
            "evidence": self.evidence,
        }
