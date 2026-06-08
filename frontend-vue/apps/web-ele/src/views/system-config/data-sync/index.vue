<script lang="ts" setup>
import type { MarketSyncJob } from '#/api/market-sync';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElCard,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus';

import {
  getBondMarketSyncStatus,
  syncBondKlineData,
  syncBondMarketData,
} from '#/api/bond';
import {
  getIndexMarketSyncStatus,
  syncIndexKlineData,
  syncIndexMarketData,
} from '#/api/index-market';
import { listLatestFullMarketSyncJobs } from '#/api/market-sync';
import {
  getStockMarketSyncStatus,
  syncStockDailyKlineData,
  syncStockMarketData,
  syncStockTrendData,
} from '#/api/stock';

type MarketKind = 'bond' | 'index' | 'stock';
type PeriodType = 'daily' | 'monthly' | 'weekly';
type SingleSyncScope = 'kline' | 'trend';
type SyncKind = 'single' | MarketKind;

interface SyncItem {
  description: string;
  key: MarketKind;
  title: string;
  scope: string;
}

const syncItems: SyncItem[] = [
  {
    description: '全量同步所有已启用股票的行情快照、分时数据和 K 线数据。',
    key: 'stock',
    title: '股票行情同步',
    scope: '股票快照 / 分时 / K线',
  },
  {
    description: '全量同步所有已启用指数的行情快照、分时数据和日/周/月 K 线。',
    key: 'index',
    title: '指数行情同步',
    scope: '指数快照 / 分时 / K线',
  },
  {
    description:
      '全量同步所有已启用可转债的行情快照、分时数据和日/周/月 K 线。',
    key: 'bond',
    title: '可转债行情同步',
    scope: '可转债快照 / 分时 / K线',
  },
];

const loadingMap = ref<Record<SyncKind, boolean>>({
  bond: false,
  index: false,
  single: false,
  stock: false,
});
const refreshingStatus = ref(false);
const runningMap = ref<Record<MarketKind, boolean>>({
  bond: false,
  index: false,
  stock: false,
});
const latestJobMap = ref<Partial<Record<MarketKind, MarketSyncJob>>>({});
const singleSyncForm = reactive<{
  code: string;
  limit: number;
  market: MarketKind;
  periodType: PeriodType;
  scope: SingleSyncScope;
}>({
  code: '',
  limit: 250,
  market: 'stock',
  periodType: 'daily',
  scope: 'trend',
});
const marketOptions = [
  { label: '股票', value: 'stock' },
  { label: '指数', value: 'index' },
  { label: '可转债', value: 'bond' },
] as const;
const periodOptions = [
  { label: '日 K', value: 'daily' },
  { label: '周 K', value: 'weekly' },
  { label: '月 K', value: 'monthly' },
] as const;
const singleScopeOptions = computed(() => {
  if (singleSyncForm.market === 'stock') {
    return [
      { label: '分时', value: 'trend' },
      { label: '日 K', value: 'kline' },
    ] as const;
  }
  return [{ label: 'K 线', value: 'kline' }] as const;
});
const showPeriodSelect = computed(
  () => singleSyncForm.scope === 'kline' && singleSyncForm.market !== 'stock',
);
const healthItems = computed(() =>
  syncItems.map((item) => ({
    ...item,
    job: latestJobMap.value[item.key],
    running: runningMap.value[item.key],
  })),
);

const delay = (ms: number) =>
  new Promise((resolve) => {
    setTimeout(resolve, ms);
  });

onMounted(() => {
  void refreshMarketStatuses(true);
});

watch(
  () => singleSyncForm.market,
  () => {
    const firstScope = singleScopeOptions.value[0]?.value ?? 'kline';
    singleSyncForm.scope = firstScope;
    singleSyncForm.periodType = 'daily';
  },
);

async function refreshMarketStatuses(silent = false) {
  if (refreshingStatus.value) return;
  refreshingStatus.value = true;
  try {
    const [stock, index, bond, jobs] = await Promise.all([
      getStockMarketSyncStatus(),
      getIndexMarketSyncStatus(),
      getBondMarketSyncStatus(),
      listLatestFullMarketSyncJobs(),
    ]);
    runningMap.value = {
      bond: bond.running,
      index: index.running,
      stock: stock.running,
    };
    const jobMap: Partial<Record<MarketKind, MarketSyncJob>> = {};
    for (const job of jobs) {
      jobMap[job.targetType] = job;
    }
    latestJobMap.value = jobMap;
  } catch {
    if (!silent) {
      ElMessage.error('同步状态刷新失败');
    }
  } finally {
    refreshingStatus.value = false;
  }
}

async function runMarketSync(kind: MarketKind) {
  if (loadingMap.value[kind]) return;
  loadingMap.value[kind] = true;
  runningMap.value[kind] = true;
  try {
    const status = await startMarketSync(kind);
    ElMessage.info(
      status.started
        ? `${labelOf(kind)}同步已开始`
        : `${labelOf(kind)}同步正在执行`,
    );
    const completed = await waitMarketSyncCompleted(kind);
    if (completed) {
      ElMessage.success(`${labelOf(kind)}同步完成`);
    } else {
      ElMessage.warning(
        `${labelOf(kind)}同步仍在后台执行，可稍后到行情页面刷新查看`,
      );
    }
  } finally {
    await refreshMarketStatuses(true);
    loadingMap.value[kind] = false;
  }
}

async function startMarketSync(kind: MarketKind) {
  if (kind === 'stock') {
    return syncStockMarketData();
  }
  if (kind === 'index') {
    return syncIndexMarketData();
  }
  return syncBondMarketData();
}

async function waitMarketSyncCompleted(kind: MarketKind) {
  for (let i = 0; i < 120; i += 1) {
    await delay(3000);
    const status = await getMarketSyncStatus(kind);
    runningMap.value[kind] = status.running;
    if (!status.running) {
      return true;
    }
  }
  return false;
}

async function getMarketSyncStatus(kind: MarketKind) {
  if (kind === 'stock') {
    return getStockMarketSyncStatus();
  }
  if (kind === 'index') {
    return getIndexMarketSyncStatus();
  }
  return getBondMarketSyncStatus();
}

async function runSingleSync() {
  const code = singleSyncForm.code.trim();
  if (!code || loadingMap.value.single) return;
  loadingMap.value.single = true;
  try {
    await startSingleSync(code);
    ElMessage.success(`${singleSyncLabel.value}同步完成`);
  } finally {
    loadingMap.value.single = false;
  }
}

async function startSingleSync(code: string) {
  if (singleSyncForm.market === 'stock' && singleSyncForm.scope === 'trend') {
    return syncStockTrendData(code);
  }
  if (singleSyncForm.market === 'stock') {
    return syncStockDailyKlineData(code);
  }
  if (singleSyncForm.market === 'index') {
    return syncIndexKlineData(
      code,
      singleSyncForm.periodType,
      normalizedLimit(),
    );
  }
  return syncBondKlineData(code, singleSyncForm.periodType, normalizedLimit());
}

function normalizedLimit() {
  const limit = Number(singleSyncForm.limit);
  if (!Number.isFinite(limit)) {
    return 250;
  }
  return Math.min(250, Math.max(1, Math.trunc(limit)));
}

function normalizeLimitInput() {
  singleSyncForm.limit = normalizedLimit();
}

function latestJobOf(kind: MarketKind) {
  return latestJobMap.value[kind];
}

function isItemRunning(kind: MarketKind) {
  return runningMap.value[kind] || latestJobOf(kind)?.status === 'running';
}

function statusLabel(kind: MarketKind) {
  const job = latestJobOf(kind);
  if (isItemRunning(kind)) {
    return '运行中';
  }
  if (job?.status === 'success') {
    return '成功';
  }
  if (job?.status === 'failed') {
    return '失败';
  }
  return '暂无记录';
}

function statusTagType(kind: MarketKind) {
  const job = latestJobOf(kind);
  if (isItemRunning(kind)) {
    return 'warning';
  }
  if (job?.status === 'success') {
    return 'success';
  }
  if (job?.status === 'failed') {
    return 'danger';
  }
  return 'info';
}

function triggerLabel(triggerType?: string) {
  if (triggerType === 'manual') {
    return '手动';
  }
  if (triggerType === 'scheduled') {
    return '定时';
  }
  return '-';
}

function triggerText(kind: MarketKind) {
  return `触发：${triggerLabel(latestJobOf(kind)?.triggerType)}`;
}

function finishedText(kind: MarketKind) {
  const job = latestJobOf(kind);
  if (isItemRunning(kind)) {
    return '运行中';
  }
  return job?.finishedAt ?? '暂无记录';
}

function durationText(durationMs?: number) {
  if (durationMs === null || durationMs === undefined) {
    return '-';
  }
  if (durationMs < 1000) {
    return `${durationMs}ms`;
  }
  const seconds = Math.round(durationMs / 1000);
  if (seconds < 60) {
    return `${seconds}s`;
  }
  const minutes = Math.floor(seconds / 60);
  return `${minutes}m ${seconds % 60}s`;
}

const singleSyncLabel = computed(() => {
  const market = labelOf(singleSyncForm.market);
  if (singleSyncForm.scope === 'trend') {
    return `${singleSyncForm.code.trim()} ${market}分时`;
  }
  const period =
    periodOptions.find((item) => item.value === singleSyncForm.periodType)
      ?.label ?? '日 K';
  return `${singleSyncForm.code.trim()} ${market}${period}`;
});

function labelOf(kind: MarketKind) {
  if (kind === 'stock') {
    return '股票';
  }
  if (kind === 'index') {
    return '指数';
  }
  return '可转债';
}
</script>

<template>
  <Page title="数据同步">
    <div class="data-sync-page">
      <section class="health-grid">
        <ElCard
          v-for="item in healthItems"
          :key="item.key"
          class="health-card"
          shadow="never"
        >
          <div class="health-card-header">
            <h2>{{ labelOf(item.key) }}</h2>
            <ElTag :type="statusTagType(item.key)" effect="plain" size="small">
              {{ statusLabel(item.key) }}
            </ElTag>
          </div>
          <dl>
            <div>
              <dt>全量任务</dt>
              <dd>{{ statusLabel(item.key) }}</dd>
            </div>
            <div>
              <dt>最近完成</dt>
              <dd>{{ finishedText(item.key) }}</dd>
            </div>
            <div>
              <dt>耗时</dt>
              <dd>{{ durationText(item.job?.durationMs) }}</dd>
            </div>
          </dl>
        </ElCard>
      </section>

      <ElCard class="sync-panel" shadow="never">
        <div class="panel-header">
          <div>
            <h2>全量同步任务</h2>
            <p>刷新已启用标的的快照、分时与 K 线数据。</p>
          </div>
          <ElButton
            :loading="refreshingStatus"
            @click="refreshMarketStatuses()"
          >
            刷新状态
          </ElButton>
        </div>
        <div class="sync-task-list">
          <div v-for="item in syncItems" :key="item.key" class="sync-task-row">
            <div class="task-title">
              <h3>{{ item.title }}</h3>
              <ElTag effect="plain" size="small">
                {{ item.scope }}
              </ElTag>
            </div>
            <p>{{ item.description }}</p>
            <div class="task-status">
              <span
                class="status-dot"
                :class="[
                  {
                    'is-failed':
                      !isItemRunning(item.key) &&
                      latestJobOf(item.key)?.status === 'failed',
                    'is-running': isItemRunning(item.key),
                  },
                ]"
              ></span>
              {{ statusLabel(item.key) }}
            </div>
            <div class="task-meta">
              <span>{{ triggerText(item.key) }}</span>
              <span>完成：{{ finishedText(item.key) }}</span>
            </div>
            <ElButton
              :disabled="isItemRunning(item.key) && !loadingMap[item.key]"
              :loading="loadingMap[item.key]"
              type="primary"
              @click="runMarketSync(item.key)"
            >
              执行全量同步
            </ElButton>
          </div>
        </div>
      </ElCard>

      <ElCard class="sync-panel" shadow="never">
        <div class="panel-header">
          <div>
            <h2>指定标的补数</h2>
            <p>补齐单个标的的分时或 K 线数据。</p>
          </div>
        </div>
        <div class="single-sync-form">
          <label>
            <span>标的类型</span>
            <ElSelect v-model="singleSyncForm.market">
              <ElOption
                v-for="item in marketOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </label>
          <label>
            <span>标的代码</span>
            <ElInput
              v-model="singleSyncForm.code"
              clearable
              maxlength="6"
              placeholder="例如 000001"
              @keyup.enter="runSingleSync"
            />
          </label>
          <label>
            <span>数据类型</span>
            <ElSelect v-model="singleSyncForm.scope">
              <ElOption
                v-for="item in singleScopeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </label>
          <label v-if="showPeriodSelect">
            <span>K 线周期</span>
            <ElSelect v-model="singleSyncForm.periodType">
              <ElOption
                v-for="item in periodOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </label>
          <label v-if="showPeriodSelect">
            <span>拉取条数</span>
            <ElInput
              v-model.number="singleSyncForm.limit"
              type="number"
              min="1"
              max="250"
              @blur="normalizeLimitInput"
              @keyup.enter="runSingleSync"
            />
          </label>
          <ElButton
            class="single-sync-button"
            :disabled="!singleSyncForm.code.trim()"
            :loading="loadingMap.single"
            type="primary"
            @click="runSingleSync"
          >
            同步指定标的
          </ElButton>
        </div>
      </ElCard>
    </div>
  </Page>
</template>

<style scoped>
.data-sync-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.health-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.health-card,
.sync-panel {
  background: var(--el-bg-color);
  border-color: var(--el-border-color-light);
  border-radius: 8px;
}

.health-card-header,
.panel-header,
.task-title,
.task-status {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
}

.panel-header {
  margin-bottom: 16px;
}

.health-card dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 16px 0 0;
}

.health-card dl div {
  padding: 10px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

dt {
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

dd {
  margin: 2px 0 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--el-text-color-primary);
}

h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
  color: var(--el-text-color-primary);
}

h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
  color: var(--el-text-color-primary);
}

p {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--el-text-color-regular);
}

.sync-task-list {
  overflow: hidden;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.sync-task-row {
  display: grid;
  grid-template-columns:
    minmax(180px, 1.1fr) minmax(240px, 1.8fr) 88px minmax(160px, 1fr)
    132px;
  gap: 16px;
  align-items: center;
  padding: 14px 16px;
}

.sync-task-row + .sync-task-row {
  border-top: 1px solid var(--el-border-color-light);
}

.task-title {
  justify-content: flex-start;
}

.sync-task-row p {
  margin: 0;
}

.task-status {
  gap: 8px;
  justify-content: flex-start;
  font-size: 13px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

.status-dot {
  display: inline-flex;
  width: 8px;
  height: 8px;
  background: #57d188;
  border-radius: 999px;
}

.status-dot.is-running {
  background: #efbd48;
}

.status-dot.is-failed {
  background: #dc4446;
}

.task-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.single-sync-form {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
}

.single-sync-form label {
  display: flex;
  flex: 1 1 160px;
  flex-direction: column;
  gap: 6px;
  min-width: 150px;
}

.single-sync-form label span {
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-regular);
}

.single-sync-button {
  flex: 0 0 auto;
}

@media (max-width: 960px) {
  .sync-task-row {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .sync-task-row :deep(.el-button) {
    width: 100%;
  }
}

@media (max-width: 640px) {
  .health-card dl {
    grid-template-columns: 1fr;
  }

  .panel-header {
    flex-direction: column;
    align-items: stretch;
  }

  .single-sync-form,
  .single-sync-button {
    width: 100%;
  }
}
</style>
