# OCR 流程实现说明

## 目标

OCR 流程用于把用户上传的 PDF 或图片转换为可人工复核、可入库检索的知识文本。当前实现已经覆盖：

- Java 上传文件、创建 OCR 任务、发布第一阶段消息。
- Python 消费 RabbitMQ，完成文档标准化、OCR 识别、文本清洗和向量索引。
- Java 消费清洗结果，创建人工复核任务。
- 前端人工复核文本，支持修改、删除、合并段落。
- 复核确认后生成最终文本，发布向量索引消息。
- 向量写入 `knowledge_vector`，供知识库页面查询。

## 服务边界

### Java 服务

Java 负责业务入口、复核和页面接口：

- 接收 OCR 文件上传。
- 校验文件类型和大小。
- 将原始文件写入 MinIO。
- 创建 `ocr_task`。
- 发布 `ocr.document.normalize` 到 RabbitMQ。
- 消费 `finance.ocr.quality.validate`，读取 `cleaned.json`，创建 `ocr_review` 草稿。
- 提供 OCR 任务分页、软删除、人工复核详情、图片代理、草稿保存和确认提交接口。
- 人工复核确认后写入 `reviewed.json`，更新最终分段数，发布 `ocr.embedding.index`。
- 软删除任务时同步删除该任务对应的 `knowledge_vector`。

### Python 服务

Python 作为通用 RabbitMQ worker 运行，不绑定单一 OCR worker 名称。当前 OCR 处理职责：

- 消费 `finance.ocr.document.normalize`。
- 消费 `finance.ocr.recognize`。
- 消费 `finance.ocr.text.clean`。
- 消费 `finance.ocr.embedding.index`。
- 按阶段记录 `ocr_task_stage`。
- 阶段成功后写入 MinIO 产物，并发布下一阶段消息。
- 阶段失败时按 retry / DLQ 策略处理。
- 收到已软删除任务的消息时直接 ack 丢弃，不重试、不进死信。

## RabbitMQ

当前 OCR 使用 topic exchange：

| exchange | 类型 | 说明 |
| --- | --- | --- |
| `finance.ocr.topic` | topic | 正常阶段消息 |
| `finance.ocr.retry.topic` | topic | 重试消息 |
| `finance.ocr.dlx` | topic | 死信消息 |

阶段队列：

| 阶段 | routing key | queue | 消费方 |
| --- | --- | --- | --- |
| 文档标准化 | `ocr.document.normalize` | `finance.ocr.document.normalize` | Python |
| OCR 识别 | `ocr.recognize` | `finance.ocr.recognize` | Python |
| 文本清洗 | `ocr.text.clean` | `finance.ocr.text.clean` | Python |
| 质量校验 / 复核入口 | `ocr.quality.validate` | `finance.ocr.quality.validate` | Java |
| 向量索引 | `ocr.embedding.index` | `finance.ocr.embedding.index` | Python |

## 阶段流转

```text
Java 上传文件
 -> 创建 ocr_task，状态 ready，阶段 document.normalize
 -> 发布 ocr.document.normalize

Python document.normalize
 -> 读取原始文件
 -> PDF 按页转 PNG，图片统一转 PNG
 -> 页面图片写入 MinIO
 -> 更新页数
 -> 发布 ocr.recognize

Python ocr.recognize
 -> 读取页面图片
 -> 调用 qwen-vl-ocr-latest
 -> 写入 result.json
 -> 更新原始 OCR 分段数
 -> 发布 ocr.text.clean

Python text.clean
 -> 读取 result.json
 -> 清洗文本、生成 paragraphs、标记低置信度和疑似乱码
 -> 写入 cleaned.json
 -> 发布 ocr.quality.validate

Java quality.validate
 -> 读取 cleaned.json
 -> 创建或更新 ocr_review
 -> 标记任务 manual_review_required

人工复核
 -> 前端读取 cleaned 草稿和页面图片
 -> 用户修改、删除、合并段落
 -> 保存草稿或确认提交
 -> 确认提交后写 reviewed.json
 -> 用最终 paragraphs.length 更新 ocr_task.segment_count
 -> 发布 ocr.embedding.index

Python embedding.index
 -> 读取 reviewed.json
 -> 一个最终段落生成一个 chunk
 -> 生成 embedding
 -> 重建该 taskNo 的 knowledge_vector
 -> 写入 embedding_result.json
 -> 标记任务 finished
```

## 分段和 Chunk 规则

当前规则以人工复核结果为准：

- OCR 原始 `segments` 只作为来源信息。
- 文本清洗阶段把 OCR segment 转成初始 `paragraphs`。
- 人工复核可以删除、修改、合并段落。
- 复核提交时，最终 `paragraphs.length` 就是任务最终分段数。
- 向量索引时，一个最终 paragraph 生成一个 chunk。

示例：OCR 原来识别 10 条，人工复核合并成 2 条，则：

- `ocr_task.segment_count = 2`
- `knowledge_vector` 写入 2 条
- chunk 序号为 `1`、`2`

## 存储目录

所有阶段产物都按阶段和日期隔离，避免后续维护时混在同一目录。

| 阶段 | 产物 | 示例 objectKey |
| --- | --- | --- |
| 原始文件 | 上传文件 | `original/2026/05/25/ocr-xxx.pdf` |
| 文档标准化 | 页面图片 | `stage-1-output/2026/05/25/ocr-xxx/pages/page-001.png` |
| OCR 识别 | 原始识别结果 | `stage-2-output/2026/05/25/ocr-xxx/ocr/raw/result.json` |
| 文本清洗 | 清洗结果 | `stage-3-output/2026/05/25/ocr-xxx/text/clean/cleaned.json` |
| 人工复核 | 确认结果 | `stage-4-output/2026/05/25/ocr-xxx/review/reviewed.json` |
| 向量索引 | 索引结果 | `stage-5-output/2026/05/25/ocr-xxx/embedding/embedding_result.json` |

## 消息结构

### Java 发布文档标准化消息

```json
{
  "eventId": "uuid",
  "eventType": "ocr.document.normalize.requested",
  "taskNo": "ocr-xxx",
  "stage": "document.normalize",
  "storageType": "minio",
  "bucket": "finance-ocr",
  "objectKey": "original/2026/05/25/ocr-xxx.pdf",
  "originalFilename": "report.pdf",
  "contentType": "application/pdf",
  "fileSize": 123456,
  "createdAt": "2026-05-25T16:30:00"
}
```

### 文档标准化后发布 OCR 识别消息

文档标准化阶段不写 `manifest.json`。页面列表直接放进下一阶段 RabbitMQ 消息。

```json
{
  "eventId": "uuid",
  "taskNo": "ocr-xxx",
  "stage": "ocr.recognize",
  "attempt": 1,
  "sourceRef": {
    "storageType": "minio",
    "bucket": "finance-ocr",
    "objectKey": "original/2026/05/25/ocr-xxx.pdf"
  },
  "pages": [
    {
      "pageNo": 1,
      "imageRef": {
        "storageType": "minio",
        "bucket": "finance-ocr",
        "objectKey": "stage-1-output/2026/05/25/ocr-xxx/pages/page-001.png"
      },
      "width": 2480,
      "height": 3508,
      "dpi": 300,
      "rotation": 0
    }
  ],
  "pageCount": 1,
  "outputPrefix": {
    "storageType": "minio",
    "bucket": "finance-ocr",
    "objectKey": "stage-2-output/2026/05/25/ocr-xxx/ocr/raw/"
  },
  "createdAt": "2026-05-25T16:35:00"
}
```

### OCR 识别产物 result.json

```json
{
  "taskNo": "ocr-xxx",
  "engine": "qwen-vl-ocr-latest",
  "pageCount": 1,
  "pages": [
    {
      "pageNo": 1,
      "imageRef": {
        "storageType": "minio",
        "bucket": "finance-ocr",
        "objectKey": "stage-1-output/2026/05/25/ocr-xxx/pages/page-001.png"
      },
      "width": 2480,
      "height": 3508,
      "enabled": true,
      "segments": [
        {
          "segmentNo": 1,
          "confidence": 0.95,
          "content": "通讯股权投资的基础条件"
        }
      ],
      "rawContent": "{\"enabled\":true,\"segments\":[...]}",
      "usage": {
        "prompt_tokens": 8376,
        "completion_tokens": 597,
        "total_tokens": 8973
      }
    }
  ],
  "metrics": {
    "enabledPageCount": 1,
    "emptyPageCount": 0,
    "segmentCount": 1,
    "avgConfidence": 0.95,
    "usage": {
      "promptTokens": 8376,
      "completionTokens": 597,
      "totalTokens": 8973
    }
  },
  "createdAt": "2026-05-25T16:40:00"
}
```

### OCR 识别后发布文本清洗消息

```json
{
  "eventId": "uuid",
  "taskNo": "ocr-xxx",
  "stage": "text.clean",
  "attempt": 1,
  "inputRef": {
    "storageType": "minio",
    "bucket": "finance-ocr",
    "objectKey": "stage-2-output/2026/05/25/ocr-xxx/ocr/raw/result.json"
  },
  "pageCount": 1,
  "segmentCount": 1,
  "metrics": {
    "enabledPageCount": 1,
    "emptyPageCount": 0,
    "segmentCount": 1,
    "avgConfidence": 0.95
  },
  "outputPrefix": {
    "storageType": "minio",
    "bucket": "finance-ocr",
    "objectKey": "stage-3-output/2026/05/25/ocr-xxx/text/clean/"
  },
  "createdAt": "2026-05-25T16:41:00"
}
```

### 文本清洗产物 cleaned.json

```json
{
  "taskNo": "ocr-xxx",
  "paragraphCount": 1,
  "paragraphs": [
    {
      "paragraphNo": 1,
      "text": "通讯股权投资的基础条件",
      "sourcePages": [1],
      "sourceSegments": [
        {
          "pageNo": 1,
          "segmentNo": 1
        }
      ],
      "avgConfidence": 0.95,
      "warnings": []
    }
  ],
  "metrics": {
    "paragraphCount": 1,
    "emptySegmentCount": 0,
    "warningCount": 0,
    "lowConfidenceParagraphCount": 0,
    "avgConfidence": 0.95
  },
  "sourceRef": {
    "storageType": "minio",
    "bucket": "finance-ocr",
    "objectKey": "stage-2-output/2026/05/25/ocr-xxx/ocr/raw/result.json"
  },
  "createdAt": "2026-05-25T16:45:00"
}
```

### 文本清洗后发布质量校验消息

```json
{
  "eventId": "uuid",
  "taskNo": "ocr-xxx",
  "stage": "quality.validate",
  "attempt": 1,
  "inputRef": {
    "storageType": "minio",
    "bucket": "finance-ocr",
    "objectKey": "stage-3-output/2026/05/25/ocr-xxx/text/clean/cleaned.json"
  },
  "paragraphCount": 1,
  "metrics": {
    "paragraphCount": 1,
    "warningCount": 0,
    "avgConfidence": 0.95
  },
  "outputPrefix": {
    "storageType": "minio",
    "bucket": "finance-ocr",
    "objectKey": "stage-4-output/2026/05/25/ocr-xxx/quality/"
  },
  "createdAt": "2026-05-25T16:46:00"
}
```

### 人工复核确认产物 reviewed.json

```json
{
  "taskNo": "ocr-xxx",
  "reviewedAt": "2026-05-25T17:00:00",
  "content": {
    "taskNo": "ocr-xxx",
    "paragraphCount": 1,
    "paragraphs": [
      {
        "paragraphNo": 1,
        "text": "通讯股权投资的基础条件\n1. 选股能力\n2. 风险承受能力",
        "sourcePages": [1],
        "sourceSegments": [
          {
            "pageNo": 1,
            "segmentNo": 1
          },
          {
            "pageNo": 1,
            "segmentNo": 2
          }
        ],
        "avgConfidence": 0.95,
        "warnings": []
      }
    ]
  }
}
```

### Java 发布向量索引消息

```json
{
  "eventId": "uuid",
  "taskNo": "ocr-xxx",
  "stage": "embedding.index",
  "attempt": 1,
  "inputRef": {
    "storageType": "minio",
    "bucket": "finance-ocr",
    "objectKey": "stage-4-output/2026/05/25/ocr-xxx/review/reviewed.json"
  },
  "outputPrefix": {
    "storageType": "minio",
    "bucket": "finance-ocr",
    "objectKey": "stage-5-output/2026/05/25/ocr-xxx/embedding/"
  },
  "createdAt": "2026-05-25T17:01:00"
}
```

### 向量索引产物 embedding_result.json

```json
{
  "taskNo": "ocr-xxx",
  "stage": "embedding.index",
  "chunkCount": 1,
  "embeddingModel": "BAAI/bge-small-zh-v1.5",
  "finishedAt": "2026-05-25T17:02:00"
}
```

## 数据库表

### ocr_task

任务主表，记录整体状态。

关键字段：

- `task_no`：任务编号。
- `original_filename`：原文件名。
- `file_type`：文件类型。
- `status`：任务状态。
- `current_stage`：当前阶段。
- `progress`：进度百分比。
- `page_count`：页数。
- `segment_count`：分段数。人工复核提交后改为最终段落数。
- `deleted_at`：软删除时间。

状态：

- `ready`
- `running`
- `manual_review_required`
- `finished`
- `failed`

阶段：

- `document.normalize`
- `ocr.recognize`
- `text.clean`
- `quality.validate`
- `embedding.index`

### ocr_task_stage

阶段记录表，保存每个阶段的输入、输出、指标和错误。

关键字段：

- `task_no`
- `stage`
- `status`
- `attempt_count`
- `max_attempts`
- `input_message`
- `output_message`
- `input_ref`
- `output_ref`
- `metrics`
- `error_message`

### ocr_review

人工复核任务表。

关键字段：

- `task_no`
- `status`：`pending`、`saved`、`approved`、`rejected`
- `cleaned_ref`
- `reviewed_ref`
- `draft_content`
- `overall_confidence`
- `paragraph_count`
- `warning_count`

### knowledge_vector

知识库向量表。一个最终 paragraph 对应一条向量记录。

关键字段：

- `task_no`
- `chunk_index`
- `text`
- `embedding`
- `metadata`

向量索引使用重建策略：同一个 `task_no` 重新入库前先删除旧向量，再写入新向量。任务软删除时也会删除该 `task_no` 的向量。

## Java 接口

### OCR 任务

| 接口 | 说明 |
| --- | --- |
| `POST /api/ai/ocr/tasks` | 上传 OCR 文件，创建任务并发布第一阶段消息 |
| `POST /api/ai/ocr/tasks/page` | 分页查询任务，支持 `pageNum`、`pageSize`、`status` |
| `POST /api/ai/ocr/tasks/delete` | 软删除任务，并删除对应知识库向量 |
| `GET /api/knowledge/chunks?pageNum=1&pageSize=20` | 分页查询知识库 chunk |
| `GET /api/knowledge/stats` | 知识库统计 |
| `PUT /api/knowledge/chunks/{id}` | 编辑 chunk 文本 |

分页查询请求：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "status": "manual_review_required"
}
```

分页查询默认排除 `deleted_at is not null` 的任务。

### 人工复核

| 接口 | 说明 |
| --- | --- |
| `GET /api/ai/ocr/reviews/{taskNo}` | 查询复核详情 |
| `PUT /api/ai/ocr/reviews/{taskNo}/draft` | 保存草稿 |
| `POST /api/ai/ocr/reviews/{taskNo}/submit` | 确认提交 |
| `GET /api/ai/ocr/reviews/{taskNo}/pages/{pageNo}/image` | 页面图片代理 |

图片代理接口允许匿名访问，用于浏览器 `<img>` 直接加载。复核详情、草稿保存和确认提交仍需要正常认证。

### 知识库

| 接口 | 说明 |
| --- | --- |
| `GET /api/knowledge/stats` | 知识库统计 |
| `GET /api/knowledge/chunks?pageNum=1&pageSize=20` | 分页查询 chunk |
| `GET /api/knowledge/chunks/{id}` | 查询 chunk 详情 |
| `PUT /api/knowledge/chunks/{id}` | 编辑 chunk 文本，更新向量索引 |

知识库统计在数据库层聚合，不把全表数据查到 Java 内存里计算。

## Worker ACK / Retry / DLQ

Python worker 使用手动 ack。

成功：

```text
handler 成功
 -> 发布下一阶段消息
 -> ack 当前消息
```

已软删除任务：

```text
消费消息
 -> 查询 ocr_task.deleted_at
 -> 如果已软删除，直接 ack
 -> 不进入业务 handler
 -> 不进入 retry
 -> 不进入 DLQ
```

可重试失败：

```text
handler 抛出 RetryableMessageError 或普通异常未超过最大次数
 -> 记录阶段失败信息
 -> 发布到 retry exchange
 -> ack 当前消息
```

超过最大次数或永久失败：

```text
handler 抛出 PermanentMessageError 或达到最大尝试次数
 -> 记录阶段失败信息
 -> 标记任务 failed
 -> 发布到 DLQ
 -> ack 当前消息
```

重试边界在当前阶段，不从头开始。例如第三页 OCR 识别失败，重试的是 `ocr.recognize` 阶段消息，而不是重新上传文件或重新创建任务。

## 人工复核规则

人工复核是当前质量校验的必经步骤。

前端展示：

- 左侧或中间为可编辑段落。
- 每个段落保留 `avgConfidence` 和 warnings。
- 支持删除、修改、合并段落。
- PDF 多页时按页展示原始图片，段落通过 `sourcePages` 和 `sourceSegments` 追溯来源。

提交规则：

- 后端以提交时的完整 `paragraphs` 为准。
- `paragraphs.length` 写回 `ocr_task.segment_count`。
- `reviewed.json` 是后续向量索引的唯一输入。
- 后续知识库 chunk 数与最终段落数一致。

## 软删除规则

OCR 任务删除使用软删除：

- 更新 `ocr_task.deleted_at`。
- 分页查询默认排除软删除任务。
- Python 后续消费到该任务消息时直接 ack 丢弃。
- Java 删除任务时同步删除 `knowledge_vector` 中对应 `task_no` 的向量。
- MinIO 原始文件和阶段产物当前不删除，用于后续排查和审计。

## 当前 OCR 引擎

OCR 识别使用阿里云 DashScope OpenAI 兼容接口：

- model：`qwen-vl-ocr-latest`
- 图片通过 `data:image/png;base64,...` 传入。
- Prompt 要求只返回 JSON 对象。
- 返回格式：

```json
{
  "enabled": true,
  "segments": [
    {
      "confidence": 0.95,
      "content": "识别出的文本内容"
    }
  ]
}
```

如果图片没有可识别文本：

```json
{
  "enabled": false,
  "segments": []
}
```

## 当前限制

- OCR 质量依赖大模型识别结果，所有任务都需要人工复核后再入库。
- 文本清洗目前只做基础规范化、低置信度和疑似乱码标记。
- 向量 chunk 当前不做长度切分，按人工复核后的段落一段一个 chunk。
- MinIO 产物暂不随软删除清理。
