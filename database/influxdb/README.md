# InfluxDB Local Service

本目录提供股票、指数和可转债分钟级分时走势的 InfluxDB 本地容器配置。Java 后端已经通过 `finance-data` 中的 InfluxDB Manage 封装读写该服务。

## 数据用途

InfluxDB 当前用于保存最新同步批次的分钟级分时走势。`finance-service` 的股票、指数和可转债同步任务会从腾讯行情接口拉取分时数据，并分别写入独立 bucket。

查询接口：

- `GET /api/stocks/intraday-trends?stockCode=688526`
- `GET /api/indices/intraday-trends?indexCode=000001`
- `GET /api/bonds/intraday-trends?bondCode=110059`
- `POST /api/ai/chat` 在 Query Rewrite 需要 `stock_intraday_by_code` 时也会读取该数据。

## 默认配置

配置项放在项目根目录 `.env`，未设置时使用以下默认值：

```env
INFLUXDB_PORT=8086
INFLUXDB_USERNAME=admin
INFLUXDB_PASSWORD=admin123456
INFLUXDB_ORG=finance
INFLUXDB_BUCKET=stock_intraday
INFLUXDB_STOCK_MINUTE_BUCKET=stock_intraday
INFLUXDB_INDEX_MINUTE_BUCKET=index_intraday
INFLUXDB_BOND_MINUTE_BUCKET=bond_intraday
INFLUXDB_ADMIN_TOKEN=finance-management-local-token
INFLUXDB_URL=http://localhost:8086
INFLUXDB_STOCK_MINUTE_MEASUREMENT=stock_minute
INFLUXDB_INDEX_MINUTE_MEASUREMENT=index_minute
INFLUXDB_BOND_MINUTE_MEASUREMENT=bond_minute
INFLUXDB_TIMEZONE=Asia/Shanghai
```

Java 应用读取的配置位于 `backend-java/finance-service/src/main/resources/application.yml` 的 `influxdb` 分组。

应用启动时会检查并创建 `stock/index/bond` 三类分时 bucket；如果 InfluxDB 暂不可用，只记录 warning，不阻断后端启动。

## 数据模型

默认 bucket 和 measurement：

```text
stock_intraday / stock_minute
index_intraday / index_minute
bond_intraday / bond_minute
```

标签：

- `syncBatchNo`：一次股票同步生成的批次号。
- `stockCode`：股票代码。
- `stockName`：股票名称。
- `secid`：腾讯行情证券 ID。

字段：

- `openPrice`
- `closePrice`
- `highPrice`
- `lowPrice`
- `averagePrice`
- `volume`
- `turnoverAmount`
- `previousClosePrice`
- `syncedAtEpoch`

时间戳使用分时点位时间，默认按 `Asia/Shanghai` 转换。

## 单独启动 InfluxDB

```bash
docker compose -f database/influxdb/docker-compose.yml up -d
```

## 随项目数据库一起启动

```bash
docker compose -f database/docker-compose.yml up -d influxdb
```

## 随完整后端环境启动

```bash
docker compose -f docker/docker-compose.yml up -d influxdb
```

## 访问

- Web UI：`http://localhost:8086`
- Organization：`finance`
- Bucket：`stock_intraday`、`index_intraday`、`bond_intraday`
