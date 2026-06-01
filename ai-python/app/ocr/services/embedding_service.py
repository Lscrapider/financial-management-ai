from dataclasses import dataclass, field

from app.ocr.services.chunk_tag_schema import empty_scenes


@dataclass
class VectorChunk:
    chunk_id: str
    chunk_index: int
    text: str
    embedding: list[float]
    metadata: dict = field(default_factory=dict)


class EmbeddingService:
    def __init__(self, engine: "BaseEmbeddingEngine", model_name: str) -> None:
        self._engine = engine
        self.model_name = model_name

    def embed(self, reviewed_json: dict) -> list[VectorChunk]:
        content = reviewed_json.get("content") or {}
        paragraphs = content.get("paragraphs") or []
        task_no = reviewed_json.get("taskNo") or ""

        if not paragraphs:
            return []

        texts = [p.get("text") or "" for p in paragraphs]
        embeddings = self._engine.embed(texts)

        chunks: list[VectorChunk] = []
        for i, paragraph in enumerate(paragraphs):
            text = texts[i]
            if not text.strip():
                continue
            chunk_index = i + 1
            paragraph_metadata = paragraph.get("metadata") or {}
            if isinstance(paragraph_metadata, dict) and paragraph_metadata.get("deleted") is True:
                continue
            metadata = {
                "taskNo": task_no,
                "documentId": task_no,
                "chunkId": f"{task_no}:chunk:{chunk_index:04d}",
                "pageNos": paragraph.get("sourcePages") or [],
                "paragraphNos": [paragraph.get("paragraphNo") or chunk_index],
                "sourceType": "ocr_reviewed",
                "avgConfidence": paragraph.get("avgConfidence"),
                "warnings": paragraph.get("warnings") or [],
                "version": 1,
                "deleted": False,
                "scenes": empty_scenes(),
                "keywords": [],
                "summary": "",
                "tagging": {},
            }
            if isinstance(paragraph_metadata, dict):
                metadata["scenes"] = paragraph_metadata.get("scenes") or {}
                metadata["keywords"] = paragraph_metadata.get("keywords") or []
                metadata["summary"] = paragraph_metadata.get("summary") or ""
                metadata["deleted"] = bool(paragraph_metadata.get("deleted"))
                metadata["tagging"] = paragraph_metadata.get("tagging") or {}
            chunks.append(
                VectorChunk(
                    chunk_id=f"{task_no}:chunk:{chunk_index:04d}",
                    chunk_index=chunk_index,
                    text=text,
                    embedding=embeddings[i],
                    metadata=metadata,
                )
            )
        return chunks
