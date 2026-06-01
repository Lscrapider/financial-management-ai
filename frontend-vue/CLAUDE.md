# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

编写代码前请先阅读上级目录的 [AGENTS.md](../AGENTS.md) 和 [docs/CODEX_GUIDELINES.md](../docs/CODEX_GUIDELINES.md)，并遵循其中的要求。项目文档使用中文。非 trivial 实现任务请使用 `karpathy-guidelines` skill。

## 常用命令

```bash
pnpm dev:ele          # 启动 web-ele 开发服务器 (端口 5777)
pnpm build:ele        # 构建 web-ele
pnpm build            # 构建所有包和应用
pnpm dev              # 启动所有应用开发服务器（turbo-run dev）
pnpm lint             # 运行 oxlint + eslint 检查
pnpm format           # 运行 oxfmt 格式化
pnpm check:type       # 运行 TypeScript 类型检查
pnpm check:cspell     # 拼写检查
pnpm check            # 全量检查：循环依赖 + 依赖检查 + 类型检查 + 拼写检查
pnpm test:unit        # 运行 Vitest 单元测试
pnpm test:e2e         # 运行 E2E 测试（Playwright）
pnpm clean            # 清理 node_modules 和构建产物
pnpm reinstall        # 清理并重新安装依赖
pnpm commit           # 交互式提交（czg）
cd apps/web-ele && pnpm dev   # 直接在 app 目录下启动
```

## 项目架构

基于 **Vue Vben Admin v5** 的 pnpm monorepo，使用 Turborepo 编排。Vue 3 + Element Plus + TypeScript + Tailwind CSS 4。

### 目录分层

- `apps/web-ele/` — 主应用（Element Plus 变体）
  - `src/api/` — 按业务域分目录（`bond/`、`stock/`、`stock-alert/`、`watch-pool/`、`ai-chat/`、`knowledge/`、`ocr-task/`、`ocr-review/`、`index-market/` 等），`request.ts` 封装请求客户端
  - `src/views/` — 按功能分目录的页面组件（`dashboard/`、`ai-center/`、`knowledge/`、`_core/`）
  - `src/router/` — 路由配置，核心路由在 `routes/core.ts`，动态路由放在 `routes/modules/*.ts`
  - `src/store/` — 应用级 Pinia store（`auth.ts` 等）
  - `src/locales/` — i18n 语言包（`langs/zh-CN/`、`langs/en-US/`），JSON 格式，通过 `import.meta.glob` 自动加载
  - `src/adapter/` — 组件适配器（表单、组件库桥接）
  - `src/layouts/` — 布局组件（`basic.vue`、`auth.vue`、`iframe.vue`）
- `packages/@core/` — 核心包（`base`、`composables`、`preferences`、`ui-kit`）
- `packages/effects/` — 效果包（`access` 权限、`common-ui`、`hooks`、`layouts`、`plugins`、`request`）
- `packages/` — 共享包（`constants`、`icons`、`locales`、`stores`、`styles`、`types`、`utils`）
- `internal/` — 内部构建工具（`lint-configs`、`node-utils`、`tailwind-config`、`tsconfig`、`vite-config`）

### 关键 @vben 包用途

| 包 | 用途 |
|---|---|
| `@vben/request` | HTTP 请求客户端（`RequestClient`），含 token 刷新、错误拦截 |
| `@vben/access` | 权限控制，提供 `generateAccessible` 动态路由生成和权限指令 |
| `@vben/stores` | 全局 store（`useAccessStore`、`useUserStore`），含持久化加密 |
| `@vben/preferences` | 应用偏好设置（主题、布局、语言等） |
| `@vben/locales` | i18n 核心（`$t`、`setupI18n`、`loadLocalesMapFromDir`） |
| `@vben/layouts` | 布局组件（侧边栏、页头、标签页等） |
| `@vben/common-ui` | 通用 UI 组件和指令（loading、tippy 等） |
| `@vben/hooks` | 组合式函数（`useAppConfig` 等） |
| `@vben/utils` | 工具函数（`mergeRouteModules`、`traverseTreeValues` 等） |

### 启动流程

`main.ts` → 初始化偏好设置 → `bootstrap.ts`：
1. 初始化组件适配器（Element Plus 图标注册等）
2. 初始化 Vben 表单适配器
3. 创建 Vue app → 注册 loading/spinning 指令 → 注册 i18n → 初始化 Pinia store → 注册权限指令 → 初始化 tippy → 注册路由及守卫 → 注册 Motion 插件 → 动态标题 watch → 挂载

### API 请求

使用 `@vben/request` 的 `RequestClient`，统一响应格式 `{ code: 0, data: ... }`。

请求拦截器链：
1. 请求头注入 Authorization（Bearer token）和 Accept-Language
2. `defaultResponseInterceptor` — 按 `codeField: 'code'`、`dataField: 'data'`、`successCode: 0` 解包响应
3. `authenticateResponseInterceptor` — token 过期自动刷新，登录态过期弹窗
4. `errorMessageResponseInterceptor` — 兜底错误提示（优先取 `response.data.error` 或 `response.data.message`）

两种请求客户端：
- `requestClient` — 默认 `responseReturn: 'data'`，直接返回解包后的 data
- `baseRequestClient` — 返回完整响应体，不自动解包

调用方也可在单次请求中覆写 `responseReturn: 'body'` 来获取完整响应。

### 路由与权限

- **核心路由** (`routes/core.ts`)：无需权限，始终可见（登录、注册、404 等）。`Root` 路由使用 `BasicLayout`，`Authentication` 路由使用 `AuthPageLayout`
- **动态路由** (`routes/modules/*.ts`)：文件通过 `import.meta.glob('./modules/**/*.ts')` 自动发现，由 `mergeRouteModules` 合并
- **权限生成** (`guard.ts` → `access.ts`)：`generateAccess` 根据用户角色调用后端菜单接口 + `@vben/access` 的 `generateAccessible` 动态过滤路由
- 路由守卫在 `guard.ts` 中注册：通用守卫（页面加载进度条 + 缓存追踪）和权限守卫（token 检查 + 动态路由生成）

### i18n

语言包位于 `apps/web-ele/src/locales/langs/{locale}/*.json`，通过 `import.meta.glob` 自动扫描加载。key 按页面组织，使用 `$t('page.dashboard.workspace')` 访问。支持 `zh-CN` 和 `en-US`，默认 `zh-CN`。同时加载 dayjs 和 Element Plus 的语言包。

### 环境变量

| 变量 | 说明 |
|---|---|
| `VITE_PORT` | 开发服务器端口（默认 5777） |
| `VITE_GLOB_API_URL` | API 接口地址（开发环境默认 `/api`） |
| `VITE_APP_TITLE` | 应用标题 |
| `VITE_APP_NAMESPACE` | store 缓存命名空间前缀 |
| `VITE_NITRO_MOCK` | 是否开启 Nitro Mock 服务 |
| `VITE_DEVTOOLS` | 是否开启 Vue DevTools |
| `VITE_COMPRESS` | 生产构建压缩方式（none/brotli/gzip） |
| `VITE_ROUTER_HISTORY` | 路由模式（hash/history） |

### 关键约定

- 包管理器：`pnpm >= 11.0.0`，不允许使用 npm/yarn
- Node：`^22.18.0 || ^24.0.0`
- 导入别名 `#/` 指向 `apps/web-ele/src/`
- 默认格式化工具：oxfmt（oxc）
- Pre-commit：Lefthook 自动运行 lint + typecheck
- 响应数据字段：`codeField: 'code'`、`dataField: 'data'`、`successCode: 0`
- 依赖版本统一在 `pnpm-workspace.yaml` 的 `catalog` 中管理
