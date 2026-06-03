<script lang="ts" setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElEmpty,
  ElInput,
  ElMessage,
  ElPagination,
  ElPopconfirm,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  getSceneReportDetail,
  listSceneReportHistory,
  listSceneReportTargets,
  regenerateSceneReport,
  type SceneAnalysisReportDetail,
  type SceneAnalysisReportHistory,
  type SceneAnalysisReportTarget,
  type SceneReportStatus,
} from '#/api/scene-analysis';
import { useReportPollingStore } from '#/store';

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
const targetKeyword = ref('');

const pollingTaskNos = computed(() => Object.keys(pollingStore.tasks));
const renderedReportHtml = computed(() =>
  renderMarkdown(selectedReport.value?.reportText || '暂无报告正文'),
);

onMounted(async () => {
  await loadTargets();
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

async function loadTargets() {
  loadingTargets.value = true;
  try {
    const page = await listSceneReportTargets({
      keyword: targetKeyword.value.trim(),
      pageNum: targetPageNum.value,
      pageSize: targetPageSize.value,
    });
    targets.value = page.records;
    targetTotal.value = page.total;
    targetPageNum.value = page.pageNum;
    targetPageSize.value = page.pageSize;
  } finally {
    loadingTargets.value = false;
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

async function regenerate(row: SceneAnalysisReportTarget | SceneAnalysisReportHistory) {
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

function taskNoOf(row: SceneAnalysisReportTarget | SceneAnalysisReportHistory) {
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
      blocks.push(`<h1>${escapeHtml(text.slice(2))}</h1>`);
      return;
    }
    if (text.startsWith('## ')) {
      flushList();
      blocks.push(`<h2>${escapeHtml(text.slice(3))}</h2>`);
      return;
    }
    if (text.startsWith('### ')) {
      flushList();
      blocks.push(`<h3>${escapeHtml(text.slice(4))}</h3>`);
      return;
    }
    if (text.startsWith('- ')) {
      listItems.push(`<li>${escapeHtml(text.slice(2))}</li>`);
      return;
    }
    flushList();
    blocks.push(`<p>${escapeHtml(text)}</p>`);
  });
  flushList();
  return blocks.join('');
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
        <ElButton :loading="loadingTargets" @click="loadTargets">
          刷新
        </ElButton>
      </div>

      <div class="filter-row">
        <ElInput
          v-model="targetKeyword"
          clearable
          placeholder="按标的名称、代码或类型筛选"
          @clear="searchTargets"
          @keyup.enter="searchTargets"
        />
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
          <ElButton link type="primary" @click="detailFullscreen = !detailFullscreen">
            {{ detailFullscreen ? '退出全屏' : '全屏' }}
          </ElButton>
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
            <ElDescriptionsItem label="任务编号">
              {{ selectedReport.taskNo }}
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

.filter-row {
  display: flex;
  gap: 8px;
  max-width: 460px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
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
</style>
