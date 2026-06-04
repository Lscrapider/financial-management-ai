# finance-ai AI 能力模块

## 模块定位

`finance-ai` 是理财分析 AI 项目的 AI 编排模块，不独立启动。`finance-service` 依赖本模块，并由主应用扫描 `com.scrapider.finance` 根包后暴露 `/api/ai/**` 接口。

## 主要能力

- AI Chat：面向个人投资研究的理财分析对话。
- Query Rewrite：将用户问题改写为结构化意图、数据范围和后端可执行的数据请求。
- 行情上下文查询：根据 Query Rewrite 结果查询股票/指数行情数据，并提供给最终回答模型。
- Token 用量记录：记录 Spring AI ChatResponse 或 DeepSeek 原始响应中的 Token 用量。
- AI 控制台指标：汇总用户数、访问量、Token 用量和访问趋势。
- OCR 任务提交：上传 PDF、PNG、JPG、JPEG、WEBP 文件，创建待处理任务并保存文件。
- OCR 人工复核：消费文本清洗结果，提供复核草稿、页面图片、确认提交和任务软删除能力。
- 手动知识导入：用户按 chunk 录入文本，保存草稿后可直接提交到场景打标和向量入库流程。
- 知识库浏览与概览：支持 chunk 分页、详情、统计、场景标签过滤、标签分布概览、文本编辑和单 chunk 重嵌入。
- 投资报告 / 场景分析：支持股票、指数、可转债报告任务提交、currentScenes 回调、知识库召回、报告生成、历史查询和重新生成。

## 目录结构

```text
backend-java/finance-ai/
├── pom.xml
└── src/main/java/com/scrapider/finance/ai/
    ├── controller/              # AI Chat、Token 用量、控制台指标接口
    ├── domain/
    │   ├── param/               # AI 请求参数
    │   └── vo/                  # AI 返回对象
    └── service/
        └── impl/                # Chat、Rewrite、行情查询、统计实现
```

## 接口分组

| 接口 | 说明 |
| --- | --- |
| `POST /api/ai/chat` | 接收用户问题，执行 Query Rewrite、行情上下文查询和最终模型回答。 |
| `POST /api/ai/ocr/tasks` | 上传 OCR 文件，保存本地文件并创建待处理任务。 |
| `POST /api/ai/ocr/tasks/page` | 分页查询 OCR 任务，支持 `pageNum`、`pageSize`、`status`。默认排除软删除任务。 |
| `GET /api/ai/ocr/tasks/{taskNo}/stages` | 查询 OCR 任务级阶段明细，用于前端流程节点弹窗展示。 |
| `GET /api/ai/ocr/tasks/{taskNo}/chunk-tags` | 查询场景打标 chunk 明细，用于前端弹窗展示每个 chunk 的页码、状态、LLM 标记和最终标签。 |
| `POST /api/ai/ocr/tasks/delete` | 软删除 OCR 任务，只更新 `deleted_at`，不删除 MinIO 产物。 |
| `GET /api/ai/ocr/reviews/{taskNo}` | 查询 OCR 人工复核详情。 |
| `PUT /api/ai/ocr/reviews/{taskNo}/draft` | 保存 OCR 人工复核草稿。 |
| `POST /api/ai/ocr/reviews/{taskNo}/submit` | 确认提交 OCR 人工复核结果，写入 `reviewed.json` 并发布场景打标消息。 |
| `GET /api/ai/ocr/reviews/{taskNo}/pages/{pageNo}/image` | 代理读取复核页图片，允许匿名访问以支持浏览器 `<img>` 加载。 |
| `POST /api/ai/manual-knowledge/tasks/page` | 分页查询手动知识导入任务，只返回 `source_type=manual_text`。 |
| `POST /api/ai/manual-knowledge/tasks` | 创建手动知识草稿，每个文本框对应一个 chunk。 |
| `GET /api/ai/manual-knowledge/tasks/{taskNo}` | 查询手动知识草稿详情。 |
| `PUT /api/ai/manual-knowledge/tasks/{taskNo}/draft` | 保存手动知识草稿。 |
| `POST /api/ai/manual-knowledge/tasks/{taskNo}/submit` | 提交手动知识，写入 `reviewed.json` 并发布场景打标消息。 |
| `POST /api/ai/manual-knowledge/tasks/delete` | 软删除手动知识任务，并删除对应知识库向量。 |
| `GET /api/knowledge/stats` | 查询知识库总量统计。 |
| `GET /api/knowledge/overview` | 查询知识库概览和 7 大类场景标签分布。 |
| `GET /api/knowledge/chunks` | 分页查询知识库 chunk，支持文件名、来源类型、场景大类和场景标签过滤。 |
| `GET /api/knowledge/chunks/{id}` | 查询知识库 chunk 详情。 |
| `PUT /api/knowledge/chunks/{id}` | 编辑 chunk 文本和场景标签，可选发布单 chunk 重嵌入消息。 |
| `POST /api/ai/scene-analysis/tasks` | 提交投资报告场景分析任务。 |
| `POST /api/ai/scene-analysis/tasks/{taskNo}/callback` | Python worker 回调 currentScenes 或 retrievalEmbeddings。 |
| `GET /api/ai/scene-analysis/tasks/{taskNo}/report` | 查询任务最新报告，供前端轮询。 |
| `POST /api/ai/scene-analysis/tasks/{taskNo}/report/regenerate` | 基于已保存上下文重新生成报告。 |
| `GET /api/ai/scene-analysis/tasks/reports/targets` | 按标的分页查询最新报告概览。 |
| `GET /api/ai/scene-analysis/tasks/reports` | 查询单标的报告历史。 |
| `GET /api/ai/scene-analysis/tasks/reports/{reportId}` | 查询报告详情。 |
| `GET /api/ai/scene-analysis/targets/search` | 搜索可生成报告的股票、指数、可转债标的。 |
| `GET /api/ai/scene-analysis/config-profiles` | 查询报告配置档。 |
| `GET /api/ai/scene-analysis/config-profiles/parameter-schema` | 查询报告参数 schema。 |
| `GET /api/ai/scene-analysis/config-profiles/report-types` | 查询报告类型。 |
| `POST /api/ai/scene-analysis/config-profiles` | 创建报告配置档。 |
| `PUT /api/ai/scene-analysis/config-profiles/{id}` | 更新报告配置档。 |
| `DELETE /api/ai/scene-analysis/config-profiles/{id}` | 删除报告配置档。 |
| `POST /api/ai/token-usage/deepseek-response` | 传入 DeepSeek 原始响应 JSON，提取并保存 Token 用量。 |
| `GET /api/ai/token-usage/overview` | 查询 Token 用量汇总。 |
| `GET /api/ai/token-usage/trends` | 查询 Token 用量趋势。 |
| `GET /api/ai/console/overview` | 查询 AI 控制台总览。 |
| `GET /api/ai/console/visit-trends` | 查询应用访问趋势。 |

详细参数和返回字段见 `docs/API_DOCUMENTATION.md`。

## Chat 处理流程

```text
用户问题
  -> AiChatController
  -> AiChatService
  -> AiQueryRewriteService
  -> AiMarketDataQueryService
  -> Spring AI ChatClient
  -> AiTokenUsageService
  -> AiChatVO
```

处理规则：

- 空消息会返回 `400`，错误信息为 `message不能为空`。
- Query Rewrite 只允许理财分析、股票、指数、市场、资产配置、投资研究、财务指标解释类问题。
- 非理财范围问题会返回拒答说明，不再查询行情数据。
- 需要行情数据的问题会按结构化 `dataRequests` 查询数据库。
- 如果数据库没有足够数据，系统提示要求模型明确说明缺少数据，不编造具体数值。
- 回答原则为清晰、克制，区分事实、推断和风险提示，不提供确定性买卖建议。

## Query Rewrite 输出

`AiQueryRewriteVO` 包含：

- `enabled`：是否允许进入理财分析流程。
- `disabledReason`：禁用原因。
- `intent`：意图，如 `stock_analysis`、`index_analysis`、`market_overview`。
- `requiresMarketData`：是否需要行情数据。
- `targetType`、`targetName`、`stockCode`、`indexCode`：分析对象。
- `timeRange`：时间范围，如 `intraday`、`recent_7d`、`recent_250d`。
- `dataScopes`：数据范围，如 `quote`、`intraday_trend`、`daily_kline`。
- `dataRequests`：后端可执行的数据请求。
- `rewrittenQuestion`：改写后的问题。

当前支持的 `queryType`：

- `stock_quote_by_code`
- `stock_intraday_by_code`
- `stock_quote_list`
- `index_quote_by_code`
- `index_quote_list`
- `index_daily_kline_by_code`

## 数据上下文

`AiMarketDataQueryService` 会根据 `dataRequests` 查询：

- 股票最新行情快照：`stock_quote_snapshot`
- 股票最新批次分时走势：InfluxDB `stock_minute`
- 指数最新行情快照：`index_quote_snapshot`
- 指数日 K：`index_daily_kline`

如果用户只给出股票或指数名称，服务会先尝试精确匹配名称，再尝试模糊匹配名称，找到后补齐代码。

## Token 用量统计

Token 用量记录保存到 `ai_token_usage_log`，统计接口默认查询最近 7 天，最大支持 365 天。

记录字段包括：

- provider
- responseId
- model
- finishReason
- promptTokens
- completionTokens
- totalTokens
- cachedTokens
- reasoningTokens
- occurredAt

## OCR 任务

`OcrTaskService` 当前实现文件接收、基础校验、MinIO 存储、任务入库和 RabbitMQ 投递：

- 支持文件类型：`pdf`、`png`、`jpg`、`jpeg`、`webp`。
- 单文件最大：`50MB`。
- 单次请求支持上传多个文件，每个文件会创建一个独立 OCR 任务。
- 原始文件保存到 MinIO bucket，默认 bucket 为 `finance-ocr`。
- 新任务默认状态：`ready`。
- 新任务默认阶段：`document.normalize`。
- 任务入库成功后发布 `ocr.document.normalize` 消息到 `finance.ocr.topic`。

任务列表使用分页查询：

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

OCR 任务列表只返回 `ocr_task.source_type = 'ocr'` 的任务。手动复制文本导入的任务使用独立接口和页面查询。

删除任务使用软删除：

- 接口：`POST /api/ai/ocr/tasks/delete`
- 请求体：`{ "taskNo": "ocr-xxx" }`
- 只更新 `ocr_task.deleted_at` 和 `updated_at`。
- 分页查询默认过滤 `deleted_at is null`。
- Python worker 消费到已软删除任务消息时直接 ack 跳过，不进入重试和死信队列。

人工复核流程：

- Java 消费 `ocr.quality.validate` 消息后读取 `cleaned.json`，创建 `ocr_review` 草稿，并将任务标记为 `manual_review_required`。
- 前端复核页允许修改、删除、合并和调整段落。
- 保存草稿只更新 `ocr_review.draft_content`。
- 确认提交会将最终内容写入 MinIO `reviewed.json`，把 `ocr_review.status` 更新为 `approved`，并发布 `ocr.chunk.tag.rule`。
- 确认提交时后端根据最终 `paragraphs.length` 更新 `ocr_task.segment_count`，因此任务列表“分段”显示人工确认后的最终段落数。

## 手动知识导入

手动知识导入复用 `ocr_task` 和 `ocr_review`，通过 `ocr_task.source_type = 'manual_text'` 与文件 OCR 任务隔离：

- 新任务编号前缀为 `manual-`，标题由用户输入；标题为空时取第一个非空 chunk 前 5 个字并追加 `...`。
- 每个文本框保存为一个 paragraph/chunk，`sourcePages`、`sourceSegments` 为空，`avgConfidence` 为 `1`。
- 保存草稿会写入兼容 OCR 复核结构的 `cleaned.json`，并更新 `ocr_review.draft_content`。
- 提交时跳过 `document.normalize`、`ocr.recognize`、`text.clean`，从 `quality.validate` 等价的草稿页直接进入 `chunk.tag.rule`。
- 提交后复用现有 `chunk.tag.rule -> chunk.tag.llm -> chunk.tag.correct -> embedding.index` 流程。

## 知识库浏览

知识库接口读取 `knowledge_vector`，并通过 `ocr_task` 补充来源文件名和来源类型：

- `GET /api/knowledge/chunks` 支持 `pageNum`、`pageSize`、`filename`、`sourceType`、`category`、`tag` 查询参数。
- `sourceType` 常用值为 `ocr` 和 `manual_text`。
- `category` 只能使用 7 大类场景标签：`asset`、`price`、`volume`、`trend`、`valuation`、`sentiment`、`risk_strategy`。
- `tag` 支持逗号分隔多个标签。传入 `category` 时只匹配该大类下标签；不传 `category` 时跨所有大类匹配。
- `GET /api/knowledge/overview` 返回任务数、chunk 数、文本总长度、最近入库时间和完整白名单标签分布。
- `PUT /api/knowledge/chunks/{id}` 可更新文本和 `metadata.scenes`。`reembed=true` 时会发布 `knowledge.chunk.reembed` 消息，由 Python worker 重建该 chunk 向量。

## 投资报告 / 场景分析

场景分析报告由 Java 和 Python worker 协同完成：

```text
前端提交任务
  -> Java 创建 scene_analysis_task，组装行情和配置快照
  -> RabbitMQ 发布 current scene 分析消息
  -> Python 计算 currentScenes 并回调 Java
  -> Java 计算 chunkAllocation 和 retrievalTasks
  -> RabbitMQ 发布 retrieval embedding 消息
  -> Python 生成 queryEmbedding 并回调 Java
  -> Java 召回 knowledge_vector、类内重排并构建 knowledgeContext
  -> Java 异步调用 DeepSeek 生成结构化报告
  -> 写入 scene_analysis_report，前端轮询展示
```

当前支持：

- 标的类型：`STOCK`、`INDEX`、`CONVERTIBLE_BOND`。
- 报告类型：`quick_analysis`、`risk_check`、`valuation_report`。
- 任务状态：`pending`、`processing_current_scenes`、`current_scenes_ready`、`retrieving_knowledge`、`generating_report`、`success`、`failed`。
- 报告历史版本：初次生成使用 `initial`，重新生成使用 `regenerate`。
- 配置档：系统默认配置 `system_recommended`，并支持用户创建、更新、删除自定义配置。

## 构建

在项目根目录执行：

```bash
mvn -pl backend-java/finance-ai -am package
```
