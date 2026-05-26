# OCR 流水线设计

## 目标

OCR 流水线在 Java 服务保存上传文件并创建 OCR 任务之后启动。Java 负责文件上传、文件元数据、任务创建和第一条消息投递。Python 负责文档标准化、OCR 识别、文本清洗、质量校验和向量索引。

## 服务边界

Java 职责：

- 接收上传文件。
- 通过文件存储服务保存原始文件。
- 创建 `ocr_task` 记录。
- 将任务标记为 `ready`。
- 使用路由键 `ocr.document.normalize` 发布第一条 RabbitMQ 消息。
- 提供任务查询、重试、人工复核和面向业务的接口。

Python 职责：

- 消费 RabbitMQ 中的 OCR 阶段消息。
- 通过存储引用读取文件和中间产物。
- 执行上传之后的所有 OCR 处理阶段。
- 持久化每个阶段的输入、输出、指标和错误信息。
- 更新任务和阶段状态。
- 每个阶段成功后发布下一阶段消息。

## 流水线阶段

流水线使用阶段级消息。每个阶段都有独立的输入、输出、状态、重试边界和路由键。

| 顺序 | 阶段 | 路由键 | 目的 | 主要输出 |
| --- | --- | --- | --- | --- |
| 1 | 文档标准化 | `ocr.document.normalize` | 校验文件格式，并将 PDF/图片转换为统一的文档格式。 | 标准文档清单 |
| 2 | OCR 识别 | `ocr.recognize` | 对标准化后的页面执行 OCR，并产出结构化识别结果。 | 页面/块/行级 OCR 结果 |
| 3 | 文本清洗 | `ocr.text.clean` | 规范化文本、合并段落、保留格式提示，并标记疑似错字。 | 清洗后的文本和段落模型 |
| 4 | 质量校验 | `ocr.quality.validate` | 计算质量指标，并判断是否需要人工复核。 | 校验报告 |
| 5 | 向量索引 | `ocr.embedding.index` | 拆分已通过的文本、生成 embedding，并将向量写入向量存储。 | 向量索引引用 |
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

## 消息契约

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

每个阶段都先将大产物写入存储，再发布下一条消息。文档标准化阶段只将页面图片写入对象存储，页面列表直接放入下一阶段 RabbitMQ 消息体。

文档标准化输出：

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
        "objectKey": "stage-1-output/2026/05/25/ocr-xxx/pages/page-001.png"
      },
      "width": 2480,
      "height": 3508,
      "dpi": 300,
      "rotation": 0
    }
  ]
}
```

OCR 识别输出：

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

文本清洗输出：

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

Python quality.validate
 -> 计算质量分
 -> 如果需要人工复核，则将任务标记为 manual_review_required
 -> 否则发布 ocr.embedding.index

Python embedding.index
 -> 拆分文本
 -> 生成 embedding
 -> 写入向量
 -> 将任务标记为 finished
```
