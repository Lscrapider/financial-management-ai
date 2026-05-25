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

`stockCode`、`stockName`、`secid`、`marketCode`、`exchangeCode`、`latestPrice`、`openPrice`、`highPrice`、`lowPrice`、`previousClosePrice`、`changeAmount`、`changePercent`、`volume`、`turnoverAmount`、`turnoverRate`、`amplitude`、`totalMarketValue`、`floatMarketValue`、`syncedAt`。

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

### 查询 OCR 任务列表

`GET /api/ai/ocr/tasks`

需要 Token。

请求参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `limit` | number | 否 | 返回最近任务数量，默认 `50`，最大 `200` |

返回：`OcrTaskVO[]`，按提交时间倒序排列。

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
| `fileSize` | number | 文件大小，单位字节 |
| `status` | string | 任务状态 code，初始为 `ready`，可选值：`ready`、`running`、`manual_review_required`、`finished`、`failed` |
| `currentStage` | string | 当前阶段 code，初始为 `document.normalize`，可选值：`document.normalize`、`ocr.recognize`、`text.clean`、`quality.validate`、`embedding.index` |
| `progress` | number | 处理进度，初始为 `0` |
| `pageCount` | number | 页数，初始为 `0` |
| `segmentCount` | number | 文本分段数，初始为 `0` |
| `submittedAt` | string | 提交时间 |
| `updatedAt` | string | 更新时间 |

当前接口只负责上传文件、保存文件和创建待处理任务。

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
