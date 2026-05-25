# OCR Pipeline Design

## Goal

The OCR pipeline starts after the Java service has stored the uploaded file and created an OCR task. Java owns upload, file metadata, task creation, and the first message dispatch. Python owns document normalization, OCR recognition, text cleanup, quality validation, and embedding indexing.

## Service Boundary

Java responsibilities:

- Receive the uploaded file.
- Store the original file through the file storage service.
- Create the `ocr_task` record.
- Mark the task as `ready`.
- Publish the first RabbitMQ message with routing key `ocr.document.normalize`.
- Expose task query, retry, manual review, and business-facing APIs.

Python responsibilities:

- Consume OCR stage messages from RabbitMQ.
- Read files and artifacts through storage references.
- Execute every OCR processing stage after upload.
- Persist stage inputs, outputs, metrics, and errors.
- Update task and stage status.
- Publish the next stage message after each successful stage.

## Pipeline Stages

The pipeline uses stage-level messages. Each stage has its own input, output, status, retry boundary, and routing key.

| Order | Stage | Routing key | Purpose | Main output |
| --- | --- | --- | --- | --- |
| 1 | Document normalization | `ocr.document.normalize` | Validate file format and convert PDF/images into a unified document format. | Standard document manifest |
| 2 | OCR recognition | `ocr.recognize` | Run OCR on normalized pages and produce structured recognition results. | Page/block/line OCR result |
| 3 | Text cleanup | `ocr.text.clean` | Normalize text, merge paragraphs, keep formatting hints, and mark suspected typos. | Cleaned text and paragraph model |
| 4 | Quality validation | `ocr.quality.validate` | Calculate quality metrics and decide whether manual review is required. | Validation report |
| 5 | Embedding indexing | `ocr.embedding.index` | Split approved text, generate embeddings, and write vectors to the vector store. | Vector index references |
| 6 | Task finished | No separate processing stage required | Mark the OCR task as finished after embedding succeeds. | Final task status |

Java does not publish `ocr.file.stored`. File storage is already complete before Java sends the first OCR message.

## Status Model

The task table should represent the overall lifecycle.

Recommended `ocr_task.status` values:

- `ready`: File is stored and the first stage message has been published.
- `running`: Python has started processing at least one pipeline stage.
- `manual_review_required`: Quality validation found that human review is needed.
- `finished`: Embedding indexing completed successfully.
- `failed`: A stage failed permanently or exceeded retry limits.

Recommended `ocr_task.current_stage` values:

- `document.normalize`
- `ocr.recognize`
- `text.clean`
- `quality.validate`
- `embedding.index`

Each stage should also have a separate stage record, for example `ocr_task_stage`.

Recommended stage fields:

- `task_no`
- `stage`
- `status`: `pending`, `running`, `finished`, `failed`
- `attempt_count`
- `max_attempts`
- `input_ref`
- `output_ref`
- `metrics`
- `error_message`
- `started_at`
- `finished_at`
- `updated_at`

## Message Contract

Messages should be lightweight. They should carry identifiers and storage references, not file bytes or large OCR payloads.

Initial message published by Java:

```json
{
  "eventId": "uuid",
  "taskNo": "ocr-xxx",
  "stage": "document.normalize",
  "storageType": "minio",
  "bucket": "ocr-scans",
  "objectKey": "2026/05/25/ocr-xxx.pdf",
  "contentType": "application/pdf",
  "fileSize": 123456,
  "createdAt": "2026-05-25T16:30:00"
}
```

Stage messages published by Python:

```json
{
  "eventId": "uuid",
  "taskNo": "ocr-xxx",
  "stage": "ocr.recognize",
  "attempt": 1,
  "inputRef": {
    "storageType": "minio",
    "bucket": "ocr-artifacts",
    "objectKey": "ocr-xxx/document/manifest.json"
  },
  "outputPrefix": {
    "storageType": "minio",
    "bucket": "ocr-artifacts",
    "objectKey": "ocr-xxx/ocr/raw/"
  },
  "createdAt": "2026-05-25T16:35:00"
}
```

## Artifact Flow

Every stage writes its result to storage before publishing the next message.

Document normalization output:

```json
{
  "taskNo": "ocr-xxx",
  "sourceType": "pdf",
  "pageCount": 12,
  "pages": [
    {
      "pageNo": 1,
      "imageRef": {
        "storageType": "minio",
        "bucket": "ocr-artifacts",
        "objectKey": "ocr-xxx/pages/page-001.png"
      },
      "width": 2480,
      "height": 3508,
      "dpi": 300,
      "rotation": 0
    }
  ]
}
```

OCR recognition output:

```json
{
  "taskNo": "ocr-xxx",
  "pages": [
    {
      "pageNo": 1,
      "blocks": [
        {
          "type": "text",
          "bbox": [100, 120, 800, 260],
          "confidence": 0.93,
          "lines": [
            {
              "text": "现金流折现模型",
              "confidence": 0.95,
              "bbox": [120, 140, 760, 180]
            }
          ]
        }
      ]
    }
  ]
}
```

Text cleanup output:

```json
{
  "taskNo": "ocr-xxx",
  "paragraphs": [
    {
      "paragraphNo": 1,
      "text": "现金流折现模型用于...",
      "sourcePages": [1],
      "warnings": [
        {
          "type": "possible_typo",
          "text": "现今流",
          "suggestion": "现金流"
        }
      ]
    }
  ]
}
```

Quality validation output:

```json
{
  "taskNo": "ocr-xxx",
  "qualityScore": 78,
  "manualReviewRequired": false,
  "metrics": {
    "avgConfidence": 0.89,
    "lowConfidenceRatio": 0.08,
    "blankPageRatio": 0,
    "suspectedTypoCount": 12,
    "garbledTextRatio": 0.02
  }
}
```

## Quality Decision

Recommended quality thresholds:

- `qualityScore >= 85`: Pass automatically and continue to embedding.
- `60 <= qualityScore < 85`: Continue to embedding, but mark the result as low confidence.
- `qualityScore < 60`: Stop the automatic pipeline and set task status to `manual_review_required`.

The score should consider:

- Average OCR confidence.
- Low-confidence text ratio.
- Blank page ratio.
- Garbled text ratio.
- Suspected typo count.
- Page count and page artifact completeness.
- Text density anomalies.
- Required structured field completeness, if the document type requires fields.

## Retry Boundary

Retries should happen at the failed stage, not from the beginning.

Examples:

- If `document.normalize` fails, retry `ocr.document.normalize`.
- If `ocr.recognize` fails, retry `ocr.recognize` using the normalized document manifest.
- If `ocr.text.clean` fails, retry `ocr.text.clean` using the OCR recognition output.
- If `ocr.quality.validate` fails, retry `ocr.quality.validate` using cleaned text.
- If `ocr.embedding.index` fails, retry `ocr.embedding.index` using the validated cleaned text.

RabbitMQ can use one topic exchange and stage-specific queues:

- Exchange: `finance.ocr.topic`
- Queues:
  - `finance.ocr.document.normalize`
  - `finance.ocr.recognize`
  - `finance.ocr.text.clean`
  - `finance.ocr.quality.validate`
  - `finance.ocr.embedding.index`

Recommended RabbitMQ features:

- Durable exchange and queues.
- Persistent messages.
- Manual ack.
- Worker `prefetch` set to `1` or `2`.
- Stage-specific retry queues.
- Stage-specific or unified dead-letter queue.
- Idempotency based on `taskNo` and `stage`.

## Worker Processing Rule

Each worker should follow the same processing pattern:

```text
consume message
 -> load task and stage records
 -> if stage is already finished, ack message
 -> validate whether this stage can run
 -> mark task running and stage running
 -> load input artifact
 -> execute stage logic
 -> save output artifact
 -> save metrics
 -> mark stage finished
 -> publish next stage message, if any
 -> ack current message
```

On failure:

```text
recoverable failure
 -> save error
 -> increase attempt count
 -> publish or route to retry queue
 -> ack or reject according to the retry strategy

permanent failure
 -> save error
 -> mark stage failed
 -> mark task failed
 -> route message to DLQ or ack after failure is persisted
```

## End-to-End Flow

```text
Java stores file
 -> Java creates ocr_task with status ready
 -> Java publishes ocr.document.normalize

Python document.normalize
 -> validates and standardizes the file
 -> publishes ocr.recognize

Python ocr.recognize
 -> runs OCR against normalized pages
 -> publishes ocr.text.clean

Python text.clean
 -> cleans text, normalizes format, and marks suspected typos
 -> publishes ocr.quality.validate

Python quality.validate
 -> computes quality score
 -> if manual review is required, marks task manual_review_required
 -> otherwise publishes ocr.embedding.index

Python embedding.index
 -> splits text
 -> generates embeddings
 -> writes vectors
 -> marks task finished
```
