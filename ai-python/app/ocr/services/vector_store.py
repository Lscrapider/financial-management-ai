import json

import psycopg
from pgvector.psycopg import register_vector

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
                    metadata = json.dumps(chunk.metadata, ensure_ascii=False)
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
                            metadata,
                        ),
                    )
            connection.commit()
        return len(chunks)
