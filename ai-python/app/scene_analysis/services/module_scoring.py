from __future__ import annotations

from collections.abc import Mapping
from typing import Any


def clamp(value: float) -> float:
    return min(max(value, 0.0), 1.0)


def number(value: Any) -> float | None:
    if value is None or value == "":
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def score_value(value: Any) -> float | None:
    numeric = number(value)
    return None if numeric is None else clamp(numeric)


def active_tags(tags: Mapping[str, float | None]) -> dict[str, float]:
    return {key: score for key, score in tags.items() if score is not None and score > 0}


def module_score(tags: Mapping[str, float]) -> float:
    return max(tags.values()) if tags else 0.0


def module_level(score: float) -> str:
    if score >= 0.7:
        return "high"
    if score >= 0.3:
        return "medium"
    return "low"


def percentile_rank(value: float | None, history_values: list[float]) -> float | None:
    if value is None or not history_values:
        return None
    return clamp(sum(1 for item in history_values if item <= value) / len(history_values))


def weighted_sum(signals: Mapping[str, float | None], weights: Mapping[str, Any] | None) -> float | None:
    if not weights:
        return None
    valid_items = [
        (name, score, number(weights.get(name)))
        for name, score in signals.items()
        if score is not None and weights.get(name) is not None
    ]
    weighted_items = [(name, score, weight) for name, score, weight in valid_items if weight is not None and weight > 0]
    weight_sum = sum(weight for _, _, weight in weighted_items)
    if weight_sum <= 0:
        return None
    return clamp(sum(weight * score for _, score, weight in weighted_items) / weight_sum)


def noisy_or(scores: list[float | None]) -> float:
    result = 1.0
    for score in scores:
        if score is None:
            continue
        result *= 1 - clamp(score)
    return clamp(1 - result)
