# Database Services

本目录维护后端本地数据库依赖、PostgreSQL 初始化脚本和 InfluxDB 本地服务配置。

## 服务组成

| 服务 | 说明 |
| --- | --- |
| PostgreSQL | 业务关系型数据库，默认库名 `finance_management`。 |
| database-init | 等待 PostgreSQL 可用后，创建业务库并按文件名顺序执行 `migrations` 和 `seed`。 |
| InfluxDB | 股票分钟级分时走势时序库，默认 bucket 为 `stock_intraday`。 |

## 启动数据库依赖

在项目根目录执行：

```bash
docker compose -f database/docker-compose.yml up -d
```

常用地址：

- PostgreSQL：`localhost:${POSTGRES_PORT:-5432}`
- InfluxDB：`http://localhost:${INFLUXDB_PORT:-8086}`

## 初始化流程

`database-init` 使用 `init_database.py` 执行以下步骤：

1. 读取项目根目录 `.env`。
2. 等待 PostgreSQL 可连接。
3. 如果目标数据库不存在，则创建数据库。
4. 按文件名顺序执行 `database/migrations/*.sql`。
5. 按文件名顺序执行 `database/seed/*.sql`。

## 迁移脚本

| 文件 | 说明 |
| --- | --- |
| `001_create_stock_config.sql` | 创建股票配置表 `stock_config`。 |
| `002_create_stock_market_sync_tables.sql` | 创建股票最新行情快照表 `stock_quote_snapshot`。 |
| `003_create_app_user.sql` | 创建系统用户表 `app_user`，并初始化 `admin / 123456`。 |
| `004_create_index_market_tables.sql` | 创建指数配置、指数行情快照、指数日 K 表。 |
| `005_insert_core_index_config.sql` | 初始化核心指数配置。 |
| `006_create_ai_token_usage_log.sql` | 创建 AI Token 用量日志表。 |
| `007_create_app_visit_log.sql` | 创建应用访问日志表。 |
| `008_create_ocr_task.sql` | 创建 OCR 识别任务表。 |

## 种子数据

| 文件 | 说明 |
| --- | --- |
| `seed/001_insert_stock_config.sql` | 初始化默认关注股票配置。 |
| `seed/002_insert_index_config.sql` | 初始化默认指数配置。 |

## 表用途

| 表 | 用途 |
| --- | --- |
| `app_user` | 登录用户、角色和默认首页。 |
| `stock_config` | 股票同步清单和腾讯行情 `secid`。 |
| `stock_quote_snapshot` | 股票最新行情快照。 |
| `index_config` | 指数同步清单和腾讯行情 `secid`。 |
| `index_quote_snapshot` | 指数最新行情快照。 |
| `index_daily_kline` | 指数日 K 数据。 |
| `ai_token_usage_log` | AI 模型 Token 用量日志。 |
| `app_visit_log` | 后端 API 访问日志。 |
| `ocr_task` | OCR 文件上传和识别任务状态。 |

股票分钟级分时走势不存 PostgreSQL，由 Java 后端写入 InfluxDB。

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `POSTGRES_PORT` | `5432` | 本地 PostgreSQL 映射端口。 |
| `POSTGRES_DB` | `finance_management` | 业务库名。 |
| `POSTGRES_ADMIN_DATABASE` | `finance_management` / `postgres` | 容器初始化库或脚本连接的管理库。 |
| `POSTGRES_USER` | `postgres` | PostgreSQL 用户名。 |
| `POSTGRES_PASSWORD` | `123456` | PostgreSQL 密码。 |
| `DATABASE_INIT_MAX_ATTEMPTS` | `30` | 初始化脚本等待数据库最大重试次数。 |
| `DATABASE_INIT_RETRY_SECONDS` | `2` | 初始化脚本重试间隔秒数。 |
| `INFLUXDB_PORT` | `8086` | 本地 InfluxDB 映射端口。 |
| `INFLUXDB_ORG` | `finance` | InfluxDB 组织。 |
| `INFLUXDB_BUCKET` | `stock_intraday` | 股票分时 bucket。 |
| `INFLUXDB_ADMIN_TOKEN` | `finance-management-local-token` | InfluxDB 管理 Token。 |

## 单独启动 InfluxDB

```bash
docker compose -f database/influxdb/docker-compose.yml up -d
```
