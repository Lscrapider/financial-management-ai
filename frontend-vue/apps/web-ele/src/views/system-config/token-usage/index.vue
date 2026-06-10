<script lang="ts" setup>
import type {
  AiTokenUsageLog,
  AiTokenUsageLogPageParams,
  AiTokenUsageOverview,
  AiTokenUsageQueryParams,
  AiTokenUsageTrend,
} from '#/api/ai-token-usage';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import {
  ElButton,
  ElCard,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElPagination,
  ElSelect,
  ElSkeleton,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  getAiTokenUsageOverview,
  listAiTokenUsageLogs,
  listAiTokenUsageTrends,
} from '#/api/ai-token-usage';
import PageHero from '#/components/page-hero/index.vue';

import TokenUsageTrend from './components/token-usage-trend.vue';

type FilterForm = {
  model: string;
  phase: string;
  source: string;
  timeRange: [string, string] | [];
  username: string;
};

interface MetricCard {
  detail: string;
  label: string;
  tone?: 'cost';
  value: string;
}

const PERIOD_DAYS = 30;
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];
const SOURCE_OPTIONS = [
  { label: 'Agent 对话', value: 'agent' },
  { label: '报告生成', value: 'report' },
];
const PHASE_OPTIONS = [
  { label: '规划', value: 'planning' },
  { label: '初始规划', value: 'initial_planning' },
  { label: '工具后续规划', value: 'tool_followup_planning' },
  { label: '直接回答', value: 'direct_answer' },
  { label: '基于工具结果回答', value: 'tool_result_answer' },
  { label: '最终整理回答', value: 'final_answer' },
  { label: '报告生成', value: 'report_generate' },
];

const loading = ref(false);
const tableLoading = ref(false);
const overview = ref<AiTokenUsageOverview>();
const trends = ref<AiTokenUsageTrend[]>([]);
const logs = ref<AiTokenUsageLog[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);
const selectedLog = ref<AiTokenUsageLog>();
const detailVisible = ref(false);

const filters = reactive<FilterForm>({
  model: '',
  phase: '',
  source: '',
  timeRange: [],
  username: '',
});
const appliedQueryParams = ref<AiTokenUsageQueryParams>({ days: PERIOD_DAYS });
const appliedPeriodLabel = ref(`近 ${PERIOD_DAYS} 天`);

const hasData = computed(
  () => Boolean(overview.value) || trends.value.length > 0 || logs.value.length > 0,
);

const metricCards = computed<MetricCard[]>(() => {
  const data = overview.value;
  return [
    {
      detail: `${appliedPeriodLabel.value}模型调用预估费用`,
      label: '总消耗',
      tone: 'cost',
      value: formatCost(data?.estimatedCost?.totalCost, data?.estimatedCost?.currency),
    },
    {
      detail: `最近调用 ${formatTime(data?.latestOccurredAt)}`,
      label: '请求次数',
      value: formatNumber(data?.requestCount),
    },
    {
      detail: `输入 ${formatNumber(data?.promptTokens)}，输出 ${formatNumber(
        data?.completionTokens,
      )}`,
      label: '总 Token',
      value: formatNumber(data?.totalTokens),
    },
    {
      detail: `缓存命中口径由模型响应提供`,
      label: '缓存 Token',
      value: formatNumber(data?.cachedTokens),
    },
    {
      detail: `模型推理过程消耗`,
      label: '推理 Token',
      value: formatNumber(data?.reasoningTokens),
    },
  ];
});

onMounted(() => {
  void loadPage(true);
});

async function loadPage(silent = false) {
  if (loading.value) return;
  loading.value = true;
  tableLoading.value = true;
  const queryParams = buildQueryParams();
  try {
    const [overviewResult, trendResult, logPage] = await Promise.all([
      getAiTokenUsageOverview(queryParams),
      listAiTokenUsageTrends(queryParams),
      listAiTokenUsageLogs(buildLogParams(queryParams)),
    ]);
    appliedQueryParams.value = queryParams;
    appliedPeriodLabel.value = periodLabel(queryParams);
    overview.value = overviewResult;
    trends.value = trendResult;
    applyLogPage(logPage);
  } catch {
    if (!silent) {
      ElMessage.error('Token 用量数据刷新失败');
    }
  } finally {
    loading.value = false;
    tableLoading.value = false;
  }
}

async function loadLogs() {
  if (tableLoading.value) return;
  tableLoading.value = true;
  try {
    const page = await listAiTokenUsageLogs(buildLogParams(appliedQueryParams.value));
    applyLogPage(page);
  } catch {
    ElMessage.error('Token 用量明细加载失败');
  } finally {
    tableLoading.value = false;
  }
}

function buildLogParams(queryParams: AiTokenUsageQueryParams): AiTokenUsageLogPageParams {
  return {
    ...queryParams,
    pageNum: pageNum.value,
    pageSize: pageSize.value,
  };
}

function buildQueryParams(): AiTokenUsageQueryParams {
  const [startTime, endTime] = filters.timeRange;
  return {
    days: PERIOD_DAYS,
    endTime,
    model: emptyToUndefined(filters.model),
    phase: emptyToUndefined(filters.phase),
    source: emptyToUndefined(filters.source),
    startTime,
    username: emptyToUndefined(filters.username),
  };
}

function periodLabel(params: AiTokenUsageQueryParams) {
  if (params.startTime || params.endTime) {
    return '筛选范围';
  }
  return `近 ${PERIOD_DAYS} 天`;
}

function applyLogPage(page: {
  pageNum: number;
  pageSize: number;
  records: AiTokenUsageLog[];
  total: number;
}) {
  logs.value = page.records;
  total.value = page.total;
  pageNum.value = page.pageNum;
  pageSize.value = page.pageSize;
}

function handleSearch() {
  pageNum.value = 1;
  void loadPage();
}

function handleReset() {
  filters.model = '';
  filters.phase = '';
  filters.source = '';
  filters.timeRange = [];
  filters.username = '';
  pageNum.value = 1;
  void loadPage();
}

function handlePageChange(value: number) {
  pageNum.value = value;
  void loadLogs();
}

function handlePageSizeChange(value: number) {
  pageSize.value = value;
  pageNum.value = 1;
  void loadLogs();
}

function openDetail(row: AiTokenUsageLog) {
  selectedLog.value = row;
  detailVisible.value = true;
}

function sourceLabel(value?: null | string) {
  return SOURCE_OPTIONS.find((item) => item.value === value)?.label ?? value ?? '-';
}

function phaseLabel(value?: null | string) {
  return PHASE_OPTIONS.find((item) => item.value === value)?.label ?? value ?? '-';
}

function logPhaseLabel(log?: AiTokenUsageLog) {
  return log?.phaseLabel || phaseLabel(log?.phase);
}

function tagType(value?: null | string) {
  if (value === 'report') return 'warning';
  if (value === 'agent') return 'success';
  return 'info';
}

function emptyToUndefined(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}

function formatNumber(value?: null | number) {
  return Number(value ?? 0).toLocaleString();
}

function formatCost(value?: null | number, currency?: null | string) {
  if (value === null || value === undefined) return '-';
  const unit = currency === 'CNY' ? '元' : (currency ?? '');
  const formatted = Number(value).toLocaleString('zh-CN', {
    maximumFractionDigits: 6,
    minimumFractionDigits: 2,
  });
  return unit ? `${formatted} ${unit}` : formatted;
}

function formatTime(value?: null | string) {
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
    <div class="token-usage-page">
      <PageHero
        description="查看模型调用消耗、来源归因和响应记录。"
        title="Token 用量"
      >
        <template #actions>
          <ElButton :loading="loading" type="primary" @click="loadPage()">
            <IconifyIcon icon="lucide:refresh-cw" />
            刷新用量
          </ElButton>
        </template>
      </PageHero>

      <ElSkeleton v-if="loading && !hasData" :rows="12" animated />

      <template v-else>
        <section class="metric-grid">
          <ElCard
            v-for="item in metricCards"
            :key="item.label"
            class="metric-card"
            :class="{ 'is-cost': item.tone === 'cost' }"
            shadow="never"
          >
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <p>{{ item.detail }}</p>
          </ElCard>
        </section>

        <ElCard class="usage-panel" shadow="never">
          <div class="panel-header">
            <div>
              <h3>{{ appliedPeriodLabel }}趋势</h3>
              <p>输入与输出 Token 消耗变化。</p>
            </div>
            <ElTag effect="plain" size="small">AI</ElTag>
          </div>
          <div class="chart-box">
            <TokenUsageTrend :trends="trends" />
          </div>
        </ElCard>

        <ElCard class="usage-panel" shadow="never">
          <div class="panel-header">
            <div>
              <h3>用量明细</h3>
            </div>
          </div>

          <ElForm class="filter-form" :model="filters" label-position="top">
            <ElFormItem label="发生时间">
              <ElDatePicker
                v-model="filters.timeRange"
                end-placeholder="结束时间"
                range-separator="至"
                start-placeholder="开始时间"
                type="datetimerange"
                value-format="YYYY-MM-DDTHH:mm:ss"
              />
            </ElFormItem>
            <ElFormItem label="来源">
              <ElSelect v-model="filters.source" clearable placeholder="全部来源">
                <ElOption
                  v-for="item in SOURCE_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="阶段">
              <ElSelect v-model="filters.phase" clearable placeholder="全部阶段">
                <ElOption
                  v-for="item in PHASE_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="模型">
              <ElInput v-model="filters.model" clearable placeholder="模型名称" />
            </ElFormItem>
            <ElFormItem label="用户名">
              <ElInput
                v-model="filters.username"
                clearable
                placeholder="用户名"
              />
            </ElFormItem>
            <ElFormItem class="filter-actions" label="操作">
              <ElButton type="primary" @click="handleSearch">查询</ElButton>
              <ElButton @click="handleReset">重置</ElButton>
            </ElFormItem>
          </ElForm>

          <ElTable
            v-loading="tableLoading"
            :data="logs"
            border
            class="usage-table"
            row-key="id"
          >
            <ElTableColumn label="发生时间" min-width="160">
              <template #default="{ row }">
                {{ formatTime(row.occurredAt) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="来源" min-width="110">
              <template #default="{ row }">
                <ElTag :type="tagType(row.source)" effect="plain" size="small">
                  {{ sourceLabel(row.source) }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="阶段" min-width="120">
              <template #default="{ row }">
                {{ logPhaseLabel(row) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="模型" min-width="150" prop="model" show-overflow-tooltip />
            <ElTableColumn label="用户名" min-width="130" prop="username" show-overflow-tooltip />
            <ElTableColumn align="right" label="输入" min-width="100">
              <template #default="{ row }">{{ formatNumber(row.promptTokens) }}</template>
            </ElTableColumn>
            <ElTableColumn align="right" label="输出" min-width="100">
              <template #default="{ row }">
                {{ formatNumber(row.completionTokens) }}
              </template>
            </ElTableColumn>
            <ElTableColumn align="right" label="总量" min-width="110">
              <template #default="{ row }">{{ formatNumber(row.totalTokens) }}</template>
            </ElTableColumn>
            <ElTableColumn align="right" label="缓存" min-width="100">
              <template #default="{ row }">{{ formatNumber(row.cachedTokens) }}</template>
            </ElTableColumn>
            <ElTableColumn align="right" label="推理" min-width="100">
              <template #default="{ row }">
                {{ formatNumber(row.reasoningTokens) }}
              </template>
            </ElTableColumn>
            <ElTableColumn align="right" label="预估费用" min-width="120">
              <template #default="{ row }">
                {{ formatCost(row.estimatedCost?.totalCost, row.estimatedCost?.currency) }}
              </template>
            </ElTableColumn>
            <ElTableColumn fixed="right" label="操作" width="88">
              <template #default="{ row }">
                <ElButton link type="primary" @click="openDetail(row)">
                  详情
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>

          <div class="pagination-row">
            <ElPagination
              v-model:current-page="pageNum"
              v-model:page-size="pageSize"
              :page-sizes="PAGE_SIZE_OPTIONS"
              :total="total"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="handlePageChange"
              @size-change="handlePageSizeChange"
            />
          </div>
        </ElCard>
      </template>
    </div>

    <ElDrawer v-model="detailVisible" size="560px" title="Token 用量详情">
      <template v-if="selectedLog">
        <ElDescriptions :column="1" border>
          <ElDescriptionsItem label="记录 ID">{{ selectedLog.id }}</ElDescriptionsItem>
          <ElDescriptionsItem label="发生时间">
            {{ formatTime(selectedLog.occurredAt) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="服务商">
            {{ selectedLog.provider || '-' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="模型">{{ selectedLog.model || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="来源">
            {{ sourceLabel(selectedLog.source) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="阶段">
            {{ logPhaseLabel(selectedLog) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="用户名">
            {{ selectedLog.username || '-' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="响应 ID">
            {{ selectedLog.responseId || '-' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="结束原因">
            {{ selectedLog.finishReason || '-' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="缓存命中">
            {{ formatNumber(selectedLog.promptCacheHitTokens) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="缓存未命中">
            {{ formatNumber(selectedLog.promptCacheMissTokens) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="缓存命中费用">
            {{
              formatCost(
                selectedLog.estimatedCost?.cacheHitInputCost,
                selectedLog.estimatedCost?.currency,
              )
            }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="缓存未命中费用">
            {{
              formatCost(
                selectedLog.estimatedCost?.cacheMissInputCost,
                selectedLog.estimatedCost?.currency,
              )
            }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="输出费用">
            {{
              formatCost(
                selectedLog.estimatedCost?.outputCost,
                selectedLog.estimatedCost?.currency,
              )
            }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="预估总费用">
            {{
              formatCost(
                selectedLog.estimatedCost?.totalCost,
                selectedLog.estimatedCost?.currency,
              )
            }}
          </ElDescriptionsItem>
        </ElDescriptions>
      </template>
    </ElDrawer>
  </Page>
</template>

<style scoped>
.token-usage-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.metric-card,
.usage-panel {
  background: var(--el-bg-color);
  border-color: var(--el-border-color-light);
  border-radius: 8px;
}

.metric-card.is-cost {
  background: linear-gradient(
    135deg,
    rgb(64 158 255 / 12%),
    var(--el-bg-color) 58%
  );
  border-color: rgb(64 158 255 / 42%);
}

.metric-card span {
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

.metric-card.is-cost strong {
  font-size: 30px;
  color: var(--el-color-primary);
}

.metric-card p,
.panel-header p {
  margin: 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--el-text-color-regular);
}

.metric-card p {
  margin-top: 6px;
}

.panel-header {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

h3 {
  margin: 0;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

h3 {
  font-size: 15px;
  line-height: 22px;
}

.chart-box {
  min-width: 0;
  height: 320px;
}

.filter-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 8px 12px;
  align-items: end;
  margin-bottom: 14px;
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.filter-form :deep(.el-date-editor),
.filter-form :deep(.el-select),
.filter-form :deep(.el-input) {
  width: 100%;
}

.filter-actions :deep(.el-form-item__content) {
  display: flex;
  gap: 8px;
  flex-wrap: nowrap;
}

.usage-table {
  width: 100%;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

@media (max-width: 760px) {
  .panel-header {
    flex-direction: column;
    align-items: stretch;
  }

  .chart-box {
    height: 280px;
  }

  .pagination-row {
    justify-content: flex-start;
    overflow-x: auto;
  }
}
</style>
