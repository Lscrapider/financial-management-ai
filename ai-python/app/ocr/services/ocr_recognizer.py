from datetime import datetime
from typing import Any

from app.ocr.engines.qwen_vl_ocr_engine import QwenVlOcrEngine
from app.ocr.services.opendataloader_pdf_recognizer import OpenDataLoaderPdfRecognizer
from app.ocr.storage import OcrArtifactStorage


class OcrRecognizer:
    def __init__(self, storage: OcrArtifactStorage, engine: QwenVlOcrEngine) -> None:
        self._storage = storage
        self._engine = engine
        self._pdf_recognizer = OpenDataLoaderPdfRecognizer()

    def recognize(self, message: dict[str, Any]) -> dict[str, Any]:
        pages = message.get("pages") or []
        if not isinstance(pages, list) or not pages:
            raise ValueError("ocr.recognize message pages must be a non-empty list")

        output_prefix = self._output_prefix(message)
        if self._is_pdf_source(message):
            result = self._recognize_pdf(message)
        else:
            recognized_pages = [self._recognize_page(page) for page in pages]
            metrics = self._metrics(recognized_pages)
            result = {
                "taskNo": message["taskNo"],
                "engine": "qwen-vl-ocr-latest",
                "pageCount": len(recognized_pages),
                "pages": recognized_pages,
                "metrics": metrics,
                "createdAt": datetime.now().isoformat(timespec="seconds"),
            }
        output_key = f"{output_prefix['objectKey'].rstrip('/')}/result.json"
        self._storage.put_json(output_prefix["bucket"], output_key, result)
        return {
            "outputRef": {
                "storageType": "minio",
                "bucket": output_prefix["bucket"],
                "objectKey": output_key,
            },
            "pageCount": int(result["pageCount"]),
            "segmentCount": int(result["metrics"]["segmentCount"]),
            "metrics": result["metrics"],
        }

    def _recognize_pdf(self, message: dict[str, Any]) -> dict[str, Any]:
        source_ref = message.get("sourceRef") or {}
        source_bucket = str(source_ref["bucket"])
        source_object_key = str(source_ref["objectKey"])
        pdf_bytes = self._storage.get_bytes(source_bucket, source_object_key)
        return self._pdf_recognizer.recognize(str(message["taskNo"]), pdf_bytes, message)

    def _recognize_page(self, page: dict[str, Any]) -> dict[str, Any]:
        image_ref = page.get("imageRef") or {}
        image_bytes = self._storage.get_bytes(str(image_ref["bucket"]), str(image_ref["objectKey"]))
        engine_result = self._engine.recognize_png(image_bytes)
        parsed = engine_result["parsed"]
        return {
            "pageNo": page.get("pageNo"),
            "imageRef": image_ref,
            "width": page.get("width"),
            "height": page.get("height"),
            "enabled": parsed["enabled"],
            "segments": parsed["segments"],
            "rawContent": engine_result["rawContent"],
            "usage": engine_result["usage"],
        }

    def _output_prefix(self, message: dict[str, Any]) -> dict[str, Any]:
        output_prefix = message.get("outputPrefix") or {}
        if not output_prefix.get("bucket") or not output_prefix.get("objectKey"):
            raise ValueError("ocr.recognize message outputPrefix.bucket and outputPrefix.objectKey are required")
        return output_prefix

    def _is_pdf_source(self, message: dict[str, Any]) -> bool:
        source_ref = message.get("sourceRef") or {}
        object_key = str(source_ref.get("objectKey") or "")
        return object_key.rsplit(".", maxsplit=1)[-1].lower() == "pdf" if "." in object_key else False

    def _metrics(self, pages: list[dict[str, Any]]) -> dict[str, Any]:
        enabled_page_count = sum(1 for page in pages if page["enabled"])
        segment_count = sum(len(page["segments"]) for page in pages)
        confidences = [
            float(segment["confidence"])
            for page in pages
            for segment in page["segments"]
        ]
        usage = self._sum_usage(pages)
        return {
            "enabledPageCount": enabled_page_count,
            "emptyPageCount": len(pages) - enabled_page_count,
            "segmentCount": segment_count,
            "avgConfidence": round(sum(confidences) / len(confidences), 4) if confidences else 0,
            "usage": usage,
        }

    def _sum_usage(self, pages: list[dict[str, Any]]) -> dict[str, int]:
        total_tokens = 0
        prompt_tokens = 0
        completion_tokens = 0
        for page in pages:
            usage = page.get("usage") or {}
            total_tokens += int(usage.get("total_tokens") or 0)
            prompt_tokens += int(usage.get("prompt_tokens") or 0)
            completion_tokens += int(usage.get("completion_tokens") or 0)
        return {
            "promptTokens": prompt_tokens,
            "completionTokens": completion_tokens,
            "totalTokens": total_tokens,
        }
