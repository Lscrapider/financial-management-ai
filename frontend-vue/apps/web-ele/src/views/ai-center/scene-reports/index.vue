<script lang="ts" setup>
import type {
  SceneAnalysisConfigProfile,
  SceneAnalysisReportDetail,
  SceneAnalysisReportHistory,
  SceneAnalysisReportTarget,
  SceneAnalysisReportType,
  SceneAnalysisTargetOption,
  SceneReportStatus,
} from '#/api/scene-analysis';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElPagination,
  ElPopconfirm,
  ElSelect,
  ElSlider,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTooltip,
} from 'element-plus';

import {
  createSceneAnalysisConfigProfile,
  deleteSceneAnalysisConfigProfile,
  getSceneAnalysisConfigParameterSchema,
  getSceneAnalysisReportTypes,
  getSceneReportDetail,
  listSceneAnalysisConfigProfiles,
  listSceneReportHistory,
  listSceneReportTargets,
  regenerateSceneReport,
  searchSceneAnalysisTargets,
  submitSceneAnalysisTask,
  updateSceneAnalysisConfigProfile,
} from '#/api/scene-analysis';
import { useReportPollingStore } from '#/store';

interface ParameterField {
  defaultValue: number;
  description: string;
  key: string;
  label: string;
  max: number;
  min: number;
  path: string[];
  recommended: string;
  step: number;
  unit?: null | string;
}

interface ParameterGroup {
  fields: ParameterField[];
  label: string;
  name: string;
}

const route = useRoute();
const pollingStore = useReportPollingStore();

const loadingTargets = ref(false);
const loadingHistory = ref(false);
const loadingDetail = ref(false);
const regeneratingTaskNo = ref('');
const targets = ref<SceneAnalysisReportTarget[]>([]);
const targetPageNum = ref(1);
const targetPageSize = ref(20);
const targetTotal = ref(0);
const histories = ref<SceneAnalysisReportHistory[]>([]);
const selectedTarget = ref<SceneAnalysisReportTarget>();
const selectedReport = ref<SceneAnalysisReportDetail>();
const historyDrawerVisible = ref(false);
const detailDrawerVisible = ref(false);
const detailFullscreen = ref(false);
const targetNameFilter = ref('');
const targetCodeFilter = ref('');
const targetTypeFilter = ref('');
const createDrawerVisible = ref(false);
const loadingProfiles = ref(false);
const loadingParameterSchema = ref(false);
const submittingTask = ref(false);
const savingProfile = ref(false);
const selectedProfileId = ref<null | number>(null);
const configProfiles = ref<SceneAnalysisConfigProfile[]>([]);
const selectedConfigGroup = ref('');
const loadingTargetOptions = ref(false);
const targetOptions = ref<SceneAnalysisTargetOption[]>([]);
const reportTypeOptions = ref<SceneAnalysisReportType[]>([
  { code: 'quick_analysis', label: '快速分析' },
  { code: 'risk_check', label: '风险检查' },
  { code: 'valuation_report', label: '估值报告' },
]);
const parameterGroups = ref<ParameterGroup[]>([]);
const parameterValues = ref<Record<string, number>>(defaultParameterValues());
const customProfileName = ref('');
const customProfileGroup = ref('自定义');
const DEFAULT_DAILY_KLINE_LIMIT = 90;
const DEFAULT_WEEKLY_KLINE_LIMIT = 52;
const DEFAULT_MONTHLY_KLINE_LIMIT = 60;
const MIN_DAILY_KLINE_LIMIT = 60;
const newReportForm = ref({
  configProfile: 'system_recommended',
  dailyKlineLimit: DEFAULT_DAILY_KLINE_LIMIT,
  monthlyKlineLimit: DEFAULT_MONTHLY_KLINE_LIMIT,
  reportType: 'quick_analysis',
  targetCode: '',
  targetName: '',
  targetType: 'STOCK',
  totalChunks: 10,
  weeklyKlineLimit: DEFAULT_WEEKLY_KLINE_LIMIT,
});

const pollingTaskNos = computed(() => Object.keys(pollingStore.tasks));
const selectedProfile = computed(() =>
  configProfiles.value.find((profile) => profile.id === selectedProfileId.value),
);
const configGroupOptions = computed(() =>
  [...new Set(configProfiles.value.map((profile) => profile.configGroup))],
);
const filteredConfigProfiles = computed(() =>
  selectedConfigGroup.value
    ? configProfiles.value.filter((profile) => profile.configGroup === selectedConfigGroup.value)
    : configProfiles.value,
);
const renderedReportHtml = computed(() =>
  renderMarkdown(selectedReport.value?.reportText || '暂无报告正文'),
);
const selectedReportPrintTitle = computed(() =>
  selectedReport.value ? `${displayTarget(selectedReport.value)} 分析报告` : '标的分析报告',
);

onMounted(async () => {
  await Promise.all([loadTargets(), loadParameterSchema(), loadReportTypes()]);
  await loadProfiles();
  const reportId = Number(route.query.reportId);
  if (Number.isFinite(reportId) && reportId > 0) {
    await openDetail(reportId);
  }
});

watch(
  pollingTaskNos,
  () => {
    void loadTargets();
    if (selectedTarget.value) {
      void loadHistory(selectedTarget.value);
    }
  },
);

watch(
  () => newReportForm.value.targetType,
  () => {
    newReportForm.value.targetCode = '';
    newReportForm.value.targetName = '';
    targetOptions.value = [];
    void searchReportTargetOptions('');
  },
);

async function loadTargets() {
  loadingTargets.value = true;
  try {
    const page = await listSceneReportTargets({
      pageNum: targetPageNum.value,
      pageSize: targetPageSize.value,
      targetCode: targetCodeFilter.value.trim(),
      targetName: targetNameFilter.value.trim(),
      targetType: targetTypeFilter.value,
    });
    targets.value = page.records;
    targetTotal.value = page.total;
    targetPageNum.value = page.pageNum;
    targetPageSize.value = page.pageSize;
  } finally {
    loadingTargets.value = false;
  }
}

async function loadProfiles() {
  loadingProfiles.value = true;
  try {
    configProfiles.value = await listSceneAnalysisConfigProfiles();
    selectProfile(selectedProfileId.value);
  } finally {
    loadingProfiles.value = false;
  }
}

async function loadReportTypes() {
  const types = await getSceneAnalysisReportTypes();
  if (types.length > 0) {
    reportTypeOptions.value = types;
  }
}

async function loadParameterSchema() {
  loadingParameterSchema.value = true;
  try {
    parameterGroups.value = await getSceneAnalysisConfigParameterSchema();
    parameterValues.value = defaultParameterValues();
  } finally {
    loadingParameterSchema.value = false;
  }
}

function searchTargets() {
  targetPageNum.value = 1;
  void loadTargets();
}

function changeTargetPageNum(value: number) {
  targetPageNum.value = value;
  void loadTargets();
}

function changeTargetPageSize(value: number) {
  targetPageNum.value = 1;
  targetPageSize.value = value;
  void loadTargets();
}

function openCreateReport() {
  createDrawerVisible.value = true;
  if (configProfiles.value.length === 0) {
    void loadProfiles();
    return;
  }
  selectProfile(selectedProfileId.value);
  applyProfile(selectedProfile.value);
  void searchReportTargetOptions('');
}

function changeProfile(profileId: number) {
  selectProfile(profileId);
}

function changeConfigGroup(configGroup: string) {
  selectedConfigGroup.value = configGroup;
  const profile = filteredConfigProfiles.value[0];
  selectedProfileId.value = profile?.id ?? null;
  applyProfile(profile);
}

function selectProfile(profileId?: null | number) {
  const profile = configProfiles.value.find((item) => item.id === profileId)
    ?? configProfiles.value.find((item) => item.configProfile === 'system_recommended')
    ?? configProfiles.value[0];
  selectedProfileId.value = profile?.id ?? null;
  selectedConfigGroup.value = profile?.configGroup ?? '';
  applyProfile(profile);
}

function applyProfile(profile?: SceneAnalysisConfigProfile) {
  if (!profile) {
    return;
  }
  const config = profile.configJson || {};
  newReportForm.value = {
    configProfile: textValue(config.configProfile, profile.configProfile),
    dailyKlineLimit: numberValue(config.dailyKlineLimit, DEFAULT_DAILY_KLINE_LIMIT),
    monthlyKlineLimit: numberValue(config.monthlyKlineLimit, DEFAULT_MONTHLY_KLINE_LIMIT),
    reportType: textValue(config.reportType, profile.reportType || 'quick_analysis'),
    targetCode: newReportForm.value.targetCode,
    targetName: newReportForm.value.targetName,
    targetType: textValue(config.targetType, profile.targetType || 'STOCK'),
    totalChunks: numberValue(config.totalChunks, 10),
    weeklyKlineLimit: numberValue(config.weeklyKlineLimit, DEFAULT_WEEKLY_KLINE_LIMIT),
  };
  parameterValues.value = parameterValuesFromOverrides(config.userOverrides);
  void searchReportTargetOptions('');
}

async function searchReportTargetOptions(keyword: string) {
  loadingTargetOptions.value = true;
  try {
    targetOptions.value = await searchSceneAnalysisTargets({
      keyword: keyword?.trim() || undefined,
      limit: 20,
      targetType: newReportForm.value.targetType,
    });
  } finally {
    loadingTargetOptions.value = false;
  }
}

function changeReportTarget(targetCode: string) {
  const option = targetOptions.value.find((item) => item.targetCode === targetCode);
  newReportForm.value.targetName = option?.targetName || '';
}

async function createReportTask() {
  const targetCode = newReportForm.value.targetCode.trim();
  if (!targetCode) {
    ElMessage.warning('请选择标的');
    return;
  }
  const userOverrides = buildUserOverrides();
  submittingTask.value = true;
  try {
    const result = await submitSceneAnalysisTask({
      configProfile: newReportForm.value.configProfile,
      dailyKlineLimit: normalizedDailyKlineLimit(),
      monthlyKlineLimit: normalizedMonthlyKlineLimit(),
      reportType: newReportForm.value.reportType,
      targetCode,
      targetName: newReportForm.value.targetName.trim() || undefined,
      targetType: newReportForm.value.targetType,
      totalChunks: newReportForm.value.totalChunks,
      weeklyKlineLimit: normalizedWeeklyKlineLimit(),
      userOverrides,
    });
    pollingStore.start({
      targetCode,
      targetName: newReportForm.value.targetName.trim() || targetCode,
      taskNo: result.taskNo,
    });
    createDrawerVisible.value = false;
    ElMessage.success('已提交报告生成任务');
    await new Promise((resolve) => setTimeout(resolve, 500));
    await loadTargets();
  } finally {
    submittingTask.value = false;
  }
}

async function saveCurrentProfile() {
  const name = customProfileName.value.trim();
  if (!name) {
    ElMessage.warning('请输入配置名称');
    return;
  }
  const configJson = currentConfigJson();
  if (!configJson) {
    return;
  }
  savingProfile.value = true;
  try {
    const created = await createSceneAnalysisConfigProfile({
      configGroup: customProfileGroup.value.trim() || '自定义',
      configJson,
      name,
      reportType: newReportForm.value.reportType,
      targetType: newReportForm.value.targetType,
    });
    await loadProfiles();
    selectedConfigGroup.value = created.configGroup;
    selectedProfileId.value = created.id;
    applyProfile(created);
    ElMessage.success('配置已保存');
  } finally {
    savingProfile.value = false;
  }
}

async function updateCurrentProfile() {
  const profile = selectedProfile.value;
  if (!profile || profile.systemDefault) {
    return;
  }
  const configJson = currentConfigJson();
  if (!configJson) {
    return;
  }
  savingProfile.value = true;
  try {
    const updated = await updateSceneAnalysisConfigProfile(profile.id, {
      configGroup: profile.configGroup,
      configJson,
      name: profile.name,
      reportType: newReportForm.value.reportType,
      targetType: newReportForm.value.targetType,
    });
    await loadProfiles();
    selectedConfigGroup.value = updated.configGroup;
    selectedProfileId.value = updated.id;
    applyProfile(updated);
    ElMessage.success('配置已更新');
  } finally {
    savingProfile.value = false;
  }
}

async function deleteCurrentProfile() {
  const profile = selectedProfile.value;
  if (!profile || profile.systemDefault) {
    return;
  }
  savingProfile.value = true;
  try {
    await deleteSceneAnalysisConfigProfile(profile.id);
    selectedProfileId.value = null;
    await loadProfiles();
    ElMessage.success('配置已删除');
  } finally {
    savingProfile.value = false;
  }
}

async function loadHistory(target: SceneAnalysisReportTarget) {
  selectedTarget.value = target;
  loadingHistory.value = true;
  try {
    histories.value = await listSceneReportHistory(
      target.targetType,
      target.targetCode,
    );
  } finally {
    loadingHistory.value = false;
  }
}

async function openHistory(target: SceneAnalysisReportTarget) {
  historyDrawerVisible.value = true;
  await loadHistory(target);
}

async function openDetail(reportId?: null | number) {
  if (!reportId) {
    return;
  }
  detailDrawerVisible.value = true;
  loadingDetail.value = true;
  try {
    selectedReport.value = await getSceneReportDetail(reportId);
  } finally {
    loadingDetail.value = false;
  }
}

async function regenerate(row: SceneAnalysisReportHistory | SceneAnalysisReportTarget) {
  regeneratingTaskNo.value = taskNoOf(row);
  try {
    await regenerateSceneReport(regeneratingTaskNo.value);
    pollingStore.start({
      targetCode: row.targetCode,
      targetName: row.targetName,
      taskNo: regeneratingTaskNo.value,
    });
    ElMessage.success('已提交报告生成任务');
  } finally {
    regeneratingTaskNo.value = '';
  }
}

function taskNoOf(row: SceneAnalysisReportHistory | SceneAnalysisReportTarget) {
  return 'taskNo' in row ? row.taskNo : row.latestTaskNo;
}

function displayTarget(row: Pick<SceneAnalysisReportTarget, 'targetCode' | 'targetName'>) {
  return row.targetName ? `${row.targetName} ${row.targetCode}` : row.targetCode;
}

function statusLabel(status: SceneReportStatus | string) {
  const labels: Record<string, string> = {
    failed: '失败',
    generating_report: '生成中',
    pending: '等待中',
    processing_current_scenes: '场景计算中',
    retrieving_knowledge: '知识召回中',
    success: '完成',
  };
  return labels[status] ?? status;
}

function statusType(status: SceneReportStatus | string) {
  const types: Record<string, 'danger' | 'info' | 'success' | 'warning'> = {
    failed: 'danger',
    generating_report: 'warning',
    pending: 'info',
    processing_current_scenes: 'warning',
    retrieving_knowledge: 'warning',
    success: 'success',
  };
  return types[status] ?? 'info';
}

function generationLabel(type?: null | string) {
  if (type === 'initial') {
    return '首次生成';
  }
  if (type === 'regenerate') {
    return '重新生成';
  }
  return type || '-';
}

function formatDateTime(value?: null | string) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 19);
}

function currentConfigJson() {
  return {
    dailyKlineLimit: normalizedDailyKlineLimit(),
    monthlyKlineLimit: normalizedMonthlyKlineLimit(),
    reportType: newReportForm.value.reportType,
    targetType: newReportForm.value.targetType,
    totalChunks: newReportForm.value.totalChunks,
    weeklyKlineLimit: normalizedWeeklyKlineLimit(),
    userOverrides: buildUserOverrides(),
  };
}

function normalizedDailyKlineLimit() {
  return Math.max(
    numberValue(newReportForm.value.dailyKlineLimit, DEFAULT_DAILY_KLINE_LIMIT),
    MIN_DAILY_KLINE_LIMIT,
  );
}

function normalizedWeeklyKlineLimit() {
  return numberValue(newReportForm.value.weeklyKlineLimit, DEFAULT_WEEKLY_KLINE_LIMIT);
}

function normalizedMonthlyKlineLimit() {
  return numberValue(newReportForm.value.monthlyKlineLimit, DEFAULT_MONTHLY_KLINE_LIMIT);
}

function buildUserOverrides() {
  const overrides: Record<string, unknown> = {
    asset_type: assetTypeByTargetType(newReportForm.value.targetType),
  };
  parameterGroups.value.flatMap((group) => group.fields).forEach((field) => {
    setNestedValue(overrides, field.path, parameterValues.value[field.key] ?? field.defaultValue);
  });
  setNestedValue(overrides, ['volume_config', 'volume_distribution_source'], 'asset_history_then_industry');
  setNestedValue(overrides, ['volume_config', 'turnover_distribution_source'], 'asset_history_then_industry');
  return overrides;
}

function parameterValuesFromOverrides(value: unknown) {
  const overrides = objectValue(value);
  const result = defaultParameterValues();
  parameterGroups.value.flatMap((group) => group.fields).forEach((field) => {
    const nestedValue = getNestedValue(overrides, field.path);
    if (typeof nestedValue === 'number' && Number.isFinite(nestedValue)) {
      result[field.key] = nestedValue;
    }
  });
  return result;
}

function defaultParameterValues() {
  const result: Record<string, number> = {};
  for (const group of parameterGroups.value) {
    for (const field of group.fields) {
      result[field.key] = field.defaultValue;
    }
  }
  return result;
}

function objectValue(value: unknown) {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function getNestedValue(source: Record<string, unknown>, path: string[]) {
  let current: unknown = source;
  for (const key of path) {
    if (!current || typeof current !== 'object' || Array.isArray(current)) {
      return undefined;
    }
    current = (current as Record<string, unknown>)[key];
  }
  return current;
}

function setNestedValue(target: Record<string, unknown>, path: string[], value: unknown) {
  let cursor = target;
  path.forEach((key, index) => {
    if (index === path.length - 1) {
      cursor[key] = value;
      return;
    }
    if (!cursor[key] || typeof cursor[key] !== 'object' || Array.isArray(cursor[key])) {
      cursor[key] = {};
    }
    cursor = cursor[key] as Record<string, unknown>;
  });
}

function assetTypeByTargetType(targetType: string) {
  if (targetType === 'INDEX') {
    return 'index';
  }
  if (targetType === 'CONVERTIBLE_BOND') {
    return 'convertible_bond';
  }
  return 'stock';
}

function textValue(value: unknown, fallback: string) {
  return typeof value === 'string' && value.trim() ? value.trim() : fallback;
}

function numberValue(value: unknown, fallback: number) {
  return typeof value === 'number' && value > 0 ? value : fallback;
}

function parameterTooltip(field: ParameterField) {
  return `${field.description} 推荐范围：${field.recommended}`;
}

function parameterValueLabel(field: ParameterField) {
  const value = parameterValues.value[field.key] ?? field.defaultValue;
  return `${value}${field.unit || ''}`;
}

function renderMarkdown(markdown: string) {
  const blocks: string[] = [];
  let listItems: string[] = [];

  const flushList = () => {
    if (listItems.length === 0) {
      return;
    }
    blocks.push(`<ul>${listItems.join('')}</ul>`);
    listItems = [];
  };

  markdown.split(/\r?\n/).forEach((line) => {
    const text = line.trim();
    if (!text) {
      flushList();
      return;
    }
    if (text.startsWith('# ')) {
      flushList();
      blocks.push(`<h1>${renderInlineMarkdown(text.slice(2))}</h1>`);
      return;
    }
    if (text.startsWith('## ')) {
      flushList();
      blocks.push(`<h2>${renderInlineMarkdown(text.slice(3))}</h2>`);
      return;
    }
    if (text.startsWith('### ')) {
      flushList();
      blocks.push(`<h3>${renderInlineMarkdown(text.slice(4))}</h3>`);
      return;
    }
    if (text.startsWith('- ')) {
      listItems.push(`<li>${renderInlineMarkdown(text.slice(2))}</li>`);
      return;
    }
    flushList();
    blocks.push(`<p>${renderInlineMarkdown(text)}</p>`);
  });
  flushList();
  return blocks.join('');
}

function renderInlineMarkdown(text: string) {
  return escapeHtml(text).replaceAll(
    /（引用：[^）]+）/g,
    (reference) => `<strong class="report-reference">${reference}</strong>`,
  );
}

function exportSelectedReportPdf() {
  if (!selectedReport.value) {
    ElMessage.warning('请先打开报告详情');
    return;
  }

  const printWindow = window.open('', '_blank', 'width=960,height=720');
  if (!printWindow) {
    ElMessage.warning('浏览器阻止了弹窗，请允许弹窗后重试');
    return;
  }

  const report = selectedReport.value;
  const title = selectedReportPrintTitle.value;
  printWindow.document.write(`
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <title>${escapeHtml(title)}</title>
  <style>
    * { box-sizing: border-box; }
    body {
      color: #1f2937;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
      line-height: 1.75;
      margin: 0;
      padding: 32px;
    }
    .report-export { margin: 0 auto; max-width: 860px; }
    .report-title {
      border-bottom: 2px solid #111827;
      font-size: 24px;
      line-height: 1.35;
      margin: 0 0 18px;
      padding-bottom: 12px;
    }
    .report-meta {
      border: 1px solid #d1d5db;
      border-collapse: collapse;
      margin-bottom: 24px;
      width: 100%;
    }
    .report-meta th,
    .report-meta td {
      border: 1px solid #d1d5db;
      font-size: 13px;
      padding: 8px 10px;
      text-align: left;
      vertical-align: top;
    }
    .report-meta th {
      background: #f3f4f6;
      color: #374151;
      font-weight: 600;
      width: 96px;
    }
    .markdown-report h1 { font-size: 22px; line-height: 1.4; margin: 0 0 18px; }
    .markdown-report h2 {
      border-bottom: 1px solid #e5e7eb;
      font-size: 18px;
      margin: 22px 0 10px;
      padding-bottom: 6px;
    }
    .markdown-report h3 { font-size: 16px; margin: 18px 0 8px; }
    .markdown-report p { margin: 0 0 10px; }
    .markdown-report ul { margin: 0 0 14px; padding-left: 22px; }
    .markdown-report li { margin: 4px 0; }
    .report-reference { font-weight: 700; }
    @page { margin: 16mm; size: A4; }
    @media print {
      body { padding: 0; }
      .report-export { max-width: none; }
    }
  </style>
</head>
<body>
  <main class="report-export">
    <h1 class="report-title">${escapeHtml(title)}</h1>
    <table class="report-meta">
      <tbody>
        <tr>
          <th>标的</th>
          <td>${escapeHtml(displayTarget(report))}</td>
          <th>版本</th>
          <td>${escapeHtml(`${generationLabel(report.generationType)} #${report.versionNo}`)}</td>
        </tr>
        <tr>
          <th>状态</th>
          <td>${escapeHtml(statusLabel(report.status))}</td>
          <th>模型</th>
          <td>${escapeHtml(report.model || '-')}</td>
        </tr>
        <tr>
          <th>生成时间</th>
          <td colspan="3">${escapeHtml(formatDateTime(report.generatedAt || report.createdAt))}</td>
        </tr>
      </tbody>
    </table>
    <article class="markdown-report">${renderedReportHtml.value}</article>
  </main>
</body>
</html>`);
  printWindow.document.close();
  printWindow.focus();
  window.setTimeout(() => {
    printWindow.print();
  }, 250);
}

function escapeHtml(text: string) {
  return text
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}
</script>

<template>
  <Page title="标的分析报告">
    <div class="scene-report-page">
      <div class="toolbar">
        <div class="toolbar-copy">
          <div class="toolbar-title">报告标的</div>
          <div class="toolbar-meta">
            共 {{ targets.length }} 个标的，{{ pollingTaskNos.length }} 个报告生成中
          </div>
        </div>
        <div class="toolbar-actions">
          <ElButton type="primary" @click="openCreateReport">
            新建报告
          </ElButton>
          <ElButton :loading="loadingTargets" @click="loadTargets">
            刷新
          </ElButton>
        </div>
      </div>

      <div class="filter-row">
        <ElInput
          v-model="targetNameFilter"
          clearable
          placeholder="标的名称"
          @clear="searchTargets"
          @keyup.enter="searchTargets"
        />
        <ElInput
          v-model="targetCodeFilter"
          clearable
          placeholder="标的代码"
          @clear="searchTargets"
          @keyup.enter="searchTargets"
        />
        <ElSelect
          v-model="targetTypeFilter"
          clearable
          placeholder="标的类型"
          @clear="searchTargets"
          @change="searchTargets"
        >
          <ElOption label="股票" value="STOCK" />
          <ElOption label="指数" value="INDEX" />
          <ElOption label="可转债" value="CONVERTIBLE_BOND" />
        </ElSelect>
        <ElButton :loading="loadingTargets" type="primary" @click="searchTargets">
          查询
        </ElButton>
      </div>

      <ElTable
        v-loading="loadingTargets"
        :data="targets"
        border
        class="report-table"
        row-key="latestReportId"
      >
        <ElTableColumn label="标的" min-width="180">
          <template #default="{ row }">
            <div class="target-cell">
              <strong>{{ displayTarget(row) }}</strong>
              <span>{{ row.targetType }}</span>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="最新状态" width="110">
          <template #default="{ row }">
            <ElTag :type="statusType(row.latestStatus)">
              {{ statusLabel(row.latestStatus) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="历史报告" prop="reportCount" width="100" />
        <ElTableColumn label="最新版本" width="130">
          <template #default="{ row }">
            {{ generationLabel(row.latestGenerationType) }} #{{ row.latestVersionNo }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="最新生成时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.latestGeneratedAt || row.latestCreatedAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="摘要" min-width="260">
          <template #default="{ row }">
            <span class="preview-text">{{ row.latestReportPreview || '-' }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn fixed="right" label="操作" width="250">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openDetail(row.latestReportId)">
              查看
            </ElButton>
            <ElButton link type="primary" @click="openHistory(row)">
              历史
            </ElButton>
            <ElPopconfirm
              title="基于这次报告的上下文重新生成？"
              @confirm="regenerate(row)"
            >
              <template #reference>
                <ElButton
                  :loading="regeneratingTaskNo === row.latestTaskNo"
                  link
                  type="warning"
                >
                  重新生成
                </ElButton>
              </template>
            </ElPopconfirm>
          </template>
        </ElTableColumn>
      </ElTable>

      <div class="pagination-row">
        <ElPagination
          v-model:current-page="targetPageNum"
          v-model:page-size="targetPageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="targetTotal"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="changeTargetPageNum"
          @size-change="changeTargetPageSize"
        />
      </div>
    </div>

    <ElDrawer
      v-model="createDrawerVisible"
      destroy-on-close
      size="620px"
      title="新建报告任务"
    >
      <div class="create-report-panel">
        <ElForm label-position="top">
          <div class="form-grid">
            <ElFormItem label="配置分组">
              <ElSelect
                v-model="selectedConfigGroup"
                :loading="loadingProfiles"
                placeholder="选择配置分组"
                @change="changeConfigGroup"
              >
                <ElOption
                  v-for="group in configGroupOptions"
                  :key="group"
                  :label="group"
                  :value="group"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="配置模板">
              <ElSelect
                :loading="loadingProfiles"
                :model-value="selectedProfileId"
                filterable
                placeholder="选择配置模板"
                @change="changeProfile"
              >
                <ElOption
                  v-for="profile in filteredConfigProfiles"
                  :key="profile.id"
                  :label="`${profile.name}${profile.systemDefault ? '（系统）' : ''}`"
                  :value="profile.id"
                />
              </ElSelect>
            </ElFormItem>
          </div>

          <div class="form-grid">
            <ElFormItem label="标的类型">
              <ElSelect v-model="newReportForm.targetType">
                <ElOption label="股票" value="STOCK" />
                <ElOption label="指数" value="INDEX" />
                <ElOption label="可转债" value="CONVERTIBLE_BOND" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="标的">
              <ElSelect
                v-model="newReportForm.targetCode"
                clearable
                filterable
                :loading="loadingTargetOptions"
                placeholder="输入名称、代码或 secid 搜索"
                remote
                :remote-method="searchReportTargetOptions"
                @change="changeReportTarget"
              >
                <ElOption
                  v-for="option in targetOptions"
                  :key="`${option.targetType}-${option.targetCode}`"
                  :label="`${option.targetName || option.targetCode} ${option.targetCode}`"
                  :value="option.targetCode"
                >
                  <div class="target-option">
                    <strong>{{ option.targetName || option.targetCode }}</strong>
                    <span>{{ option.targetCode }} · {{ option.exchangeCode || '-' }}</span>
                  </div>
                </ElOption>
              </ElSelect>
            </ElFormItem>
          </div>

          <div class="form-grid">
            <ElFormItem label="报告类型">
              <ElSelect v-model="newReportForm.reportType">
                <ElOption
                  v-for="reportType in reportTypeOptions"
                  :key="reportType.code"
                  :label="reportType.label"
                  :value="reportType.code"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="召回片段数">
              <ElInputNumber
                v-model="newReportForm.totalChunks"
                :min="1"
                :step="1"
                controls-position="right"
              />
            </ElFormItem>
          </div>

          <div class="form-grid three-columns">
            <ElFormItem label="日线 K 线数">
              <ElInputNumber
                v-model="newReportForm.dailyKlineLimit"
                :max="250"
                :min="MIN_DAILY_KLINE_LIMIT"
                :step="1"
                controls-position="right"
              />
            </ElFormItem>
            <ElFormItem label="周线 K 线数">
              <ElInputNumber
                v-model="newReportForm.weeklyKlineLimit"
                :max="250"
                :min="1"
                :step="1"
                controls-position="right"
              />
            </ElFormItem>
            <ElFormItem label="月线 K 线数">
              <ElInputNumber
                v-model="newReportForm.monthlyKlineLimit"
                :max="250"
                :min="1"
                :step="1"
                controls-position="right"
              />
            </ElFormItem>
          </div>

          <section v-loading="loadingParameterSchema" class="parameter-panel">
            <div class="parameter-panel-title">场景参数</div>
            <div v-if="parameterGroups.length > 0" class="parameter-groups">
              <section
                v-for="group in parameterGroups"
                :key="group.name"
                class="parameter-group"
              >
                <div class="parameter-group-title">{{ group.label }}</div>
                <div class="parameter-list">
                  <div
                    v-for="field in group.fields"
                    :key="field.key"
                    class="parameter-item"
                  >
                    <div class="parameter-header">
                      <span>{{ field.label }}</span>
                      <ElTooltip
                        effect="dark"
                        placement="top"
                        :content="parameterTooltip(field)"
                      >
                        <IconifyIcon class="parameter-info" icon="lucide:info" />
                      </ElTooltip>
                    </div>
                    <div class="parameter-control">
                      <ElSlider
                        v-model="parameterValues[field.key]"
                        :max="field.max"
                        :min="field.min"
                        :step="field.step"
                      />
                      <span class="parameter-value">
                        {{ parameterValueLabel(field) }}
                      </span>
                    </div>
                    <div class="parameter-recommended">
                      推荐 {{ field.recommended }}
                    </div>
                  </div>
                </div>
              </section>
            </div>
            <ElEmpty v-else description="暂无参数配置" />
          </section>
        </ElForm>

        <section class="profile-save-panel">
          <div class="profile-save-title">保存为配置</div>
          <div class="profile-save-row">
            <ElInput
              v-model="customProfileName"
              clearable
              placeholder="配置名称"
            />
            <ElInput
              v-model="customProfileGroup"
              clearable
              placeholder="配置分组"
            />
            <ElButton :loading="savingProfile" @click="saveCurrentProfile">
              保存
            </ElButton>
          </div>
          <div
            v-if="selectedProfile && !selectedProfile.systemDefault"
            class="profile-edit-actions"
          >
            <ElButton
              :loading="savingProfile"
              type="primary"
              @click="updateCurrentProfile"
            >
              更新当前配置
            </ElButton>
            <ElPopconfirm title="删除当前自定义配置？" @confirm="deleteCurrentProfile">
              <template #reference>
                <ElButton :loading="savingProfile" type="danger">
                  删除当前配置
                </ElButton>
              </template>
            </ElPopconfirm>
          </div>
        </section>

        <div class="drawer-footer">
          <ElButton @click="createDrawerVisible = false">
            取消
          </ElButton>
          <ElButton
            :loading="submittingTask"
            type="primary"
            @click="createReportTask"
          >
            创建任务
          </ElButton>
        </div>
      </div>
    </ElDrawer>

    <ElDrawer
      v-model="historyDrawerVisible"
      :title="selectedTarget ? `${displayTarget(selectedTarget)} 历史报告` : '历史报告'"
      size="720px"
    >
      <ElTable
        v-loading="loadingHistory"
        :data="histories"
        border
        row-key="reportId"
      >
        <ElTableColumn label="版本" width="120">
          <template #default="{ row }">
            {{ generationLabel(row.generationType) }} #{{ row.versionNo }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90">
          <template #default="{ row }">
            <ElTag :type="statusType(row.status)">
              {{ statusLabel(row.status) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="任务编号" prop="taskNo" min-width="180" />
        <ElTableColumn label="时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.generatedAt || row.createdAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn fixed="right" label="操作" width="160">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openDetail(row.reportId)">
              查看
            </ElButton>
            <ElPopconfirm
              title="基于这条报告关联的上下文重新生成？"
              @confirm="regenerate(row)"
            >
              <template #reference>
                <ElButton
                  :loading="regeneratingTaskNo === row.taskNo"
                  link
                  type="warning"
                >
                  重生成
                </ElButton>
              </template>
            </ElPopconfirm>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElDrawer>

    <ElDrawer
      v-model="detailDrawerVisible"
      destroy-on-close
      :size="detailFullscreen ? '100%' : '68%'"
    >
      <template #header>
        <div class="drawer-header">
          <span>报告详情</span>
          <div class="drawer-header-actions">
            <ElButton
              :disabled="!selectedReport"
              link
              type="primary"
              @click="exportSelectedReportPdf"
            >
              <IconifyIcon icon="lucide:file-down" />
              导出 PDF
            </ElButton>
            <ElButton link type="primary" @click="detailFullscreen = !detailFullscreen">
              {{ detailFullscreen ? '退出全屏' : '全屏' }}
            </ElButton>
          </div>
        </div>
      </template>
      <div v-loading="loadingDetail" class="detail-panel">
        <template v-if="selectedReport">
          <ElDescriptions :column="3" border>
            <ElDescriptionsItem label="标的">
              {{ displayTarget(selectedReport) }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="版本">
              {{ generationLabel(selectedReport.generationType) }}
              #{{ selectedReport.versionNo }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="状态">
              <ElTag :type="statusType(selectedReport.status)">
                {{ statusLabel(selectedReport.status) }}
              </ElTag>
            </ElDescriptionsItem>
            <ElDescriptionsItem label="模型">
              {{ selectedReport.model || '-' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="生成时间">
              {{ formatDateTime(selectedReport.generatedAt || selectedReport.createdAt) }}
            </ElDescriptionsItem>
          </ElDescriptions>

          <div v-if="selectedReport.errorMessage" class="error-message">
            {{ selectedReport.errorMessage }}
          </div>

          <section class="detail-section">
            <h3>报告正文</h3>
            <article class="markdown-report" v-html="renderedReportHtml"></article>
          </section>
        </template>
        <ElEmpty v-else description="暂无报告详情" />
      </div>
    </ElDrawer>
  </Page>
</template>

<style scoped>
.scene-report-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 46px;
}

.toolbar-copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.toolbar-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.filter-row {
  display: grid;
  gap: 8px;
  grid-template-columns: minmax(160px, 1fr) minmax(140px, 1fr) minmax(140px, 180px) auto;
  max-width: 760px;
}

@media (max-width: 768px) {
  .filter-row {
    grid-template-columns: 1fr;
    max-width: none;
  }
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
}

.create-report-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: 72px;
}

.form-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}

.form-grid.three-columns {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.target-option {
  align-items: center;
  display: flex;
  justify-content: space-between;
  width: 100%;
}

.target-option span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.parameter-panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 12px 14px 6px;
}

.parameter-panel-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}

.parameter-groups {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.parameter-group {
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 12px;
}

.parameter-group:first-child {
  border-top: 0;
  padding-top: 0;
}

.parameter-group-title {
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 700;
  line-height: 18px;
  margin-bottom: 10px;
}

.parameter-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.parameter-item {
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding-bottom: 12px;
}

.parameter-item:last-child {
  border-bottom: 0;
}

.parameter-header {
  align-items: center;
  display: flex;
  font-size: 13px;
  font-weight: 600;
  gap: 6px;
  line-height: 18px;
}

.parameter-info {
  color: var(--el-text-color-secondary);
  cursor: help;
  font-size: 15px;
}

.parameter-control {
  align-items: center;
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(0, 1fr) 58px;
}

.parameter-value {
  color: var(--el-text-color-primary);
  font-size: 12px;
  text-align: right;
}

.parameter-recommended {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 16px;
  margin-top: -4px;
}

.profile-save-panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
}

.profile-save-title {
  font-size: 14px;
  font-weight: 600;
}

.profile-save-row,
.profile-edit-actions {
  display: flex;
  gap: 8px;
}

.drawer-footer {
  align-items: center;
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-light);
  bottom: 0;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  left: 0;
  padding: 12px 20px;
  position: absolute;
  right: 0;
}

.toolbar-title {
  font-size: 16px;
  font-weight: 600;
  line-height: 20px;
}

.toolbar-meta,
.target-cell span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 16px;
}

.report-table {
  width: 100%;
}

.target-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.preview-text {
  color: var(--el-text-color-regular);
  line-height: 1.5;
}

.detail-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 240px;
}

.detail-section h3 {
  font-size: 15px;
  margin: 0 0 8px;
}

.drawer-header {
  align-items: center;
  display: flex;
  justify-content: space-between;
  width: 100%;
}

.drawer-header-actions {
  align-items: center;
  display: flex;
  gap: 8px;
}

.markdown-report {
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  color: var(--el-text-color-primary);
  line-height: 1.75;
  max-height: calc(100vh - 240px);
  overflow: auto;
  padding: 18px 22px;
}

.error-message {
  background: var(--el-color-danger-light-9);
  border: 1px solid var(--el-color-danger-light-7);
  border-radius: 6px;
  color: var(--el-color-danger);
  padding: 10px 12px;
}

:deep(.markdown-report h1) {
  font-size: 22px;
  line-height: 1.4;
  margin: 0 0 18px;
}

:deep(.markdown-report h2) {
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 17px;
  margin: 22px 0 10px;
  padding-bottom: 6px;
}

:deep(.markdown-report h3) {
  font-size: 15px;
  margin: 18px 0 8px;
}

:deep(.markdown-report p) {
  margin: 0 0 10px;
}

:deep(.markdown-report ul) {
  margin: 0 0 14px;
  padding-left: 22px;
}

:deep(.markdown-report li) {
  margin: 4px 0;
}

:deep(.markdown-report .report-reference) {
  color: var(--el-text-color-primary);
  font-weight: 700;
}

@media (max-width: 768px) {
  .form-grid,
  .form-grid.three-columns,
  .profile-save-row,
  .profile-edit-actions {
    grid-template-columns: 1fr;
  }

  .profile-save-row,
  .profile-edit-actions,
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
