# Java 后端模块

本目录是理财分析 AI 项目的 Java 后端聚合工程，采用 Maven 多模块组织。后端应用由 `finance-service` 启动，公共数据层由 `finance-data` 提供，AI 编排能力由 `finance-ai` 提供。

## 模块划分

| 模块 | 是否独立启动 | 主要职责 |
| --- | --- | --- |
| `finance-service` | 是 | Spring Boot 主应用、认证登录、用户设置、行情查询与同步、观察池、预警、安全配置和应用配置。 |
| `finance-data` | 否 | 公共领域对象、请求参数、返回对象、MyBatis-Plus Mapper、Manage 封装、PostgreSQL 表映射和 InfluxDB 读写配置。 |
| `finance-ai` | 否 | AI Chat、Query Rewrite、行情上下文、OCR、手动知识导入、知识库、投资报告、Token 用量统计和 AI 控制台指标。 |

## 调用关系

```text
finance-service
├── 依赖 finance-data：复用 PO / VO / Param / Mapper / Manage / InfluxDB 配置
└── 依赖 finance-ai：暴露 /api/ai/** 接口和 AI 业务能力

外部请求 -> Controller -> Service -> Manage / API / Mapper -> Domain
```

`finance-service` 的 `FinanceApplication` 会扫描 `com.scrapider.finance` 根包，因此 `finance-data` 和 `finance-ai` 中同根包下的 Bean 会随主应用一起装配。

## 技术栈

- Java 17+
- Spring Boot 3.x
- Spring Security
- Spring Scheduling
- Spring AI OpenAI 兼容接口
- MyBatis-Plus
- PostgreSQL
- InfluxDB 2.x
- Lombok
- Hutool

## 运行方式

### 本地启动主服务

在项目根目录执行：

```bash
mvn -pl backend-java/finance-service -am spring-boot:run
```

默认端口为 `8081`。

### Docker 启动主服务及依赖

```bash
docker compose -f docker/docker-compose.yml up --build finance-service
```

该命令会同时拉起 PostgreSQL、InfluxDB 和数据库初始化容器。

## 构建

构建整个 Java 后端聚合工程：

```bash
mvn -pl backend-java -am package
```

构建单个模块及其依赖：

```bash
mvn -pl backend-java/finance-data -am package
mvn -pl backend-java/finance-ai -am package
mvn -pl backend-java/finance-service -am package
```

## 配置入口

主配置文件位于：

```text
backend-java/finance-service/src/main/resources/application.yml
```

核心配置分组：

- `server`：服务端口。
- `spring.datasource`：PostgreSQL 连接。
- `spring.ai.openai`：DeepSeek/OpenAI 兼容模型配置。
- `deepseek.chat`：DeepSeek 思考模式相关配置。
- `stock.sync`：股票行情同步任务配置。
- `index.sync`：指数行情同步任务配置。
- `bond.sync`：可转债行情同步任务配置。
- `influxdb`：股票分钟走势 InfluxDB 配置。

## 接口范围

后端当前暴露以下接口分组：

- `/api/auth/**`：登录、注册、登出、刷新 Token。
- `/api/user/**`：当前用户信息、资料更新、密码修改和通知设置。
- `/api/stocks/**`：股票行情、分时走势和日 K 同步。
- `/api/indices/**`：指数行情和日 K 查询。
- `/api/bonds/**`：可转债行情和日 K 查询。
- `/api/watch-pool/**`：投资观察池分组和标的管理。
- `/api/stock-alerts/**`：预警配置、标的选项和手动检查。
- `/api/ai/chat`：理财分析 AI 对话。
- `/api/ai/ocr/**`：OCR 文件任务和人工复核。
- `/api/ai/manual-knowledge/**`：手动知识导入。
- `/api/knowledge/**`：知识库查询、概览和 chunk 编辑。
- `/api/ai/scene-analysis/**`：投资报告任务、配置、历史和重新生成。
- `/api/ai/token-usage/**`：AI Token 用量记录与统计。
- `/api/ai/console/**`：AI 控制台概览和访问趋势。

`docs/API_DOCUMENTATION.md` 对接口参数和返回结构做了集中说明。

## 权限规则

- `POST /api/auth/login` 和 `POST /api/auth/register` 允许匿名访问。
- `/api/ai/**` 需要登录认证。
- 其他接口需要 `admin` 角色。
- Token 使用 `Authorization: Bearer <accessToken>` 传递。

## 数据来源

- 股票/指数/可转债最新行情、股票分时、股票/指数/可转债日 K：腾讯行情接口。
- 行情快照、日 K、用户、观察池、预警、OCR、知识库、报告、Token 用量、访问日志：PostgreSQL。
- 股票分钟分时走势：InfluxDB。
