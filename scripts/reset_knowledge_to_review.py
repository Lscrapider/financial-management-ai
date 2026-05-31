#!/usr/bin/env python3
import argparse
import json
import os
from typing import Any


def env_int(name: str, default: int) -> int:
    value = os.getenv(name)
    return int(value) if value else default


def postgres_dsn() -> str:
    return (
        f"host={os.getenv('POSTGRES_HOST', 'localhost')} "
        f"port={env_int('POSTGRES_PORT', 5432)} "
        f"dbname={os.getenv('POSTGRES_DB', 'finance_management')} "
        f"user={os.getenv('POSTGRES_USERNAME', os.getenv('POSTGRES_USER', 'postgres'))} "
        f"password={os.getenv('POSTGRES_PASSWORD', '123456')}"
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Reset existing knowledge_vector tasks to manual review with draft content already filled."
    )
    parser.add_argument("--execute", action="store_true", help="Actually update ocr_task and ocr_review.")
    parser.add_argument("--include-deleted", action="store_true", help="Include soft-deleted OCR tasks.")
    parser.add_argument("--limit", type=int, default=0, help="Limit task count for a small batch run.")
    parser.add_argument("--task-no", action="append", default=[], help="Only reset the given taskNo. Can be repeated.")
    return parser.parse_args()


def load_tasks(connection: Any, args: argparse.Namespace) -> list[dict[str, Any]]:
    from psycopg.rows import dict_row

    filters = ["t.task_no IS NOT NULL"]
    params: list[Any] = []
    if not args.include_deleted:
        filters.append("t.deleted_at IS NULL")
    if args.task_no:
        filters.append("t.task_no = ANY(%s)")
        params.append(args.task_no)

    limit_sql = ""
    if args.limit > 0:
        limit_sql = "LIMIT %s"
        params.append(args.limit)

    sql = f"""
        WITH knowledge_tasks AS (
            SELECT
                task_no,
                COUNT(*) AS chunk_count,
                jsonb_agg(
                    jsonb_build_object(
                        'chunkIndex', chunk_index,
                        'text', text,
                        'metadata', COALESCE(metadata, '{{}}'::jsonb)
                    )
                    ORDER BY chunk_index
                ) AS chunks
            FROM knowledge_vector
            WHERE task_no IS NOT NULL
              AND task_no <> ''
            GROUP BY task_no
        )
        SELECT
            t.task_no,
            t.status AS task_status,
            t.current_stage,
            kt.chunk_count,
            kt.chunks,
            COALESCE(
                r.cleaned_ref,
                text_stage.output_ref,
                jsonb_build_object(
                    'storageType', 'minio',
                    'bucket', %s,
                    'objectKey', ''
                )
            ) AS cleaned_ref
        FROM knowledge_tasks kt
        JOIN ocr_task t ON t.task_no = kt.task_no
        LEFT JOIN ocr_review r ON r.task_no = kt.task_no
        LEFT JOIN ocr_task_stage text_stage
               ON text_stage.task_no = kt.task_no
              AND text_stage.stage = 'text.clean'
        WHERE {" AND ".join(filters)}
        ORDER BY t.updated_at DESC, t.task_no
        {limit_sql}
    """
    with connection.cursor(row_factory=dict_row) as cursor:
        cursor.execute(sql, [os.getenv("MINIO_OCR_BUCKET", "finance-ocr"), *params])
        return list(cursor.fetchall())


def build_draft_content(row: dict[str, Any]) -> dict[str, Any]:
    chunks = ensure_list(row["chunks"])
    paragraphs = []
    warning_count = 0
    confidence_sum = 0.0

    for index, chunk in enumerate(chunks):
        metadata = ensure_dict(chunk.get("metadata"))
        warnings = metadata.get("warnings") if isinstance(metadata.get("warnings"), list) else []
        avg_confidence = metadata.get("avgConfidence")
        if not isinstance(avg_confidence, int | float):
            avg_confidence = 1.0
        source_pages = metadata.get("pageNos") if isinstance(metadata.get("pageNos"), list) else []
        paragraph_nos = metadata.get("paragraphNos") if isinstance(metadata.get("paragraphNos"), list) else []
        paragraph_no = int(paragraph_nos[0]) if paragraph_nos else index + 1

        warning_count += len(warnings)
        confidence_sum += float(avg_confidence)
        paragraphs.append(
            {
                "paragraphNo": paragraph_no,
                "text": str(chunk.get("text") or ""),
                "sourcePages": source_pages,
                "avgConfidence": round(float(avg_confidence), 4),
                "warnings": warnings,
            }
        )

    paragraph_count = len(paragraphs)
    overall_confidence = round(confidence_sum / paragraph_count, 4) if paragraph_count else 0.0
    return {
        "paragraphCount": paragraph_count,
        "paragraphs": paragraphs,
        "metrics": {
            "paragraphCount": paragraph_count,
            "avgConfidence": overall_confidence,
            "warningCount": warning_count,
        },
    }


def reset_to_review(connection: Any, row: dict[str, Any]) -> None:
    draft_content = build_draft_content(row)
    paragraph_count = draft_content["paragraphCount"]
    overall_confidence = draft_content["metrics"]["avgConfidence"]
    warning_count = draft_content["metrics"]["warningCount"]
    cleaned_ref = ensure_dict(row["cleaned_ref"])
    task_no = str(row["task_no"])

    with connection.cursor() as cursor:
        cursor.execute(
            """
            INSERT INTO ocr_review (
                task_no, status, cleaned_ref, reviewed_ref, draft_content,
                overall_confidence, paragraph_count, warning_count,
                reviewed_at, created_at, updated_at
            )
            VALUES (
                %s, 'pending', %s::jsonb, NULL, %s::jsonb,
                %s, %s, %s,
                NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            )
            ON CONFLICT (task_no)
            DO UPDATE SET
                status = 'pending',
                cleaned_ref = EXCLUDED.cleaned_ref,
                reviewed_ref = NULL,
                draft_content = EXCLUDED.draft_content,
                overall_confidence = EXCLUDED.overall_confidence,
                paragraph_count = EXCLUDED.paragraph_count,
                warning_count = EXCLUDED.warning_count,
                reviewed_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            """,
            (
                task_no,
                json.dumps(cleaned_ref, ensure_ascii=False),
                json.dumps(draft_content, ensure_ascii=False),
                overall_confidence,
                paragraph_count,
                warning_count,
            ),
        )
        cursor.execute(
            """
            UPDATE ocr_task
            SET status = 'manual_review_required',
                current_stage = 'quality.validate',
                progress = 60,
                segment_count = %s,
                error_message = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE task_no = %s
            """,
            (paragraph_count, task_no),
        )
    connection.commit()


def ensure_dict(value: Any) -> dict[str, Any]:
    if value is None:
        return {}
    if isinstance(value, dict):
        return value
    if isinstance(value, str):
        parsed = json.loads(value)
        if isinstance(parsed, dict):
            return parsed
    raise TypeError(f"expected JSON object, got {type(value).__name__}")


def ensure_list(value: Any) -> list[Any]:
    if isinstance(value, list):
        return value
    if isinstance(value, str):
        parsed = json.loads(value)
        if isinstance(parsed, list):
            return parsed
    raise TypeError(f"expected JSON array, got {type(value).__name__}")


def main() -> None:
    args = parse_args()
    try:
        import psycopg
    except ModuleNotFoundError as exc:
        raise SystemExit(
            "Missing dependency: psycopg. Install ai-python requirements first, "
            "for example: pip install -r ai-python/requirements.txt"
        ) from exc

    with psycopg.connect(postgres_dsn()) as connection:
        rows = load_tasks(connection, args)
        print(f"knowledge tasks found: {len(rows)}")
        for row in rows:
            draft_content = build_draft_content(row)
            print(
                "READY "
                f"task_no={row['task_no']} "
                f"status={row['task_status']} "
                f"stage={row['current_stage']} "
                f"chunks={row['chunk_count']} "
                f"paragraphs={draft_content['paragraphCount']}"
            )

        if not args.execute:
            print("dry-run only. Re-run with --execute to reset tasks to manual review.")
            return

        for row in rows:
            reset_to_review(connection, row)
        print(f"done. reset_to_review={len(rows)}")


if __name__ == "__main__":
    main()
