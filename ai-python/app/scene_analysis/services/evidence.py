from __future__ import annotations

from app.scene_analysis.services.module_scoring import number


def build_evidence(
    tags: dict[str, float],
    reasons: dict[str, str],
    threshold: float = 0.3,
) -> list[str]:
    evidence: list[str] = []
    for tag, reason in reasons.items():
        score = number(tags.get(tag))
        if score is not None and score >= threshold:
            evidence.append(reason)
    return evidence


def active_signal_names(signals: dict[str, float | None], labels: dict[str, str], threshold: float = 0.3) -> list[str]:
    return [
        labels[key]
        for key, value in signals.items()
        if key in labels and (score := number(value)) is not None and score >= threshold
    ]


def joined_signal_reason(signals: list[str], fallback: str, suffix: str) -> str:
    if not signals:
        return fallback
    return f"{'、'.join(signals)}{suffix}"
