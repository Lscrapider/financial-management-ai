from dataclasses import dataclass
from typing import Any

import psycopg
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb

from app.core.config import PostgresSettings


@dataclass(frozen=True)
class OcrTask:
    task_no: str
    status: str
    current_stage: str


class OcrTaskRepository:
    def __init__(self, settings: PostgresSettings) -> None:
        self._settings = settings

    def find_by_task_no(self, task_no: str) -> OcrTask | None:
        with psycopg.connect(self._settings.dsn, row_factory=dict_row) as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    SELECT task_no, status, current_stage
                    FROM ocr_task
                    WHERE task_no = %s
                    """,
                    (task_no,),
                )
                row = cursor.fetchone()
        if row is None:
            return None
        return OcrTask(
            task_no=str(row["task_no"]),
            status=str(row["status"]),
            current_stage=str(row["current_stage"]),
        )

    def mark_running(self, task_no: str, stage: str, progress: int) -> None:
        with psycopg.connect(self._settings.dsn) as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    UPDATE ocr_task
                    SET status = 'running',
                        current_stage = %s,
                        progress = %s,
                        error_message = NULL,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE task_no = %s
                    """,
                    (stage, progress, task_no),
                )
            connection.commit()

    def start_stage(
        self,
        task_no: str,
        stage: str,
        attempt: int,
        max_attempts: int,
        input_message: dict[str, Any],
        input_ref: dict[str, Any],
    ) -> None:
        with psycopg.connect(self._settings.dsn) as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    INSERT INTO ocr_task_stage (
                        task_no, stage, status, attempt_count, max_attempts,
                        input_message, input_ref, error_message, started_at, updated_at
                    )
                    VALUES (%s, %s, 'running', %s, %s, %s, %s, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (task_no, stage)
                    DO UPDATE SET
                        status = 'running',
                        attempt_count = EXCLUDED.attempt_count,
                        max_attempts = EXCLUDED.max_attempts,
                        input_message = EXCLUDED.input_message,
                        input_ref = EXCLUDED.input_ref,
                        error_message = NULL,
                        started_at = CURRENT_TIMESTAMP,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    (
                        task_no,
                        stage,
                        attempt,
                        max_attempts,
                        Jsonb(input_message),
                        Jsonb(input_ref),
                    ),
                )
            connection.commit()

    def finish_stage(
        self,
        task_no: str,
        stage: str,
        next_stage: str,
        progress: int,
        page_count: int,
        segment_count: int,
        output_ref: dict[str, Any],
        output_message: dict[str, Any],
        metrics: dict[str, Any],
    ) -> None:
        with psycopg.connect(self._settings.dsn) as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    UPDATE ocr_task
                    SET status = 'running',
                        current_stage = %s,
                        progress = %s,
                        page_count = %s,
                        segment_count = %s,
                        error_message = NULL,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE task_no = %s
                    """,
                    (next_stage, progress, page_count, segment_count, task_no),
                )
                cursor.execute(
                    """
                    UPDATE ocr_task_stage
                    SET status = 'finished',
                        output_ref = %s,
                        output_message = %s,
                        metrics = %s,
                        error_message = NULL,
                        finished_at = CURRENT_TIMESTAMP,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE task_no = %s
                      AND stage = %s
                    """,
                    (
                        Jsonb(output_ref),
                        Jsonb(output_message),
                        Jsonb(metrics),
                        task_no,
                        stage,
                    ),
                )
            connection.commit()

    def fail_stage(self, task_no: str, stage: str, error_message: str, mark_task_failed: bool) -> None:
        with psycopg.connect(self._settings.dsn) as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    UPDATE ocr_task_stage
                    SET status = 'failed',
                        error_message = %s,
                        finished_at = CURRENT_TIMESTAMP,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE task_no = %s
                      AND stage = %s
                    """,
                    (error_message, task_no, stage),
                )
                task_status_sql = "status = 'failed'," if mark_task_failed else ""
                cursor.execute(
                    f"""
                    UPDATE ocr_task
                    SET {task_status_sql}
                        current_stage = %s,
                        error_message = %s,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE task_no = %s
                    """,
                    (stage, error_message, task_no),
                )
            connection.commit()
