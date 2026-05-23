# Java Backend API

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
| `changeAmount` | 涨跌额 |
| `changePercent` | 涨跌幅 |
| `volume` | 成交量 |
| `turnoverAmount` | 成交额 |
| `turnoverRate` | 换手率 |
| `amplitude` | 振幅 |
| `totalMarketValue` | 总市值 |
| `floatMarketValue` | 流通市值 |
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
