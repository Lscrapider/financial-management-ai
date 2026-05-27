import json

import psycopg
from pgvector.psycopg import register_vector
from psycopg.types.json import Jsonb

from app.core.config import PostgresSettings
from app.ocr.services.embedding_service import VectorChunk


class VectorStore:
    def __init__(self, settings: PostgresSettings) -> None:
        self._settings = settings

    def rebuild_task_vectors(self, task_no: str, chunks: list[VectorChunk]) -> int:
        if not chunks:
            return 0
        with psycopg.connect(self._settings.dsn) as connection:
            register_vector(connection)
            with connection.cursor() as cursor:
                cursor.execute(
                    "DELETE FROM knowledge_vector WHERE task_no = %s",
                    (task_no,),
                )
                for chunk in chunks:
                    cursor.execute(
                        """
                        INSERT INTO knowledge_vector (task_no, chunk_index, text, embedding, metadata)
                        VALUES (%s, %s, %s, %s, %s)
                        """,
                        (
                            task_no,
                            chunk.chunk_index,
                            chunk.text,
                            chunk.embedding,
                            Jsonb(chunk.metadata),
                        ),
                    )
            connection.commit()
        return len(chunks)

    def update_chunk_embedding(self, chunk_id: str, new_text: str, embedding: list[float]) -> bool:
        """Update text and embedding for a single chunk, increment version."""
        with psycopg.connect(self._settings.dsn) as connection:
            register_vector(connection)
            with connection.cursor() as cursor:
                # chunk_id is stored in metadata->>'chunkId'
                cursor.execute(
                    """
                    SELECT metadata FROM knowledge_vector
                    WHERE metadata->>'chunkId' = %s
                    """,
                    (chunk_id,),
                )
                row = cursor.fetchone()
                if row is None:
                    return False
                meta = self._metadata_to_dict(row[0])
                new_version = meta.get("version", 0) + 1
                meta["version"] = new_version
                cursor.execute(
                    """
                    UPDATE knowledge_vector
                    SET text = %s, embedding = %s, metadata = %s
                    WHERE metadata->>'chunkId' = %s
                    """,
                    (new_text, embedding, Jsonb(meta), chunk_id),
                )
            connection.commit()
        return True

    def _metadata_to_dict(self, metadata: object) -> dict:
        if metadata is None:
            return {}
        if isinstance(metadata, dict):
            return metadata
        if isinstance(metadata, str):
            parsed = json.loads(metadata)
            if isinstance(parsed, dict):
                return parsed
        raise TypeError(f"unsupported knowledge_vector metadata type: {type(metadata).__name__}")
