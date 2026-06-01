# 接口文档

## 基础信息

- 默认服务地址：`http://localhost:8081`
- 请求与响应格式：`application/json`
- Token 请求头：`Authorization: Bearer <accessToken>`
- 匿名接口：`POST /api/auth/login`、`POST /api/auth/register`
- `/api/ai/**`：需要登录
- 其他接口：需要 `admin` 角色

## 通用响应

认证相关接口使用统一包装：

```json
{
  "code": 0,
  "data": {},
  "error": null,
  "message": "ok"
}
```

行情和 AI 接口多数直接返回对象或数组。参数错误通常返回：

```json
{
  "message": "错误信息"
}
```

## 认证与用户

### 登录

`POST /api/auth/login`

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 | 用户名 |
| `password` | string | 是 | 密码 |

返回 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `accessToken` | string | 后续请求使用的 Bearer Token |

示例：

```json
{
  "username": "admin",
  "password": "123456"
}
```

### 注册

`POST /api/auth/register`

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 | 用户名，不能重复 |
| `password` | string | 是 | 密码 |
| `confirmPassword` | string | 是 | 确认密码，必须等于 `password` |

返回：`data` 为 `null`。

### 登出

`POST /api/auth/logout`

需要 Token。服务会移除当前 Token，返回 `data` 为 `null`。

### 刷新 Token

`POST /api/auth/refresh`

需要 Token。当前实现返回原 Token 字符串。

### 权限码

`GET /api/auth/codes`

返回空数组：

```json
[]
```

### 当前用户信息

`GET /api/user/info`

需要 Token 和 `admin` 角色。

返回 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | string | 用户 ID |
| `username` | string | 用户名 |
| `realName` | string | 展示名 |
| `avatar` | string | 头像 |
| `roles` | string[] | 角色列表 |
| `desc` | string | 用户描述 |
| `homePath` | string | 默认首页 |
| `token` | string | 当前 Token |

## 股票行情

### 股票行情列表

`GET /api/stocks/quotes`

需要 Token 和 `admin` 角色。

查询参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `marketCode` | string | 否 | 无 | 市场编码，如 `ASHARE`、`STAR`、`CHINEXT`、`SH_MAIN`、`SZ_MAIN` |
| `limit` | number | 否 | `100` | 返回条数，最大 `500` |
| `sortField` | string | 否 | `changePercent` | 排序字段 |
| `sortOrder` | string | 否 | `desc` | `asc` 或 `desc` |

`sortField` 支持：`stockCode`、`latestPrice`、`changePercent`、`volume`、`turnoverAmount`、`turnoverRate`、`amplitude`、`totalMarketValue`、`syncedAt`。

返回：`StockQuoteVO[]`

核心字段：

`stockCode`、`stockName`、`secid`、`marketCode`、`exchangeCode`、`latestPrice`、`openPrice`、`highPrice`、`lowPrice`、`previousClosePrice`、`averagePrice`、`changeAmount`、`changePercent`、`volume`、`externalVolume`、`internalVolume`、`currentVolume`、`turnoverAmount`、`turnoverRate`、`amplitude`、`volumeRatio`、`limitUpPrice`、`limitDownPrice`、`totalMarketValue`、`floatMarketValue`、`peTtm`、`peDynamic`、`peStatic`、`pbRatio`、`quoteDetails`、`syncedAt`。

### 股票分时走势

`GET /api/stocks/intraday-trends`

需要 Token 和 `admin` 角色。

查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `stockCode` | string | 是 | 股票代码 |

返回：`StockIntradayTrendVO[]`

核心字段：

`stockCode`、`stockName`、`secid`、`syncBatchNo`、`trendTime`、`trendDate`、`trendMinute`、`openPrice`、`closePrice`、`highPrice`、`lowPrice`、`averagePrice`、`volume`、`turnoverAmount`、`previousClosePrice`、`syncedAt`。

## 指数行情

### 指数行情列表

`GET /api/indices/quotes`

需要 Token 和 `admin` 角色。

查询参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `marketCode` | string | 否 | 无 | 市场编码，如 `INDEX` |
| `limit` | number | 否 | `100` | 返回条数，最大 `500` |
| `sortField` | string | 否 | `indexCode` | 排序字段 |
| `sortOrder` | string | 否 | `desc` | `asc` 或 `desc` |

`sortField` 支持：`indexCode`、`latestPrice`、`changePercent`、`volume`、`turnoverAmount`、`amplitude`、`syncedAt`。

返回：`IndexQuoteVO[]`

核心字段：

`indexCode`、`indexName`、`secid`、`marketCode`、`exchangeCode`、`latestPrice`、`openPrice`、`highPrice`、`lowPrice`、`previousClosePrice`、`changeAmount`、`changePercent`、`volume`、`turnoverAmount`、`amplitude`、`syncedAt`。

### 指数日 K

`GET /api/indices/daily-klines`

需要 Token 和 `admin` 角色。

查询参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `indexCode` | string | 条件必填 | 无 | 指数代码，和 `secid` 至少传一个 |
| `secid` | string | 条件必填 | 无 | 腾讯行情证券 ID，和 `indexCode` 至少传一个 |
| `startDate` | string | 否 | 无 | 开始日期，格式 `yyyy-MM-dd` |
| `endDate` | string | 否 | 无 | 结束日期，格式 `yyyy-MM-dd` |
| `limit` | number | 否 | `250` | 返回条数，最大 `500` |

返回：`IndexDailyKlineVO[]`，按 `tradeDate` 升序。

核心字段：

`indexCode`、`indexName`、`secid`、`marketCode`、`exchangeCode`、`tradeDate`、`openPrice`、`closePrice`、`highPrice`、`lowPrice`、`changeAmount`、`changePercent`、`volume`、`turnoverAmount`、`amplitude`、`turnoverRate`、`syncedAt`。

## 可转债行情

### 可转债列表

`GET /api/bonds/quotes`

需要 Token 和 `admin` 角色。

查询参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `limit` | number | 否 | `100` | 返回条数，最大 `500` |
| `sortField` | string | 否 | `changePercent` | 排序字段 |
| `sortOrder` | string | 否 | `desc` | `asc` 或 `desc` |

返回：`BondQuoteVO[]`

核心字段：

`bondCode`、`bondName`、`secid`、`marketCode`、`exchangeCode`、`latestPrice`、`openPrice`、`highPrice`、`lowPrice`、`previousClosePrice`、`changeAmount`、`changePercent`、`volume`、`averagePrice`、`currentVolume`、`turnoverAmount`、`amplitude`、`turnoverRate`、`bondRating`、`quoteDetails`、`syncedAt`。

### 可转债分时走势

`GET /api/bonds/intraday-trends?bondCode=xxx`

需要 Token。返回：`BondIntradayTrendVO[]`。

### 可转债日 K

`GET /api/bonds/daily-klines`

查询参数：`bondCode` 或 `secid`、`startDate`、`endDate`、`limit`。

## 行情同步

### 触发手动全量同步

`POST /api/stocks/sync`、`POST /api/indices/sync`、`POST /api/bonds/sync`

需要 Token。返回：`{ "running": true/false, "started": true/false }`。

### 同步状态查询

`GET /api/stocks/sync/status`、`GET /api/indices/sync/status`、`GET /api/bonds/sync/status`

返回同步任务是否正在运行。

### 单只股票分时手动同步

`POST /api/stocks/sync/trends/{stockCode}`

返回 `MarketSyncStatusVO`。

### 单只可转债分时手动同步

`POST /api/bonds/sync/trends/{bondCode}`

## 股票预警

### 预警列表

`GET /api/stock-alerts?targetType=STOCK`

需要 Token。返回 `StockAlertConfigVO[]`。

### 新增预警

`POST /api/stock-alerts`

### 删除预警

`POST /api/stock-alerts/delete`

### 手动触发预警检查

`POST /api/stock-alerts/check`

## 投资观察池

### 分组列表

`GET /api/watch-pool/groups`

### 新增/更新分组

`POST /api/watch-pool/groups`

### 删除分组

`POST /api/watch-pool/groups/delete`

### 新增/更新标的

`POST /api/watch-pool/items`

### 删除标的

`POST /api/watch-pool/items/delete`

## 知识库

### 分页查询

`GET /api/knowledge/chunks?pageNum=1&pageSize=20`

返回 `KnowledgeChunkPageVO`。

### 统计

`GET /api/knowledge/stats`

返回：`taskCount`、`chunkCount`、`totalTextLength`、`latestCreatedAt`。

### 更新文本

`PUT /api/knowledge/chunks/{id}`

请求体：`{ "text": "新文本内容" }`，返回更新后的 `KnowledgeChunkVO`。

## AI Chat

### 理财分析对话

`POST /api/ai/chat`

需要 Token。

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `message` | string | 是 | 用户问题 |

返回：`AiChatVO`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `message` | string | 归一化后的原始问题 |
| `answer` | string | AI 回答 |
| `model` | string | 使用的模型 |
| `queryRewrite` | object | Query Rewrite 结果 |
| `databaseContext` | object | 后端查询到的行情上下文 |
| `answeredAt` | string | 回答时间 |

`queryRewrite.dataRequests[].queryType` 当前支持：

- `stock_quote_by_code`
- `stock_intraday_by_code`
- `stock_quote_list`
- `index_quote_by_code`
- `index_quote_list`
- `index_daily_kline_by_code`

## OCR 任务

### 分页查询 OCR 任务

`POST /api/ai/ocr/tasks/page`

需要 Token。只返回 `ocr_task.source_type = 'ocr'` 的文件 OCR 任务。

请求体：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `pageNum` | number | 否 | `1` | 页码 |
| `pageSize` | number | 否 | `20` | 每页条数，最大 `200` |
| `status` | string | 否 | 无 | 状态过滤，可选值：`ready`、`running`、`manual_review_required`、`finished`、`failed`，不传则返回全部 |

返回：`OcrTaskPageVO`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `records` | OcrTaskVO[] | 任务列表 |
| `total` | number | 总数 |
| `pageNum` | number | 当前页码 |
| `pageSize` | number | 每页条数 |
| `pages` | number | 总页数 |

### 软删除任务

`POST /api/ai/ocr/tasks/delete`

需要 Token。请求体：`{ "taskNo": "ocr-xxx" }`。删除任务并同步删除对应知识库向量。

### 查询任务阶段明细

`GET /api/ai/ocr/tasks/{taskNo}/stages`

需要 Token。用于 OCR 任务页普通流程节点弹窗展示任务级阶段记录。

返回：`OcrStageDetailVO`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `taskNo` | string | 任务编号 |
| `stages` | array | 任务级阶段记录，不包含 chunk 级记录 |

`stages` 元素：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `stage` | string | 阶段 code |
| `status` | string | 阶段状态 |
| `attemptCount` | number | 当前尝试次数 |
| `maxAttempts` | number | 最大尝试次数 |
| `inputRef` | object | 输入产物引用 |
| `outputRef` | object | 输出产物引用 |
| `metrics` | object | 阶段指标 |
| `errorMessage` | string | 失败原因 |
| `startedAt` | string | 开始时间 |
| `finishedAt` | string | 完成时间 |
| `updatedAt` | string | 更新时间 |

### 查询场景打标明细

`GET /api/ai/ocr/tasks/{taskNo}/chunk-tags`

需要 Token。用于 OCR 任务页“场景打标”弹窗展示 chunk 粒度处理状态，不改变 RabbitMQ 消息结构。

返回：`OcrChunkTagDetailVO`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `taskNo` | string | 任务编号 |
| `totalChunkCount` | number | 总 chunk 数 |
| `finishedChunkCount` | number | 已完成 chunk 数 |
| `failedChunkCount` | number | 失败 chunk 数 |
| `pendingChunkCount` | number | 待处理 chunk 数 |
| `llmChunkCount` | number | 需要 LLM 补标的 chunk 数 |
| `ruleOnlyChunkCount` | number | 规则直通 chunk 数 |
| `deletedChunkCount` | number | 标签回正后标记 `metadata.deleted=true` 的 chunk 数 |
| `chunks` | array | chunk 明细列表 |

`chunks` 元素：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `chunkId` | string | chunk 唯一标识 |
| `chunkIndex` | number | chunk 序号 |
| `pageNos` | number[] | 来源页码 |
| `paragraphNos` | number[] | 来源段落号 |
| `status` | string | 当前 chunk 状态 |
| `currentStage` | string | 当前 chunk 阶段 |
| `needLlm` | boolean | 是否需要 LLM 补标 |
| `deleted` | boolean | 是否因无最终标签被跳过入库 |
| `scenes` | object | 当前可用标签，优先使用最终标签 |
| `errorMessage` | string | 失败原因 |
| `text` | string | chunk 文本 |

### 提交 OCR 文件

`POST /api/ai/ocr/tasks`

需要 Token。请求格式为 `multipart/form-data`。

请求参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | file[] | 是 | 支持重复传入多个 `file`，每个文件支持 `pdf`、`png`、`jpg`、`jpeg`、`webp`，单文件最大 `50MB` |

返回：`OcrTaskVO[]`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | number | 任务 ID |
| `taskNo` | string | 任务编号 |
| `originalFilename` | string | 原始文件名 |
| `fileType` | string | 文件类型 |
| `sourceType` | string | 来源类型，文件 OCR 为 `ocr` |
| `fileSize` | number | 文件大小，单位字节 |
| `status` | string | 任务状态 code，初始为 `ready`，可选值：`ready`、`running`、`manual_review_required`、`finished`、`failed` |
| `currentStage` | string | 当前阶段 code，初始为 `document.normalize`，可选值：`document.normalize`、`ocr.recognize`、`text.clean`、`quality.validate`、`chunk.tag.rule`、`chunk.tag.llm`、`chunk.tag.correct`、`embedding.index` |
| `progress` | number | 处理进度，初始为 `0` |
| `pageCount` | number | 页数，初始为 `0` |
| `segmentCount` | number | 文本分段数，初始为 `0` |
| `submittedAt` | string | 提交时间 |
| `updatedAt` | string | 更新时间 |

当前接口只负责上传文件、保存文件和创建待处理任务。

## 手动知识导入

手动知识导入复用 `ocr_task` 和 `ocr_review`，任务来源为 `ocr_task.source_type = 'manual_text'`，不会出现在 OCR 处理进度列表中。草稿提交后跳过格式校验、OCR 识别和文本清洗，直接发布 `ocr.chunk.tag.rule`，继续复用场景打标、标签回正和向量入库流程。

### 分页查询手动知识任务

`POST /api/ai/manual-knowledge/tasks/page`

需要 Token。

请求体同 OCR 任务分页：`pageNum`、`pageSize`、`status`。

返回：`OcrTaskPageVO`，其中 `records[].sourceType` 为 `manual_text`。

### 创建手动知识草稿

`POST /api/ai/manual-knowledge/tasks`

需要 Token。

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `title` | string | 否 | 标题；为空时取第一个非空 chunk 的前 5 个字并追加 `...` |
| `chunks` | string[] | 是 | 手动输入的文本分段；每一项保存为一个 chunk |

返回：`OcrTaskVO`。任务编号前缀为 `manual-`，初始状态为 `manual_review_required`，当前阶段为 `quality.validate`。

### 查询手动知识草稿

`GET /api/ai/manual-knowledge/tasks/{taskNo}`

需要 Token。返回结构同 `OcrReviewVO`，`pages` 固定为空数组。

### 保存手动知识草稿

`PUT /api/ai/manual-knowledge/tasks/{taskNo}/draft`

需要 Token。请求体同创建草稿。仅 `manual_review_required` 状态的手动任务允许保存。

### 提交手动知识

`POST /api/ai/manual-knowledge/tasks/{taskNo}/submit`

需要 Token。请求体同创建草稿。提交后写入 `reviewed.json`，更新 `ocr_review.status = approved`，将 `ocr_task.current_stage` 更新为 `chunk.tag.rule`，并发布场景打标消息。

### 删除手动知识任务

`POST /api/ai/manual-knowledge/tasks/delete`

需要 Token。请求体：`{ "taskNo": "manual-xxx" }`。只允许删除 `source_type = 'manual_text'` 的任务，并同步删除对应知识库向量。

## AI Token 用量

### 记录 DeepSeek 原始响应用量

`POST /api/ai/token-usage/deepseek-response`

需要 Token。

请求体：DeepSeek Chat Completions 原始响应 JSON，必须包含 `usage`。

返回：`AiTokenUsageLogVO`

字段：`id`、`provider`、`responseId`、`model`、`finishReason`、`promptTokens`、`completionTokens`、`totalTokens`、`cachedTokens`、`reasoningTokens`、`occurredAt`。

### Token 用量概览

`GET /api/ai/token-usage/overview`

需要 Token。

查询参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `days` | number | 否 | `7` | 统计最近 N 天，最大 `365` |

返回：`AiTokenUsageOverviewVO`

字段：`requestCount`、`promptTokens`、`completionTokens`、`totalTokens`、`cachedTokens`、`reasoningTokens`、`latestOccurredAt`。

### Token 用量趋势

`GET /api/ai/token-usage/trends`

需要 Token。

查询参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `days` | number | 否 | `7` | 统计最近 N 天，最大 `365` |

返回：`AiTokenUsageTrendVO[]`

字段：`timeBucket`、`promptTokens`、`completionTokens`、`totalTokens`、`requestCount`。

## AI 控制台

### 控制台概览

`GET /api/ai/console/overview`

需要 Token。

查询参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `days` | number | 否 | `7` | 统计最近 N 天，最大 `365` |

返回：`AiConsoleOverviewVO`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `user.totalUserCount` | number | 启用用户数 |
| `visit.totalVisitCount` | number | 总访问量 |
| `visit.periodVisitCount` | number | 周期内访问量 |
| `visit.uniqueUserCount` | number | 周期内独立用户数 |
| `visit.latestOccurredAt` | string | 最近访问时间 |
| `tokenUsage` | object | Token 用量概览 |

### 访问趋势

`GET /api/ai/console/visit-trends`

需要 Token。

查询参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `hours` | number | 否 | `24` | 统计最近 N 小时，最大 `720` |

返回：`AppVisitTrendVO[]`

字段：`timeBucket`、`visitCount`、`uniqueUserCount`。
