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
| `POST /api/ai/token-usage/deepseek-response` | 传入 DeepSeek 原始响应 JSON，提取并保存 Token 用量。 |
| `GET /api/ai/token-usage/overview` | 查询 Token 用量汇总。 |
| `GET /api/ai/token-usage/trends` | 查询 Token 用量趋势。 |
| `GET /api/ai/console/overview` | 查询 AI 控制台总览。 |
| `GET /api/ai/console/visit-trends` | 查询应用访问趋势。 |

详细参数和返回字段见根目录 `API_DOCUMENTATION.md`。

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

当前模块只创建 OCR 任务并投递标准化阶段消息，后续识别、分段和结果查询需要在 Python 任务处理链路中继续实现。

## 构建

在项目根目录执行：

```bash
mvn -pl backend-java/finance-ai -am package
```
