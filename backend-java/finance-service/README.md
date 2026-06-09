# finance-service 主业务模块

## 服务定位

`finance-service` 是理财分析 AI 项目的主业务模块，不独立启动。它负责认证业务入口、用户设置、行情同步任务、外部行情 API 调用、观察池、预警和普通业务编排。应用由 `finance-app` 启动，公共数据层来自 `finance-data`，登录态能力来自 `finance-security`，AI Agent 与知识库能力来自 `finance-ai`。

## 基础信息

- 包名：`com.scrapider.finance`
- 启动模块：`finance-app`
- 默认端口：`8081`
- Web 框架：Spring Boot 3.x
- 权限：来自 `finance-security`
- ORM：MyBatis-Plus
- 定时任务：Spring Scheduling
- 关系型数据库：PostgreSQL
- 时序数据库：InfluxDB
- 外部数据源：腾讯行情、Tushare、东方财富

## 目录结构

```text
backend-java/finance-service/
├── Dockerfile
├── pom.xml
└── src/main/
    ├── java/com/scrapider/finance/
    │   ├── api/                               # 腾讯行情、Tushare、东方财富等外部数据 API
    │   ├── config/                            # RestTemplate、WebMvc、DeepSeek 请求配置
    │   ├── converter/                         # 认证、行情查询、观察池、预警、系统配置等对象转换
    │   ├── controller/                        # Auth、行情、观察池、预警接口
    │   ├── interceptor/                       # 访问日志拦截器
    │   ├── service/                           # 业务服务接口
    │   │   ├── impl/                          # 业务服务实现
    │   │   └── provider/                      # 行情、K 线、基本面等数据提供者接口
    │   │       └── impl/                      # 腾讯行情、Tushare、东方财富 provider 实现
    │   └── task/                              # 股票和指数行情同步任务
    └── resources/
        └── application.yml                    # 应用配置
```

## 转换层约定

`converter` 包负责本模块的对象拷贝、PO/DTO/VO 装配、对象转 Map、Map 转对象和业务字段收敛。行情查询结果的 PO 到 VO 转换由 `MarketQueryConverter` 统一处理，`service.impl` 只保留业务流程、校验、查询、同步和事务编排；可替换的数据源能力放入 `service.provider` 和 `service.provider.impl`；新增手动转换逻辑时应放入 converter，避免在业务服务中分散维护。

## 接口分组

| 分组 | 路径 | 说明 |
| --- | --- | --- |
| 认证 | `/api/auth/**` | 登录、注册、登出、刷新 Token、权限码占位接口。 |
| 用户 | `/api/user/**` | 当前用户信息、资料更新、密码修改和通知设置。 |
| 股票 | `/api/stocks/**` | 股票最新行情列表、单只股票最新一批分时走势、单只股票日 K 同步。 |
| 指数 | `/api/indices/**` | 指数最新行情列表、指数日 K。 |
| 可转债 | `/api/bonds/**` | 可转债最新行情列表、可转债日 K、同步状态和手动同步。 |
| 投资观察池 | `/api/watch-pool/**` | 观察池分组和标的管理。 |
| 股票预警 | `/api/stock-alerts/**` | 股票/指数/可转债预警配置、标的选项和手动检查。 |
| AI | `/api/ai/**` | 由 `finance-ai` 提供，随 `finance-app` 启动后暴露，包括 Chat、OCR、Token 用量和控制台指标。 |
| 知识库 | `/api/knowledge/**` | 由 `finance-ai` 提供，随 `finance-app` 启动后暴露，包括知识库分页、统计、概览和 chunk 编辑。 |

`docs/API_DOCUMENTATION.md` 中维护了接口级参数和返回字段说明。

## 权限规则

```text
匿名可访问：
POST /api/auth/login
POST /api/auth/register

登录后可访问：
/api/ai/**

admin 角色可访问：
除上述规则外的其他接口
```

登录后使用以下请求头传递 Token：

```http
Authorization: Bearer <accessToken>
```

当前密码编码器为 Base64 编码，默认初始化用户来自数据库迁移：

```text
username: admin
password: 123456
role: admin
```

## 核心业务

### 认证与用户

- 登录成功后返回 `accessToken`。
- 注册时校验用户名、密码和确认密码，并默认创建 `admin` 角色用户。
- 登出会从内存 `TokenStore` 移除当前 Token。
- 刷新 Token 接口当前返回原 Token。
- 用户信息接口返回用户 ID、用户名、展示名、头像、角色、默认首页和当前 Token。
- 支持更新用户资料、修改密码和更新通知设置。

### 股票行情

- 定时读取启用的 `stock_config` 股票配置。
- 调用腾讯行情接口同步股票最新行情快照到 PostgreSQL。
- 调用腾讯分时接口同步股票分钟走势到 InfluxDB。
- 查询接口支持按市场过滤、限制数量、排序字段和排序方向。
- 分时查询返回指定股票最新同步批次的数据。
- 支持按需同步单只股票分时走势和日 K 数据。

### 指数行情

- 定时读取启用的 `index_config` 指数配置。
- 调用腾讯行情接口同步指数最新行情快照到 PostgreSQL。
- 可同步指数日 K 到 PostgreSQL。
- 查询接口支持指数行情列表和指定指数/`secid` 的日 K。

### 可转债行情

- 定时读取启用的 `bond_config` 可转债配置。
- 调用腾讯行情接口同步可转债最新行情快照到 PostgreSQL。
- 可同步可转债日 K 到 PostgreSQL。
- 查询接口支持可转债行情列表和指定可转债/`secid` 的日 K。

### 投资观察池

- 支持用户维度的观察池分组新增、更新、删除。
- 支持股票、指数、可转债多类型标的新增、更新、删除和排序。
- 支持保存买入价和买入价格位置，便于前端展示标的观察状态。

### 股票预警

- 支持股票、指数、可转债预警配置列表、新增/更新、删除。
- 支持按涨跌幅阈值设置预警。
- 支持查询可创建预警的标的选项。
- 支持定时检查和管理员手动触发检查。

### 访问日志

`AppVisitLogInterceptor` 会记录 `/api/**` 请求，排除 `/api/ai/console/**`，记录用户名、请求方法、路径、状态码、耗时、客户端地址和 User-Agent。

## 配置项

### 数据库

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/finance_management` | PostgreSQL JDBC 地址。 |
| `POSTGRES_USERNAME` | `postgres` | PostgreSQL 用户名。 |
| `POSTGRES_PASSWORD` | `123456` | PostgreSQL 密码。 |
| `INFLUXDB_URL` | `http://localhost:8086` | InfluxDB 地址。 |
| `INFLUXDB_ADMIN_TOKEN` | `finance-management-local-token` | InfluxDB Token。 |
| `INFLUXDB_ORG` | `finance` | InfluxDB Org。 |
| `INFLUXDB_BUCKET` | `stock_intraday` | InfluxDB Bucket。 |

### AI

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DEEPSEEK_API_KEY` | 空 | DeepSeek/OpenAI 兼容接口 Key。 |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | 模型服务地址。 |
| `DEEPSEEK_COMPLETIONS_PATH` | `/chat/completions` | Chat Completions 路径。 |
| `DEEPSEEK_MODEL` | `deepseek-v4-pro` | 默认模型。 |
| `DEEPSEEK_TEMPERATURE` | `0.3` | 生成温度。 |
| `DEEPSEEK_REASONING_EFFORT` | `high` | 推理强度。 |
| `DEEPSEEK_THINKING_ENABLED` | `true` | 是否启用思考模式。 |
| `DEEPSEEK_THINKING_TYPE` | `enabled` | 思考模式类型。 |

### 同步任务

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `STOCK_SYNC_ENABLED` | `true` | 是否启用股票同步。 |
| `STOCK_SYNC_INITIAL_DELAY_MS` | `10000` | 股票同步首次延迟。 |
| `STOCK_SYNC_FIXED_DELAY_MS` | `120000` | 股票同步固定延迟。 |
| `STOCK_SYNC_REQUEST_INTERVAL_MS` | `1500` | 股票接口请求间隔。 |
| `STOCK_SYNC_START_TIME` | `09:29` | 股票同步窗口开始。 |
| `STOCK_SYNC_END_TIME` | `16:00` | 股票同步窗口结束。 |
| `MARKET_TRADING_CLOSED_DATES` | 2026 年沪深交易所公告休市日 | 自动同步闭市日期，逗号分隔，格式 `yyyy-MM-dd`；周六、周日会自动跳过。 |
| `INDEX_SYNC_ENABLED` | `true` | 是否启用指数同步。 |
| `INDEX_DAILY_KLINE_SYNC_ENABLED` | `true` | 是否同步指数日 K。 |
| `INDEX_DAILY_KLINE_LIMIT` | `250` | 指数日 K 同步条数。 |
| `INDEX_SYNC_FIXED_DELAY_MS` | `300000` | 指数同步固定延迟。 |
| `BOND_SYNC_ENABLED` | `true` | 是否启用可转债同步。 |
| `BOND_DAILY_KLINE_SYNC_ENABLED` | `true` | 是否同步可转债日 K。 |
| `BOND_DAILY_KLINE_LIMIT` | `250` | 可转债日 K 同步条数。 |
| `BOND_SYNC_INITIAL_DELAY_MS` | `20000` | 可转债同步首次延迟。 |
| `BOND_SYNC_FIXED_DELAY_MS` | `300000` | 可转债同步固定延迟。 |
| `BOND_SYNC_REQUEST_INTERVAL_MS` | `1500` | 可转债接口请求间隔。 |
| `BOND_SYNC_START_TIME` | `09:28` | 可转债同步窗口开始。 |
| `BOND_SYNC_END_TIME` | `16:00` | 可转债同步窗口结束。 |
| `BOND_SYNC_TIMEZONE` | `Asia/Shanghai` | 可转债同步时区。 |

## 启动方式

### 本地启动

项目未提交 Maven Wrapper，本地需要先安装 Maven，并使用 JDK 17+。

```bash
mvn -pl backend-java/finance-app -am spring-boot:run
```

### Docker 启动

在项目根目录执行：

```bash
docker compose -f docker/docker-compose.yml up --build finance-service
```

## 构建

```bash
mvn -pl backend-java/finance-app -am package
```

验证完整 Java 后端 Reactor 时，也可以在 `backend-java` 目录执行：

```bash
mvn test
```
