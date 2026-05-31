#!/usr/bin/env python3
import argparse
import json
import os
import uuid
from datetime import date, datetime
from typing import Any


OCR_TOPIC_EXCHANGE = "finance.ocr.topic"
QUALITY_VALIDATE_ROUTING_KEY = "ocr.quality.validate"
QUALITY_VALIDATE_STAGE = "quality.validate"


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


def rabbitmq_params():
    import pika

    credentials = pika.PlainCredentials(
        os.getenv("RABBITMQ_USERNAME", "finance"),
        os.getenv("RABBITMQ_PASSWORD", "finance123456"),
    )
    return pika.ConnectionParameters(
        host=os.getenv("RABBITMQ_HOST", "localhost"),
        port=env_int("RABBITMQ_PORT", 5672),
        virtual_host=os.getenv("RABBITMQ_VHOST", "finance"),
        credentials=credentials,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Requeue existing knowledge_vector tasks to OCR quality.validate stage."
    )
    parser.add_argument("--execute", action="store_true", help="Actually update DB state and publish RabbitMQ messages.")
    parser.add_argument("--include-deleted", action="store_true", help="Include soft-deleted OCR tasks.")
    parser.add_argument("--limit", type=int, default=0, help="Limit task count for a small batch run.")
    parser.add_argument("--task-no", action="append", default=[], help="Only requeue the given taskNo. Can be repeated.")
    return parser.parse_args()


def load_candidates(connection: Any, args: argparse.Namespace) -> list[dict[str, Any]]:
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
            SELECT task_no, COUNT(*) AS chunk_count
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
            COALESCE(r.cleaned_ref, text_stage.output_ref) AS cleaned_ref,
            COALESCE(r.paragraph_count, t.segment_count, kt.chunk_count)::int AS paragraph_count,
            COALESCE(text_stage.metrics, '{{}}'::jsonb) AS metrics
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
        cursor.execute(sql, params)
        return list(cursor.fetchall())


def reset_task_to_quality_validate(connection: Any, task_no: str) -> None:
    with connection.cursor() as cursor:
        cursor.execute(
            """
            UPDATE ocr_task
            SET status = 'running',
                current_stage = %s,
                progress = 60,
                error_message = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE task_no = %s
            """,
            (QUALITY_VALIDATE_STAGE, task_no),
        )
        cursor.execute(
            """
            UPDATE ocr_review
            SET status = 'pending',
                reviewed_ref = NULL,
                reviewed_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE task_no = %s
            """,
            (task_no,),
        )
    connection.commit()


def quality_validate_message(row: dict[str, Any]) -> dict[str, Any]:
    cleaned_ref = ensure_dict(row["cleaned_ref"])
    task_no = str(row["task_no"])
    bucket = cleaned_ref.get("bucket")
    return {
        "eventId": str(uuid.uuid4()),
        "taskNo": task_no,
        "stage": QUALITY_VALIDATE_STAGE,
        "attempt": 1,
        "inputRef": cleaned_ref,
        "paragraphCount": int(row.get("paragraph_count") or 0),
        "metrics": ensure_dict(row.get("metrics")),
        "outputPrefix": {
            "storageType": "minio",
            "bucket": bucket,
            "objectKey": f"stage-4-output/{date.today():%Y/%m/%d}/{task_no}/quality/",
        },
        "createdAt": datetime.now().isoformat(timespec="seconds"),
    }


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


def publish_messages(rows: list[dict[str, Any]]) -> None:
    import pika

    connection = pika.BlockingConnection(rabbitmq_params())
    try:
        channel = connection.channel()
        for row in rows:
            body = quality_validate_message(row)
            channel.basic_publish(
                exchange=OCR_TOPIC_EXCHANGE,
                routing_key=QUALITY_VALIDATE_ROUTING_KEY,
                body=json.dumps(body, ensure_ascii=False).encode("utf-8"),
                properties=pika.BasicProperties(
                    content_type="application/json",
                    delivery_mode=pika.DeliveryMode.Persistent,
                ),
            )
            print(f"published task_no={body['taskNo']} input={body['inputRef'].get('objectKey')}")
    finally:
        connection.close()


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
        candidates = load_candidates(connection, args)
        ready = [row for row in candidates if row.get("cleaned_ref")]
        skipped = [row for row in candidates if not row.get("cleaned_ref")]

        print(f"knowledge tasks found: {len(candidates)}")
        print(f"ready to requeue: {len(ready)}")
        print(f"skipped without cleaned_ref: {len(skipped)}")
        for row in ready:
            print(
                "READY "
                f"task_no={row['task_no']} "
                f"status={row['task_status']} "
                f"stage={row['current_stage']} "
                f"chunks={row['chunk_count']} "
                f"cleaned={ensure_dict(row['cleaned_ref']).get('objectKey')}"
            )
        for row in skipped:
            print(f"SKIP task_no={row['task_no']} reason=missing cleaned_ref")

        # if not args.execute:
        #     print("dry-run only. Re-run with --execute to update DB and publish RabbitMQ messages.")
        #     return

        for row in ready:
            reset_task_to_quality_validate(connection, str(row["task_no"]))
        publish_messages(ready)
        print(f"done. requeued={len(ready)}")


if __name__ == "__main__":
    main()
