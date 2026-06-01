# OCR 流程实现说明

## 目标

OCR 流程用于把用户上传的 PDF 或图片转换为可人工复核、可入库检索的知识文本。当前实现已经覆盖：

- Java 上传文件、创建 OCR 任务、发布第一阶段消息。
- 手动知识导入复用 `ocr_task` 和 `ocr_review`，通过 `source_type=manual_text` 与文件 OCR 任务隔离。
- Python 消费 RabbitMQ，完成文档标准化、OCR 识别、文本清洗、chunk 打标签、标签回正和向量索引。
- Java 消费清洗结果，创建人工复核任务。
- 前端人工复核文本，支持修改、删除、合并段落。
- 复核确认后生成最终文本，发布规则标签消息。
- chunk 标签经过规则标签、可选 LLM 标签、标签回正后写入 `metadata.scenes`。
- 向量写入 `knowledge_vector`，供知识库页面查询和报告动态检索使用。

## 服务边界

### Java 服务

Java 负责业务入口、复核和页面接口：

- 接收 OCR 文件上传。
- 接收手动文本知识导入，创建 `manual_text` 来源任务。
- 校验文件类型和大小。
- 将原始文件写入 MinIO。
- 创建 `ocr_task`。
- 发布 `ocr.document.normalize` 到 RabbitMQ。
- 消费 `finance.ocr.quality.validate`，读取 `cleaned.json`，创建 `ocr_review` 草稿。
- 提供 OCR 任务分页、软删除、人工复核详情、图片代理、草稿保存和确认提交接口。
- 提供手动知识导入分页、草稿保存、确认提交和软删除接口。
- 提供知识库分页、详情、统计、概览、标签过滤、文本编辑和场景标签编辑接口。
- 人工复核确认后写入 `reviewed.json`，更新最终分段数，发布 `ocr.chunk.tag.rule`。
- 软删除任务时同步删除该任务对应的 `knowledge_vector`。

### Python 服务

Python 作为通用 RabbitMQ worker 运行，不绑定单一 OCR worker 名称。当前 OCR 处理职责：

- 消费 `finance.ocr.document.normalize`。
- 消费 `finance.ocr.recognize`。
- 消费 `finance.ocr.text.clean`。
- 消费 `finance.ocr.chunk.tag.rule`。
- 消费 `finance.ocr.chunk.tag.llm`。
- 消费 `finance.ocr.chunk.tag.correct`。
- 消费 `finance.ocr.embedding.index`。
- 消费 `finance.knowledge.chunk.reembed`。
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
| 规则标签 | `ocr.chunk.tag.rule` | `finance.ocr.chunk.tag.rule` | Python |
| LLM 标签 | `ocr.chunk.tag.llm` | `finance.ocr.chunk.tag.llm` | Python |
| 标签回正 | `ocr.chunk.tag.correct` | `finance.ocr.chunk.tag.correct` | Python |
| 向量索引 | `ocr.embedding.index` | `finance.ocr.embedding.index` | Python |
| 单 chunk 重嵌入 | `knowledge.chunk.reembed` | `finance.knowledge.chunk.reembed` | Python |

重试队列由 `finance.ocr.retry.topic` 投递，routing key 统一在原 routing key 后追加 `.retry`。每个 retry 队列设置 `x-message-ttl=30000`，TTL 到期后通过 DLX 回到 `finance.ocr.topic` 的原业务 routing key。

| 业务 routing key | retry routing key | retry queue |
| --- | --- | --- |
| `ocr.document.normalize` | `ocr.document.normalize.retry` | `finance.ocr.document.normalize.retry` |
| `ocr.recognize` | `ocr.recognize.retry` | `finance.ocr.recognize.retry` |
| `ocr.text.clean` | `ocr.text.clean.retry` | `finance.ocr.text.clean.retry` |
| `ocr.quality.validate` | `ocr.quality.validate.retry` | `finance.ocr.quality.validate.retry` |
| `ocr.chunk.tag.rule` | `ocr.chunk.tag.rule.retry` | `finance.ocr.chunk.tag.rule.retry` |
| `ocr.chunk.tag.llm` | `ocr.chunk.tag.llm.retry` | `finance.ocr.chunk.tag.llm.retry` |
| `ocr.chunk.tag.correct` | `ocr.chunk.tag.correct.retry` | `finance.ocr.chunk.tag.correct.retry` |
| `ocr.embedding.index` | `ocr.embedding.index.retry` | `finance.ocr.embedding.index.retry` |

所有业务队列配置 `x-dead-letter-exchange=finance.ocr.dlx`。业务队列 reject 且 `requeue=false` 后，消息进入 `finance.ocr.dlx`，最终由 `#` 绑定进入 `finance.ocr.dlq`。

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
 -> 根据源文件类型选择识别策略
 -> 图片源文件读取页面图片，调用 qwen-vl-ocr-latest
 -> PDF 源文件读取原始 PDF，调用 OpenDataLoader 解析、清洗并切 chunk
 -> PDF chunk 适配为标准 OCR segments
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
 -> 发布 ocr.chunk.tag.rule

Python chunk.tag.rule
 -> 读取 reviewed.json
 -> 一个最终段落生成一个 chunk
 -> 生成 ruleScenes 和 ruleScenesWithConfidence
 -> 通过 RuleTagQualityGate 判断是否需要 LLM
 -> 写入 rule_tag_result.json
 -> 逐 chunk 发布 ocr.chunk.tag.llm 或 ocr.chunk.tag.correct

Python chunk.tag.llm
 -> 对规则标签质量不足的 chunk 调用 LLM 标签
 -> LLM 只能从 7 大类标签白名单选择标签
 -> 发布 ocr.chunk.tag.correct

Python chunk.tag.correct
 -> 合并 LLM 标签和高置信规则标签
 -> 删除非白名单标签、去重、补齐 7 大类空数组
 -> 聚合同一 taskNo 的全部 chunk
 -> 写入 tagged_reviewed.json
 -> 发布 ocr.embedding.index

Python embedding.index
 -> 读取 tagged_reviewed.json
 -> 生成 embedding
 -> 重建该 taskNo 的 knowledge_vector
 -> 写入 embedding_result.json
 -> 标记任务 finished
```

## 分段和 Chunk 规则

当前规则以人工复核结果为准：

- OCR 原始 `segments` 只作为来源信息。
- 图片源文件的 `segments` 来自视觉 OCR 识别结果。
- PDF 源文件的 `segments` 来自 OpenDataLoader 解析后的 chunk，每个 chunk 适配为一个 segment，并使用 OpenDataLoader 元素的 `page number` 关联到对应页面。
- 手动知识导入的每个文本框会保存为一个 paragraph/chunk，页码和来源 OCR segment 为空，置信度为 `1`。
- 文本清洗阶段把 OCR segment 转成初始 `paragraphs`。
- 人工复核可以删除、修改、合并段落。
- 复核提交时，最终 `paragraphs.length` 就是任务最终分段数。
- 规则标签阶段，一个最终 paragraph 生成一个 chunk。
- 向量索引阶段，使用标签回正后的 `tagged_reviewed.json` 生成 embedding 和写入向量库。

示例：OCR 原来识别 10 条，人工复核合并成 2 条，则：

- `ocr_task.segment_count = 2`
- `knowledge_vector` 写入 2 条
- chunk 序号为 `1`、`2`

## Chunk 打标签和入库规则

复核确认后的文本不直接进入向量索引。系统先按 `docs/chunk入库打标签文档.md` 和 `docs/REPORT_PIPELINE.md` 第三节定义的规则生成场景标签，最后把标签写入 `knowledge_vector.metadata.scenes`。

### 标签结构

每个 chunk 的最终 metadata 必须补齐 7 大类：

```json
{
  "scenes": {
    "asset": [],
    "price": [],
    "volume": [],
    "trend": [],
    "valuation": [],
    "sentiment": [],
    "risk_strategy": []
  },
  "keywords": [],
  "summary": ""
}
```

7 大类含义：

| 大类 | 说明 |
| --- | --- |
| `asset` | 资产类型，例如通用经验、股票、指数、可转债、基金、银行股、低价股等 |
| `price` | 价格位置，例如上涨、下跌、横盘、接近高位、突破、回调等 |
| `volume` | 成交量 / 换手，例如放量、缩量、高换手、量价配合、量价背离等 |
| `trend` | 趋势结构，例如上升趋势、下降趋势、区间震荡、反弹、趋势反转等 |
| `valuation` | 估值 / 基本面，例如低 PE、低 PB、高股息、估值陷阱、基本面风险等 |
| `sentiment` | 情绪 / 异动，例如短线情绪、消息驱动、板块轮动、机构行为等 |
| `risk_strategy` | 风险 / 策略，例如追高风险、仓位控制、等待确认、止盈止损等 |

标签生成不是判断这段话表面出现了哪些词，而是判断这段知识以后适合在哪些投资场景下被检索出来。不相关的大类保持空数组，不为了填满 7 类强行打标签。

### 标签阶段

```text
reviewed.json
  ↓
chunk.tag.rule
  ↓
RuleTagQualityGate
  ├─ 规则标签足够可靠：chunk.tag.correct
  └─ 规则标签质量不足：chunk.tag.llm -> chunk.tag.correct
  ↓
tagged_reviewed.json
  ↓
embedding.index
```

`chunk.tag.rule` 输出 `ruleScenes` 和 `ruleScenesWithConfidence`。规则标签质量门第一版使用：

```text
1. 覆盖率：至少命中 2 个非空大类。
2. 置信度：所有命中大类的最高置信度不低于 0.75。
```

不满足条件的 chunk 才进入 `chunk.tag.llm`。LLM 只能从标签白名单选择标签，不能创造新标签。

`chunk.tag.correct` 是标签进入向量库前的统一出口：

```text
1. 合并 LLM 标签和高置信规则标签。
2. 删除不在白名单中的标签。
3. 去重。
4. 补齐 7 大类空数组。
5. 生成最终 metadata.scenes。
6. 如果 7 大类全部为空，标记 metadata.deleted=true。
```

最终检索只使用 `metadata.scenes`，不直接使用 `ruleScenes` 或 `llmScenes`。`metadata.deleted=true` 的 chunk 会保留在 `tagged_reviewed.json` 中用于排查，但不会进入 embedding 和 `knowledge_vector`。

## 存储目录

所有阶段产物都按阶段和日期隔离，避免后续维护时混在同一目录。

| 阶段 | 产物 | 示例 objectKey |
| --- | --- | --- |
| 原始文件 | 上传文件 | `original/2026/05/25/ocr-xxx.pdf` |
| 文档标准化 | 页面图片 | `stage-1-output/2026/05/25/ocr-xxx/pages/page-001.png` |
| OCR 识别 | 原始识别结果 | `stage-2-output/2026/05/25/ocr-xxx/ocr/raw/result.json` |
| 文本清洗 | 清洗结果 | `stage-3-output/2026/05/25/ocr-xxx/text/clean/cleaned.json` |
| 人工复核 | 确认结果 | `stage-4-output/2026/05/25/ocr-xxx/review/reviewed.json` |
| 规则标签 | 规则标签结果 | `stage-5-output/2026/05/25/ocr-xxx/chunk-tag/rule_tag_result.json` |
| 标签回正 | 带标签复核结果 | `stage-5-output/2026/05/25/ocr-xxx/chunk-tag/tagged_reviewed.json` |
| 向量索引 | 索引结果 | `stage-5-output/2026/05/25/ocr-xxx/chunk-tag/embedding/embedding_result.json` |

PDF 识别阶段的 OpenDataLoader 原始 JSON、Markdown、临时 PDF 等只作为进程内临时文件使用，处理结束后删除，不作为 MinIO 阶段产物保存。系统只保留标准 `result.json`。

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

`ocr.recognize` 的输出结构固定为 `pages[].segments[]`。图片源文件使用 `qwen-vl-ocr-latest` 生成 segment；PDF 源文件使用 OpenDataLoader 直接解析原始 PDF，并把内部 chunk 按 OpenDataLoader `page number` 适配到对应页面的 segment。后续 `text.clean` 不区分来源，统一把 segment 转成 paragraph。

图片源文件示例：

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

PDF 源文件示例：

```json
{
  "taskNo": "ocr-xxx",
  "engine": "opendataloader_pdf",
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
          "confidence": 1.0,
          "content": "OpenDataLoader 已清洗并切好的 chunk 文本"
        }
      ],
      "rawContent": "",
      "usage": {}
    }
  ],
  "metrics": {
    "enabledPageCount": 1,
    "emptyPageCount": 0,
    "segmentCount": 1,
    "avgConfidence": 1.0,
    "usage": {
      "promptTokens": 0,
      "completionTokens": 0,
      "totalTokens": 0
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

### Java 发布规则标签消息

```json
{
  "eventId": "uuid",
  "taskNo": "ocr-xxx",
  "stage": "chunk.tag.rule",
  "attempt": 1,
  "inputRef": {
    "storageType": "minio",
    "bucket": "finance-ocr",
    "objectKey": "stage-4-output/2026/05/25/ocr-xxx/review/reviewed.json"
  },
  "outputPrefix": {
    "storageType": "minio",
    "bucket": "finance-ocr",
    "objectKey": "stage-5-output/2026/05/25/ocr-xxx/chunk-tag/"
  },
  "createdAt": "2026-05-25T17:01:00"
}
```

### 规则标签产物 rule_tag_result.json

```json
{
  "taskNo": "ocr-xxx",
  "stage": "chunk.tag.rule",
  "inputRef": {
    "storageType": "minio",
    "bucket": "finance-ocr",
    "objectKey": "stage-4-output/2026/05/25/ocr-xxx/review/reviewed.json"
  },
  "finishedAt": "2026-05-25T17:01:20",
  "result": {
    "taskNo": "ocr-xxx",
    "tagVersion": "rule-v1.1",
    "confidenceThreshold": 0.75,
    "minCoveredCategories": 2,
    "chunkCount": 1,
    "totalChunkCount": 1,
    "needLlm": false,
    "llmChunkCount": 0,
    "ruleOnlyChunkCount": 1,
    "chunks": [
      {
        "chunkId": "ocr-xxx:chunk:0001",
        "chunkIndex": 1,
        "text": "低价股突然放量上涨时，需要观察换手率和第二天是否站稳。",
        "pageNos": [1],
        "paragraphNos": [1],
        "ruleScenes": {
          "asset": ["stock", "low_price_stock"],
          "price": ["price_rise"],
          "volume": ["volume_expand", "high_turnover"],
          "trend": [],
          "valuation": [],
          "sentiment": [],
          "risk_strategy": ["wait_confirm", "observe_next_day"]
        },
        "ruleScenesWithConfidence": {
          "asset": {
            "stock": 0.86,
            "low_price_stock": 0.88
          },
          "price": {
            "price_rise": 0.78
          },
          "volume": {
            "volume_expand": 0.75,
            "high_turnover": 0.78
          },
          "trend": {},
          "valuation": {},
          "sentiment": {},
          "risk_strategy": {
            "wait_confirm": 0.82,
            "observe_next_day": 0.82
          }
        },
        "qualityGate": {
          "needLlm": false,
          "reason": "RULE_TAGS_CONFIDENT_ENOUGH",
          "coveredCategoryCount": 4,
          "confidenceThreshold": 0.75,
          "lowConfidenceCategories": []
        }
      }
    ]
  }
}
```

### 规则标签后发布单 chunk 标签消息

规则标签阶段会按 chunk 粒度发布后续消息：

- `qualityGate.needLlm = true`：发布到 `ocr.chunk.tag.llm`。
- `qualityGate.needLlm = false`：发布到 `ocr.chunk.tag.correct`。

```json
{
  "eventId": "uuid",
  "taskNo": "ocr-xxx",
  "stage": "chunk.tag.correct",
  "attempt": 1,
  "tagPipeline": {
    "version": "v1.0",
    "sourceStage": "chunk.tag.rule",
    "targetStage": "chunk.tag.correct"
  },
  "taskChunkSummary": {
    "totalChunkCount": 1,
    "llmChunkCount": 0,
    "ruleOnlyChunkCount": 1
  },
  "outputPrefix": {
    "storageType": "minio",
    "bucket": "finance-ocr",
    "objectKey": "stage-5-output/2026/05/25/ocr-xxx/chunk-tag/"
  },
  "chunk": {
    "chunkId": "ocr-xxx:chunk:0001",
    "chunkIndex": 1,
    "text": "低价股突然放量上涨时，需要观察换手率和第二天是否站稳。",
    "pageNos": [1],
    "paragraphNos": [1]
  },
  "ruleTagging": {
    "ruleScenes": {},
    "ruleScenesWithConfidence": {},
    "qualityGate": {},
    "tagVersion": "rule-v1.1"
  },
  "createdAt": "2026-05-25T17:01:30"
}
```

### LLM 标签后发布标签回正消息

只有规则标签质量不足的 chunk 会进入该阶段。

```json
{
  "eventId": "uuid",
  "taskNo": "ocr-xxx",
  "stage": "chunk.tag.correct",
  "attempt": 1,
  "tagPipeline": {
    "version": "v1.0",
    "sourceStage": "chunk.tag.llm",
    "targetStage": "chunk.tag.correct"
  },
  "chunk": {
    "chunkId": "ocr-xxx:chunk:0001",
    "chunkIndex": 1,
    "text": "低价股突然放量上涨时，需要观察换手率和第二天是否站稳。"
  },
  "ruleTagging": {
    "ruleScenes": {},
    "ruleScenesWithConfidence": {},
    "qualityGate": {}
  },
  "llmTagging": {
    "llmScenes": {
      "asset": ["stock", "low_price_stock"],
      "price": ["price_rise"],
      "volume": ["volume_expand", "high_turnover"],
      "trend": ["breakout_from_range"],
      "valuation": [],
      "sentiment": ["short_term_emotion"],
      "risk_strategy": ["chase_high_risk", "wait_confirm", "observe_next_day"]
    },
    "model": "deepseek-chat",
    "usage": {}
  },
  "createdAt": "2026-05-25T17:01:40"
}
```

### 标签回正产物 tagged_reviewed.json

`chunk.tag.correct` 会等同一 `taskNo` 下所有 chunk 都完成回正后，再写入一个聚合后的 `tagged_reviewed.json`。

```json
{
  "taskNo": "ocr-xxx",
  "stage": "chunk.tag.correct",
  "taggedAt": "2026-05-25T17:01:50",
  "content": {
    "paragraphCount": 1,
    "paragraphs": [
      {
        "paragraphNo": 1,
        "text": "低价股突然放量上涨时，需要观察换手率和第二天是否站稳。",
        "sourcePages": [1],
        "avgConfidence": 1,
        "warnings": [],
        "metadata": {
          "scenes": {
            "asset": ["stock", "low_price_stock"],
            "price": ["price_rise"],
            "volume": ["volume_expand", "high_turnover"],
            "trend": ["breakout_from_range"],
            "valuation": [],
            "sentiment": ["short_term_emotion"],
            "risk_strategy": ["chase_high_risk", "wait_confirm", "observe_next_day"]
          },
          "keywords": [],
          "summary": "",
          "tagging": {
            "ruleTagging": {},
            "llmTagging": {}
          }
        }
      }
    ]
  }
}
```

### 标签回正后发布向量索引消息

```json
{
  "eventId": "uuid",
  "taskNo": "ocr-xxx",
  "stage": "embedding.index",
  "attempt": 1,
  "inputRef": {
    "storageType": "minio",
    "bucket": "finance-ocr",
    "objectKey": "stage-5-output/2026/05/25/ocr-xxx/chunk-tag/tagged_reviewed.json"
  },
  "outputPrefix": {
    "storageType": "minio",
    "bucket": "finance-ocr",
    "objectKey": "stage-5-output/2026/05/25/ocr-xxx/chunk-tag/embedding"
  },
  "createdAt": "2026-05-25T17:01:55"
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
- `chunk.tag.rule`
- `chunk.tag.llm`
- `chunk.tag.correct`
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

知识库向量表。一个最终 paragraph 对应一条向量记录，正式检索使用标签回正后的 `metadata.scenes`。

关键字段：

- `task_no`
- `chunk_index`
- `text`
- `embedding`
- `metadata`：包含 `taskNo`、`chunkId`、`pageNos`、`paragraphNos`、`sourceType`、`scenes`、`keywords`、`summary`、`tagging` 等。

向量索引使用重建策略：同一个 `task_no` 重新入库前先删除旧向量，再写入新向量。任务软删除时也会删除该 `task_no` 的向量。

## Java 接口

### OCR 任务

| 接口 | 说明 |
| --- | --- |
| `POST /api/ai/ocr/tasks` | 上传 OCR 文件，创建任务并发布第一阶段消息 |
| `POST /api/ai/ocr/tasks/page` | 分页查询任务，支持 `pageNum`、`pageSize`、`status` |
| `POST /api/ai/ocr/tasks/delete` | 软删除任务，并删除对应知识库向量 |
| `GET /api/ai/ocr/tasks/{taskNo}/stages` | 查询任务级阶段明细 |
| `GET /api/ai/ocr/tasks/{taskNo}/chunk-tags` | 查询 chunk 粒度场景打标明细 |

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

### 手动知识导入

| 接口 | 说明 |
| --- | --- |
| `POST /api/ai/manual-knowledge/tasks/page` | 分页查询手动知识任务 |
| `POST /api/ai/manual-knowledge/tasks` | 创建手动知识草稿 |
| `GET /api/ai/manual-knowledge/tasks/{taskNo}` | 查询手动知识草稿详情 |
| `PUT /api/ai/manual-knowledge/tasks/{taskNo}/draft` | 保存手动知识草稿 |
| `POST /api/ai/manual-knowledge/tasks/{taskNo}/submit` | 提交手动知识并进入场景打标 |
| `POST /api/ai/manual-knowledge/tasks/delete` | 软删除手动知识任务，并删除对应知识库向量 |

手动知识任务只查询 `ocr_task.source_type = 'manual_text'` 的数据，不出现在文件 OCR 任务列表中。

### 知识库

| 接口 | 说明 |
| --- | --- |
| `GET /api/knowledge/stats` | 知识库统计 |
| `GET /api/knowledge/overview` | 知识库概览，包含标签分布 |
| `GET /api/knowledge/chunks?pageNum=1&pageSize=20` | 分页查询 chunk，支持 `filename`、`sourceType`、`category`、`tag` 过滤 |
| `GET /api/knowledge/chunks/{id}` | 查询 chunk 详情 |
| `PUT /api/knowledge/chunks/{id}` | 编辑 chunk 文本和 `metadata.scenes`，`reembed=true` 时发布单 chunk 重嵌入消息 |

知识库统计在数据库层聚合，不把全表数据查到 Java 内存里计算。
知识库概览基于 `metadata.scenes` 统计 7 大类白名单标签，标签不存在时按 0 返回，便于前端固定展示完整标签体系。

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
handler 抛出 RetryableMessageError 且未超过最大次数
 -> 记录阶段失败信息
 -> 发布到 retry exchange
 -> ack 当前消息
```

超过最大次数或永久失败：

```text
handler 抛出 PermanentMessageError、普通异常，或 RetryableMessageError 达到最大尝试次数
 -> 记录阶段失败信息
 -> 标记任务 failed
 -> basic_reject(requeue=false)
 -> 当前业务队列的 x-dead-letter-exchange 投递到 finance.ocr.dlx
 -> finance.ocr.dlx 通过 # 绑定进入 finance.ocr.dlq
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
- `reviewed.json` 是后续 chunk 打标签的唯一输入。
- `tagged_reviewed.json` 是后续向量索引的唯一输入。
- 后续知识库 chunk 数与最终段落数一致。

## 软删除规则

OCR 任务删除使用软删除：

- 更新 `ocr_task.deleted_at`。
- 分页查询默认排除软删除任务。
- Python 后续消费到该任务消息时直接 ack 丢弃。
- Java 删除任务时同步删除 `knowledge_vector` 中对应 `task_no` 的向量。
- MinIO 原始文件和阶段产物当前不删除，用于后续排查和审计。

## 当前 OCR 识别策略

`ocr.recognize` 按源文件类型选择识别策略：

- 图片源文件：使用阿里云 DashScope OpenAI 兼容接口调用 `qwen-vl-ocr-latest`。
- PDF 源文件：使用 OpenDataLoader 解析原始 PDF，清洗正文并切 chunk，再适配为系统标准 `segments`。

图片 OCR 配置：

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
- `keywords` 和 `summary` 已预留在 metadata 中，当前标签回正阶段默认写入空数组和空字符串。
- MinIO 产物暂不随软删除清理。
