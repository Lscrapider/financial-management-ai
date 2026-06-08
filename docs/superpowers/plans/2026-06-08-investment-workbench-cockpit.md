# Investment Workbench Cockpit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a token-matched investment cockpit on the investment workbench, centered on a restrained signal radar, action queue, and state-driven physical micro-interactions.

**Architecture:** Keep the feature in the existing Vue SFC because the page already owns the data fetches, computed summaries, routing handlers, and scoped styles. Add computed cockpit state above the template, insert a new cockpit section before the existing detail panels, then style it using existing Element Plus theme tokens and CSS-only motion with reduced-motion fallbacks.

**Tech Stack:** Vue 3 `<script setup>`, Element Plus, Vben layout components, scoped CSS, CSS gradients, CSS transforms, existing route and API helpers.

---

## File Structure

- Modify: `frontend-vue/apps/web-ele/src/views/dashboard/investment-workbench/index.vue`
  - Add cockpit computed state and action derivation in the existing `<script setup>`.
  - Add cockpit markup between the loading skeleton and existing detail grid.
  - Add scoped CSS for token-matched layout, radar, signal rows, action queue, report flow, and reduced-motion.
- No new runtime files.
- No backend/API changes.
- No new dependencies.

## Task 1: Add Cockpit State Derivation

**Files:**
- Modify: `frontend-vue/apps/web-ele/src/views/dashboard/investment-workbench/index.vue`

- [ ] **Step 1: Add signal and action types near existing `WorkbenchTargetType`**

Add these types after `type WorkbenchTargetType = ...`:

```ts
type CockpitTone = 'blue' | 'green' | 'red' | 'yellow';

interface CockpitAction {
  description: string;
  key: string;
  label: string;
  onClick: () => void;
  tone: CockpitTone;
}

interface CockpitSignal {
  description: string;
  key: string;
  label: string;
  tone: CockpitTone;
  value: string;
}
```

- [ ] **Step 2: Add running report status constant**

Add this constant near the other top-level constants, before `const router = useRouter();`:

```ts
const RUNNING_REPORT_STATUSES = [
  'generating_report',
  'pending',
  'processing_current_scenes',
  'retrieving_knowledge',
] as const;
```

- [ ] **Step 3: Replace duplicated running status array usage**

Change `runningReportCount` to use the new constant:

```ts
const runningReportCount = computed(() => {
  return reports.value.filter((item) =>
    RUNNING_REPORT_STATUSES.includes(
      item.latestStatus as (typeof RUNNING_REPORT_STATUSES)[number],
    ),
  ).length;
});
```

- [ ] **Step 4: Add cockpit signal computed values**

Add these computed values after `topWatchGroups`:

```ts
const cockpitSignal = computed(() => {
  if (failedReportCount.value > 0 || triggeredAlerts.value.length > 0) {
    return {
      description: '存在越界预警或报告异常',
      label: '风险',
      tone: 'red' as const,
    };
  }
  if (nearAlerts.value.length > 0 || runningReportCount.value > 0) {
    return {
      description: '有接近阈值或生成中的研究任务',
      label: '关注',
      tone: 'yellow' as const,
    };
  }
  return {
    description: '未发现需要立即处理的异常',
    label: '稳健',
    tone: 'green' as const,
  };
});

const cockpitSignals = computed<CockpitSignal[]>(() => [
  {
    description: '观察池动量',
    key: 'up',
    label: '上涨标的',
    tone: 'red',
    value: String(marketDirectionSummary.value.up),
  },
  {
    description: '风险扩散',
    key: 'down',
    label: '下跌标的',
    tone: 'green',
    value: String(marketDirectionSummary.value.down),
  },
  {
    description: `已启用 ${enabledAlerts.value.length} · 覆盖 ${alertCoverageCount.value}/${watchItems.value.length}`,
    key: 'coverage',
    label: '预警覆盖',
    tone: alertCoveragePercent.value >= 80 ? 'green' : 'yellow',
    value: `${Math.round(alertCoveragePercent.value)}%`,
  },
  {
    description: `生成中 ${runningReportCount.value} · 失败 ${failedReportCount.value}`,
    key: 'reports',
    label: '报告生成',
    tone: failedReportCount.value > 0 ? 'red' : 'blue',
    value: String(runningReportCount.value),
  },
]);
```

- [ ] **Step 5: Add cockpit action computed values**

Add this computed block after `cockpitSignals`:

```ts
const cockpitActions = computed<CockpitAction[]>(() => {
  const actions: CockpitAction[] = [];
  if (triggeredAlerts.value.length > 0) {
    actions.push({
      description: `${triggeredAlerts.value.length} 条预警已越界`,
      key: 'triggered-alerts',
      label: '优先查看越界预警',
      onClick: openStockAlert,
      tone: 'red',
    });
  }
  if (nearAlerts.value.length > 0) {
    actions.push({
      description: `${nearAlerts.value.length} 条预警接近阈值`,
      key: 'near-alerts',
      label: '复核接近阈值标的',
      onClick: openStockAlert,
      tone: 'yellow',
    });
  }
  if (runningReportCount.value > 0) {
    actions.push({
      description: `${runningReportCount.value} 份报告正在生成`,
      key: 'running-reports',
      label: '继续跟进报告生成',
      onClick: openSceneReports,
      tone: 'blue',
    });
  }
  if (alertCoveragePercent.value < 80 && watchItems.value.length > 0) {
    actions.push({
      description: `当前覆盖 ${Math.round(alertCoveragePercent.value)}%`,
      key: 'coverage',
      label: '补齐自选预警覆盖',
      onClick: openStockAlert,
      tone: 'green',
    });
  }
  if (actions.length === 0) {
    actions.push({
      description: '当前信号稳定，可继续扩展观察池',
      key: 'stable',
      label: '查看观察池结构',
      onClick: openWatchPool,
      tone: 'green',
    });
  }
  return actions.slice(0, 3);
});
```

- [ ] **Step 6: Add style helper functions**

Add these functions near existing formatting helpers:

```ts
function cockpitToneClass(tone: CockpitTone) {
  return `is-${tone}`;
}

function hasRunningReports() {
  return runningReportCount.value > 0;
}
```

- [ ] **Step 7: Run typecheck for early validation**

Run:

```bash
pnpm -C frontend-vue --pm-on-fail=ignore check:type
```

Expected: `vue-tsc --noEmit --skipLibCheck` passes.

## Task 2: Insert Cockpit Template Structure

**Files:**
- Modify: `frontend-vue/apps/web-ele/src/views/dashboard/investment-workbench/index.vue`

- [ ] **Step 1: Change the v-else wrapper**

Find:

```vue
<div v-else class="workbench-grid">
```

Replace it with:

```vue
<div v-else class="workbench-content">
  <section class="cockpit-grid" aria-labelledby="cockpit-title">
    <!-- cockpit content goes here in next steps -->
  </section>

  <div class="workbench-grid">
```

Then add the matching extra closing `</div>` after the existing `workbench-grid` block and before the end of the `v-else` content.

- [ ] **Step 2: Add signal radar panel inside `cockpit-grid`**

Insert this as the first child of `cockpit-grid`:

```vue
<ElCard class="workbench-panel cockpit-panel signal-cockpit-panel" shadow="never">
  <template #header>
    <div class="panel-header">
      <div>
        <span id="cockpit-title">信号雷达</span>
        <small>沿用当前表格底色，聚合今日投资研究信号</small>
      </div>
      <ElButton link type="primary" @click="refreshWorkbench">
        刷新信号
      </ElButton>
    </div>
  </template>

  <div class="signal-cockpit-body">
    <div
      class="signal-radar"
      :class="cockpitToneClass(cockpitSignal.tone)"
      aria-hidden="true"
    >
      <div class="signal-radar-center">
        <strong>{{ cockpitSignal.label }}</strong>
        <span>{{ cockpitSignal.description }}</span>
      </div>
    </div>

    <div class="signal-list">
      <button
        v-for="signal in cockpitSignals"
        :key="signal.key"
        class="signal-row"
        :class="cockpitToneClass(signal.tone)"
        type="button"
      >
        <span>
          <strong>{{ signal.label }}</strong>
          <small>{{ signal.description }}</small>
        </span>
        <b>{{ signal.value }}</b>
      </button>
    </div>
  </div>
</ElCard>
```

- [ ] **Step 3: Add cockpit side column**

Insert this after the signal panel:

```vue
<div class="cockpit-side-stack">
  <ElCard class="workbench-panel action-panel" shadow="never">
    <template #header>
      <div class="panel-header">
        <div>
          <span>今日行动</span>
          <small>按风险优先级生成</small>
        </div>
      </div>
    </template>

    <div class="action-list">
      <button
        v-for="action in cockpitActions"
        :key="action.key"
        class="action-row"
        :class="cockpitToneClass(action.tone)"
        type="button"
        @click="action.onClick"
      >
        <span>
          <strong>{{ action.label }}</strong>
          <small>{{ action.description }}</small>
        </span>
        <IconifyIcon icon="lucide:arrow-up-right" />
      </button>
    </div>
  </ElCard>

  <ElCard class="workbench-panel report-flow-panel" shadow="never">
    <template #header>
      <div class="panel-header">
        <div>
          <span>报告状态流</span>
          <small>只在报告运行中显示动效</small>
        </div>
      </div>
    </template>

    <div class="report-flow" :class="{ 'is-running': hasRunningReports() }">
      <span></span>
      <span></span>
      <span></span>
    </div>
    <div class="report-flow-text">
      生成中 {{ runningReportCount }} · 失败 {{ failedReportCount }}
    </div>
  </ElCard>
</div>
```

- [ ] **Step 4: Run typecheck**

Run:

```bash
pnpm -C frontend-vue --pm-on-fail=ignore check:type
```

Expected: typecheck passes. If Vue reports a malformed template, fix the closing tags before moving on.

## Task 3: Add Token-Matched Cockpit Styling and Motion

**Files:**
- Modify: `frontend-vue/apps/web-ele/src/views/dashboard/investment-workbench/index.vue`

- [ ] **Step 1: Add cockpit layout CSS after `.workbench-grid` rules**

Add:

```css
.workbench-content {
  display: grid;
  gap: 16px;
}

.cockpit-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(300px, 0.85fr);
  gap: 16px;
}

.cockpit-panel {
  min-height: 430px;
}

.cockpit-side-stack {
  display: grid;
  gap: 16px;
}
```

- [ ] **Step 2: Add radar and signal list CSS**

Add:

```css
.signal-cockpit-body {
  display: grid;
  grid-template-columns: 210px minmax(0, 1fr);
  gap: 18px;
  align-items: center;
}

.signal-radar {
  position: relative;
  aspect-ratio: 1;
  background:
    radial-gradient(circle, var(--el-bg-color) 0 30%, transparent 31%),
    repeating-radial-gradient(
      circle,
      transparent 0 29px,
      color-mix(in srgb, var(--el-text-color-secondary), transparent 82%) 30px 31px
    ),
    conic-gradient(
      from -20deg,
      color-mix(in srgb, #57d188, transparent 18%) 0 22%,
      color-mix(in srgb, #efbd48, transparent 22%) 22% 45%,
      color-mix(in srgb, #dc4446, transparent 20%) 45% 56%,
      var(--el-fill-color-lighter) 56% 100%
    );
  border: 1px solid var(--el-border-color-light);
  border-radius: 50%;
}

.signal-radar::before {
  position: absolute;
  inset: 0;
  content: '';
  background: conic-gradient(
    from 0deg,
    color-mix(in srgb, #006be6, transparent 52%),
    color-mix(in srgb, #006be6, transparent 100%) 48deg,
    transparent 49deg
  );
  border-radius: inherit;
  animation: radar-needle 5.5s linear infinite;
}

.signal-radar-center {
  position: absolute;
  inset: 50px;
  z-index: 1;
  display: grid;
  place-items: center;
  text-align: center;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 50%;
}

.signal-radar-center strong {
  font-size: 24px;
  line-height: 1;
}

.signal-radar-center span {
  max-width: 110px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.signal-list {
  display: grid;
  gap: 10px;
}

.signal-row,
.action-row {
  display: grid;
  width: 100%;
  min-height: 58px;
  padding: 12px;
  text-align: left;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  transition:
    transform 180ms cubic-bezier(0.22, 1, 0.36, 1),
    border-color 180ms,
    background-color 180ms;
}

.signal-row {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
}

.signal-row:hover,
.signal-row:focus-visible {
  background: var(--el-fill-color-light);
  border-color: var(--el-border-color-light);
  outline: none;
  transform: translateX(4px);
}

.signal-row strong,
.action-row strong {
  display: block;
  color: var(--el-text-color-primary);
}

.signal-row small,
.action-row small,
.report-flow-text {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.signal-row b {
  font-size: 20px;
}
```

- [ ] **Step 3: Add tone CSS**

Add:

```css
.is-blue b,
.is-blue .signal-radar-center strong {
  color: #006be6;
}

.is-green b,
.is-green .signal-radar-center strong {
  color: #57d188;
}

.is-yellow b,
.is-yellow .signal-radar-center strong {
  color: #efbd48;
}

.is-red b,
.is-red .signal-radar-center strong {
  color: #dc4446;
}

.signal-row.is-blue:hover,
.action-row.is-blue:hover,
.action-row.is-blue:focus-visible {
  border-color: #006be6;
}

.signal-row.is-green:hover,
.action-row.is-green:hover,
.action-row.is-green:focus-visible {
  border-color: #57d188;
}

.signal-row.is-yellow:hover,
.action-row.is-yellow:hover,
.action-row.is-yellow:focus-visible {
  border-color: #efbd48;
}

.signal-row.is-red:hover,
.action-row.is-red:hover,
.action-row.is-red:focus-visible {
  border-color: #dc4446;
}
```

- [ ] **Step 4: Add action and report-flow CSS**

Add:

```css
.action-list {
  display: grid;
  gap: 10px;
}

.action-row {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
}

.action-row:hover,
.action-row:focus-visible {
  outline: none;
  transform: translateY(-2px);
}

.report-flow {
  display: grid;
  gap: 10px;
}

.report-flow span {
  display: block;
  height: 2px;
  background: var(--el-border-color-lighter);
  border-radius: 999px;
}

.report-flow.is-running span {
  background: linear-gradient(90deg, transparent, #006be6, #57d188, transparent);
  background-size: 180% 100%;
  animation: report-flow-line 2.8s cubic-bezier(0.25, 1, 0.5, 1) infinite;
}

.report-flow.is-running span:nth-child(2) {
  animation-delay: 180ms;
  opacity: 0.8;
}

.report-flow.is-running span:nth-child(3) {
  animation-delay: 360ms;
  opacity: 0.65;
}

@keyframes radar-needle {
  to {
    transform: rotate(360deg);
  }
}

@keyframes report-flow-line {
  from {
    background-position: 120% 0;
  }
  to {
    background-position: -80% 0;
  }
}
```

- [ ] **Step 5: Add responsive and reduced-motion CSS**

Add near existing media queries:

```css
@media (prefers-reduced-motion: reduce) {
  .signal-radar::before,
  .report-flow.is-running span {
    animation: none;
  }

  .signal-row:hover,
  .signal-row:focus-visible,
  .action-row:hover,
  .action-row:focus-visible {
    transform: none;
  }
}

@media (max-width: 1280px) {
  .cockpit-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .signal-cockpit-body {
    grid-template-columns: 1fr;
  }

  .signal-radar {
    width: min(220px, 72vw);
    margin: 0 auto;
  }
}
```

- [ ] **Step 6: Run style and format checks**

Run:

```bash
pnpm -C frontend-vue --pm-on-fail=ignore exec stylelint "apps/web-ele/src/views/dashboard/investment-workbench/index.vue"
pnpm -C frontend-vue --pm-on-fail=ignore exec oxfmt --check apps/web-ele/src/views/dashboard/investment-workbench/index.vue
```

Expected: both commands pass.

## Task 4: Browser Verification and Final Checks

**Files:**
- Modify: `frontend-vue/apps/web-ele/src/views/dashboard/investment-workbench/index.vue` only if verification finds layout defects.

- [ ] **Step 1: Run full focused verification**

Run:

```bash
pnpm -C frontend-vue --pm-on-fail=ignore check:type
pnpm -C frontend-vue --pm-on-fail=ignore build:ele
pnpm -C frontend-vue --pm-on-fail=ignore exec eslint apps/web-ele/src/views/dashboard/investment-workbench/index.vue
pnpm -C frontend-vue --pm-on-fail=ignore exec stylelint "apps/web-ele/src/views/dashboard/investment-workbench/index.vue"
pnpm -C frontend-vue --pm-on-fail=ignore exec oxfmt --check apps/web-ele/src/views/dashboard/investment-workbench/index.vue
git diff --check
```

Expected:
- Typecheck passes.
- Build exits 0. Existing dependency `INVALID_ANNOTATION` warnings are acceptable if build succeeds.
- Target ESLint/stylelint/format checks pass.
- `git diff --check` has no whitespace errors.

- [ ] **Step 2: Start dev server for visual verification**

Run:

```bash
pnpm -C frontend-vue --pm-on-fail=ignore dev:ele --host 127.0.0.1 --port 5778
```

Expected: Vite serves `http://127.0.0.1:5778/`.

- [ ] **Step 3: Verify desktop workbench**

Open `http://127.0.0.1:5778/` and navigate to the investment workbench route. If login blocks navigation, use the app's existing auth flow or inspect the rendered route after login.

Check:
- The cockpit uses the same dark table/card background as existing pages.
- The radar is a focal point but does not look like a separate blue skin.
- Signal rows and action rows do not overflow.
- Existing movement, asset, alert, and report sections still render below the cockpit.

- [ ] **Step 4: Verify mobile width**

Set viewport width to `390px`.

Check:
- No horizontal overflow.
- Radar stacks above signal rows.
- Action rows remain at least 44px tall.
- Text does not overlap or clip.

- [ ] **Step 5: Verify reduced motion**

Simulate `prefers-reduced-motion: reduce`.

Check:
- Radar scan stops.
- Report flow lines stop.
- All state text remains visible.

- [ ] **Step 6: Commit implementation**

Stage only implementation files:

```bash
git add frontend-vue/apps/web-ele/src/views/dashboard/investment-workbench/index.vue
git commit -m "[upd] : 1. add investment workbench signal cockpit"
```

Expected: commit succeeds and includes only the workbench implementation.
