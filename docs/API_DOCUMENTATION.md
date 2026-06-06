# 接口文档

## 基础信息

- 默认服务地址：`http://localhost:8081`
- 请求与响应格式：`application/json`
- Token 请求头：`Authorization: Bearer <accessToken>`
- 匿名接口：`POST /api/auth/login`、`POST /api/auth/register`
- OCR 复核图片代理：`GET /api/ai/ocr/reviews/{taskNo}/pages/{pageNo}/image` 匿名可访问，用于浏览器图片加载
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

### 更新当前用户资料

`PUT /api/user/info`

需要 Token 和 `admin` 角色。用于更新当前用户展示资料。

### 修改密码

`PUT /api/user/password`

需要 Token 和 `admin` 角色。用于修改当前用户密码。

### 更新通知设置

`PUT /api/user/notification`

需要 Token 和 `admin` 角色。用于更新当前用户通知偏好。

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

### 指数 K 线

`GET /api/indices/klines`

需要 Token 和 `admin` 角色。

查询参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `indexCode` | string | 条件必填 | 无 | 指数代码，和 `secid` 至少传一个 |
| `secid` | string | 条件必填 | 无 | 腾讯行情证券 ID，和 `indexCode` 至少传一个 |
| `startDate` | string | 否 | 无 | 开始日期，格式 `yyyy-MM-dd` |
| `endDate` | string | 否 | 无 | 结束日期，格式 `yyyy-MM-dd` |
| `limit` | number | 否 | `250` | 返回条数，最大 `500` |

返回：`IndexKlineVO[]`，按 `tradeDate` 升序。

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

`bondCode`、`bondName`、`secid`、`marketCode`、`exchangeCode`、`latestPrice`、`openPrice`、`highPrice`、`lowPrice`、`previousClosePrice`、`changeAmount`、`changePercent`、`volume`、`averagePrice`、`currentVolume`、`turnoverAmount`、`amplitude`、`turnoverRate`、`conversionValue`、`conversionPremiumRate`、`bondRating`、`quoteDetails`、`syncedAt`。

其中 `conversionValue` 和 `conversionPremiumRate` 是系统在可转债快照同步时基于转债快照价、正股快照价和转股价计算后写入 `bond_quote_snapshot` 的实时字段，不来自腾讯原始快照字段。

### 可转债 K 线

`GET /api/bonds/klines`

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

### 单只股票日 K 手动同步

`POST /api/stocks/sync/daily-klines/{stockCode}`

返回 `MarketSyncStatusVO`。

## 股票预警

### 预警列表

`GET /api/stock-alerts?targetType=STOCK`

需要 Token。返回 `StockAlertConfigVO[]`。

### 新增预警

`POST /api/stock-alerts`

### 删除预警

`DELETE /api/stock-alerts/{id}`

### 预警标的选项

`GET /api/stock-alerts/target-options?targetType=STOCK`

返回可创建预警的标的选项。

### 手动触发预警检查

`POST /api/stock-alerts/check`

## 投资观察池

### 分组列表

`GET /api/watch-pool/groups`

### 新增/更新分组

`POST /api/watch-pool/groups`

### 删除分组

`DELETE /api/watch-pool/groups/{id}`

### 新增/更新标的

`POST /api/watch-pool/items`

### 删除标的

`DELETE /api/watch-pool/items/{id}`

## 知识库

### 分页查询

`GET /api/knowledge/chunks?pageNum=1&pageSize=20`

查询参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `pageNum` | number | 否 | `1` | 页码 |
| `pageSize` | number | 否 | `20` | 每页条数，最大 `200` |
| `filename` | string | 否 | 无 | 按 OCR 原始文件名模糊过滤 |
| `sourceType` | string | 否 | 无 | 来源过滤，常用值：`ocr`、`manual_text` |
| `category` | string | 否 | 无 | 场景大类过滤，可选：`asset`、`price`、`volume`、`trend`、`valuation`、`sentiment`、`risk_strategy` |
| `tag` | string | 否 | 无 | 场景标签过滤，支持逗号分隔多个标签；传 `category` 时只在该大类下匹配，不传则跨所有大类匹配 |

返回 `KnowledgeChunkPageVO`。

### 统计

`GET /api/knowledge/stats`

返回：`taskCount`、`chunkCount`、`totalTextLength`、`latestCreatedAt`。

### 概览

`GET /api/knowledge/overview`

返回：`taskCount`、`chunkCount`、`totalTextLength`、`latestCreatedAt`、`tagDistributions`。

`tagDistributions` 元素：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `categoryKey` | string | 场景大类 |
| `tags` | array | 该大类下所有白名单标签的统计 |

`tags` 元素：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `categoryKey` | string | 场景大类 |
| `tagKey` | string | 标签 key |
| `count` | number | 命中该标签的 chunk 数 |
| `categoryPercentage` | number | 在当前大类命中标签总数中的占比 |
| `totalPercentage` | number | 在全部 chunk 中的占比 |

### 查询详情

`GET /api/knowledge/chunks/{id}`

返回单条 `KnowledgeChunkVO`，包含文本、来源任务、页码、段落号、原始文件名和 `metadata.scenes`。

### 更新文本

`PUT /api/knowledge/chunks/{id}`

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `text` | string | 否 | 新文本内容，非空时更新 chunk 文本 |
| `scenes` | object | 否 | 新场景标签，key 必须是 7 大类之一，标签必须在白名单内 |
| `reembed` | boolean | 否 | 更新文本后是否发布单 chunk 重嵌入消息 |

返回更新后的 `KnowledgeChunkVO`。

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
- `index_kline_by_code`

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

## OCR 人工复核

### 查询复核详情

`GET /api/ai/ocr/reviews/{taskNo}`

需要 Token。返回 `OcrReviewVO`，包含任务信息、页面列表和段落草稿。

### 保存复核草稿

`PUT /api/ai/ocr/reviews/{taskNo}/draft`

需要 Token。请求体为 `OcrReviewDraftParam`，用于保存当前复核段落草稿。

### 提交复核结果

`POST /api/ai/ocr/reviews/{taskNo}/submit`

需要 Token。请求体可为空；传入草稿时会先保存最终内容。提交后写入 `reviewed.json`，更新任务最终分段数，并发布 `ocr.chunk.tag.rule` 消息。

### 查询复核页图片

`GET /api/ai/ocr/reviews/{taskNo}/pages/{pageNo}/image`

返回 PNG 图片字节，用于前端复核页加载标准化后的页面图片。

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

## 投资报告 / 场景分析

场景分析报告用于按标的生成结构化投资研究报告。当前流程为：Java 创建任务并组装行情上下文，Python 计算当前场景标签并回调 Java，Java 分配 chunk 召回数量并发布检索 embedding 任务，Python 回调 query embedding，Java 执行 pgvector 召回和重排，最后调用 DeepSeek 生成报告并写入历史表。

### 提交报告任务

`POST /api/ai/scene-analysis/tasks`

需要 Token。

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `targetType` | string | 是 | 标的类型：`STOCK`、`INDEX`、`CONVERTIBLE_BOND` |
| `targetCode` | string | 是 | 标的代码 |
| `targetName` | string | 否 | 标的名称 |
| `reportType` | string | 否 | 报告类型：`quick_analysis`、`risk_check`、`valuation_report`，默认 `quick_analysis` |
| `configProfile` | string | 否 | 配置档 code，默认系统推荐配置 |
| `totalChunks` | number | 是 | 本次报告期望召回的知识库 chunk 数，必须大于 0 |
| `dailyKlineLimit` | number | 否 | 日 K 查询数量。股票默认 90，最小 60，最大 250；指数和可转债使用对应同步配置或请求值 |
| `weeklyKlineLimit` | number | 否 | 周 K 查询数量。股票默认 52，最小 1，最大 250；指数和可转债使用对应同步配置或请求值 |
| `monthlyKlineLimit` | number | 否 | 月 K 查询数量。股票默认 60，最小 1，最大 250；配置值按自然月理解，例如 320 表示向前取约 320 个月窗口 |
| `userOverrides` | object | 否 | 用户覆盖参数；当前只保留 Python 实际参与计算的阈值、敏感度和权重配置 |

返回：`SceneAnalysisSubmitVO`，核心字段：`taskNo`、`targetType`、`targetCode`、`configProfile`、`status`。

数据源口径：

- 快照和分时仍使用腾讯行情接口。
- 历史 K 线通过 `HistoricalKlineProvider` 获取，当前默认 Tushare，可由 `MARKET_HISTORICAL_KLINE_PROVIDER` 切换。
- 股票 K 线会落库无复权、前复权、后复权三套数据；Tushare HTTP 不直接调用 `pro_bar`，系统通过 `adj_factor` 本地计算复权价。
- 股票、指数分别使用 Tushare 日/周/月接口；可转债使用 `cb_daily`，周 K、月 K 由日线聚合生成。
- 股票基本面和可转债资料也通过 provider 获取；股票基本面当前默认东方财富，优先保证行业、估值历史、财务指标和分红历史字段完整度；可转债资料当前默认 Tushare。

### Python 回调任务

`POST /api/ai/scene-analysis/tasks/{taskNo}/callback`

需要 Token。由 Python worker 回调 Java，提交 `currentScenesPayload` 或 `retrievalEmbeddings`，推动报告流水线继续执行。

### 查询任务报告

`GET /api/ai/scene-analysis/tasks/{taskNo}/report`

需要 Token。前端轮询该接口获取报告状态和最终报告。

返回：`SceneAnalysisReportVO`，核心字段：`taskNo`、`reportId`、`status`、`errorMessage`、`generationType`、`versionNo`、`reportContent`、`reportText`、`model`、`generatedAt`。

### 重新生成报告

`POST /api/ai/scene-analysis/tasks/{taskNo}/report/regenerate`

需要 Token。基于已保存的 `currentScenesPayload` 和 `knowledgeContext` 重新调用模型生成新版本报告。

### 报告标的分页

`GET /api/ai/scene-analysis/tasks/reports/targets`

需要 Token。

查询参数：`pageNum`、`pageSize`、`targetName`、`targetCode`、`targetType`。

返回：`SceneAnalysisReportTargetPageVO`，用于报告页面按标的聚合展示最新报告和报告数量。

### 单标的报告历史

`GET /api/ai/scene-analysis/tasks/reports?targetType=STOCK&targetCode=000001`

需要 Token。返回 `SceneAnalysisReportHistoryVO[]`。

### 报告详情

`GET /api/ai/scene-analysis/tasks/reports/{reportId}`

需要 Token。返回 `SceneAnalysisReportDetailVO`，包含结构化报告 JSON 和渲染文本。

### 标的搜索

`GET /api/ai/scene-analysis/targets/search`

需要 Token。

查询参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `targetType` | string | 否 | `STOCK` | 标的类型：`STOCK`、`INDEX`、`CONVERTIBLE_BOND` |
| `keyword` | string | 否 | 无 | 代码或名称关键字 |
| `limit` | number | 否 | 无 | 返回数量限制 |

### 报告配置档

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/ai/scene-analysis/config-profiles` | GET | 查询当前可用配置档 |
| `/api/ai/scene-analysis/config-profiles/parameter-schema` | GET | 查询可配置参数 schema |
| `/api/ai/scene-analysis/config-profiles/report-types` | GET | 查询报告类型 |
| `/api/ai/scene-analysis/config-profiles` | POST | 新增配置档 |
| `/api/ai/scene-analysis/config-profiles/{id}` | PUT | 更新配置档 |
| `/api/ai/scene-analysis/config-profiles/{id}` | DELETE | 删除配置档 |

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
