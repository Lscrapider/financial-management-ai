from datetime import datetime
import re
from typing import Any

from app.ocr.storage import OcrArtifactStorage


LOW_CONFIDENCE_THRESHOLD = 0.7


class TextCleaner:
    def __init__(self, storage: OcrArtifactStorage) -> None:
        self._storage = storage

    def clean(self, message: dict[str, Any]) -> dict[str, Any]:
        input_ref = message.get("inputRef") or {}
        result = self._storage.get_json(str(input_ref["bucket"]), str(input_ref["objectKey"]))
        paragraphs, empty_segment_count = self._build_paragraphs(result)
        metrics = self._metrics(paragraphs, empty_segment_count)
        cleaned = {
            "taskNo": message["taskNo"],
            "paragraphCount": len(paragraphs),
            "paragraphs": paragraphs,
            "metrics": metrics,
            "sourceRef": input_ref,
            "createdAt": datetime.now().isoformat(timespec="seconds"),
        }
        output_prefix = self._output_prefix(message)
        output_key = f"{output_prefix['objectKey'].rstrip('/')}/cleaned.json"
        self._storage.put_json(output_prefix["bucket"], output_key, cleaned)
        return {
            "outputRef": {
                "storageType": "minio",
                "bucket": output_prefix["bucket"],
                "objectKey": output_key,
            },
            "paragraphCount": len(paragraphs),
            "metrics": metrics,
        }

    def _build_paragraphs(self, result: dict[str, Any]) -> tuple[list[dict[str, Any]], int]:
        paragraphs: list[dict[str, Any]] = []
        empty_segment_count = 0
        for page in result.get("pages") or []:
            page_no = page.get("pageNo")
            for segment in page.get("segments") or []:
                text = self._normalize_text(str(segment.get("content") or ""))
                if not text:
                    empty_segment_count += 1
                    continue
                confidence = float(segment.get("confidence") or 0)
                paragraphs.append(
                    {
                        "paragraphNo": len(paragraphs) + 1,
                        "text": text,
                        "sourcePages": [page_no],
                        "sourceSegments": [
                            {
                                "pageNo": page_no,
                                "segmentNo": segment.get("segmentNo"),
                            }
                        ],
                        "avgConfidence": confidence,
                        "warnings": self._warnings(text, confidence),
                    }
                )
        return paragraphs, empty_segment_count

    def _normalize_text(self, text: str) -> str:
        normalized = text.replace("\u3000", " ")
        normalized = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]", "", normalized)
        normalized = re.sub(r"[ \t]+", " ", normalized)
        return normalized.strip()

    def _warnings(self, text: str, confidence: float) -> list[dict[str, Any]]:
        warnings: list[dict[str, Any]] = []
        if confidence < LOW_CONFIDENCE_THRESHOLD:
            warnings.append(
                {
                    "type": "low_confidence",
                    "confidence": confidence,
                }
            )
        if self._garbled_ratio(text) > 0.3:
            warnings.append(
                {
                    "type": "possible_garbled_text",
                }
            )
        return warnings

    def _garbled_ratio(self, text: str) -> float:
        if not text:
            return 0
        suspicious = sum(1 for char in text if char in "�□■◆◇●○")
        return suspicious / len(text)

    def _metrics(self, paragraphs: list[dict[str, Any]], empty_segment_count: int) -> dict[str, Any]:
        confidences = [float(paragraph["avgConfidence"]) for paragraph in paragraphs]
        warning_count = sum(len(paragraph["warnings"]) for paragraph in paragraphs)
        low_confidence_count = sum(
            1
            for paragraph in paragraphs
            if float(paragraph["avgConfidence"]) < LOW_CONFIDENCE_THRESHOLD
        )
        return {
            "paragraphCount": len(paragraphs),
            "emptySegmentCount": empty_segment_count,
            "warningCount": warning_count,
            "lowConfidenceParagraphCount": low_confidence_count,
            "avgConfidence": round(sum(confidences) / len(confidences), 4) if confidences else 0,
        }

    def _output_prefix(self, message: dict[str, Any]) -> dict[str, Any]:
        output_prefix = message.get("outputPrefix") or {}
        if not output_prefix.get("bucket") or not output_prefix.get("objectKey"):
            raise ValueError("text.clean message outputPrefix.bucket and outputPrefix.objectKey are required")
        return output_prefix
