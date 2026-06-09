# AI Token 用量页面 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在系统一级导航下新增 Token 用量页面，支持查看 AI Token 汇总、趋势和 `ai_token_usage_log` 分页明细。

**Architecture:** 后端沿用 `Controller -> Service -> Manage -> Mapper` 分层，新增只读分页查询参数和分页 VO，不直接返回 PO。前端在系统菜单下新增页面，复用现有系统监控的汇总与趋势接口，并新增日志分页 API 展示明细。

**Tech Stack:** Spring Boot 3、MyBatis-Plus、Vue 3、TypeScript、Element Plus、Vben Admin。

---

### Task 1: 后端 Token 用量分页查询

**Files:**
- Create: `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/domain/param/AiTokenUsageLogPageParam.java`
- Create: `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/domain/vo/AiTokenUsageLogPageVO.java`
- Modify: `backend-java/finance-data/src/main/java/com/scrapider/finance/manage/AiTokenUsageLogManage.java`
- Modify: `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/AiTokenUsageService.java`
- Modify: `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/AiTokenUsageServiceImpl.java`
- Modify: `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/controller/AiTokenUsageController.java`

- [ ] **Step 1: 新增查询参数对象**

创建 `AiTokenUsageLogPageParam`，字段包含 `pageNum`、`pageSize`、`startTime`、`endTime`、`source`、`phase`、`model`、`userId`、`sourceRefId`、`responseId`。

- [ ] **Step 2: 新增分页响应 VO**

创建 `AiTokenUsageLogPageVO`，从 `Page<AiTokenUsageLogPO>` 转成 `records`、`total`、`pageNum`、`pageSize`、`pages`。

- [ ] **Step 3: Manage 增加分页数据库查询**

使用 MyBatis-Plus `lambdaQuery()`，按可选条件过滤，默认 `occurredAt` 倒序。

- [ ] **Step 4: Service 暴露分页方法**

在 `AiTokenUsageService` 增加 `pageLogs(AiTokenUsageLogPageParam param)`，实现层归一化分页大小并调用 Manage。

- [ ] **Step 5: Controller 增加 GET `/logs`**

用 `@ModelAttribute AiTokenUsageLogPageParam param` 接收 query 参数，返回 `AiTokenUsageLogPageVO`。

### Task 2: 前端 Token 用量页面

**Files:**
- Create: `frontend-vue/apps/web-ele/src/api/ai-token-usage/index.ts`
- Create: `frontend-vue/apps/web-ele/src/views/system-config/token-usage/index.vue`
- Modify: `frontend-vue/apps/web-ele/src/router/routes/modules/dashboard.ts`
- Modify: `frontend-vue/apps/web-ele/src/layouts/navigation-workspaces.ts`
- Modify: `frontend-vue/apps/web-ele/src/locales/langs/zh-CN/page.json`
- Modify: `frontend-vue/apps/web-ele/src/locales/langs/en-US/page.json`

- [ ] **Step 1: 新增前端 API**

封装 `getAiTokenUsageOverview`、`listAiTokenUsageTrends`、`listAiTokenUsageLogs`，类型与后端 VO 对齐。

- [ ] **Step 2: 新增路由和系统导航**

在 `SystemConfig` 子路由加入 `TokenUsage`，并在系统工作区二级导航加入 `Token 用量`。

- [ ] **Step 3: 新增页面**

页面包含筛选栏、汇总卡片、趋势图、明细表和详情抽屉；表格用 Element Plus 分页。

- [ ] **Step 4: 加载与错误状态**

页面初始化加载 30 天汇总、趋势和第一页日志；刷新失败用 `ElMessage.error` 提示。

### Task 3: 文档与验证

**Files:**
- Modify: `docs/API_DOCUMENTATION.md`

- [ ] **Step 1: 更新 API 文档**

补充 `GET /api/ai/token-usage/logs` 参数和返回字段。

- [ ] **Step 2: 后端编译验证**

运行 `mvn -pl backend-java/finance-app -am -DskipTests compile`。

- [ ] **Step 3: 前端类型验证**

运行前端项目现有类型检查或构建命令，确认新增页面和 API 类型可编译。
