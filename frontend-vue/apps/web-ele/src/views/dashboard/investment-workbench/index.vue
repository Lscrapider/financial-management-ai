<script lang="ts" setup>
import type { SceneAnalysisReportTarget, SceneReportStatus } from '#/api/scene-analysis';
import type { StockAlertConfig } from '#/api/stock-alert';
import type { WatchGroup } from '#/api/watch-pool';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import {
  ElButton,
  ElCard,
  ElEmpty,
  ElSkeleton,
  ElTag,
} from 'element-plus';

import { listSceneReportTargets } from '#/api/scene-analysis';
import { listStockAlerts } from '#/api/stock-alert';
import { listWatchGroups } from '#/api/watch-pool';

type WorkbenchTargetType = 'BOND' | 'FUND' | 'INDEX' | 'SECTOR' | 'STOCK' | string;

const router = useRouter();
const loading = ref(false);
const watchGroups = ref<WatchGroup[]>([]);
const alerts = ref<StockAlertConfig[]>([]);
const reports = ref<SceneAnalysisReportTarget[]>([]);

const watchItems = computed(() => {
  return watchGroups.value.flatMap((group) => group.items);
});

const movementGroups = computed(() => {
  return watchGroups.value
    .map((group) => ({
      ...group,
      items: [...group.items].sort(
        (left, right) =>
          Math.abs(toNumber(right.changePercent)) -
          Math.abs(toNumber(left.changePercent)),
      ),
    }))
    .filter((group) => group.items.length > 0);
});

const triggeredAlerts = computed(() => alerts.value.filter((item) => item.outOfThreshold));

const enabledAlerts = computed(() => alerts.value.filter((item) => item.enabled));

const alertCoverageCount = computed(() => {
  const enabledAlertKeys = new Set(
    enabledAlerts.value.map((item) => targetKey(item.targetType, item.stockCode)),
  );
  return watchItems.value.filter((item) =>
    enabledAlertKeys.has(targetKey(item.targetType, item.targetCode)),
  ).length;
});

const alertCoveragePercent = computed(() => {
  if (watchItems.value.length === 0) {
    return 0;
  }
  return (alertCoverageCount.value / watchItems.value.length) * 100;
});

const nearAlerts = computed(() => {
  return enabledAlerts.value
    .filter((item) => !item.outOfThreshold && alertUsage(item) >= 0.8)
    .sort((left, right) => alertUsage(right) - alertUsage(left))
    .slice(0, 5);
});

const alertItems = computed(() => {
  return [...triggeredAlerts.value, ...nearAlerts.value].slice(0, 6);
});

const latestReports = computed(() => reports.value.slice(0, 4));

const runningReportCount = computed(() => {
  return reports.value.filter((item) =>
    ['generating_report', 'pending', 'processing_current_scenes', 'retrieving_knowledge'].includes(item.latestStatus),
  ).length;
});

const failedReportCount = computed(() => {
  return reports.value.filter((item) => item.latestStatus === 'failed').length;
});

const assetTypeStats = computed(() => {
  const typeOrder: WorkbenchTargetType[] = ['STOCK', 'INDEX', 'BOND', 'FUND', 'SECTOR'];
  return typeOrder
    .map((type) => ({
      count: watchItems.value.filter((item) => item.targetType === type).length,
      label: targetTypeLabel(type),
      type,
    }))
    .filter((item) => item.count > 0);
});

const marketDirectionSummary = computed(() => {
  return watchItems.value.reduce(
    (summary, item) => {
      const changePercent = toNullableNumber(item.changePercent);
      if (changePercent === null) {
        summary.noQuote += 1;
      } else if (changePercent > 0) {
        summary.up += 1;
      } else if (changePercent < 0) {
        summary.down += 1;
      } else {
        summary.flat += 1;
      }
      return summary;
    },
    { down: 0, flat: 0, noQuote: 0, up: 0 },
  );
});

const marketDirectionItems = computed(() => [
  { className: 'text-red-500', label: '上涨', value: marketDirectionSummary.value.up },
  { className: 'text-emerald-500', label: '下跌', value: marketDirectionSummary.value.down },
  { className: '', label: '平盘', value: marketDirectionSummary.value.flat },
  { className: '', label: '无行情', value: marketDirectionSummary.value.noQuote },
]);

const topWatchGroups = computed(() => {
  return [...watchGroups.value]
    .sort((left, right) => right.items.length - left.items.length)
    .slice(0, 5);
});

onMounted(() => {
  void refreshWorkbench();
});

async function refreshWorkbench() {
  loading.value = true;
  try {
    const [groups, alertRows, reportPage] = await Promise.all([
      listWatchGroups(),
      listStockAlerts(),
      listSceneReportTargets({ pageNum: 1, pageSize: 4 }),
    ]);
    watchGroups.value = groups;
    alerts.value = alertRows;
    reports.value = reportPage.records;
  } finally {
    loading.value = false;
  }
}

function openWatchPool() {
  void router.push({ name: 'WatchPool' });
}

function openWatchPoolTarget(groupId: string, itemId: string) {
  void router.push({
    name: 'WatchPool',
    query: {
      groupId,
      itemId,
    },
  });
}

function openStockAlert() {
  void router.push({ name: 'StockAlert' });
}

function openSceneReports() {
  void router.push({ name: 'AiSceneReports' });
}

function openReportWorkspace(reportId?: null | number) {
  if (!reportId) {
    openSceneReports();
    return;
  }
  void router.push({
    name: 'AiReportWorkspace',
    query: { reportId },
  });
}

function targetTypeLabel(type: WorkbenchTargetType) {
  const labels: Record<string, string> = {
    BOND: '可转债',
    CONVERTIBLE_BOND: '可转债',
    FUND: '基金',
    INDEX: '指数',
    SECTOR: '板块',
    STOCK: '股票',
  };
  return labels[type] ?? type;
}

function targetTypeTag(type: WorkbenchTargetType) {
  const tagTypes: Record<string, 'danger' | 'info' | 'primary' | 'success' | 'warning'> = {
    BOND: 'warning',
    CONVERTIBLE_BOND: 'warning',
    FUND: 'success',
    INDEX: 'primary',
    SECTOR: 'info',
    STOCK: 'danger',
  };
  return tagTypes[type] ?? 'info';
}

function statusLabel(status: SceneReportStatus | string) {
  const labels: Record<string, string> = {
    failed: '失败',
    generating_report: '生成中',
    pending: '等待中',
    processing_current_scenes: '场景计算',
    retrieving_knowledge: '检索知识库',
    success: '已完成',
  };
  return labels[status] ?? status;
}

function statusTag(status: SceneReportStatus | string) {
  const tagTypes: Record<string, 'danger' | 'info' | 'success' | 'warning'> = {
    failed: 'danger',
    generating_report: 'warning',
    pending: 'info',
    processing_current_scenes: 'warning',
    retrieving_knowledge: 'warning',
    success: 'success',
  };
  return tagTypes[status] ?? 'info';
}

function reportTypeLabel(type?: null | string) {
  const labels: Record<string, string> = {
    quick_analysis: '快速分析',
    risk_check: '风险检查',
    valuation_report: '估值报告',
  };
  return type ? (labels[type] ?? type) : '-';
}

function generationTypeLabel(type?: null | string) {
  const labels: Record<string, string> = {
    initial: '首次生成',
    regenerate: '重新生成',
  };
  return type ? (labels[type] ?? type) : '-';
}

function formatReportPreview(value?: null | string) {
  const preview = value?.trim();
  return preview || '暂无摘要预览';
}

function changeClass(value?: null | number | string) {
  const numberValue = toNumber(value);
  if (numberValue > 0) return 'text-red-500';
  if (numberValue < 0) return 'text-emerald-500';
  return '';
}

function formatChangePercent(value?: null | number | string) {
  const numberValue = toNullableNumber(value);
  if (numberValue === null) return '-';
  return `${numberValue > 0 ? '+' : ''}${numberValue.toFixed(2)}%`;
}

function formatPrice(value?: null | number | string) {
  const numberValue = toNullableNumber(value);
  return numberValue === null ? '-' : numberValue.toFixed(3);
}

function formatThreshold(value?: null | number | string) {
  const numberValue = toNullableNumber(value);
  return numberValue === null ? '-' : `${numberValue.toFixed(2)}%`;
}

function formatDateTime(value?: null | string) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 16);
}

function alertUsage(item: StockAlertConfig) {
  const changePercent = Math.abs(toNumber(item.changePercent));
  const threshold = Math.abs(toNumber(item.thresholdPercent));
  return threshold <= 0 ? 0 : changePercent / threshold;
}

function targetKey(targetType: string, targetCode: string) {
  return `${targetType}:${targetCode}`;
}

function alertStateLabel(item: StockAlertConfig) {
  if (item.outOfThreshold) {
    return '已触发';
  }
  if (alertUsage(item) >= 0.8) {
    return '接近触发';
  }
  return item.enabled ? '监控中' : '未启用';
}

function alertStateTag(item: StockAlertConfig) {
  if (item.outOfThreshold) {
    return 'danger';
  }
  if (alertUsage(item) >= 0.8) {
    return 'warning';
  }
  return item.enabled ? 'success' : 'info';
}

function toNullableNumber(value?: number | string | null) {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
}

function toNumber(value?: number | string | null) {
  return toNullableNumber(value) ?? 0;
}
</script>

<template>
  <Page title="投资工作台">
    <div class="investment-workbench">
      <section class="workbench-hero">
        <div>
          <div class="page-title">投资工作台</div>
          <div class="page-meta">
            聚合观察池异动、资产结构、布控风险和报告动态，优先呈现今天需要关注的投资研究信号。
          </div>
        </div>
        <div class="hero-actions">
          <ElButton :loading="loading" @click="refreshWorkbench">
            <IconifyIcon icon="lucide:refresh-cw" />
            刷新
          </ElButton>
          <ElButton type="primary" @click="openWatchPool">
            <IconifyIcon icon="lucide:spool" />
            查看观察池
          </ElButton>
        </div>
      </section>

      <section class="summary-grid">
        <div class="summary-item">
          <span>观察池标的</span>
          <strong>{{ watchItems.length }}</strong>
        </div>
        <div class="summary-item">
          <span>观察分组</span>
          <strong>{{ watchGroups.length }}</strong>
        </div>
        <div class="summary-item">
          <span>上涨 / 下跌</span>
          <strong>
            <span class="text-red-500">{{ marketDirectionSummary.up }}</span>
            <span class="summary-separator">/</span>
            <span class="text-emerald-500">{{ marketDirectionSummary.down }}</span>
          </strong>
        </div>
        <div class="summary-item">
          <span>报告异常</span>
          <strong :class="failedReportCount > 0 ? 'text-red-500' : ''">
            {{ failedReportCount }}
          </strong>
        </div>
      </section>

      <ElSkeleton v-if="loading" :rows="10" animated />

      <div v-else class="workbench-grid">
        <ElCard class="workbench-panel movement-panel" shadow="never">
          <template #header>
            <div class="panel-header">
              <div>
                <span>观察池异动</span>
                <small>按涨跌幅绝对值排序</small>
              </div>
              <ElButton link type="primary" @click="openWatchPool">全部</ElButton>
            </div>
          </template>

          <div v-if="movementGroups.length > 0" class="movement-list">
            <section
              v-for="group in movementGroups"
              :key="group.id"
              class="movement-group"
            >
              <div class="movement-group-header">
                <span>{{ group.name }}</span>
                <small>{{ group.items.length }} 个标的</small>
              </div>
              <button
                v-for="item in group.items"
                :key="item.id"
                class="movement-row"
                type="button"
                @click="openWatchPoolTarget(group.id, item.id)"
              >
                <div class="target-main">
                  <ElTag
                    :type="targetTypeTag(item.targetType)"
                    effect="plain"
                    size="small"
                  >
                    {{ targetTypeLabel(item.targetType) }}
                  </ElTag>
                  <div>
                    <strong>{{ item.targetName }}</strong>
                    <small>{{ item.targetCode }}</small>
                  </div>
                </div>
                <div class="target-metric">
                  <strong :class="changeClass(item.changePercent)">
                    {{ formatChangePercent(item.changePercent) }}
                  </strong>
                  <small>现价 {{ formatPrice(item.latestPrice) }}</small>
                </div>
              </button>
            </section>
          </div>
          <ElEmpty v-else description="暂无观察池数据" />
        </ElCard>

        <ElCard class="workbench-panel asset-panel" shadow="never">
          <template #header>
            <div class="panel-header">
              <div>
                <span>资产视图</span>
                <small>观察池结构和行情分布</small>
              </div>
              <ElButton link type="primary" @click="openWatchPool">管理</ElButton>
            </div>
          </template>

          <div class="asset-overview">
            <div class="asset-section">
              <div class="asset-section-title">类型分布</div>
              <div v-if="assetTypeStats.length > 0" class="asset-type-grid">
                <div
                  v-for="item in assetTypeStats"
                  :key="item.type"
                  class="asset-type-item"
                >
                  <ElTag
                    :type="targetTypeTag(item.type)"
                    effect="plain"
                    size="small"
                  >
                    {{ item.label }}
                  </ElTag>
                  <strong>{{ item.count }}</strong>
                </div>
              </div>
              <ElEmpty v-else description="暂无观察池数据" />
            </div>

            <div class="asset-section">
              <div class="asset-section-title">涨跌状态</div>
              <div class="direction-grid">
                <div
                  v-for="item in marketDirectionItems"
                  :key="item.label"
                  class="direction-item"
                >
                  <span>{{ item.label }}</span>
                  <strong :class="item.className">{{ item.value }}</strong>
                </div>
              </div>
            </div>

            <div class="asset-section">
              <div class="asset-section-title">分组规模</div>
              <div v-if="topWatchGroups.length > 0" class="group-rank-list">
                <div
                  v-for="group in topWatchGroups"
                  :key="group.id"
                  class="group-rank-row"
                >
                  <span>{{ group.name }}</span>
                  <strong>{{ group.items.length }}</strong>
                </div>
              </div>
              <ElEmpty v-else description="暂无分组数据" />
            </div>
          </div>
        </ElCard>

        <ElCard class="workbench-panel alert-panel" shadow="never">
          <template #header>
            <div class="panel-header">
              <div>
                <span>布控与风险</span>
                <small>
                  已启用 {{ enabledAlerts.length }} · 覆盖 {{ alertCoverageCount }}/{{ watchItems.length }}
                </small>
              </div>
              <ElButton link type="primary" @click="openStockAlert">配置</ElButton>
            </div>
          </template>

          <div class="alert-coverage">
            <div>
              <span>布控覆盖率</span>
              <strong>{{ alertCoveragePercent.toFixed(0) }}%</strong>
            </div>
            <div>
              <span>触发 / 接近</span>
              <strong>
                <span class="text-red-500">{{ triggeredAlerts.length }}</span>
                <span class="summary-separator">/</span>
                <span class="text-amber-500">{{ nearAlerts.length }}</span>
              </strong>
            </div>
          </div>

          <div v-if="alertItems.length > 0" class="alert-list">
            <button
              v-for="item in alertItems"
              :key="item.id"
              class="alert-row"
              type="button"
              @click="openStockAlert"
            >
              <div class="target-main">
                <ElTag
                  :type="targetTypeTag(item.targetType)"
                  effect="plain"
                  size="small"
                >
                  {{ targetTypeLabel(item.targetType) }}
                </ElTag>
                <div>
                  <strong>{{ item.stockName }}</strong>
                  <small>
                    {{ item.stockCode }} · 阈值 {{ formatThreshold(item.thresholdPercent) }}
                  </small>
                </div>
              </div>
              <div class="target-metric">
                <ElTag :type="alertStateTag(item)" effect="dark" size="small">
                  {{ alertStateLabel(item) }}
                </ElTag>
                <small :class="changeClass(item.changePercent)">
                  {{ formatChangePercent(item.changePercent) }}
                </small>
              </div>
            </button>
          </div>
          <div v-else class="alert-calm-state">
            <strong>暂无触发或接近触发的布控</strong>
            <span>
              当前页面保留布控覆盖和启用数量，具体规则可以进入布控配置继续维护。
            </span>
          </div>
        </ElCard>

        <ElCard class="workbench-panel report-panel" shadow="never">
          <template #header>
            <div class="panel-header">
              <div>
                <span>报告动态</span>
                <small>
                  生成中 {{ runningReportCount }} · 失败 {{ failedReportCount }}
                </small>
              </div>
              <ElButton link type="primary" @click="openSceneReports">全部</ElButton>
            </div>
          </template>

          <div v-if="latestReports.length > 0" class="report-list">
            <button
              v-for="item in latestReports"
              :key="`${item.targetType}-${item.targetCode}-${item.latestTaskNo}`"
              class="report-row"
              type="button"
              @click="openReportWorkspace(item.latestReportId)"
            >
              <div class="report-row-top">
                <div class="report-title">
                  <strong>{{ item.targetName || item.targetCode }}</strong>
                  <small>
                    {{ targetTypeLabel(item.targetType) }} · {{ item.targetCode }}
                  </small>
                </div>
                <ElTag :type="statusTag(item.latestStatus)" effect="plain" size="small">
                  {{ statusLabel(item.latestStatus) }}
                </ElTag>
              </div>

              <p class="report-preview">
                {{ formatReportPreview(item.latestReportPreview) }}
              </p>

              <div class="report-meta-grid">
                <div>
                  <span>报告类型</span>
                  <strong>{{ reportTypeLabel(item.latestReportType) }}</strong>
                </div>
                <div>
                  <span>生成方式</span>
                  <strong>{{ generationTypeLabel(item.latestGenerationType) }}</strong>
                </div>
                <div>
                  <span>版本</span>
                  <strong>{{ item.latestVersionNo ? `v${item.latestVersionNo}` : '-' }}</strong>
                </div>
                <div>
                  <span>报告数</span>
                  <strong>{{ item.reportCount }}</strong>
                </div>
              </div>

              <div class="report-footer">
                <span>模型 {{ item.latestModel || '-' }}</span>
                <span>任务 {{ item.latestTaskNo || '-' }}</span>
                <span>{{ formatDateTime(item.latestGeneratedAt || item.latestCreatedAt) }}</span>
              </div>
            </button>
          </div>
          <ElEmpty v-else description="暂无报告动态" />
        </ElCard>
      </div>
    </div>
  </Page>
</template>

<style scoped>
.investment-workbench {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
}

.workbench-hero,
.summary-item,
.workbench-panel {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.workbench-hero {
  align-items: center;
  display: flex;
  justify-content: space-between;
  padding: 20px 24px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}

.page-meta {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-top: 8px;
}

.hero-actions {
  align-items: center;
  display: flex;
  gap: 10px;
}

.summary-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 86px;
  padding: 16px;
}

.summary-item span,
.panel-header small,
.target-main small,
.target-metric small,
.report-title small,
.report-footer,
.report-meta-grid span {
  color: var(--el-text-color-secondary);
}

.summary-item strong {
  font-size: 26px;
  line-height: 1.1;
}

.summary-separator {
  color: var(--el-text-color-secondary);
  font-size: 18px;
  margin: 0 4px;
}

.workbench-grid {
  align-items: stretch;
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.report-panel {
  grid-column: 1 / -1;
}

.asset-panel,
.movement-panel,
.alert-panel {
  height: 520px;
}

.asset-panel :deep(.el-card__body),
.movement-panel :deep(.el-card__body),
.alert-panel :deep(.el-card__body) {
  height: calc(100% - 57px);
  overflow: hidden;
}

.panel-header {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.panel-header span {
  display: block;
  font-size: 16px;
  font-weight: 700;
}

.panel-header small {
  display: block;
  font-size: 12px;
  margin-top: 4px;
}

.movement-list,
.report-list {
  display: grid;
  gap: 10px;
}

.report-list {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.movement-list,
.alert-list {
  display: grid;
  gap: 10px;
  max-height: 100%;
  overflow-y: auto;
  padding-right: 4px;
  scrollbar-width: thin;
}

.asset-overview {
  display: grid;
  gap: 16px;
  max-height: 100%;
  overflow-y: auto;
  padding-right: 4px;
  scrollbar-width: thin;
}

.asset-section {
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 12px;
}

.asset-section-title {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-bottom: 10px;
}

.asset-type-grid,
.direction-grid {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.asset-type-item,
.direction-item,
.group-rank-row {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.asset-type-item,
.direction-item {
  background: var(--el-bg-color);
  border-radius: 6px;
  min-height: 38px;
  padding: 8px;
}

.asset-type-item strong,
.direction-item strong,
.group-rank-row strong {
  font-size: 18px;
  line-height: 1;
}

.direction-item span,
.group-rank-row span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.group-rank-list {
  display: grid;
  gap: 8px;
}

.group-rank-row {
  border-bottom: 1px dashed var(--el-border-color-lighter);
  min-height: 30px;
  padding-bottom: 8px;
}

.group-rank-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.alert-coverage {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-bottom: 12px;
}

.alert-coverage > div {
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 68px;
  padding: 12px;
}

.alert-coverage span,
.alert-calm-state span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.alert-coverage strong {
  font-size: 22px;
  line-height: 1.1;
}

.alert-calm-state {
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
}

.movement-group {
  display: grid;
  gap: 8px;
}

.movement-group + .movement-group {
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 10px;
}

.movement-group-header {
  align-items: center;
  display: flex;
  justify-content: space-between;
  padding: 0 2px;
}

.movement-group-header span {
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 700;
}

.movement-group-header small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.movement-row,
.alert-row,
.report-row {
  align-items: center;
  background: var(--el-fill-color-lighter);
  border: 1px solid transparent;
  border-radius: 6px;
  color: inherit;
  cursor: pointer;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  padding: 12px;
  text-align: left;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease;
  width: 100%;
}

.movement-row:hover,
.alert-row:hover,
.report-row:hover {
  background: var(--el-fill-color-light);
  border-color: var(--el-color-primary-light-5);
}

.target-main {
  align-items: center;
  display: flex;
  gap: 10px;
  min-width: 0;
}

.target-main div,
.report-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.target-main strong,
.report-title strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.target-metric {
  align-items: flex-end;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  gap: 6px;
  text-align: right;
}

.target-metric strong {
  font-size: 18px;
  line-height: 1.1;
}

.report-row {
  align-items: stretch;
  display: flex;
  flex-direction: column;
  gap: 12px;
  justify-content: flex-start;
  min-height: 168px;
}

.report-row-top {
  align-items: flex-start;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.report-preview {
  color: var(--el-text-color-regular);
  display: -webkit-box;
  font-size: 13px;
  line-height: 1.5;
  margin: 0;
  min-height: 39px;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.report-meta-grid {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.report-meta-grid div {
  background: var(--el-bg-color);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding: 8px;
}

.report-meta-grid span,
.report-footer {
  font-size: 12px;
}

.report-meta-grid strong {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.report-footer {
  border-top: 1px dashed var(--el-border-color-lighter);
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  padding-top: 10px;
}

@media (max-width: 1200px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workbench-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .movement-panel {
    grid-column: 1 / -1;
  }
}

@media (max-width: 768px) {
  .investment-workbench {
    padding: 12px;
  }

  .workbench-hero {
    align-items: stretch;
    flex-direction: column;
    gap: 16px;
  }

  .hero-actions,
  .summary-grid,
  .workbench-grid {
    grid-template-columns: 1fr;
  }

  .movement-panel,
  .asset-panel,
  .alert-panel {
    height: 460px;
  }

  .hero-actions,
  .movement-row,
  .alert-row,
  .report-row {
    align-items: stretch;
    flex-direction: column;
  }

  .target-metric {
    align-items: flex-start;
    text-align: left;
  }

  .report-list,
  .report-meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
