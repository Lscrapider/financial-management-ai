# Java Backend API

本文件保留主服务行情接口摘录。完整、最新的接口说明以项目根目录 [docs/API_DOCUMENTATION.md](../../docs/API_DOCUMENTATION.md) 为准。

当前后端接口组包括：

- `/api/auth/**`、`/api/user/**`：认证、用户资料、密码和通知设置。
- `/api/stocks/**`、`/api/indices/**`、`/api/bonds/**`：股票、指数、可转债行情和同步接口。
- `/api/watch-pool/**`：投资观察池。
- `/api/stock-alerts/**`：预警配置。
- `/api/ai/**`：AI Chat、OCR、手动知识导入、投资报告、Token 用量、控制台指标。
- `/api/knowledge/**`：知识库浏览、概览和 chunk 编辑。

## 股票行情列表

```http
GET /api/stocks/quotes
```

用于前端展示股票行情列表，数据来自数据库表 `stock_quote_snapshot`，不会直接调用第三方行情接口。

### Query Parameters

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `marketCode` | 否 | 市场编码，例如 `STAR`、`CHINEXT`、`SH_MAIN`、`SZ_MAIN`。不传则查询全部。 |
| `limit` | 否 | 返回数量，默认 `100`，最大 `500`。 |
| `sortField` | 否 | 排序字段，默认 `changePercent`。支持 `stockCode`、`latestPrice`、`changePercent`、`volume`、`turnoverAmount`、`turnoverRate`、`amplitude`、`totalMarketValue`、`syncedAt`。 |
| `sortOrder` | 否 | 排序方向，默认 `desc`。支持 `asc`、`desc`。 |

### Example

```http
GET /api/stocks/quotes?marketCode=CHINEXT&limit=50&sortField=changePercent&sortOrder=desc
```

### Response Fields

| 字段 | 说明 |
| --- | --- |
| `stockCode` | 股票代码 |
| `stockName` | 股票名称 |
| `secid` | 第三方行情接口证券 ID |
| `marketCode` | 市场编码 |
| `exchangeCode` | 交易所编码 |
| `latestPrice` | 最新价 |
| `openPrice` | 今开 |
| `highPrice` | 最高价 |
| `lowPrice` | 最低价 |
| `previousClosePrice` | 昨收 |
| `averagePrice` | 均价 |
| `changeAmount` | 涨跌额 |
| `changePercent` | 涨跌幅 |
| `volume` | 成交量 |
| `externalVolume` | 外盘 |
| `internalVolume` | 内盘 |
| `currentVolume` | 现手 |
| `turnoverAmount` | 成交额 |
| `turnoverRate` | 换手率 |
| `amplitude` | 振幅 |
| `volumeRatio` | 量比 |
| `limitUpPrice` | 涨停价 |
| `limitDownPrice` | 跌停价 |
| `totalMarketValue` | 总市值 |
| `floatMarketValue` | 流通市值 |
| `peTtm` | TTM 市盈率 |
| `peDynamic` | 动态市盈率 |
| `peStatic` | 静态市盈率 |
| `pbRatio` | 市净率 |
| `quoteDetails` | 腾讯接口原始字段明细，用于前端“更多”弹窗 |
| `syncedAt` | 数据同步时间 |

## 股票分时走势

```http
GET /api/stocks/intraday-trends
```

用于前端绘制分时图，返回指定股票最新同步批次的分钟走势数据，数据来自 InfluxDB bucket `stock_intraday`。

### Query Parameters

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `stockCode` | 是 | 股票代码，例如 `688526`。 |

### Example

```http
GET /api/stocks/intraday-trends?stockCode=688526
```

### Response Fields

| 字段 | 说明 |
| --- | --- |
| `stockCode` | 股票代码 |
| `stockName` | 股票名称 |
| `secid` | 第三方行情接口证券 ID |
| `syncBatchNo` | 同步批次号 |
| `trendTime` | 分时时间，北京时间，例如 `2026-05-22T09:30:00`。 |
| `trendDate` | 分时日期，北京时间日期字符串，例如 `2026-05-22`。 |
| `trendMinute` | 分时时分，北京时间时分字符串，例如 `09:30`，前端画图 X 轴可直接使用。 |
| `openPrice` | 分钟开盘价 |
| `closePrice` | 分钟收盘价，用于价格线 |
| `highPrice` | 分钟最高价 |
| `lowPrice` | 分钟最低价 |
| `averagePrice` | 分钟均价，用于均价线 |
| `volume` | 成交量 |
| `turnoverAmount` | 成交额 |
| `previousClosePrice` | 昨收价，用于参考线 |
| `syncedAt` | 数据同步时间 |

## 指数行情列表

```http
GET /api/indices/quotes
```

用于前端展示指数最新快照，数据来自数据库表 `index_quote_snapshot`，不会直接调用第三方行情接口。

### Query Parameters

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `marketCode` | 否 | 市场编码，例如 `INDEX`。不传则查询全部。 |
| `limit` | 否 | 返回数量，默认 `100`，最大 `500`。 |
| `sortField` | 否 | 排序字段，默认 `indexCode`。支持 `indexCode`、`latestPrice`、`changePercent`、`volume`、`turnoverAmount`、`amplitude`、`syncedAt`。 |
| `sortOrder` | 否 | 排序方向，默认 `desc`。支持 `asc`、`desc`。 |

## 指数日K线

```http
GET /api/indices/daily-klines
```

用于前端绘制指数日K图，数据来自数据库表 `index_daily_kline`。

### Query Parameters

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `indexCode` | 条件必填 | 指数代码，例如 `000001`。`indexCode` 和 `secid` 至少传一个。 |
| `secid` | 条件必填 | 第三方行情接口证券 ID，例如 `1.000001`。 |
| `startDate` | 否 | 开始交易日，格式 `yyyy-MM-dd`。 |
| `endDate` | 否 | 结束交易日，格式 `yyyy-MM-dd`。 |
| `limit` | 否 | 返回数量，默认 `250`，最大 `500`。 |

### Example

```http
GET /api/indices/daily-klines?secid=1.000001&limit=250
```

### Response Fields

| 字段 | 说明 |
| --- | --- |
| `indexCode` | 指数代码 |
| `indexName` | 指数名称 |
| `secid` | 第三方行情接口证券 ID |
| `tradeDate` | 交易日期 |
| `openPrice` | 开盘点位 |
| `closePrice` | 收盘点位 |
| `highPrice` | 最高点位 |
| `lowPrice` | 最低点位 |
| `changeAmount` | 涨跌额 |
| `changePercent` | 涨跌幅 |
| `volume` | 成交量 |
| `turnoverAmount` | 成交额 |
| `amplitude` | 振幅 |
| `turnoverRate` | 换手率 |
| `syncedAt` | 数据同步时间 |
