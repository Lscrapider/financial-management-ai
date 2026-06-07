# finance-data 数据能力模块

## 模块定位

`finance-data` 是 Java 后端的公共数据层模块，不独立启动。它被 `finance-service` 和 `finance-ai` 依赖，用于集中放置领域对象、数据库访问对象、Mapper、Manage 封装、请求参数、返回对象和 InfluxDB 配置。

## 主要职责

- 定义业务请求参数：登录注册、用户资料、行情查询、观察池、预警、知识库、OCR 和场景分析报告参数。
- 定义前端返回对象：用户信息、登录结果、行情、观察池、预警、知识库、OCR、报告、统一响应和错误响应。
- 定义持久化对象：用户、股票/指数/可转债配置与行情、观察池、预警、知识库向量、OCR、AI Token、访问日志和报告任务。
- 定义 OCR 任务持久化对象和 Mapper/Manage 封装。
- 提供 MyBatis-Plus Mapper 和 Manage 封装，供业务服务查询、保存和统计数据。
- 提供 InfluxDB 配置与股票、指数、可转债分钟走势读写封装。
- 提供股票/指数排序枚举和行情 JSON 解析工具。
- 行情类 PO 负责第三方响应和中间行情数据到持久化对象的静态构造，例如 API 响应解析、Tushare 行转换和日 K 聚合为周/月 K。

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
| `StockKlinePO` | `stock_daily_kline` | 股票日 K 数据。 |
| `StockIntradayTrendPO` | InfluxDB `stock_intraday / stock_minute` | 股票分钟级分时走势。 |
| `IndexConfigPO` | `index_config` | 启用的指数清单和腾讯行情 `secid` 配置。 |
| `IndexQuoteSnapshotPO` | `index_quote_snapshot` | 指数最新行情快照。 |
| `IndexKlinePO` | `index_kline` | 指数 K 线数据。 |
| `IndexIntradayTrendPO` | InfluxDB `index_intraday / index_minute` | 指数分钟级分时走势。 |
| `BondConfigPO` | `bond_config` | 启用的可转债清单和腾讯行情 `secid` 配置。 |
| `BondQuoteSnapshotPO` | `bond_quote_snapshot` | 可转债最新行情快照。 |
| `BondKlinePO` | `bond_kline` | 可转债 K 线数据。 |
| `BondIntradayTrendPO` | InfluxDB `bond_intraday / bond_minute` | 可转债分钟级分时走势。 |
| `AiTokenUsageLogPO` | `ai_token_usage_log` | AI 模型 Token 用量日志。 |
| `AppVisitLogPO` | `app_visit_log` | `/api/**` 访问日志。 |
| `OcrTaskPO` | `ocr_task` | OCR 文件上传、手动文本导入和识别任务状态。 |
| `OcrTaskStagePO` | `ocr_task_stage` | OCR / chunk 各阶段输入、输出、指标和错误记录。 |
| `OcrReviewPO` | `ocr_review` | OCR 和手动知识导入复核草稿。 |
| `KnowledgeVectorPO` | `knowledge_vector` | 知识库文本、向量和 metadata。 |
| `StockAlertConfigPO` | `stock_alert_config` | 预警配置。 |
| `WatchGroupPO` | `watch_group` | 投资观察池分组。 |
| `WatchGroupItemPO` | `watch_group_item` | 投资观察池标的。 |
| `StockIndustryInfoPO` | `stock_industry_info` | 股票行业、地域和概念信息。 |
| `StockValuationHistoryPO` | `stock_valuation_history` | 股票 PE/PB 等估值历史。 |
| `StockFinancialIndicatorPO` | `stock_financial_indicator` | 股票财务指标和银行专项指标。 |
| `StockDividendHistoryPO` | `stock_dividend_history` | 股票分红股息历史。 |
| `SceneAnalysisTaskPO` | `scene_analysis_task` | 场景分析任务状态、上下文和召回 payload。 |
| `SceneAnalysisReportPO` | `scene_analysis_report` | 场景分析报告历史版本。 |
| `SceneAnalysisConfigProfilePO` | `scene_analysis_config_profile` | 场景分析报告配置档。 |

## 请求参数

| Param | 用途 | 关键字段 |
| --- | --- | --- |
| `LoginParam` | 登录 | `username`, `password` |
| `RegisterParam` | 注册 | `username`, `password`, `confirmPassword` |
| `StockQuoteListParam` | 股票行情列表 | `marketCode`, `limit`, `sortField`, `sortOrder` |
| `StockIntradayTrendParam` | 股票分时走势 | `stockCode` |
| `StockKlineParam` | 股票日 K | `stockCode`, `secid`, `startDate`, `endDate`, `limit` |
| `IndexQuoteListParam` | 指数行情列表 | `marketCode`, `limit`, `sortField`, `sortOrder` |
| `IndexKlineParam` | 指数 K 线 | `indexCode`, `secid`, `startDate`, `endDate`, `limit` |
| `BondQuoteListParam` | 可转债行情列表 | `limit`, `sortField`, `sortOrder` |
| `BondKlineParam` | 可转债 K 线 | `bondCode`, `secid`, `startDate`, `endDate`, `limit` |
| `WatchGroupSaveParam` | 观察池分组新增/更新 | `id`, `groupName` |
| `WatchGroupItemSaveParam` | 观察池标的新增/更新 | `id`, `groupId`, `targetType`, `targetCode`, `targetName`, `secid`, `remark`, `buyPrice`, `position` |
| `StockAlertConfigSaveParam` | 预警配置新增/更新 | `id`, `targetType`, `stockCode`, `thresholdPercent`, `enabled` |
| `KnowledgeChunkUpdateParam` | 知识库 chunk 编辑 | `text`, `scenes`, `reembed` |
| `ChangePasswordParam` | 修改密码 | `oldPassword`, `newPassword`, `confirmPassword` |
| `UpdateProfileParam` | 更新用户资料 | `realName`, `introduction`, `email`, `phone` |
| `UpdateNotificationParam` | 更新通知设置 | `emailNotification` |

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

可转债行情 `sortField` 支持：

- `bondCode`
- `latestPrice`
- `changePercent`
- `volume`
- `turnoverAmount`
- `turnoverRate`
- `amplitude`
- `syncedAt`

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

验证完整 Java 后端 Reactor 时，在 `backend-java` 目录执行：

```bash
mvn test
```
