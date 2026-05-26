# OCR 流水线设计

## 目标

OCR 流水线在 Java 服务保存上传文件并创建 OCR 任务之后启动。Java 负责文件上传、文件元数据、任务创建、第一条消息投递、人工复核和后续质量确认入口。Python 负责文档标准化、OCR 识别和文本清洗。

## 服务边界

Java 职责：

- 接收上传文件。
- 通过文件存储服务保存原始文件。
- 创建 `ocr_task` 记录。
- 将任务标记为 `ready`。
- 使用路由键 `ocr.document.normalize` 发布第一条 RabbitMQ 消息。
- 提供任务分页查询、软删除、人工复核和面向业务的接口。
- 消费 `ocr.quality.validate`，创建人工复核任务并将 `ocr_task.status` 标记为 `manual_review_required`。
- 人工复核确认后写入 `reviewed.json`，更新最终分段数，并发布 `ocr.embedding.index`。

Python 职责：

- 消费 RabbitMQ 中的 OCR 阶段消息。
- 通过存储引用读取文件和中间产物。
- 执行文档标准化、OCR 识别和文本清洗。
- 持久化每个阶段的输入、输出、指标和错误信息。
- 更新任务和阶段状态。
- 每个阶段成功后发布下一阶段消息。
- 消费到已软删除任务时直接 ack 丢弃，不进入 handler，不进入重试或死信队列。

## 流水线阶段

流水线使用阶段级消息。每个阶段都有独立的输入、输出、状态、重试边界和路由键。

| 顺序 | 阶段 | 路由键 | 目的 | 主要输出 |
| --- | --- | --- | --- | --- |
| 1 | 文档标准化 | `ocr.document.normalize` | 校验文件格式，并将 PDF/图片转换为统一的文档格式。 | 标准文档清单 |
| 2 | OCR 识别 | `ocr.recognize` | 对标准化后的页面执行 OCR，并产出结构化识别结果。 | 页面/块/行级 OCR 结果 |
| 3 | 文本清洗 | `ocr.text.clean` | 规范化文本、合并段落、保留格式提示，并标记疑似错字。 | 清洗后的文本和段落模型 |
| 4 | 质量校验 / 人工复核入口 | `ocr.quality.validate` | Java 消费清洗结果，创建人工复核任务。 | `ocr_review` 草稿 |
| 5 | 向量索引 | `ocr.embedding.index` | 人工复核确认后，拆分已确认文本、生成 embedding，并将向量写入向量存储。 | 向量索引引用 |
| 6 | 任务完成 | 不需要单独处理阶段 | embedding 成功后将 OCR 任务标记为完成。 | 最终任务状态 |

Java 不发布 `ocr.file.stored`。Java 发送第一条 OCR 消息之前，文件存储已经完成。

## 状态模型

任务表应该表示整体生命周期。

建议的 `ocr_task.status` 取值：

- `ready`：文件已存储，第一阶段消息已发布。
- `running`：Python 已开始处理至少一个流水线阶段。
- `manual_review_required`：质量校验发现需要人工复核。
- `finished`：向量索引已成功完成。
- `failed`：某个阶段永久失败或超过重试次数。

`ocr_task.deleted_at` 为软删除时间。默认任务列表只展示 `deleted_at is null` 的任务。软删除不改变原始业务状态，便于保留任务删除前处于 `running`、`failed` 或 `manual_review_required` 的信息。

建议的 `ocr_task.current_stage` 取值：

- `document.normalize`
- `ocr.recognize`
- `text.clean`
- `quality.validate`
- `embedding.index`

每个阶段也应该有独立的阶段记录，例如 `ocr_task_stage`。

建议的阶段字段：

- `task_no`
- `stage`
- `status`：`pending`、`running`、`finished`、`failed`
- `attempt_count`
- `max_attempts`
- `input_ref`
- `output_ref`
- `metrics`
- `error_message`
- `started_at`
- `finished_at`
- `updated_at`

## Java 接口

任务分页查询使用 POST 请求，后续筛选条件扩展都放在请求体中：

```http
POST /api/ai/ocr/tasks/page
```

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "status": "manual_review_required"
}
```

返回结构：

```json
{
  "records": [],
  "total": 0,
  "pageNum": 1,
  "pageSize": 20,
  "pages": 0
}
```

软删除接口：

```http
POST /api/ai/ocr/tasks/delete
```

```json
{
  "taskNo": "ocr-xxx"
}
```

复核页面图片代理接口：

```http
GET /api/ai/ocr/reviews/{taskNo}/pages/{pageNo}/image
```

该接口允许匿名访问，用于浏览器 `<img>` 直接加载图片。复核详情、保存草稿和确认提交接口仍需要认证。

## 消息结构

消息应该保持轻量，只携带标识和存储引用，不携带文件字节或大型 OCR 载荷。

Java 发布的初始消息：

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

Python 发布的阶段消息：

```json
{
  "eventId": "uuid",
  "taskNo": "ocr-xxx",
  "stage": "ocr.recognize",
  "attempt": 1,
  "sourceRef": {
    "storageType": "minio",
    "bucket": "ocr-scans",
    "objectKey": "2026/05/25/ocr-xxx.pdf"
  },
  "pageCount": 12,
  "pages": [
    {
      "pageNo": 1,
      "imageRef": {
        "storageType": "minio",
        "bucket": "ocr-scans",
        "objectKey": "stage-1-output/2026/05/25/ocr-xxx/pages/page-001.png"
      },
      "width": 2480,
      "height": 3508,
      "dpi": 300,
      "rotation": 0
    }
  ],
  "outputPrefix": {
    "storageType": "minio",
    "bucket": "ocr-artifacts",
    "objectKey": "stage-2-output/2026/05/25/ocr-xxx/ocr/raw/"
  },
  "createdAt": "2026-05-25T16:35:00"
}
```

## 产物流转

每个阶段都先将大产物写入存储，再发布下一条消息。文档标准化阶段只将页面图片写入对象存储，不单独写 `manifest.json`。页面列表直接放入下一阶段 `ocr.recognize` RabbitMQ 消息体。

文档标准化后的下一阶段消息体：

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
  "pageCount": 12,
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
  "outputPrefix": {
    "storageType": "minio",
    "bucket": "finance-ocr",
    "objectKey": "stage-2-output/2026/05/25/ocr-xxx/ocr/raw/"
  },
  "createdAt": "2026-05-25T16:35:00"
}
```

OCR 识别输出：

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
          "content": "现金流折现模型"
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

文本清洗输出：

```json
{
  "taskNo": "ocr-xxx",
  "paragraphCount": 1,
  "paragraphs": [
    {
      "paragraphNo": 1,
      "text": "现金流折现模型用于...",
      "sourcePages": [1],
      "sourceSegments": [
        {
          "pageNo": 1,
          "segmentNo": 1
        }
      ],
      "avgConfidence": 0.95,
      "warnings": [
        {
          "type": "low_confidence",
          "confidence": 0.62
        }
      ]
    }
  ],
  "metrics": {
    "paragraphCount": 1,
    "emptySegmentCount": 0,
    "warningCount": 1,
    "lowConfidenceParagraphCount": 1,
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

质量校验输出：

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

人工复核确认后输出 `reviewed.json`。前端可删除、修改、合并、调整段落，后端提交时以最终 `paragraphs.length` 更新 `ocr_task.segment_count`，因此处理队列中的“分段”显示人工确认后的最终段落数，而不是 OCR 原始分段数。

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
        "text": "通讯股权投资的基础条件\n1. 选股能力\n2. 风险承受...",
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

## 质量决策

建议的质量阈值：

- `qualityScore >= 85`：自动通过，并继续向量索引。
- `60 <= qualityScore < 85`：继续向量索引，但将结果标记为低置信度。
- `qualityScore < 60`：停止自动流水线，并将任务状态设置为 `manual_review_required`。

评分应考虑：

- OCR 平均置信度。
- 低置信度文本比例。
- 空白页比例。
- 乱码文本比例。
- 疑似错字数量。
- 页数和页面产物完整性。
- 文本密度异常。
- 如果文档类型要求结构化字段，则需要考虑必填结构化字段完整性。

## 重试边界

重试应该发生在失败阶段，而不是从头开始。

示例：

- 如果 `document.normalize` 失败，重试 `ocr.document.normalize`。
- 如果 `ocr.recognize` 失败，使用标准化文档清单重试 `ocr.recognize`。
- 如果 `ocr.text.clean` 失败，使用 OCR 识别输出重试 `ocr.text.clean`。
- 如果 `ocr.quality.validate` 失败，使用清洗后的文本重试 `ocr.quality.validate`。
- 如果 `ocr.embedding.index` 失败，使用已校验的清洗文本重试 `ocr.embedding.index`。

RabbitMQ 可以使用一个 topic exchange 和按阶段划分的队列：

- Exchange：`finance.ocr.topic`
- 队列：
  - `finance.ocr.document.normalize`
  - `finance.ocr.recognize`
  - `finance.ocr.text.clean`
  - `finance.ocr.quality.validate`
  - `finance.ocr.embedding.index`

建议的 RabbitMQ 特性：

- 持久化 exchange 和队列。
- 持久化消息。
- 手动 ack。
- Worker `prefetch` 设置为 `1` 或 `2`。
- 按阶段划分的重试队列。
- 按阶段划分或统一的死信队列。
- 基于 `taskNo` 和 `stage` 实现幂等。

## Worker 处理规则

每个 worker 应遵循相同的处理模式：

```text
消费消息
 -> 如果任务已软删除，则 ack 并结束
 -> 加载任务和阶段记录
 -> 如果阶段已经完成，则 ack 消息
 -> 校验当前阶段是否可以运行
 -> 将任务标记为 running，并将阶段标记为 running
 -> 加载输入产物
 -> 执行阶段逻辑
 -> 保存输出产物
 -> 保存指标
 -> 将阶段标记为 finished
 -> 如果存在下一阶段，则发布下一阶段消息
 -> ack 当前消息
```

失败时：

```text
可恢复失败
 -> 保存错误
 -> 增加尝试次数
 -> 发布或路由到重试队列
 -> 根据重试策略 ack 或 reject

永久失败
 -> 保存错误
 -> 将阶段标记为 failed
 -> 将任务标记为 failed
 -> 将消息路由到 DLQ，或在失败信息持久化后 ack
```

## 端到端流程

```text
Java 保存文件
 -> Java 创建状态为 ready 的 ocr_task
 -> Java 发布 ocr.document.normalize

Python document.normalize
 -> 校验并标准化文件
 -> 发布 ocr.recognize

Python ocr.recognize
 -> 对标准化后的页面执行 OCR
 -> 发布 ocr.text.clean

Python text.clean
 -> 清洗文本、规范化格式，并标记疑似错字
 -> 发布 ocr.quality.validate

Java quality.validate
 -> 读取 cleaned.json
 -> 创建或更新 ocr_review
 -> 将任务标记为 manual_review_required

人工复核
 -> 前端修改、删除、合并段落
 -> Java 保存草稿或确认提交
 -> 确认提交后写 reviewed.json
 -> 将 ocr_task.segment_count 更新为最终 paragraphs 数量
 -> 发布 ocr.embedding.index

Java embedding.index
 -> 拆分文本
 -> 生成 embedding
 -> 写入向量
 -> 将任务标记为 finished
```
