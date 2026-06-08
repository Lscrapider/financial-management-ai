<script lang="ts" setup>
import type {
  SceneAnalysisReportTarget,
  SceneReportStatus,
} from '#/api/scene-analysis';
import type { StockAlertConfig } from '#/api/stock-alert';
import type { WatchGroup } from '#/api/watch-pool';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { ElButton, ElCard, ElEmpty, ElSkeleton, ElTag } from 'element-plus';

import { listSceneReportTargets } from '#/api/scene-analysis';
import { listStockAlerts } from '#/api/stock-alert';
import { listWatchGroups } from '#/api/watch-pool';

type WorkbenchTargetType =
  | 'BOND'
  | 'FUND'
  | 'INDEX'
  | 'SECTOR'
  | 'STOCK'
  | string;

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

const RUNNING_REPORT_STATUSES = new Set<SceneReportStatus>([
  'generating_report',
  'pending',
  'processing_current_scenes',
  'retrieving_knowledge',
]);

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
      items: group.items.toSorted(
        (left, right) =>
          Math.abs(toNumber(right.changePercent)) -
          Math.abs(toNumber(left.changePercent)),
      ),
    }))
    .filter((group) => group.items.length > 0);
});

const triggeredAlerts = computed(() =>
  alerts.value.filter((item) => item.outOfThreshold),
);

const enabledAlerts = computed(() =>
  alerts.value.filter((item) => item.enabled),
);

const alertCoverageCount = computed(() => {
  const enabledAlertKeys = new Set(
    enabledAlerts.value.map((item) =>
      targetKey(item.targetType, item.stockCode),
    ),
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
    .toSorted((left, right) => alertUsage(right) - alertUsage(left))
    .slice(0, 5);
});

const alertItems = computed(() => {
  return [...triggeredAlerts.value, ...nearAlerts.value].slice(0, 6);
});

const latestReports = computed(() => reports.value.slice(0, 4));

const runningReportCount = computed(() => {
  return reports.value.filter((item) =>
    RUNNING_REPORT_STATUSES.has(item.latestStatus),
  ).length;
});

const failedReportCount = computed(() => {
  return reports.value.filter((item) => item.latestStatus === 'failed').length;
});

const assetTypeStats = computed(() => {
  const typeOrder: WorkbenchTargetType[] = [
    'STOCK',
    'INDEX',
    'BOND',
    'FUND',
    'SECTOR',
  ];
  return typeOrder
    .map((type) => ({
      count: watchItems.value.filter((item) => item.targetType === type).length,
      label: targetTypeLabel(type),
      type,
    }))
    .filter((item) => item.count > 0);
});

const marketDirectionSummary = computed(() => {
  const summary = { down: 0, flat: 0, noQuote: 0, up: 0 };
  for (const item of watchItems.value) {
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
  }
  return summary;
});

const marketDirectionItems = computed(() => [
  {
    className: 'text-red-500',
    label: '上涨',
    value: marketDirectionSummary.value.up,
  },
  {
    className: 'text-emerald-500',
    label: '下跌',
    value: marketDirectionSummary.value.down,
  },
  { className: '', label: '平盘', value: marketDirectionSummary.value.flat },
  {
    className: '',
    label: '无行情',
    value: marketDirectionSummary.value.noQuote,
  },
]);

const topWatchGroups = computed(() => {
  return watchGroups.value
    .toSorted((left, right) => right.items.length - left.items.length)
    .slice(0, 5);
});

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
      tone: 'yellow',
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
  const tagTypes: Record<
    string,
    'danger' | 'info' | 'primary' | 'success' | 'warning'
  > = {
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

function cockpitToneClass(tone: CockpitTone) {
  return `is-${tone}`;
}

function hasRunningReports() {
  return runningReportCount.value > 0;
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

function toNullableNumber(value?: null | number | string) {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
}

function toNumber(value?: null | number | string) {
  return toNullableNumber(value) ?? 0;
}
</script>

<template>
  <Page>
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
            <span class="text-emerald-500">{{
              marketDirectionSummary.down
            }}</span>
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

      <div v-else class="workbench-content">
        <section class="cockpit-grid" aria-labelledby="cockpit-title">
          <ElCard
            class="workbench-panel cockpit-panel signal-cockpit-panel"
            shadow="never"
          >
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
              >
                <div class="signal-radar-center">
                  <strong>{{ cockpitSignal.label }}</strong>
                  <span>{{ cockpitSignal.description }}</span>
                </div>
              </div>

              <div class="signal-list">
                <div
                  v-for="signal in cockpitSignals"
                  :key="signal.key"
                  class="signal-row"
                  :class="cockpitToneClass(signal.tone)"
                >
                  <span>
                    <strong>{{ signal.label }}</strong>
                    <small>{{ signal.description }}</small>
                  </span>
                  <b>{{ signal.value }}</b>
                </div>
              </div>
            </div>
          </ElCard>

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

              <div
                class="report-flow"
                :class="{ 'is-running': hasRunningReports() }"
                aria-hidden="true"
              >
                <span></span>
                <span></span>
                <span></span>
              </div>
              <div class="report-flow-text">
                生成中 {{ runningReportCount }} · 失败 {{ failedReportCount }}
              </div>
            </ElCard>
          </div>
        </section>

        <div class="workbench-grid">
          <ElCard class="workbench-panel movement-panel" shadow="never">
            <template #header>
              <div class="panel-header">
                <div>
                  <span>观察池异动</span>
                  <small>按涨跌幅绝对值排序</small>
                </div>
                <ElButton link type="primary" @click="openWatchPool">
                  全部
                </ElButton>
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
                <ElButton link type="primary" @click="openWatchPool">
                  管理
                </ElButton>
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
                    已启用 {{ enabledAlerts.length }} · 覆盖
                    {{ alertCoverageCount }}/{{ watchItems.length }}
                  </small>
                </div>
                <ElButton link type="primary" @click="openStockAlert">
                  配置
                </ElButton>
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
                      {{ item.stockCode }} · 阈值
                      {{ formatThreshold(item.thresholdPercent) }}
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
                    生成中 {{ runningReportCount }} · 失败
                    {{ failedReportCount }}
                  </small>
                </div>
                <ElButton link type="primary" @click="openSceneReports">
                  全部
                </ElButton>
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
                      {{ targetTypeLabel(item.targetType) }} ·
                      {{ item.targetCode }}
                    </small>
                  </div>
                  <ElTag
                    :type="statusTag(item.latestStatus)"
                    effect="plain"
                    size="small"
                  >
                    {{ statusLabel(item.latestStatus) }}
                  </ElTag>
                </div>

                <p class="report-preview">
                  {{ formatReportPreview(item.latestReportPreview) }}
                </p>

                <div class="report-meta-grid">
                  <div>
                    <span>报告类型</span>
                    <strong>{{
                      reportTypeLabel(item.latestReportType)
                    }}</strong>
                  </div>
                  <div>
                    <span>生成方式</span>
                    <strong>{{
                      generationTypeLabel(item.latestGenerationType)
                    }}</strong>
                  </div>
                  <div>
                    <span>版本</span>
                    <strong>{{
                      item.latestVersionNo ? `v${item.latestVersionNo}` : '-'
                    }}</strong>
                  </div>
                  <div>
                    <span>报告数</span>
                    <strong>{{ item.reportCount }}</strong>
                  </div>
                </div>

                <div class="report-footer">
                  <span>模型 {{ item.latestModel || '-' }}</span>
                  <span>{{
                    formatDateTime(
                      item.latestGeneratedAt || item.latestCreatedAt,
                    )
                  }}</span>
                </div>
              </button>
            </div>
            <ElEmpty v-else description="暂无报告动态" />
          </ElCard>
        </div>
      </div>
    </div>
  </Page>
</template>

<style scoped>
.investment-workbench {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.workbench-hero,
.summary-item,
.workbench-panel {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.workbench-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}

.page-meta {
  margin-top: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.hero-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
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
  margin: 0 4px;
  font-size: 18px;
  color: var(--el-text-color-secondary);
}

.workbench-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  align-items: stretch;
}

.workbench-content {
  display: grid;
  gap: 16px;
}

.cockpit-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  align-items: stretch;
}

.cockpit-panel,
.cockpit-side-stack {
  min-width: 0;
}

.cockpit-side-stack {
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  gap: 16px;
}

.cockpit-panel {
  min-height: 430px;
}

.signal-cockpit-panel {
  display: flex;
  flex-direction: column;
  grid-column: span 2;
}

.signal-cockpit-panel :deep(.el-card__body) {
  display: grid;
  flex: 1;
  min-height: 0;
}

.action-panel :deep(.el-card__body),
.report-flow-panel :deep(.el-card__body) {
  height: 100%;
}

.signal-cockpit-body {
  display: grid;
  grid-template-columns: minmax(260px, 0.8fr) minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
  height: 100%;
  min-height: 300px;
}

.signal-radar,
.signal-row,
.action-row {
  --cockpit-tone: #006be6;
  --cockpit-tone-border: rgb(0 107 230 / 28%);
  --cockpit-tone-soft: rgb(0 107 230 / 10%);
}

.signal-radar.is-blue,
.signal-row.is-blue,
.action-row.is-blue {
  --cockpit-tone: #006be6;
  --cockpit-tone-border: rgb(0 107 230 / 28%);
  --cockpit-tone-soft: rgb(0 107 230 / 10%);
}

.signal-radar.is-green,
.signal-row.is-green,
.action-row.is-green {
  --cockpit-tone: #57d188;
  --cockpit-tone-border: rgb(87 209 136 / 28%);
  --cockpit-tone-soft: rgb(87 209 136 / 10%);
}

.signal-radar.is-yellow,
.signal-row.is-yellow,
.action-row.is-yellow {
  --cockpit-tone: #efbd48;
  --cockpit-tone-border: rgb(239 189 72 / 30%);
  --cockpit-tone-soft: rgb(239 189 72 / 12%);
}

.signal-radar.is-red,
.signal-row.is-red,
.action-row.is-red {
  --cockpit-tone: #dc4446;
  --cockpit-tone-border: rgb(220 68 70 / 30%);
  --cockpit-tone-soft: rgb(220 68 70 / 12%);
}

.signal-radar {
  --radar-dial-size: min(360px, 94%);

  position: relative;
  display: grid;
  place-items: center;
  min-height: 300px;
  overflow: hidden;
  background:
    radial-gradient(
      circle at center,
      var(--cockpit-tone-soft) 0 17%,
      transparent 18% 100%
    ),
    repeating-radial-gradient(
      circle at center,
      transparent 0 46px,
      var(--el-border-color-lighter) 47px 48px
    ),
    conic-gradient(
      from 180deg,
      transparent 0deg,
      var(--cockpit-tone-soft) 70deg,
      transparent 150deg,
      var(--el-fill-color-lighter) 220deg,
      transparent 360deg
    ),
    var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  transform: translateZ(0);
}

.signal-radar::before {
  position: absolute;
  top: 50%;
  left: 50%;
  width: var(--radar-dial-size);
  aspect-ratio: 1;
  pointer-events: none;
  content: '';
  background:
    linear-gradient(var(--el-border-color-lighter), transparent 1px) 0 50% /
      100% 1px no-repeat,
    linear-gradient(90deg, var(--el-border-color-lighter), transparent 1px) 50%
      0 / 1px 100% no-repeat;
  border: 1px dashed var(--cockpit-tone-border);
  border-radius: 50%;
  opacity: 0.76;
  transform: translate(-50%, -50%);
}

.signal-radar::after {
  position: absolute;
  top: 50%;
  left: 50%;
  width: var(--radar-dial-size);
  aspect-ratio: 1;
  pointer-events: none;
  content: '';
  background: conic-gradient(
    from 0deg,
    transparent 0deg 292deg,
    color-mix(in srgb, var(--cockpit-tone) 8%, transparent) 306deg,
    color-mix(in srgb, var(--cockpit-tone) 24%, transparent) 332deg,
    color-mix(in srgb, var(--cockpit-tone) 10%, transparent) 350deg,
    transparent 360deg
  );
  border-radius: 50%;
  opacity: 0.64;
  transform: translate(-50%, -50%);
  animation: radar-needle 9s linear infinite;
}

.signal-radar-center {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  justify-content: center;
  width: min(170px, 62%);
  aspect-ratio: 1;
  padding: 18px;
  text-align: center;
  background: var(--el-bg-color);
  border: 1px solid var(--cockpit-tone-border);
  border-radius: 50%;
  transition:
    border-color 0.2s ease,
    transform 0.2s ease;
}

.signal-radar:hover .signal-radar-center {
  transform: scale(1.02);
}

.signal-radar.is-blue .signal-radar-center strong,
.signal-radar.is-green .signal-radar-center strong,
.signal-radar.is-yellow .signal-radar-center strong,
.signal-radar.is-red .signal-radar-center strong {
  color: var(--cockpit-tone);
}

.signal-radar-center strong {
  font-size: 28px;
  line-height: 1;
}

.signal-radar-center span {
  font-size: 12px;
  line-height: 1.45;
  color: var(--el-text-color-secondary);
}

.signal-list,
.action-list {
  display: grid;
  gap: 10px;
}

.signal-list {
  grid-template-rows: repeat(4, minmax(0, 1fr));
}

.signal-row {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  min-height: 66px;
  padding: 12px;
  background: var(--el-fill-color-lighter);
  border: 1px solid transparent;
  border-radius: 6px;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease;
}

.signal-row:hover {
  background: var(--el-fill-color-light);
  border-color: var(--el-border-color-light);
}

.signal-row span,
.action-row span {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.signal-row strong,
.action-row strong {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.signal-row small,
.action-row small,
.report-flow-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.signal-row b {
  flex-shrink: 0;
  font-size: 22px;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  color: var(--cockpit-tone);
}

.action-row {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 68px;
  padding: 12px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: var(--el-fill-color-lighter);
  border: 1px solid transparent;
  border-radius: 6px;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    transform 0.2s ease;
}

.action-row:hover {
  background: var(--el-fill-color-light);
  border-color: var(--cockpit-tone-border);
  transform: translateX(2px);
}

.action-row:focus-visible {
  outline: 2px solid var(--cockpit-tone);
  outline-offset: 2px;
}

.action-row svg {
  flex-shrink: 0;
  color: var(--cockpit-tone);
}

.report-flow-panel :deep(.el-card__body) {
  display: grid;
  gap: 12px;
}

.report-flow {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.report-flow span {
  display: block;
  height: 8px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 999px;
}

.report-flow.is-running span {
  background: linear-gradient(
    90deg,
    var(--el-fill-color-lighter),
    rgb(0 107 230 / 22%),
    var(--el-fill-color-lighter)
  );
  background-size: 220% 100%;
  border-color: rgb(0 107 230 / 26%);
  animation: report-flow-pulse 1.8s ease-in-out infinite;
}

.report-flow.is-running span:nth-child(2) {
  animation-delay: 0.16s;
}

.report-flow.is-running span:nth-child(3) {
  animation-delay: 0.32s;
}

.report-panel {
  grid-column: 1 / -1;
}

.asset-panel,
.movement-panel,
.alert-panel {
  display: flex;
  flex-direction: column;
  height: 520px;
}

.asset-panel :deep(.el-card__body),
.movement-panel :deep(.el-card__body),
.alert-panel :deep(.el-card__body) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-header span {
  display: block;
  font-size: 16px;
  font-weight: 700;
}

.panel-header small {
  display: block;
  margin-top: 4px;
  font-size: 12px;
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
  min-height: 0;
  max-height: 100%;
  padding-right: 4px;
  overflow-y: auto;
  scrollbar-width: thin;
}

.alert-list {
  flex: 1;
}

.asset-overview {
  display: grid;
  gap: 16px;
  min-height: 0;
  max-height: 100%;
  padding-right: 4px;
  overflow-y: auto;
  scrollbar-width: thin;
}

.asset-section {
  padding: 12px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.asset-section-title {
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.asset-type-grid,
.direction-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.asset-type-item,
.direction-item,
.group-rank-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.asset-type-item,
.direction-item {
  min-height: 38px;
  padding: 8px;
  background: var(--el-bg-color);
  border-radius: 6px;
}

.asset-type-item strong,
.direction-item strong,
.group-rank-row strong {
  font-size: 18px;
  line-height: 1;
}

.direction-item span,
.group-rank-row span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.group-rank-list {
  display: grid;
  gap: 8px;
}

.group-rank-row {
  min-height: 30px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.group-rank-row:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.alert-coverage {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.alert-coverage > div {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 68px;
  padding: 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}

.alert-coverage span,
.alert-calm-state span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.alert-coverage strong {
  font-size: 22px;
  line-height: 1.1;
}

.alert-calm-state {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.movement-group {
  display: grid;
  gap: 8px;
}

.movement-group + .movement-group {
  padding-top: 10px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.movement-group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2px;
}

.movement-group-header span {
  font-size: 13px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.movement-group-header small {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.movement-row,
.alert-row,
.report-row {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 12px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: var(--el-fill-color-lighter);
  border: 1px solid transparent;
  border-radius: 6px;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease;
}

.movement-row:hover,
.alert-row:hover,
.report-row:hover {
  background: var(--el-fill-color-light);
  border-color: var(--el-color-primary-light-5);
}

.target-main {
  display: flex;
  gap: 10px;
  align-items: center;
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
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  gap: 6px;
  align-items: flex-end;
  text-align: right;
}

.target-metric strong {
  font-size: 18px;
  line-height: 1.1;
}

.report-row {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: stretch;
  justify-content: flex-start;
  min-height: 168px;
}

.report-row-top {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  justify-content: space-between;
}

.report-preview {
  display: -webkit-box;
  min-height: 39px;
  margin: 0;
  overflow: hidden;
  -webkit-line-clamp: 2;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
  -webkit-box-orient: vertical;
}

.report-meta-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.report-meta-grid div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding: 8px;
  background: var(--el-bg-color);
  border-radius: 6px;
}

.report-meta-grid span,
.report-footer {
  font-size: 12px;
}

.report-meta-grid strong {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  white-space: nowrap;
}

.report-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

@media (max-width: 1280px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .cockpit-grid {
    grid-template-columns: 1fr;
  }

  .cockpit-side-stack {
    grid-template-rows: auto;
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
  .workbench-hero {
    flex-direction: column;
    gap: 16px;
    align-items: stretch;
  }

  .summary-grid,
  .cockpit-grid,
  .cockpit-side-stack,
  .signal-cockpit-body,
  .workbench-grid {
    grid-template-columns: 1fr;
  }

  .movement-panel,
  .asset-panel,
  .alert-panel {
    height: auto;
    max-height: min(70vh, 560px);
  }

  .asset-panel :deep(.el-card__body),
  .movement-panel :deep(.el-card__body),
  .alert-panel :deep(.el-card__body) {
    max-height: calc(min(70vh, 560px) - 57px);
    overflow-y: auto;
  }

  .movement-list,
  .alert-list,
  .asset-overview {
    max-height: none;
    padding-right: 0;
    overflow: visible;
  }

  .hero-actions,
  .action-row,
  .movement-row,
  .alert-row,
  .report-row {
    flex-direction: column;
    align-items: stretch;
  }

  .cockpit-panel {
    min-height: auto;
  }

  .signal-radar {
    --radar-dial-size: min(220px, 72vw);

    min-height: 240px;
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

@keyframes report-flow-pulse {
  0% {
    background-position: 120% 0;
    transform: scaleX(0.94);
  }

  50% {
    background-position: 0 0;
    transform: scaleX(1);
  }

  100% {
    background-position: -120% 0;
    transform: scaleX(0.94);
  }
}

@keyframes radar-needle {
  to {
    transform: translate(-50%, -50%) rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .signal-radar-center,
  .signal-row,
  .action-row,
  .movement-row,
  .alert-row,
  .report-row {
    transition: none;
  }

  .signal-radar:hover .signal-radar-center,
  .signal-row:hover,
  .action-row:hover {
    transform: none;
  }

  .signal-radar::after {
    animation: none;
  }

  .report-flow.is-running span {
    background: rgb(0 107 230 / 18%);
    animation: none;
  }
}
</style>
