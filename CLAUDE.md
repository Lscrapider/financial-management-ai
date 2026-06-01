# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

编写代码前请先阅读 [AGENTS.md](AGENTS.md) 和 [docs/CODEX_GUIDELINES.md](docs/CODEX_GUIDELINES.md)，并遵循其中的要求。项目文档使用中文。非 trivial 实现任务请使用 `karpathy-guidelines` skill。

## 项目概览

理财分析 AI 系统，Java + Python + Vue 混合架构。Java Spring Boot 负责业务系统、任务调度、接口聚合；Python RabbitMQ worker 负责 AI 计算（OCR、Embedding、向量检索、LLM 打标）；Vue 3 (Vben Admin v5) 负责前端展示；PostgreSQL + pgvector + InfluxDB 负责存储。

## 常用命令

### 环境准备

```bash
# 复制 .env.example 并填写实际密码/Key
cp .env.example .env

# 启动数据库依赖（PostgreSQL + InfluxDB + RabbitMQ + MinIO）
docker compose -f docker/docker-compose.yml up -d postgres influxdb rabbitmq minio minio-init database-init
```

### Java 后端

```bash
# 本地启动主服务（端口 8081）
mvn -pl backend-java/finance-service -am spring-boot:run

# 构建整个 Java 后端
mvn -pl backend-java -am package

# 构建单个模块
mvn -pl backend-java/finance-service -am package
mvn -pl backend-java/finance-ai -am package
mvn -pl backend-java/finance-data -am package

# 运行全部测试
mvn -pl backend-java -am test

# 运行单个模块测试
mvn -pl backend-java/finance-service -am test

# 运行单个测试类
mvn -pl backend-java/finance-service -am test -Dtest=ClassName
```

### Python AI 服务

```bash
# 安装依赖
pip install -r ai-python/requirements.txt

# 启动 RabbitMQ worker（消息队列消费者）
python -m ai-python.app.worker.main

# 运行单个测试
python ai-python/test/chunk_rule_tagger_test.py
```

### 前端

```bash
cd frontend-vue
pnpm install
pnpm dev:ele          # 启动开发服务器（端口 5777）
pnpm build:ele        # 构建
pnpm lint             # oxlint + eslint
pnpm format           # oxfmt 格式化
pnpm check:type       # TypeScript 类型检查
pnpm test:unit        # Vitest 单元测试
pnpm test:unit -- --run <pattern>  # 运行匹配的单个测试文件
```

### Docker 完整启动

```bash
docker compose -f docker/docker-compose.yml up -d   # 启动全部服务
docker compose -f docker/docker-compose.yml up --build finance-service  # 构建并启动 Java 服务
```

## 架构要点

### 服务边界

- **Java `finance-service`**（端口 8081）：主应用，Spring Boot 3.x + Spring Security + MyBatis-Plus + Spring AI。对外暴露 `/api/**` 接口，包含认证、行情查询、AI Chat、OCR 任务、Token 用量、控制台指标。
- **Java `finance-data`**：公共数据层，PO/VO/Param/Mapper/Manage/InfluxDB 配置，不独立启动。
- **Java `finance-ai`**：AI 编排层，Chat/Query Rewrite/行情上下文查询/Token 统计/OCR 复核/知识库浏览，不独立启动。
- **Python worker**：独立进程，消费 RabbitMQ 执行 OCR 全链路（文档标准化 → OCR 识别 → 文本清洗 → 向量索引）。也处理 embedding 生成和 pgvector 写入。
- **前端**：Vben Admin v5 monorepo（Turborepo + pnpm），Element Plus 版本。`apps/web-ele/` 为主应用。

### Python 模块结构

- `app/worker/`：RabbitMQ worker 入口，`main.py` 启动消费者
- `app/messaging/`：消息队列基础设施（`rabbit_worker.py` 通用 worker、`base_handler.py` 处理器基类、`models.py` 消息模型、`registry.py` 阶段注册、`errors.py` 异常定义）
- `app/ocr/`：OCR 全链路处理（`handlers/` 各阶段处理器、`services/` 业务逻辑、`engines/` OCR 引擎封装、`repository.py` 数据库操作、`storage.py` MinIO 操作）
- `app/embeddings/`：embedding 生成与 pgvector 写入
- `app/retrieval/`：知识库向量检索
- `app/api/`：FastAPI 路由（内部接口）
- `test/`：测试文件（直接 `python test/xxx_test.py` 运行）

### Java 分层规范（CODEX_GUIDELINES.md 核心规则）

调用链路：`Controller → Service → Manage / API / Mapper → Domain`

- `controller`：只做接口入口，不写业务逻辑
- `service`/`service.impl`：业务逻辑
- `manage`：MyBatis-Plus 最小数据库操作封装，不放业务编排
- `mapper`：数据库访问接口，只用 MP 函数式查询不够时才手写 SQL
- `api`：第三方接口调用（如腾讯行情）
- `domain/param`：请求入参，`domain/vo`：返回前端，`domain/po`：数据库对象，`domain/dto`：内部传输
- 不得将 PO 直接返回前端
- Controller 只调用 Service，不直接调 Mapper
- `JsonNode`/第三方响应构建 PO 的逻辑放到 PO 的静态方法中（如 `StockQuoteSnapshotPO.fromApiResponse(...)`）
- 判空、字符串、集合、数字、日期等通用操作优先用 Hutool
- Java 类调用自身方法时必须显式使用 `this`
- 集合处理、回调和函数式接口逻辑优先使用 lambda 风格
- 未使用的类、方法、字段、import、依赖必须删除，不为"以后可能用到"保留死代码
- 分页接口使用 `pageSize`/`pageNum`
- MyBatis-Plus 分页依赖 `MybatisPlusInterceptor` + `PaginationInnerInterceptor(DbType.POSTGRE_SQL)`，配置在 `MybatisPlusConfig`
- Controller 不写局部 `@ExceptionHandler`，由全局 `@RestControllerAdvice` 统一处理，异常处理器必须打印异常对象保留完整堆栈

### 接口权限

- `POST /api/auth/login`、`POST /api/auth/register`：匿名
- `/api/ai/ocr/reviews/*/pages/*/image`：匿名（页面图片代理）
- `/api/**`：需登录认证
- Token 通过 `Authorization: Bearer <accessToken>` 传递

### 数据存储分工

- PostgreSQL：业务数据、行情快照（股票/指数/可转债）、日K线（指数/可转债）、用户、OCR 任务、Token 日志、访问日志
- pgvector：知识库向量表 `knowledge_vector`
- InfluxDB：股票/可转债分钟级分时走势
- MinIO：OCR 原始文件和各阶段产物
- RabbitMQ：OCR 阶段消息传递（topic exchange `finance.ocr.topic`，retry exchange `finance.ocr.retry.topic`，DLX `finance.ocr.dlx`）

### 行情数据流

腾讯行情 API → Java 定时任务（`task/` 目录，三个市场：`StockMarketSyncTask`/`IndexMarketSyncTask`/`BondMarketSyncTask`）→ PostgreSQL（快照，批量拉取批量 upsert）/ InfluxDB（分时走势）→ Java 接口 → 前端

### AI Chat 流程

用户问题 → `AiChatController` → Query Rewrite（判断是否理财范围）→ 行情上下文查询 → Spring AI ChatClient（DeepSeek）→ Token 用量记录 → 返回结构化回答

### OCR 流程

8 个阶段通过 RabbitMQ 串联，详见 [docs/OCR_PIPELINE.md](docs/OCR_PIPELINE.md)：

1. `document.normalize`（Python）：PDF 转 PNG 分页，写入 MinIO
2. `ocr.recognize`（Python）：图片调用 `qwen-vl-ocr-latest`，PDF 调用 OpenDataLoader 解析并适配为标准 OCR segments
3. `text.clean`（Python）：清洗文本、标记低置信度/乱码
4. `quality.validate`（Java）：创建人工复核任务
5. `chunk.tag.rule`（Python）：规则引擎生成场景标签，质量不足则转 LLM
6. `chunk.tag.llm`（Python）：LLM 对低质量 chunk 补标签（白名单约束）
7. `chunk.tag.correct`（Python）：合并规则+LLM 标签、去重、补齐 7 大类，产出 `tagged_reviewed.json`
8. `embedding.index`（Python）：生成 embedding，写入 `knowledge_vector`（先删后插）

## 环境变量

主配置在 `.env`（从 `.env.example` 复制）。关键变量：

- `POSTGRES_*`：数据库连接（默认库 `finance_management`）
- `INFLUXDB_*`：InfluxDB 连接
- `RABBITMQ_*`：RabbitMQ 连接
- `MINIO_*`：MinIO 连接（OCR 文件存储）
- `DEEPSEEK_*`：DeepSeek API 配置（AI Chat 模型）
- `DASHSCOPE_*`：阿里云 DashScope（OCR 识别模型 `qwen-vl-ocr-latest`）
- `STOCK_SYNC_*`、`INDEX_SYNC_*`、`BOND_SYNC_*`：行情同步任务配置
- `stock.sync.trend-enabled`：定时任务是否同步分时数据（默认 true）

Java 主配置文件：`backend-java/finance-service/src/main/resources/application.yml`（被 .gitignore 排除，需从模板生成）。

## 前端关键约定

- 包管理器：pnpm >= 11.0.0，不允许 npm/yarn
- Node：^22.18.0 || ^24.0.0
- 导入别名 `#/` 指向 `apps/web-ele/src/`
- API 响应格式：`{ code: 0, data: ... }`
- `@vben/request` 的 `requestClient` 默认 `responseReturn: 'data'`，自动解包；需要完整响应用 `baseRequestClient` 或覆写 `responseReturn: 'body'`
- Pre-commit：Lefthook 自动 lint + typecheck
- 默认语言 zh-CN
