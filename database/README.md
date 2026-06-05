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
| `004_create_index_market_tables.sql` | 创建指数配置、指数行情快照、指数 K 线表。 |
| `005_insert_core_index_config.sql` | 初始化核心指数配置。 |
| `006_create_ai_token_usage_log.sql` | 创建 AI Token 用量日志表。 |
| `007_create_app_visit_log.sql` | 创建应用访问日志表。 |
| `008_create_ocr_task.sql` | 创建 OCR 识别任务表。 |
| `009_create_ocr_task_stage.sql` | 创建 OCR 阶段处理记录表。 |
| `010_create_ocr_review.sql` | 创建 OCR 人工复核任务表。 |
| `011_add_ocr_task_deleted_at.sql` | 为 OCR 任务增加软删除时间字段。 |
| `012_create_knowledge_vector.sql` | 创建知识库向量表 `knowledge_vector`。 |
| `013_create_stock_alert_config.sql` | 创建预警配置表 `stock_alert_config`。 |
| `014_add_user_profile_fields.sql` | 为用户表增加资料和通知设置字段。 |
| `015_drop_stock_alert_notify_email.sql` | 移除预警独立邮箱字段，改用用户通知设置。 |
| `017_add_target_type_to_stock_alert_config.sql` | 为预警配置增加标的类型字段。 |
| `018_create_watch_pool.sql` | 创建投资观察池分组和标的表。 |
| `020_add_bond_rating_to_bond_quote_snapshot.sql` | 为可转债行情快照增加评级字段。 |
| `021_add_buy_price_position_to_watch_group_item.sql` | 为观察池标的增加买入价和价格位置字段。 |
| `021_add_stock_quote_extra_fields.sql` | 为股票行情快照增加扩展行情字段。 |
| `022_add_bond_quote_extra_fields.sql` | 为可转债行情快照增加扩展行情字段。 |
| `023_add_chunk_fields_to_ocr_task_stage.sql` | 为 OCR 阶段记录增加 chunk 粒度字段。 |
| `024_add_source_type_to_ocr_task.sql` | 为 OCR 任务增加来源类型字段，用于区分文件 OCR 和手动文本导入。 |
| `025_create_stock_daily_kline.sql` | 创建股票日 K 表。 |
| `026_create_scene_analysis_task.sql` | 创建场景分析任务表。 |
| `027_create_stock_scene_data_tables.sql` | 创建股票场景分析所需的行业、估值历史、财务指标和分红历史表。 |
| `028_create_scene_analysis_report.sql` | 创建场景分析报告历史表。 |
| `029_create_scene_analysis_config_profile.sql` | 创建场景分析配置档表，并初始化系统推荐配置。 |

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
| `stock_daily_kline` | 股票日 K 数据。 |
| `stock_industry_info` | 股票行业、地域和概念信息。 |
| `stock_valuation_history` | 股票每日 PE/PB 等估值历史。 |
| `stock_financial_indicator` | 股票财务主指标和银行专项指标。 |
| `stock_dividend_history` | 股票分红股息历史。 |
| `index_config` | 指数同步清单和腾讯行情 `secid`。 |
| `index_quote_snapshot` | 指数最新行情快照。 |
| `index_kline` | 指数 K 线数据。 |
| `bond_config` | 可转债同步清单和腾讯行情 `secid`。 |
| `bond_quote_snapshot` | 可转债最新行情快照。 |
| `bond_kline` | 可转债 K 线数据。 |
| `ai_token_usage_log` | AI 模型 Token 用量日志。 |
| `app_visit_log` | 后端 API 访问日志。 |
| `ocr_task` | OCR 文件上传、识别任务状态和手动文本导入任务状态。 |
| `ocr_task_stage` | OCR 各阶段输入、输出、指标和错误记录。 |
| `ocr_review` | OCR 人工复核状态和草稿内容。 |
| `knowledge_vector` | 知识库文本、embedding 和 `metadata.scenes`。 |
| `stock_alert_config` | 股票/指数/可转债预警配置。 |
| `watch_group` | 投资观察池分组。 |
| `watch_group_item` | 投资观察池标的。 |
| `scene_analysis_task` | 投资报告场景分析任务、状态、上下文和召回 payload。 |
| `scene_analysis_report` | 投资报告历史版本、结构化 JSON、渲染文本和模型信息。 |
| `scene_analysis_config_profile` | 投资报告参数配置档。 |

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
