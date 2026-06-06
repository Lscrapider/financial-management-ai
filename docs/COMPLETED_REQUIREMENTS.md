# 已完成需求

## 后端基础架构

- 已搭建 Maven 聚合工程：根工程、`backend-java` 聚合模块、`finance-service`、`finance-data`、`finance-ai`。
- 已完成 Spring Boot 主应用 `finance-service`，默认端口 `8081`。
- 已按 `Controller -> Service -> Manage / API / Mapper -> Domain` 分层组织后端代码。
- 已接入 MyBatis-Plus、PostgreSQL、InfluxDB、Spring Security、Spring Scheduling、Spring AI。
- 已提供 Docker Compose，用于启动 PostgreSQL、InfluxDB、数据库初始化和后端服务。

## 数据库与初始化

- 已提供 PostgreSQL 本地容器和初始化脚本。
- 已实现数据库初始化流程：等待数据库、创建业务库、执行迁移、执行种子数据。
- 已创建用户表 `app_user`，并初始化默认管理员 `admin / 123456`。
- 已创建股票配置表 `stock_config` 和股票行情快照表 `stock_quote_snapshot`。
- 已创建指数配置表 `index_config`、指数行情快照表 `index_quote_snapshot`、指数 K 线表 `index_kline`。
- 已创建可转债配置表 `bond_config`、可转债行情快照表 `bond_quote_snapshot`、可转债 K 线表 `bond_kline`。
- 已创建投资观察池分组表 `watch_group` 和标的表 `watch_group_item`。
- 已创建股票预警配置表 `stock_alert_config`。
- 已创建 AI Token 用量日志表 `ai_token_usage_log`。
- 已创建应用访问日志表 `app_visit_log`。
- 已创建 OCR 识别任务表 `ocr_task`、阶段记录表 `ocr_task_stage`、复核表 `ocr_review`。
- 已创建知识库向量表 `knowledge_vector`。
- 已为 OCR 任务增加来源类型字段 `source_type`，用于区分文件 OCR 和手动文本导入。
- 已创建股票日 K 表 `stock_daily_kline`。
- 已创建场景分析任务表 `scene_analysis_task`、报告历史表 `scene_analysis_report`、报告配置档表 `scene_analysis_config_profile`。
- 已创建股票行业、估值历史、财务指标和分红历史表，用于场景分析报告。
- 已创建可转债基础资料、每日估值和份额变化表：`convertible_bond_basic`、`convertible_bond_daily_valuation`、`convertible_bond_share`。
- 已提供默认关注股票和核心指数种子数据。
- 已注册 MyBatis-Plus 分页插件 `MybatisPlusConfig`。

## 认证与权限

- 已实现登录接口，登录成功返回 `accessToken`。
- 已实现注册接口，支持用户名、密码、确认密码校验。
- 已实现登出接口，可移除当前 Token。
- 已实现当前用户信息接口。
- 已实现当前用户资料更新、密码修改和通知设置接口。
- 已实现 Token 刷新占位接口，当前返回原 Token。
- 已实现权限码占位接口，当前返回空数组。
- 已接入 Bearer Token 认证过滤器。
- 已配置接口权限：登录/注册匿名，`/api/ai/**` 登录可访问，其他接口要求 `admin` 角色。

## 股票行情

- 已接入腾讯行情接口，支持股票最新行情查询。
- 已支持历史 K 线 provider 配置切换，当前默认 Tushare，腾讯实现保留为可配置数据源。
- 已实现股票同步任务，按启用股票配置同步最新行情快照。
- 已实现股票分钟级分时同步，将最新批次分时数据写入 InfluxDB。
- 已实现股票行情列表接口，支持市场过滤、数量限制、排序字段和排序方向。
- 已实现股票分时走势接口，返回指定股票最新同步批次的分钟数据。
- 已实现股票日 K、周 K、月 K 数据同步和查询。
- 已支持股票无复权、前复权、后复权三套 K 线落库；Tushare HTTP 场景下通过 `adj_factor` 本地计算复权价。
- 已实现股票基本面 provider 配置切换，当前默认东方财富，支持行业、估值历史、财务指标和分红历史同步；Tushare 保留为可配置备选。
- 已支持股票市场枚举：A 股、沪主板、深主板、科创板、创业板。

## 指数行情

- 已接入腾讯行情接口，支持指数最新行情查询。
- 已实现指数同步任务，按启用指数配置同步最新行情快照。
- 已实现指数日 K、周 K、月 K 同步能力；当前默认通过 Tushare `index_daily`、`index_weekly`、`index_monthly` 获取。
- 已实现指数行情列表接口，支持市场过滤、数量限制、排序字段和排序方向。
- 已实现指数 K 线 查询接口，支持按 `indexCode` 或 `secid` 查询，支持日期范围和数量限制。
- 已初始化核心指数：上证指数、科创 50、深证成指、创业板指、沪深 300。

## AI 理财分析

- 已实现 `POST /api/ai/chat` 理财分析对话接口。
- 已通过 Spring AI ChatClient 调用 DeepSeek/OpenAI 兼容模型。
- 已实现 Query Rewrite，将用户问题转换为意图、目标、时间范围、数据范围和数据请求。
- 已限制 AI 处理范围为理财分析、股票、指数、市场、资产配置、投资研究和财务指标解释类问题。
- 已实现行情上下文查询，根据 Query Rewrite 结果查询股票行情、股票分时、指数行情和指数 K 线。
- 已支持按股票/指数名称反查代码，优先精确匹配，再模糊匹配。
- 已在系统提示中要求回答区分事实、推断和风险提示，不编造缺失数据，不提供确定性买卖建议。

## AI 用量与控制台

- 已实现 AI ChatResponse Token 用量自动记录。
- 已实现 DeepSeek 原始响应 Token 用量手动记录接口。
- 已实现 Token 用量概览接口，支持最近 N 天统计。
- 已实现 Token 用量趋势接口。
- 已实现 `/api/**` 访问日志拦截记录，排除 AI 控制台自身接口。
- 已实现 AI 控制台概览接口，汇总用户、访问和 Token 用量。
- 已实现应用访问趋势接口，支持最近 N 小时统计。

## 可转债行情

- 已接入腾讯行情接口，支持可转债最新行情查询。
- 已实现可转债同步任务，批量同步最新行情快照。
- 已实现可转债日 K、周 K、月 K 同步能力；当前 Tushare 使用 `cb_daily`，周 K 和月 K 由日线聚合并重新计算 MA。
- 已实现可转债基础资料、每日估值、份额变化数据同步和落表。
- 已实现可转债行情列表接口、日 K 查询接口。
- 已实现可转债手动同步和同步状态查询接口。

## 投资观察池

- 已实现多分组管理（新增/更新/删除分组）。
- 已支持多类型标的：股票（STOCK）、指数（INDEX）、可转债（BOND）。
- 已实现标的新增/删除、排序。
- 已实现分组内标的实时行情刷新（批量查询）。
- 已支持标的"更多"详情弹窗（确认字段白名单）。

## 股票预警

- 已实现预警配置新增/删除/列表查询。
- 已支持按涨跌幅阈值设置预警条件。
- 已实现定时任务自动检查预警。
- 已实现手动触发预警检查接口。

## 知识库浏览

- 已实现知识库分页查询接口 `GET /api/knowledge/chunks`。
- 已实现知识库统计接口 `GET /api/knowledge/stats`。
- 已实现知识库概览接口 `GET /api/knowledge/overview`，返回总量统计和 7 大类场景标签分布。
- 已实现知识库详情接口 `GET /api/knowledge/chunks/{id}`。
- 已支持知识库分页按文件名、来源类型、场景大类和场景标签过滤。
- 已实现单条文本、场景标签编辑接口 `PUT /api/knowledge/chunks/{id}`，支持按需发布单 chunk 重嵌入消息。
- 已实现前端知识库浏览页面（分页列表 + 标签过滤 + 详情 + 编辑）。
- 已实现前端知识库概览页面，展示任务数、chunk 数、文本长度和标签分布。
- 已支持 chunk 按 taskNo/chunkIndex 排序展示。

## 批量同步优化

- 已实现行情快照批量接口 `StockMarketApi.getQuotes(List<String> secids)`。
- 已实现 PO 层批量解析方法 `fromBatchApiResponse`（按 Tencent symbol 匹配回 stock/bond/index config）。
- 已实现 Manage 层批量 upsert 方法 `saveQuotesBatch`。
- 已改造三个同步任务（股票/指数/可转债）为批量快照 + 日 K / 分时按需同步模式。
- 已新增 `mybatis-plus-jsqlparser` 依赖支持物理分页。

## OCR 任务

- 已实现 OCR 文件上传任务接口 `POST /api/ai/ocr/tasks`。
- 已支持上传 PDF、PNG、JPG、JPEG、WEBP 文件。
- 已限制单文件最大 50MB。
- 已支持将上传文件保存到 MinIO 对象存储。
- 已支持创建 `ready` 状态 OCR 任务，发布第一阶段 RabbitMQ 消息。
- 已实现 OCR 任务分页查询、软删除。
- 已实现 8 阶段 RabbitMQ 串联：文档标准化 → OCR 识别 → 文本清洗 → 人工复核 → 规则标签 → LLM 标签 → 标签回正 → 向量入库。

## 手动知识导入

- 已实现手动知识导入任务创建、分页查询、详情查询、草稿保存、提交和软删除接口。
- 已支持通过 `ocr_task.source_type = manual_text` 与文件 OCR 任务隔离。
- 已支持手动文本跳过文档标准化、OCR 识别和文本清洗，直接进入场景打标、标签回正和向量入库流程。
- 已实现前端手动知识导入页面，并在 AI 中心中与 OCR 文件任务分开展示。

## 投资报告 / 场景分析

- 已实现投资报告任务提交接口 `POST /api/ai/scene-analysis/tasks`。
- 已支持股票、指数、可转债三类标的搜索和报告生成入口。
- 已支持快速分析、风险检查、估值报告三类报告类型。
- 已实现 Java/Python RabbitMQ 协作流程：currentScenes 计算 → retrieval embedding → 知识库召回 → 报告生成。
- 已实现基于 `metadata.scenes` 的动态 chunk 分配、pgvector 语义召回、Jaccard 标签过滤和类内重排。
- 已实现 DeepSeek 结构化 JSON 报告生成、chunkId 引用校验、Token 用量记录和 Markdown 文本渲染。
- 已实现报告历史版本表 `scene_analysis_report`，支持报告目标分页、单标的历史、报告详情和重新生成。
- 已实现报告配置档表 `scene_analysis_config_profile`，支持系统推荐配置和用户自定义配置。
- 已支持可转债报告读取 `assetSpecificData.convertibleBond`，覆盖转股溢价率、转股价值、纯债价值、YTM、评级、剩余规模、强赎/回售状态占位和正股联动字段。
- 已修复周期趋势分析 Markdown 渲染中 `trend` 和 `interpretation` 拼接导致的重复句号问题。
- 已实现前端 AI 中心报告生成页面，支持提交、轮询、历史查看、详情展示和重新生成。

## 本地运行与配置

- 已提供 `backend-java/finance-service/src/main/resources/application.yml` 作为主配置入口。
- 已支持通过环境变量配置 PostgreSQL、InfluxDB、RabbitMQ、MinIO、DeepSeek、DashScope、Tushare、数据源 provider、股票/指数/可转债同步。
- 已提供 `database/docker-compose.yml` 单独启动数据库依赖。
- 已提供 `database/influxdb/docker-compose.yml` 单独启动 InfluxDB。
- 已提供 `docker/docker-compose.yml` 启动完整后端环境。

## 文档

- 已补充 Java 后端聚合模块 README。
- 已补充 `finance-service` README。
- 已新增 `finance-data` README。
- 已补充 `finance-ai` README。
- 已补充数据库 README 和 InfluxDB README。
- 已新增接口文档 `docs/API_DOCUMENTATION.md`。
- 已新增已完成需求文档 `docs/COMPLETED_REQUIREMENTS.md`。
## 后续步骤待定

第一阶段：报告质量增强
1. 优化报告计算参数和不同 reportType / targetType 的权重配置。
2. 扩展参数考虑范围，纳入更多财务、行业、成交、波动和标的类型特征。
3. 建立报告准确度测试集和历史复盘评估机制。

第二阶段：外部数据源增强
4. 尝试接入新闻、公告、政策、行业动态和机构观点数据。
5. 为外部数据建立来源、发布时间、可信度和适用标的 metadata。
6. 在报告中区分事实、观点、模型推断和风险提示。

第三阶段：可转债报告深化
7. 完善可转债完整票息现金流 YTM、强赎/回售公告状态和触发进度。
8. 继续优化可转债差异化展示结构和正股联动分析。

第四阶段：引用溯源与复盘系统
9. 报告引用支持点击 chunk 回到 OCR 原文和页面图片。
10. 把分析报告转成复盘记录，后续填写结果并统计常见判断问题。

第五阶段：智能预警和个性化投资记忆
11. 预警触发后自动生成分析报告。
12. 支持多条件组合预警、相似历史场景检索、交易前 checklist 和常见错误提醒。
