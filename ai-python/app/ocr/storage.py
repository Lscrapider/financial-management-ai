from io import BytesIO
import json
from typing import Any

from minio import Minio

from app.core.config import MinioSettings


class OcrArtifactStorage:
    def __init__(self, settings: MinioSettings) -> None:
        self._settings = settings
        self._client = Minio(
            endpoint=settings.endpoint,
            access_key=settings.access_key,
            secret_key=settings.secret_key,
            secure=settings.secure,
        )

    def put_json(self, bucket: str, object_key: str, payload: dict[str, Any]) -> None:
        data = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
        self.put_bytes(bucket, object_key, data, content_type="application/json")

    def get_json(self, bucket: str, object_key: str) -> dict[str, Any]:
        data = self.get_bytes(bucket, object_key)
        payload = json.loads(data.decode("utf-8"))
        if not isinstance(payload, dict):
            raise ValueError(f"JSON object expected: minio://{bucket}/{object_key}")
        return payload

    def get_bytes(self, bucket: str, object_key: str) -> bytes:
        response = self._client.get_object(bucket_name=bucket, object_name=object_key)
        try:
            return response.read()
        finally:
            response.close()
            response.release_conn()

    def put_bytes(self, bucket: str, object_key: str, data: bytes, content_type: str) -> None:
        self._client.put_object(
            bucket_name=bucket,
            object_name=object_key,
            data=BytesIO(data),
            length=len(data),
            content_type=content_type,
        )

    def stat_object(self, bucket: str, object_key: str) -> None:
        self._client.stat_object(bucket_name=bucket, object_name=object_key)
