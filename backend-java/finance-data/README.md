# finance-data 数据能力模块

## 模块定位

`finance-data` 是 Java 后端的公共数据层模块，不独立启动。它被 `finance-service` 和 `finance-ai` 依赖，用于集中放置领域对象、数据库访问对象、Mapper、Manage 封装、请求参数、返回对象和 InfluxDB 配置。

## 主要职责

- 定义业务请求参数：登录注册、股票行情查询、股票分时查询、指数行情查询、指数日 K 查询。
- 定义前端返回对象：用户信息、登录结果、股票行情、股票分时、指数行情、指数日 K、统一响应和错误响应。
- 定义持久化对象：用户、股票配置、股票行情快照、指数配置、指数行情快照、指数日 K、AI Token 用量日志、应用访问日志。
- 定义 OCR 任务持久化对象和 Mapper/Manage 封装。
- 提供 MyBatis-Plus Mapper 和 Manage 封装，供业务服务查询、保存和统计数据。
- 提供 InfluxDB 配置与股票分钟走势读写封装。
- 提供股票/指数排序枚举和行情 JSON 解析工具。

## 目录结构

```text
backend-java/finance-data/
├── pom.xml
└── src/main/java/com/scrapider/finance/
    ├── config/                  # InfluxDB 配置和属性
    ├── domain/
    │   ├── constant/            # 认证常量
    │   ├── dto/                 # 统计与行情中间数据
    │   ├── enums/               # 排序、市场枚举
    │   ├── param/               # Controller 请求参数
    │   ├── po/                  # PostgreSQL / InfluxDB 数据对象
    │   ├── util/                # 行情数据解析工具
    │   └── vo/                  # 前端返回对象
    ├── manage/                  # MyBatis-Plus 和 InfluxDB 查询封装
    └── mapper/                  # MyBatis-Plus Mapper
```

## 数据表映射

| PO | 数据表/存储 | 说明 |
| --- | --- | --- |
| `AppUserPO` | `app_user` | 系统用户、角色、默认首页和登录态用户信息来源。 |
| `StockConfigPO` | `stock_config` | 启用的股票清单和腾讯行情 `secid` 配置。 |
| `StockQuoteSnapshotPO` | `stock_quote_snapshot` | 股票最新行情快照。 |
| `StockIntradayTrendPO` | InfluxDB `stock_minute` | 股票分钟级分时走势。 |
| `IndexConfigPO` | `index_config` | 启用的指数清单和腾讯行情 `secid` 配置。 |
| `IndexQuoteSnapshotPO` | `index_quote_snapshot` | 指数最新行情快照。 |
| `IndexDailyKlinePO` | `index_daily_kline` | 指数日 K 数据。 |
| `AiTokenUsageLogPO` | `ai_token_usage_log` | AI 模型 Token 用量日志。 |
| `AppVisitLogPO` | `app_visit_log` | `/api/**` 访问日志。 |
| `OcrTaskPO` | `ocr_task` | OCR 文件上传和识别任务状态。 |

## 请求参数

| Param | 用途 | 关键字段 |
| --- | --- | --- |
| `LoginParam` | 登录 | `username`, `password` |
| `RegisterParam` | 注册 | `username`, `password`, `confirmPassword` |
| `StockQuoteListParam` | 股票行情列表 | `marketCode`, `limit`, `sortField`, `sortOrder` |
| `StockIntradayTrendParam` | 股票分时走势 | `stockCode` |
| `IndexQuoteListParam` | 指数行情列表 | `marketCode`, `limit`, `sortField`, `sortOrder` |
| `IndexDailyKlineParam` | 指数日 K | `indexCode`, `secid`, `startDate`, `endDate`, `limit` |

## 排序规则

股票行情 `sortField` 支持：

- `stockCode`
- `latestPrice`
- `changePercent`
- `volume`
- `turnoverAmount`
- `turnoverRate`
- `amplitude`
- `totalMarketValue`
- `syncedAt`

指数行情 `sortField` 支持：

- `indexCode`
- `latestPrice`
- `changePercent`
- `volume`
- `turnoverAmount`
- `amplitude`
- `syncedAt`

`sortOrder` 支持 `asc`、`desc`，默认 `desc`。股票默认按 `changePercent` 排序，指数默认按 `indexCode` 排序。

## InfluxDB 配置

配置项由主应用 `application.yml` 注入：

```yaml
influxdb:
  url: http://localhost:8086
  token: finance-management-local-token
  org: finance
  bucket: stock_intraday
  stock-minute-measurement: stock_minute
  timezone: Asia/Shanghai
```

股票分时数据写入 InfluxDB，标签包含 `syncBatchNo`、`stockCode`、`stockName`、`secid`，字段包含价格、成交量、成交额、昨收价和同步时间。

## 构建

在项目根目录执行：

```bash
mvn -pl backend-java/finance-data -am package
```
