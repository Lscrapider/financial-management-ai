# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

编写代码前请先阅读上级目录的 [AGENTS.md](../AGENTS.md) 和 [CODEX_GUIDELINES.md](../CODEX_GUIDELINES.md)，并遵循其中的要求。项目文档使用中文。非 trivial 实现任务请使用 `karpathy-guidelines` skill。

## 常用命令

```bash
pnpm dev:ele          # 启动 web-ele 开发服务器 (端口 5777)
pnpm build:ele        # 构建 web-ele
pnpm build            # 构建所有包和应用
pnpm lint             # 运行 oxlint + eslint 检查
pnpm check:type       # 运行 TypeScript 类型检查
pnpm format           # 运行 oxfmt 格式化
pnpm test:unit        # 运行 Vitest 单元测试
pnpm check:cspell     # 拼写检查
cd apps/web-ele && pnpm dev   # 直接在 app 目录下启动
```

## 项目架构

这是基于 **Vue Vben Admin v5** 的 pnpm monorepo，使用 Turborepo 编排。Vue 3 + Element Plus + TypeScript + Tailwind CSS 4。

### 目录分层

- `apps/web-ele/` — 主应用（Element Plus 变体）
  - `src/api/` — 按业务域分目录（`bond/`、`stock/`、`stock-alert/`、`watch-pool/` 等），`request.ts` 封装请求客户端
  - `src/views/` — 按功能分目录的页面组件（`dashboard/`、`ai-center/`、`knowledge/`）
  - `src/router/` — 路由配置，核心路由在 `routes/core.ts`，动态路由放在 `routes/modules/*.ts`
  - `src/store/` — 应用级 Pinia store（`auth.ts` 等）
  - `src/locales/` — i18n 语言包
  - `src/adapter/` — 组件适配器（表单、组件库桥接）
- `packages/@core/` — 核心包（`base`、`composables`、`preferences`、`ui-kit`）
- `packages/effects/` — 效果包（`access` 权限、`common-ui`、`hooks`、`layouts`、`plugins`、`request`）
- `packages/` — 共享包（`constants`、`icons`、`locales`、`stores`、`styles`、`types`、`utils`）
- `internal/` — 内部构建工具（`lint-configs`、`node-utils`、`tailwind-config`、`tsconfig`、`vite-config`）

### 启动流程

`main.ts` → 初始化偏好设置 → `bootstrap.ts`（适配器初始化 → i18n → Pinia → 注册指令 → 路由 → 挂载）

### API 请求

使用 `@vben/request` 的 `RequestClient`，统一响应格式 `{ code: 0, data: ... }`。token 过期自动刷新，登录态过期出现弹窗。

### 路由权限

核心路由（登录、404 等）无需权限；动态路由放在 `routes/modules/` 下，由 `guard.ts` 中的 `generateAccess` 根据用户角色动态过滤生成。

### 关键约定

- 包管理器：`pnpm >= 11.0.0`，不允许使用 npm/yarn
- Node：`^22.18.0 || ^24.0.0`
- 导入别名 `#/` 指向 `apps/web-ele/src/`
- 默认格式化工具：oxfmt（oxc）
- Pre-commit：Lefthook 自动运行 lint + typecheck
- 响应数据字段：`codeField: 'code'`、`dataField: 'data'`、`successCode: 0`
