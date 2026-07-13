# 行情同步可靠性修复实施计划

> **执行约束：** 不创建分支、不提交 Git；不新增单元测试。仅在现有交易日历测试因构造方式变更而无法编译时做最小适配，并运行已有编译/测试作为验证。

**目标：** 以 Tushare `trade_cal` 取代 2026 年手工休市日期，保障外部行情请求有边界，并让可转债补跑只在全部启用债券当日估值已落库后才跳过。

**架构：** 交易日历结果写入 PostgreSQL 的年度缓存表；服务查询当天缓存，缺失时以 SSE 日历按年份向 Tushare 刷新。行情 API 改用专用、带连接和读取超时的 `RestTemplate`，不影响 DeepSeek 的客户端。任务节流复用代码已有的 1500ms 默认值，删除 YAML 的 `0` 覆盖。可转债补跑改为按“全部启用债券 + 当日 trade_date”判断，而不是按任意一条 `synced_at` 判断。

**技术栈：** Java 17、Spring Boot 3、MyBatis-Plus、PostgreSQL、Tushare。

---

### 任务 1：持久化并动态刷新交易日历

**涉及文件：**

- 新增：`database/migrations/07_init_market_trading_calendar.sql`
- 新增：`backend-java/finance-data/src/main/java/com/scrapider/finance/domain/po/MarketTradingCalendarPO.java`
- 新增：`backend-java/finance-data/src/main/java/com/scrapider/finance/mapper/MarketTradingCalendarMapper.java`
- 新增：`backend-java/finance-data/src/main/java/com/scrapider/finance/manage/MarketTradingCalendarManage.java`
- 修改：`backend-java/finance-service/src/main/java/com/scrapider/finance/service/MarketTradingCalendarService.java`
- 修改：`backend-java/finance-app/src/main/resources/application.yml`
- 必要时适配：`backend-java/finance-service/src/test/java/com/scrapider/finance/service/MarketTradingCalendarServiceTest.java`

**步骤：**

1. 新建幂等迁移，建立以 `exchange_code + calendar_date` 唯一约束的交易日历缓存表。
2. 按项目 PO / Mapper / Manage 分层新增数据访问能力：按交易所和日期读取，并批量幂等保存 Tushare 行。
3. 替换静态 `closed-dates` 判断：先读取缓存；缓存缺少当日记录时，用 Tushare `trade_cal` 请求该自然年并写库；将 `is_open=1` 作为唯一交易日依据。
4. 对同一年度的首次刷新做进程内串行化；Tushare 调用或存储失败时记录告警并返回非交易日，避免在未知日期盲目同步。
5. 删除 `market.trading.closed-dates` 配置及 2026 常量，不再依赖人工维护日期。

### 任务 2：隔离行情 HTTP 客户端并恢复既有请求节流

**涉及文件：**

- 修改：`backend-java/finance-service/src/main/java/com/scrapider/finance/config/RestTemplateConfig.java`
- 修改：`backend-java/finance-service/src/main/java/com/scrapider/finance/api/StockMarketApi.java`
- 修改：`backend-java/finance-service/src/main/java/com/scrapider/finance/api/EastMoneyApi.java`
- 修改：`backend-java/finance-service/src/main/java/com/scrapider/finance/api/TushareApi.java`
- 修改：`backend-java/finance-app/src/main/resources/application.yml`

**步骤：**

1. 保留默认 `RestTemplate` 供 AI 客户端使用，新增具名 `marketRestTemplate`，设置连接超时 3000ms、读取超时 15000ms。
2. 为腾讯、东方财富和 Tushare API 注入具名行情客户端，确保网络阻塞在同步任务内有明确上限。
3. 删除 `stock.sync`、`index.sync`、`bond.sync` 中的 `request-interval-ms: 0` 覆盖，使各任务复用代码既有的 1500ms 默认节流，不引入新的业务阈值。
4. 在 YAML 中显式写出新的 HTTP 技术超时，便于部署侧调整。

### 任务 3：修复可转债补跑完整性判定

**涉及文件：**

- 修改：`backend-java/finance-data/src/main/java/com/scrapider/finance/manage/ConvertibleBondDailyValuationManage.java`
- 修改：`backend-java/finance-service/src/main/java/com/scrapider/finance/task/ConvertibleBondDataSyncTask.java`

**步骤：**

1. 在估值 Manage 增加按债券代码集合和 `trade_date` 判断“是否每只都有当日估值”的查询，空集合或日期无效时不视为完成。
2. 在启动补跑前读取当前启用债券，过滤空代码；仅当存在有效债券且并非全部拥有当日估值时启动同步。
3. 删除仅凭任意一条 `synced_at` 记录完成的旧判断，避免部分失败被错误跳过。

### 任务 4：复核与验证

**步骤：**

1. 审核并行改动的文件范围、Spring 注入限定符、PO 字段和迁移幂等性，确认未触碰用户已有的 `database/rabbitmq/definitions.json` 改动。
2. 运行 Maven 模块编译；如现有交易日历测试已适配且可独立执行，运行该已有测试，不新增任何测试用例。
3. 使用 `git diff --check`、配置检索和调用链检查确认：无 `closed-dates`、三处 `request-interval-ms: 0` 已移除、补跑不再调用旧的任意记录判定。
