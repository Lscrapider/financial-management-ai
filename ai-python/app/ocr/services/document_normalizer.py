from datetime import datetime
from io import BytesIO
from typing import Any

import fitz
from PIL import Image, ImageOps

from app.ocr.storage import OcrArtifactStorage


PDF_DPI = 300


class DocumentNormalizer:
    def __init__(self, storage: OcrArtifactStorage) -> None:
        self._storage = storage

    def normalize(self, message: dict[str, Any]) -> dict[str, Any]:
        source_bucket = str(message["bucket"])
        source_object_key = str(message["objectKey"])
        source_bytes = self._storage.get_bytes(source_bucket, source_object_key)

        task_no = str(message["taskNo"])
        source_type = self._detect_source_type(message)
        if source_type == "pdf":
            pages = self._normalize_pdf(task_no, source_bucket, source_bytes)
        elif source_type == "image":
            pages = [self._normalize_image(task_no, source_bucket, source_bytes, page_no=1)]
        else:
            raise ValueError(f"unsupported document source type: {source_type}")

        manifest = {
            "taskNo": task_no,
            "sourceType": source_type,
            "pageCount": len(pages),
            "pages": pages,
            "sourceRef": {
                "storageType": message.get("storageType", "minio"),
                "bucket": source_bucket,
                "objectKey": source_object_key,
            },
            "createdAt": datetime.now().isoformat(timespec="seconds"),
        }
        manifest_key = f"{task_no}/document/manifest.json"
        self._storage.put_json(source_bucket, manifest_key, manifest)
        return {
            "manifestRef": {
                "storageType": "minio",
                "bucket": source_bucket,
                "objectKey": manifest_key,
            },
            "pageCount": len(pages),
            "metrics": {
                "sourceType": source_type,
                "pageCount": len(pages),
                "dpi": PDF_DPI if source_type == "pdf" else None,
            },
        }

    def _detect_source_type(self, message: dict[str, Any]) -> str:
        content_type = str(message.get("contentType") or "")
        if content_type == "application/pdf":
            return "pdf"
        if content_type.startswith("image/"):
            return "image"
        object_key = str(message.get("objectKey") or "")
        suffix = object_key.rsplit(".", maxsplit=1)[-1].lower() if "." in object_key else ""
        if suffix == "pdf":
            return "pdf"
        if suffix in {"png", "jpg", "jpeg", "webp"}:
            return "image"
        return "unknown"

    def _normalize_pdf(self, task_no: str, bucket: str, source_bytes: bytes) -> list[dict[str, Any]]:
        document = fitz.open(stream=source_bytes, filetype="pdf")
        try:
            pages: list[dict[str, Any]] = []
            zoom = PDF_DPI / 72
            matrix = fitz.Matrix(zoom, zoom)
            for index, page in enumerate(document, start=1):
                pixmap = page.get_pixmap(matrix=matrix, alpha=False)
                image_bytes = pixmap.tobytes("png")
                object_key = f"{task_no}/pages/page-{index:03d}.png"
                self._storage.put_bytes(bucket, object_key, image_bytes, content_type="image/png")
                pages.append(
                    {
                        "pageNo": index,
                        "imageRef": {
                            "storageType": "minio",
                            "bucket": bucket,
                            "objectKey": object_key,
                        },
                        "width": pixmap.width,
                        "height": pixmap.height,
                        "dpi": PDF_DPI,
                        "rotation": int(page.rotation or 0),
                    }
                )
            if not pages:
                raise ValueError("pdf has no pages")
            return pages
        finally:
            document.close()

    def _normalize_image(
        self,
        task_no: str,
        bucket: str,
        source_bytes: bytes,
        page_no: int,
    ) -> dict[str, Any]:
        with Image.open(BytesIO(source_bytes)) as image:
            normalized = ImageOps.exif_transpose(image)
            if normalized.mode not in {"RGB", "L"}:
                normalized = normalized.convert("RGB")
            output = BytesIO()
            normalized.save(output, format="PNG")
            image_bytes = output.getvalue()
            object_key = f"{task_no}/pages/page-{page_no:03d}.png"
            self._storage.put_bytes(bucket, object_key, image_bytes, content_type="image/png")
            return {
                "pageNo": page_no,
                "imageRef": {
                    "storageType": "minio",
                    "bucket": bucket,
                    "objectKey": object_key,
                },
                "width": normalized.width,
                "height": normalized.height,
                "dpi": None,
                "rotation": 0,
            }
