# 理财分析 AI 项目

## 项目介绍

本项目是一个面向个人投资研究的理财分析 AI 系统，目标是接入股市相关 API，采集、存储、分析市场数据，并结合自建知识库输出结构化的投资分析建议。

系统会将股市行情、财务指标、新闻或公告等数据进行清洗和结构化处理，存入 PostgreSQL 数据库；同时对知识库内容和部分文本类数据进行向量化，支持语义检索和相似内容匹配。知识库来源主要是手写副本的扫描件，需要经过 OCR 识别、文本清洗、切分、向量化后进入检索流程。

项目采用 Java、Python、Vue 混合架构：

- Java Spring Boot 负责核心业务系统、权限、任务调度、数据编排、接口聚合和后端管理能力。
- Python FastAPI 负责模型微调、Embedding、向量检索、AI 推理等复杂 AI 计算能力。
- Vue 负责前端展示，包括股市行情看板、分析结果展示、知识库管理和任务状态查看。
- PostgreSQL 负责业务数据、行情数据、结构化分析结果和向量化数据存储。
- Docker 负责项目整体部署和本地开发环境编排。

## 文档索引

根目录文档按用途拆分：

| 文档 | 说明 |
| --- | --- |
| [REPORT_PIPELINE.md](./REPORT_PIPELINE.md) | AI 分析报告全链路实现说明。 |
| [架构图.png](./架构图.png) | 系统架构图。 |
| [report流程图.png](./report流程图.png) | 分析报告流程图。 |
| [OCR_PIPELINE.md](./OCR_PIPELINE.md) | OCR 全链路实现说明，包括 Java/Python 边界、RabbitMQ 队列、阶段消息、MinIO 产物、人工复核、chunk 规则、软删除和向量入库。 |
| [API_DOCUMENTATION.md](./API_DOCUMENTATION.md) | 后端接口文档，记录主要 API 的请求方式、请求参数和响应结构。 |
| [COMPLETED_REQUIREMENTS.md](./COMPLETED_REQUIREMENTS.md) | 已完成需求记录，用于追踪阶段性功能交付情况。 |
| [CODEX_GUIDELINES.md](./CODEX_GUIDELINES.md) | Codex 在本仓库写代码、改代码和审查代码时需要遵守的协作与代码规范。 |
| [AGENTS.md](./AGENTS.md) | Agent 入口说明，要求修改代码前先阅读 `CODEX_GUIDELINES.md`。 |

模块内文档：

| 文档 | 说明 |
| --- | --- |
| [backend-java/README.md](./backend-java/README.md) | Java 后端聚合工程说明。 |
| [backend-java/finance-ai/README.md](./backend-java/finance-ai/README.md) | Java AI 能力模块说明，包括 AI Chat、OCR 上传、人工复核和知识库接口。 |
| [backend-java/finance-service/README.md](./backend-java/finance-service/README.md) | Java 主服务说明。 |
| [database/README.md](./database/README.md) | 数据库和迁移脚本说明。 |
| [frontend-vue/README.md](./frontend-vue/README.md) | 前端工程说明。 |
| [ai-python/app/ocr/OCR_PIPELINE.md](./ai-python/app/ocr/OCR_PIPELINE.md) | OCR 旧路径跳转文件，实际内容以根目录 `OCR_PIPELINE.md` 为准。 |

## 核心目标

- 接入股市 API，定时获取股票行情、指数、财务数据、公告或新闻等信息。
- 将原始市场数据清洗后存入 PostgreSQL，形成可查询、可追踪的数据资产。
- 对知识库扫描件进行 OCR、文本清洗、分段和向量化处理。
- 基于股市数据和知识库内容进行语义检索与对比分析。
- 输出结构化分析建议，例如风险提示、趋势判断、关注指标、参考依据等。
- 提供前端可视化页面，展示股市数据、AI 分析过程和结果。

## 技术栈

### 后端服务

- Java 17+
- Spring Boot 3.x + Spring Security + Spring Scheduling + Spring AI
- MyBatis-Plus 3.5 + PostgreSQL + pgvector
- InfluxDB（分时走势）
- RabbitMQ（OCR 消息队列）
- MinIO（OCR 文件存储）
- Lombok + Hutool + Jackson

### AI 服务

- Python 3.11+
- FastAPI + Uvicorn
- RabbitMQ worker（消息队列消费者）
- Sentence Transformers / Embedding 模型
- OCR 大模型接口（阿里云 DashScope `qwen-vl-ocr-latest`）

### 前端

- Vue 3 + TypeScript + Vite
- Vben Admin v5 (Element Plus)
- Pinia + Vue Router
- ECharts / TradingView Lightweight Charts

### 数据库与存储

- PostgreSQL（业务数据、行情快照、日K线）
- pgvector（向量检索）
- InfluxDB（分时走势）
- MinIO（OCR 文件存储）
- RabbitMQ（OCR 阶段消息）

### 部署方式

- Docker + Docker Compose

## 前端启动

前端骨架已接入 Vben Admin，当前默认使用 Element Plus 版本：

```bash
./scripts/run-frontend.sh
```

也可以直接进入前端目录启动：

```bash
cd frontend-vue
pnpm install
pnpm dev:ele
```

## 推荐目录结构

```text
financial-management-ai/
├── README.md                         # 项目说明文档
├── .gitignore                        # Git 忽略规则
├── pom.xml                           # Maven 聚合工程配置
├── docs/                             # 项目文档
│   ├── architecture.md               # 系统架构设计
│   ├── api.md                        # 接口文档
│   ├── database.md                   # 数据库设计
│   └── ai-workflow.md                # AI 处理流程说明
├── backend-java/                     # Java 后端聚合目录
│   ├── pom.xml                       # Java 后端聚合工程配置
│   ├── finance-service/              # 主业务 Java 服务
│   │   ├── Dockerfile                # 主业务服务 Docker 构建文件
│   │   ├── pom.xml                   # 主业务服务 Maven 配置
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/
│   │       │   │   └── com/scrapider/finance/
│   │       │   │       ├── FinanceApplication.java # Spring Boot 启动类
│   │       │   │       ├── controller/             # 控制层
│   │       │   │       ├── service/                # 服务层接口
│   │       │   │       ├── manage/                 # MyBatis-Plus 管理封装
│   │       │   │       ├── mapper/                 # MyBatis-Plus Mapper
│   │       │   │       └── domain/                 # 领域对象
│   │       │   └── resources/
│   │       │       └── application.yml             # 应用配置
│   │       └── test/                 # Java 测试代码
│   └── finance-ai/                   # Spring AI Java 能力模块
│       ├── pom.xml                   # AI 模块 Maven 配置
│       └── src/
├── ai-python/                        # Python AI 服务
│   ├── app/
│   │   ├── api/                      # FastAPI 路由
│   │   ├── core/                     # 配置、日志、通用能力
│   │   ├── embeddings/               # Embedding 生成逻辑
│   │   ├── retrieval/                # 向量检索逻辑
│   │   ├── ocr/                      # 扫描件 OCR 处理
│   │   ├── models/                   # 模型加载、微调、推理
│   │   └── schemas/                  # 请求和响应结构
│   ├── tests/                        # Python 测试代码
│   ├── requirements.txt              # Python 依赖
│   └── README.md                     # AI 服务说明
├── frontend-vue/                     # Vue 前端项目
│   ├── src/
│   │   ├── api/                      # 前端接口请求
│   │   ├── assets/                   # 静态资源
│   │   ├── components/               # 通用组件
│   │   ├── router/                   # 路由配置
│   │   ├── stores/                   # Pinia 状态管理
│   │   └── views/                    # 页面
│   ├── package.json                  # 前端依赖配置
│   └── README.md                     # 前端说明
├── database/                         # 数据库相关文件
│   ├── migrations/                   # 数据库迁移脚本
│   ├── seed/                         # 初始化数据
│   └── pgvector/                     # 向量表和索引脚本
├── data/                             # 本地数据目录，不提交 Git
│   ├── raw/                          # 原始数据
│   ├── scans/                        # 知识库扫描件
│   ├── ocr/                          # OCR 识别结果
│   └── processed/                    # 清洗后的数据
├── scripts/                          # 工具脚本
│   ├── init-db.sh                    # 初始化数据库脚本
│   ├── run-java.sh                   # 启动 Java 服务
│   ├── run-python.sh                 # 启动 Python 服务
│   └── run-frontend.sh               # 启动前端服务
└── docker/                           # Docker 相关配置
    ├── docker-compose.yml            # 项目部署和本地开发环境编排
    ├── postgres/                     # PostgreSQL 配置
    └── nginx/                        # 前端或网关配置
```

## 系统模块规划

### 行情数据模块

- 对接股票、指数、可转债等外部行情 API。
- 支持定时拉取（批量）、手动同步和失败重试。
- 保存原始响应和清洗后的结构化数据。
- 提供历史行情查询和分时走势展示接口。

### 知识库模块

- 管理手写副本扫描件和识别后的文本。
- 对扫描件执行 OCR 识别（5 阶段 RabbitMQ 串联）。
- 对识别文本进行清洗、分段和元数据标注。
- 支持人工复核修改、合并、删除段落。
- 生成向量并写入 PostgreSQL pgvector 表。

### AI 分析模块

- 接收用户分析请求，Query Rewrite 拆解意图。
- 查询行情上下文（股票/指数行情、分时、日K）。
- 向量检索召回相关知识库片段。
- 通过 DeepSeek ChatClient 输出结构化回答。

### 交易辅助模块

- 投资观察池：多分组、多类型标的（股票/指数/可转债）管理，实时行情刷新。
- 股票预警：按目标价/涨跌幅条件设置预警，定时检查触发提醒。

### 前端展示模块

- 行情总览、指数行情、可转债行情页面。
- AI 中心：知识库处理队列、人工复核。
- 知识库浏览、股票预警管理、控制台指标。
- AI Chat 对话页面。

## 初步数据流

```text
股市 API
  ↓
Java 定时任务 / 数据同步服务
  ↓
PostgreSQL 结构化存储
  ↓
Java 调用 Python AI 服务
  ↓
Python 执行 Embedding 检索与模型推理
  ↓
返回结构化分析结果
  ↓
Java 保存结果并提供接口
  ↓
Vue 前端展示
```

知识库处理流程：

```text
扫描件上传
  ↓
文档标准化
  ↓
OCR 识别
  ↓
文本清洗
  ↓
人工复核
  ↓
Embedding 向量化
  ↓
写入 PostgreSQL pgvector
  ↓
用于 AI 检索增强分析
```

OCR 详细阶段、消息体、产物目录和人工复核规则见 [OCR_PIPELINE.md](./OCR_PIPELINE.md)。

## 结构化分析结果示例

```json
{
  "stockCode": "示例股票代码",
  "analysisDate": "2026-05-22",
  "summary": "整体结论摘要",
  "trend": "趋势判断",
  "riskLevel": "中等",
  "signals": [
    {
      "name": "成交量变化",
      "description": "近期成交量出现明显变化，需要结合价格走势继续观察"
    }
  ],
  "knowledgeReferences": [
    {
      "title": "知识库片段标题",
      "content": "召回的知识库内容摘要",
      "score": 0.86
    }
  ],
  "suggestions": [
    "关注后续财报数据",
    "结合行业指数判断相对强弱",
    "避免仅根据单一指标做出决策"
  ]
}
```

## 开发约定

- 项目文档、代码注释、提交说明优先使用中文。
- Java 服务负责业务稳定性、数据一致性和系统对外接口。
- Python 服务负责 AI 能力，不直接承担复杂业务编排。
- 前后端接口返回结构保持清晰、可追踪、可扩展。
- 所有模型输出都需要保留引用依据，避免只有结论没有来源。
- 原始数据、扫描件、模型文件、日志文件不提交到 Git。

## 后续建设计划

1. ✅ 初始化 Java Spring Boot 项目。
2. ✅ 初始化 Python FastAPI / RabbitMQ Worker 项目。
3. ✅ 初始化 Vue 3 前端项目。
4. ✅ 配置 PostgreSQL 和 pgvector。
5. ✅ 设计行情数据表、知识库表、向量表和分析结果表。
6. ✅ 接入股票/指数/可转债行情 API。
7. ✅ 完成扫描件 OCR 到向量库的全链路处理。
8. ✅ 完成 AI Chat 结构化分析接口。
9. ✅ 完成前端行情展示（行情总览/指数行情/可转债行情/投资观察池）。
10. ✅ 完成 AI 中心（知识库处理队列 + 人工复核）。
11. ✅ 完成知识库浏览、股票预警、控制台指标。
12. 完成投资分析建议生成与展示。
13. 增加更多财务指标数据源。
14. 优化 AI Chat 上下文策略和反问能力。
