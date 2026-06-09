# AI Chat Floating Research Window Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有全局 AI Chat 改成不挤压主页面的覆盖式可拖拽、可缩放研究窗口。

**Architecture:** 只修改 `ai-chat-drawer.vue`，保留现有 WebSocket 发送和消息渲染逻辑。组件内部新增窗口几何状态、拖拽/缩放状态、本地存储恢复和移动端降级规则。

**Tech Stack:** Vue 3 `<script setup>`、Element Plus、`@vben/icons`、CSS scoped、浏览器 `localStorage`。

---

### Task 1: 状态与几何逻辑

**Files:**
- Modify: `frontend-vue/apps/web-ele/src/widgets/ai-chat/ai-chat-drawer.vue`

- [ ] **Step 1: 增加窗口和入口尺寸常量**

在脚本区定义入口尺寸、窗口默认尺寸、最小尺寸、安全边距和本地存储 key。

- [ ] **Step 2: 替换单一拖拽状态**

把现有只支持小球拖拽的 `dragState` 扩展为可区分 `trigger`、`panel`、`resize` 的交互状态。

- [ ] **Step 3: 增加本地状态恢复**

读取和保存入口位置、窗口位置、窗口尺寸、放大状态。拖拽或缩放结束后保存，拖拽中不频繁写入。

- [ ] **Step 4: 增加视口约束**

新增 `clampTriggerPosition()`、`clampPanelRect()`、`snapTriggerToEdge()`，保证入口和窗口不会出屏。

### Task 2: 模板交互

**Files:**
- Modify: `frontend-vue/apps/web-ele/src/widgets/ai-chat/ai-chat-drawer.vue`

- [ ] **Step 1: 更新入口按钮**

入口从 60px 圆按钮改为紧凑 pill，显示图标和“AI 研究助手”，仍支持拖拽和点击展开。

- [ ] **Step 2: 更新窗口标题栏**

标题栏成为窗口拖拽手柄，包含收起、清空、新对话、放大、专注模式和关闭按钮。

- [ ] **Step 3: 增加上下文标签与快捷问题**

消息区上方显示当前页、自选池、近 20 条会话等上下文。输入区上方提供“解释今日异动”“列复盘清单”“查相关知识”快捷问题。

- [ ] **Step 4: 增加缩放手柄**

窗口右下角增加 resize handle，仅桌面展开态显示。

### Task 3: 样式与响应式

**Files:**
- Modify: `frontend-vue/apps/web-ele/src/widgets/ai-chat/ai-chat-drawer.vue`

- [ ] **Step 1: 重写入口与窗口样式**

保持当前深色 Element Plus 设计语言，降低圆球干扰，窗口使用边框和克制阴影。

- [ ] **Step 2: 优化消息阅读和输入区**

用户消息紧凑，AI 消息更适合长文阅读，输入区固定在底部。

- [ ] **Step 3: 增加移动端降级**

小屏不自由缩放，窗口近似全屏覆盖，入口贴边。

- [ ] **Step 4: 增加 reduced-motion**

减少动画时禁用非必要 transition。

### Task 4: 验证

**Files:**
- Verify: `frontend-vue/apps/web-ele/src/widgets/ai-chat/ai-chat-drawer.vue`

- [ ] **Step 1: 类型检查**

Run: `pnpm -C frontend-vue --pm-on-fail=ignore check:type`

- [ ] **Step 2: 浏览器交互检查**

启动前端后检查入口拖拽、窗口拖拽、缩放、放大、专注模式、刷新恢复和移动视口表现。

- [ ] **Step 3: 检查 git diff**

确认只修改 AI Chat 相关文件和本实现计划。
