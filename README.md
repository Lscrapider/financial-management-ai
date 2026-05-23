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
- Spring Boot
- Spring Web
- MyBatis-Plus
- Spring Scheduling
- Lombok
- PostgreSQL

### AI 服务

- Python 3.11+
- FastAPI
- Uvicorn
- LangChain / LlamaIndex（可选）
- PyTorch / Transformers（按模型需要引入）
- Sentence Transformers / Embedding 模型
- OCR 工具，例如 PaddleOCR 或 Tesseract

### 前端

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Vben Admin
- ECharts / AntV / TradingView Lightweight Charts

### 数据库与存储

- PostgreSQL
- pgvector（用于向量检索）
- 本地文件存储或对象存储（用于保存扫描件、OCR 原文、处理后的文本）

### 部署方式

- Docker
- Docker Compose

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

- 对接股票、指数、行业板块等外部 API。
- 支持定时拉取、手动同步和失败重试。
- 保存原始响应和清洗后的结构化数据。
- 提供历史行情查询和趋势展示接口。

### 知识库模块

- 管理手写副本扫描件和识别后的文本。
- 对扫描件执行 OCR 识别。
- 对识别文本进行清洗、分段和元数据标注。
- 生成向量并写入 PostgreSQL pgvector 表。

### AI 分析模块

- 接收 Java 后端传入的股票数据、指标数据和用户分析请求。
- 基于向量检索召回相关知识库片段。
- 将市场数据与知识库内容进行对比分析。
- 输出结构化建议，包含结论、依据、风险点、关注指标和置信度。

### 前端展示模块

- 展示市场行情、个股详情、指标走势和行业信息。
- 展示 AI 分析结果和引用的知识库依据。
- 支持知识库文件上传、处理状态查看和检索测试。
- 支持分析任务列表、任务详情和历史结果回看。

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
OCR 识别
  ↓
文本清洗
  ↓
文本切分
  ↓
Embedding 向量化
  ↓
写入 PostgreSQL pgvector
  ↓
用于 AI 检索增强分析
```

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

1. 初始化 Java Spring Boot 项目。
2. 初始化 Python FastAPI 项目。
3. 初始化 Vue 3 前端项目。
4. 配置 PostgreSQL 和 pgvector。
5. 设计行情数据表、知识库表、向量表和分析结果表。
6. 接入第一版股市数据 API。
7. 完成扫描件 OCR 到向量库的处理链路。
8. 完成第一版 AI 结构化分析接口。
9. 完成前端行情展示和分析结果展示页面。
