from typing import Any

from app.ocr.services.chunk_tag_schema import SCENE_CATEGORIES, VALID_TAGS, empty_scenes


class ChunkTagCorrector:
    def correct(self, message_body: dict[str, Any]) -> dict[str, Any]:
        chunk = message_body.get("chunk") or {}
        rule_tagging = message_body.get("ruleTagging") or {}
        llm_tagging = message_body.get("llmTagging") or {}
        threshold = self._confidence_threshold(rule_tagging)
        final_scenes = empty_scenes()

        llm_scenes = llm_tagging.get("llmScenes") if isinstance(llm_tagging, dict) else None
        if isinstance(llm_scenes, dict):
            self._append_llm_tags(final_scenes, llm_scenes)

        rule_scores = rule_tagging.get("ruleScenesWithConfidence") or {}
        if isinstance(rule_scores, dict):
            self._append_high_confidence_rule_tags(final_scenes, rule_scores, threshold)

        return {
            "taskNo": message_body.get("taskNo") or "",
            "chunkId": chunk.get("chunkId") or "",
            "chunkIndex": int(chunk.get("chunkIndex") or 0),
            "text": chunk.get("text") or "",
            "pageNos": chunk.get("pageNos") or [],
            "paragraphNos": chunk.get("paragraphNos") or [],
            "metadata": {
                "scenes": final_scenes,
                "keywords": [],
                "summary": "",
                "tagging": {
                    "ruleTagging": rule_tagging,
                    "llmTagging": llm_tagging if llm_tagging else None,
                },
            },
        }

    def _append_llm_tags(self, final_scenes: dict[str, list[str]], llm_scenes: dict) -> None:
        for category in SCENE_CATEGORIES:
            tags = llm_scenes.get(category) or []
            if not isinstance(tags, list):
                continue
            for tag in tags:
                self._append_valid_tag(final_scenes, category, tag)

    def _append_high_confidence_rule_tags(
        self,
        final_scenes: dict[str, list[str]],
        rule_scores: dict,
        threshold: float,
    ) -> None:
        for category in SCENE_CATEGORIES:
            tags = rule_scores.get(category) or {}
            if not isinstance(tags, dict):
                continue
            for tag, score in tags.items():
                if self._score_value(score) >= threshold:
                    self._append_valid_tag(final_scenes, category, tag)

    def _append_valid_tag(self, final_scenes: dict[str, list[str]], category: str, tag: Any) -> None:
        if not isinstance(tag, str):
            return
        if tag not in VALID_TAGS.get(category, frozenset()):
            return
        if tag not in final_scenes[category]:
            final_scenes[category].append(tag)

    def _confidence_threshold(self, rule_tagging: dict) -> float:
        quality_gate = rule_tagging.get("qualityGate") if isinstance(rule_tagging, dict) else None
        if not isinstance(quality_gate, dict):
            return 0.75
        return self._score_value(quality_gate.get("confidenceThreshold"), default=0.75)

    def _score_value(self, value: Any, default: float = 0.0) -> float:
        if isinstance(value, (int, float)):
            return float(value)
        if isinstance(value, str):
            try:
                return float(value)
            except ValueError:
                return default
        return default
