<script lang="ts" setup>
import type {
  AiConsoleOverview,
  AiTokenUsageTrend,
  AppVisitTrend,
} from '#/api/console';
import type { MarketSyncJob } from '#/api/market-sync';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { ElButton, ElCard, ElMessage, ElSkeleton, ElTag } from 'element-plus';

import {
  getAiConsoleOverview,
  listAiTokenUsageTrends,
  listAppVisitTrends,
} from '#/api/console';
import PageHero from '#/components/page-hero/index.vue';
import { listLatestFullMarketSyncJobs } from '#/api/market-sync';

import AnalyticsTrends from './analytics-trends.vue';
import AnalyticsVisitTrends from './analytics-visit-trends.vue';

type MarketKind = 'bond' | 'index' | 'stock';
type SignalStatus = 'failed' | 'idle' | 'running' | 'success' | 'warning';

interface MetricCard {
  detail: string;
  label: string;
  status: SignalStatus;
  trend: string;
  value: string;
}

interface MonitorSignal {
  detail: string;
  key: string;
  meta: string;
  status: SignalStatus;
  title: string;
}

const PERIOD_DAYS = 30;
const VISIT_TREND_HOURS = 24;
const MARKET_LABELS: Record<MarketKind, string> = {
  bond: '可转债同步',
  index: '指数同步',
  stock: '股票同步',
};

const loading = ref(false);
const overview = ref<AiConsoleOverview>();
const tokenTrends = ref<AiTokenUsageTrend[]>([]);
const visitTrends = ref<AppVisitTrend[]>([]);
const marketSyncJobs = ref<MarketSyncJob[]>([]);

const latestJobMap = computed(() => {
  const result: Partial<Record<MarketKind, MarketSyncJob>> = {};
  for (const job of marketSyncJobs.value) {
    result[job.targetType] = job;
  }
  return result;
});

const hasRuntimeData = computed(
  () =>
    Boolean(overview.value) ||
    tokenTrends.value.length > 0 ||
    visitTrends.value.length > 0 ||
    marketSyncJobs.value.length > 0,
);

const metricCards = computed<MetricCard[]>(() => {
  const visit = overview.value?.visit;
  const tokenUsage = overview.value?.tokenUsage;
  const user = overview.value?.user;
  const runningCount = marketSyncJobs.value.filter(
    (item) => item.status === 'running',
  ).length;
  const failedCount = marketSyncJobs.value.filter(
    (item) => item.status === 'failed',
  ).length;
  const successCount = marketSyncJobs.value.filter(
    (item) => item.status === 'success',
  ).length;

  return [
    {
      detail: `累计 ${formatNumber(visit?.totalVisitCount)} 次`,
      label: '近 30 天访问',
      status: visit?.latestOccurredAt ? 'success' : 'idle',
      trend: `最近访问 ${formatTime(visit?.latestOccurredAt)}`,
      value: formatNumber(visit?.periodVisitCount),
    },
    {
      detail: `启用用户 ${formatNumber(user?.totalUserCount)} 人`,
      label: '独立用户',
      status: visit?.uniqueUserCount ? 'success' : 'idle',
      trend: '按访问日志去重',
      value: formatNumber(visit?.uniqueUserCount),
    },
    {
      detail: `Token ${formatNumber(tokenUsage?.totalTokens)}`,
      label: 'AI 调用',
      status: tokenUsage?.latestOccurredAt ? 'success' : 'idle',
      trend: `最近调用 ${formatTime(tokenUsage?.latestOccurredAt)}`,
      value: formatNumber(tokenUsage?.requestCount),
    },
    {
      detail: `成功 ${successCount}，失败 ${failedCount}`,
      label: '全量同步',
      status: marketSyncStatus(runningCount, failedCount),
      trend: runningCount > 0 ? `${runningCount} 项运行中` : latestSyncText(),
      value: `${marketSyncJobs.value.length}/3`,
    },
  ];
});

function marketSyncStatus(runningCount: number, failedCount: number) {
  if (runningCount > 0) {
    return 'running';
  }
  if (failedCount > 0) {
    return 'failed';
  }
  return 'success';
}

const monitorSignals = computed<MonitorSignal[]>(() => {
  const tokenUsage = overview.value?.tokenUsage;
  const visit = overview.value?.visit;
  const signals: MonitorSignal[] = [
    {
      detail: `输入 ${formatNumber(tokenUsage?.promptTokens)}，输出 ${formatNumber(
        tokenUsage?.completionTokens,
      )}，缓存 ${formatNumber(tokenUsage?.cachedTokens)}，推理 ${formatNumber(
        tokenUsage?.reasoningTokens,
      )}`,
      key: 'ai-token',
      meta: `近 ${PERIOD_DAYS} 天 ${formatNumber(tokenUsage?.requestCount)} 次调用`,
      status: tokenUsage?.latestOccurredAt ? 'success' : 'idle',
      title: 'AI Token 记录',
    },
    {
      detail: `总访问 ${formatNumber(visit?.totalVisitCount)}，近 ${PERIOD_DAYS} 天 ${formatNumber(
        visit?.periodVisitCount,
      )}，独立用户 ${formatNumber(visit?.uniqueUserCount)}`,
      key: 'visit-log',
      meta: `最近写入 ${formatTime(visit?.latestOccurredAt)}`,
      status: visit?.latestOccurredAt ? 'success' : 'idle',
      title: '访问日志',
    },
  ];

  (Object.keys(MARKET_LABELS) as MarketKind[]).forEach((kind) => {
    const job = latestJobMap.value[kind];
    signals.push({
      detail: job
        ? syncJobDetail(job)
        : '还没有全量同步任务记录，执行一次同步后会显示任务结果。',
      key: kind,
      meta: job ? syncJobMeta(job) : '暂无任务',
      status: job?.status ?? 'idle',
      title: MARKET_LABELS[kind],
    });
  });

  return signals;
});

onMounted(() => {
  void loadMonitorData(true);
});

async function loadMonitorData(silent = false) {
  if (loading.value) return;
  loading.value = true;
  try {
    const [overviewResult, tokenTrendResult, visitTrendResult, syncJobs] =
      await Promise.all([
        getAiConsoleOverview(PERIOD_DAYS),
        listAiTokenUsageTrends(PERIOD_DAYS),
        listAppVisitTrends(VISIT_TREND_HOURS),
        listLatestFullMarketSyncJobs(),
      ]);
    overview.value = overviewResult;
    tokenTrends.value = tokenTrendResult;
    visitTrends.value = visitTrendResult;
    marketSyncJobs.value = syncJobs;
  } catch {
    if (!silent) {
      ElMessage.error('系统监控数据刷新失败');
    }
  } finally {
    loading.value = false;
  }
}

function statusLabel(status: SignalStatus) {
  if (status === 'running') return '运行中';
  if (status === 'success') return '正常';
  if (status === 'failed') return '异常';
  if (status === 'warning') return '注意';
  return '暂无数据';
}

function statusTagType(status: SignalStatus) {
  if (status === 'running' || status === 'warning') return 'warning';
  if (status === 'success') return 'success';
  if (status === 'failed') return 'danger';
  return 'info';
}

function latestSyncText() {
  const finishedJobs = marketSyncJobs.value
    .filter((item) => item.finishedAt)
    .toSorted(
      (left, right) =>
        new Date(right.finishedAt ?? '').getTime() -
        new Date(left.finishedAt ?? '').getTime(),
    );
  const latest = finishedJobs[0];
  return latest ? `最近完成 ${formatTime(latest.finishedAt)}` : '暂无完成记录';
}

function syncJobMeta(job: MarketSyncJob) {
  if (job.status === 'running') {
    return `开始于 ${formatTime(job.startedAt)}`;
  }
  return `完成于 ${formatTime(job.finishedAt)}`;
}

function syncJobDetail(job: MarketSyncJob) {
  if (job.status === 'failed') {
    return job.errorMessage || '同步失败，未返回错误摘要。';
  }
  const trigger = job.triggerType === 'scheduled' ? '定时触发' : '手动触发';
  return `${trigger}，耗时 ${formatDuration(job.durationMs)}，任务 ${job.jobNo}`;
}

function formatDuration(durationMs?: number) {
  if (durationMs === null || durationMs === undefined) return '-';
  if (durationMs < 1000) return `${durationMs}ms`;
  const seconds = Math.round(durationMs / 1000);
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  return `${minutes}m ${seconds % 60}s`;
}

function formatNumber(value?: number) {
  return Number(value ?? 0).toLocaleString();
}

function formatTime(value?: string) {
  if (!value) return '暂无';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(
    2,
    '0',
  )}-${String(date.getDate()).padStart(2, '0')} ${String(
    date.getHours(),
  ).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}
</script>

<template>
  <Page>
    <div class="monitor-page">
      <PageHero
        description="聚合访问日志、AI Token 和行情同步任务，快速确认系统是否正常写入。"
        title="系统监控"
      >
        <template #actions>
          <ElButton
            :loading="loading"
            type="primary"
            @click="loadMonitorData()"
          >
            <IconifyIcon icon="lucide:refresh-cw" />
            刷新监控
          </ElButton>
        </template>
      </PageHero>

      <ElSkeleton v-if="loading && !hasRuntimeData" :rows="10" animated />

      <template v-else>
        <section class="metric-grid">
          <ElCard
            v-for="item in metricCards"
            :key="item.label"
            class="metric-card"
            shadow="never"
          >
            <div class="metric-card-head">
              <span>{{ item.label }}</span>
              <ElTag
                :type="statusTagType(item.status)"
                effect="plain"
                size="small"
              >
                {{ statusLabel(item.status) }}
              </ElTag>
            </div>
            <strong>{{ item.value }}</strong>
            <p>{{ item.detail }}</p>
            <small>{{ item.trend }}</small>
          </ElCard>
        </section>

        <section class="chart-grid">
          <ElCard class="monitor-panel" shadow="never">
            <div class="panel-header">
              <div>
                <h3>Token 用量趋势</h3>
                <p>近 {{ PERIOD_DAYS }} 天输入与输出 Token 消耗。</p>
              </div>
              <ElTag effect="plain" size="small">AI</ElTag>
            </div>
            <div class="chart-box">
              <AnalyticsTrends :trends="tokenTrends" />
            </div>
          </ElCard>

          <ElCard class="monitor-panel" shadow="never">
            <div class="panel-header">
              <div>
                <h3>访问写入趋势</h3>
                <p>近 {{ VISIT_TREND_HOURS }} 小时访问次数与独立用户。</p>
              </div>
              <ElTag effect="plain" size="small">访问日志</ElTag>
            </div>
            <div class="chart-box">
              <AnalyticsVisitTrends :trends="visitTrends" />
            </div>
          </ElCard>
        </section>

        <ElCard class="monitor-panel" shadow="never">
          <div class="panel-header">
            <div>
              <h3>关键运行信号</h3>
              <p>只展示已有真实数据源，未接入的运行指标不会伪造状态。</p>
            </div>
          </div>
          <div class="signal-list">
            <div
              v-for="signal in monitorSignals"
              :key="signal.key"
              class="signal-row"
            >
              <div class="signal-main">
                <span
                  class="status-dot"
                  :class="[`is-${signal.status}`]"
                ></span>
                <div>
                  <h4>{{ signal.title }}</h4>
                  <p>{{ signal.detail }}</p>
                </div>
              </div>
              <div class="signal-meta">
                <ElTag
                  :type="statusTagType(signal.status)"
                  effect="plain"
                  size="small"
                >
                  {{ statusLabel(signal.status) }}
                </ElTag>
                <span>{{ signal.meta }}</span>
              </div>
            </div>
          </div>
        </ElCard>
      </template>
    </div>
  </Page>
</template>

<style scoped>
.monitor-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.metric-grid,
.chart-grid {
  display: grid;
  gap: 16px;
}

.metric-grid {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.chart-grid {
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
}

.metric-card,
.monitor-panel {
  background: var(--el-bg-color);
  border-color: var(--el-border-color-light);
  border-radius: 8px;
}

.metric-card-head,
.panel-header,
.signal-row,
.signal-main,
.signal-meta {
  display: flex;
  align-items: center;
}

.metric-card-head,
.panel-header,
.signal-row {
  justify-content: space-between;
}

.metric-card-head {
  gap: 10px;
}

.metric-card-head span,
.signal-meta span {
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.metric-card strong {
  display: block;
  margin-top: 14px;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--el-text-color-primary);
}

.metric-card p,
.metric-card small,
.panel-header p,
.signal-main p {
  margin: 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--el-text-color-regular);
}

.metric-card p {
  margin-top: 6px;
}

.metric-card small {
  display: block;
  margin-top: 10px;
  color: var(--el-text-color-secondary);
}

.panel-header {
  gap: 16px;
  margin-bottom: 14px;
}

h3,
h4 {
  margin: 0;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

h3 {
  font-size: 15px;
  line-height: 22px;
}

h4 {
  font-size: 14px;
  line-height: 22px;
}

.chart-box {
  min-width: 0;
  height: 320px;
}

.signal-list {
  overflow: hidden;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.signal-row {
  gap: 18px;
  padding: 14px 16px;
}

.signal-row + .signal-row {
  border-top: 1px solid var(--el-border-color-light);
}

.signal-main {
  gap: 10px;
  min-width: 0;
}

.signal-main p {
  margin-top: 2px;
  overflow-wrap: break-word;
}

.signal-meta {
  flex: 0 0 220px;
  gap: 10px;
  justify-content: flex-end;
  text-align: right;
}

.status-dot {
  flex: 0 0 auto;
  width: 8px;
  height: 8px;
  background: #94a3b8;
  border-radius: 999px;
}

.status-dot.is-success {
  background: #57d188;
}

.status-dot.is-running,
.status-dot.is-warning {
  background: #efbd48;
}

.status-dot.is-failed {
  background: #dc4446;
}

@media (max-width: 900px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }

  .signal-row {
    flex-direction: column;
    align-items: stretch;
  }

  .signal-meta {
    flex: 0 0 auto;
    justify-content: flex-start;
    text-align: left;
  }
}

@media (max-width: 640px) {
  .panel-header {
    flex-direction: column;
    align-items: stretch;
  }

  .chart-box {
    height: 280px;
  }
}
</style>
