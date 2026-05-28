<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { IndexQuote } from '#/api/index-market';
import type { StockIntradayTrend, StockQuote } from '#/api/stock';

import { computed, nextTick, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import {
  ElButton,
  ElCard,
  ElEmpty,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';
import type { Sort } from 'element-plus';

import { listIndexQuotes } from '#/api/index-market';
import {
  getStockMarketSyncStatus,
  listStockIntradayTrends,
  listStockQuotes,
  syncStockMarketData,
} from '#/api/stock';

const marketOptions = [
  { label: '全部市场', relatedIndexSecids: [], value: '' },
  {
    label: '科创板',
    relatedIndexSecids: ['1.000688', '1.000001'],
    value: 'STAR',
  },
  { label: '创业板', relatedIndexSecids: ['0.399006'], value: 'CHINEXT' },
  { label: '沪市主板', relatedIndexSecids: ['1.000001'], value: 'SH_MAIN' },
  { label: '深市主板', relatedIndexSecids: ['0.399001'], value: 'SZ_MAIN' },
];

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);
const router = useRouter();

const indexQuotes = ref<IndexQuote[]>([]);
const loadingIndexQuotes = ref(false);
const loadingQuotes = ref(false);
const loadingSync = ref(false);
const loadingTrends = ref(false);
const marketCode = ref('');
const quotes = ref<StockQuote[]>([]);
const sortField = ref('changePercent');
const sortOrder = ref<'asc' | 'desc'>('desc');
const trends = ref<StockIntradayTrend[]>([]);
const selectedStockCode = ref('');

const selectedQuote = computed(() => {
  return quotes.value.find(
    (item) => item.stockCode === selectedStockCode.value,
  );
});

const relatedIndexQuotes = computed(() => {
  const option = marketOptions.find((item) => item.value === marketCode.value);
  const relatedSecids = option?.relatedIndexSecids ?? [];
  if (relatedSecids.length === 0) {
    return indexQuotes.value;
  }
  const matched = relatedSecids
    .map((secid) => indexQuotes.value.find((item) => item.secid === secid))
    .filter((item): item is IndexQuote => Boolean(item));
  return matched.length > 0 ? matched : indexQuotes.value;
});

const indexSectionTitle = computed(() => {
  const option = marketOptions.find((item) => item.value === marketCode.value);
  return option?.value ? `${option.label}参考指数` : '市场指数';
});

const indexSectionSubtitle = computed(() => {
  const option = marketOptions.find((item) => item.value === marketCode.value);
  return option?.value ? '当前市场对应指数快照' : '主要指数快照';
});

const riseCount = computed(() => {
  return quotes.value.filter((item) => toNumber(item.changePercent) > 0).length;
});

const fallCount = computed(() => {
  return quotes.value.filter((item) => toNumber(item.changePercent) < 0).length;
});

const syncedAt = computed(() => {
  return selectedQuote.value?.syncedAt ?? quotes.value[0]?.syncedAt ?? '-';
});

const latestTrend = computed(() => {
  return trends.value.at(-1);
});

const trendStatusText = computed(() => {
  if (!latestTrend.value) {
    return '暂无分时数据';
  }
  return `分时截至 ${formatDateTime(latestTrend.value.trendTime)} · 同步于 ${formatDateTime(latestTrend.value.syncedAt)}`;
});

const delay = (ms: number) =>
  new Promise((resolve) => {
    setTimeout(resolve, ms);
  });

onMounted(() => {
  refreshIndexQuotes();
  refreshQuotes();
});

async function refreshIndexQuotes() {
  loadingIndexQuotes.value = true;
  try {
    indexQuotes.value = await listIndexQuotes({
      limit: 10,
      marketCode: 'INDEX',
      sortField: 'indexCode',
      sortOrder: 'asc',
    });
  } finally {
    loadingIndexQuotes.value = false;
  }
}

async function refreshQuotes() {
  loadingQuotes.value = true;
  try {
    quotes.value = await listStockQuotes({
      limit: 100,
      marketCode: marketCode.value || undefined,
      sortField: sortField.value,
      sortOrder: sortOrder.value,
    });
    const firstStockCode = quotes.value[0]?.stockCode ?? '';
    selectedStockCode.value = quotes.value.some(
      (item) => item.stockCode === selectedStockCode.value,
    )
      ? selectedStockCode.value
      : firstStockCode;
    await refreshTrends();
  } finally {
    loadingQuotes.value = false;
  }
}

async function refreshTrends() {
  if (!selectedStockCode.value) {
    trends.value = [];
    renderTrendChart();
    return;
  }

  loadingTrends.value = true;
  try {
    trends.value = await listStockIntradayTrends(selectedStockCode.value);
    await nextTick();
    renderTrendChart();
  } finally {
    loadingTrends.value = false;
  }
}

async function manualSyncStocks() {
  if (loadingSync.value) return;
  loadingSync.value = true;
  try {
    const status = await syncStockMarketData();
    ElMessage.info(
      status.started ? '股票行情同步已开始' : '股票行情同步正在执行',
    );
    const completed = await waitStockSyncCompleted();
    await refreshQuotes();
    if (completed) {
      ElMessage.success('股票行情同步完成，数据已刷新');
    } else {
      ElMessage.warning('同步仍在后台执行，可稍后刷新查看');
    }
  } finally {
    loadingSync.value = false;
  }
}

async function waitStockSyncCompleted() {
  for (let i = 0; i < 120; i += 1) {
    await delay(3000);
    const status = await getStockMarketSyncStatus();
    if (!status.running) {
      return true;
    }
  }
  return false;
}

function selectQuote(row: StockQuote) {
  selectedStockCode.value = row.stockCode;
  refreshTrends();
}

function sortQuotes(sort: Sort) {
  sortField.value = String(sort.prop || 'changePercent');
  sortOrder.value = sort.order === 'ascending' ? 'asc' : 'desc';
  refreshQuotes();
}

function openIndexMarket(indexQuote: IndexQuote) {
  router.push({
    name: 'IndexMarket',
    query: {
      secid: indexQuote.secid,
    },
  });
}

function renderTrendChart() {
  const times = trends.value.map(
    (item) => item.trendMinute || formatTime(item.trendTime),
  );
  const prices = trends.value.map((item) => toNullableNumber(item.closePrice));
  const averages = trends.value.map((item) =>
    toNullableNumber(item.averagePrice),
  );
  const previousClose = toNullableNumber(
    trends.value[0]?.previousClosePrice ??
      selectedQuote.value?.previousClosePrice,
  );
  const previousCloseLine = trends.value.map(() => previousClose);
  const hasAverage = averages.some((item) => item !== null);
  const series = [
    {
      data: prices,
      name: '价格',
      showSymbol: false,
      smooth: true,
      type: 'line' as const,
    },
    ...(hasAverage
      ? [
          {
            data: averages,
            name: '均价',
            showSymbol: false,
            smooth: true,
            type: 'line' as const,
          },
        ]
      : []),
    {
      data: previousCloseLine,
      lineStyle: {
        type: 'dashed' as const,
        width: 1,
      },
      name: '昨收',
      showSymbol: false,
      type: 'line' as const,
    },
  ];

  renderEcharts({
    color: ['#089981', '#f59e0b', '#ef4444'],
    grid: {
      bottom: 36,
      left: 48,
      right: 24,
      top: 36,
    },
    legend: {
      data: hasAverage ? ['价格', '均价', '昨收'] : ['价格', '昨收'],
      top: 0,
    },
    series,
    tooltip: {
      trigger: 'axis',
    },
    xAxis: {
      boundaryGap: false,
      data: times,
      type: 'category',
    },
    yAxis: {
      scale: true,
      type: 'value',
    },
  });
}

function changeClass(value?: number | string) {
  const numberValue = toNumber(value);
  if (numberValue > 0) {
    return 'text-red-500';
  }
  if (numberValue < 0) {
    return 'text-emerald-500';
  }
  return '';
}

function formatChangePercent(value?: number | string) {
  const numberValue = toNullableNumber(value);
  if (numberValue === null) {
    return '-';
  }
  return `${numberValue > 0 ? '+' : ''}${numberValue.toFixed(2)}%`;
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 19);
}

function formatMarket(value?: string) {
  return (
    marketOptions.find((item) => item.value === value)?.label ?? value ?? '-'
  );
}

function formatMoney(value?: number | string) {
  const numberValue = toNullableNumber(value);
  if (numberValue === null) {
    return '-';
  }
  if (Math.abs(numberValue) >= 100_000_000) {
    return `${(numberValue / 100_000_000).toFixed(2)}亿`;
  }
  if (Math.abs(numberValue) >= 10_000) {
    return `${(numberValue / 10_000).toFixed(2)}万`;
  }
  return numberValue.toFixed(2);
}

function formatPrice(value?: number | string) {
  const numberValue = toNullableNumber(value);
  return numberValue === null ? '-' : numberValue.toFixed(2);
}

function formatTime(value?: string) {
  if (!value) {
    return '';
  }
  return value.replace('T', ' ').slice(11, 16);
}

function formatVolume(value?: number) {
  if (!value) {
    return '-';
  }
  if (value >= 10_000) {
    return `${(value / 10_000).toFixed(2)}万`;
  }
  return String(value);
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
  <Page title="行情总览">
    <div class="stock-workspace">
      <section class="market-index-section">
        <div class="section-header">
          <div>
            <h2>{{ indexSectionTitle }}</h2>
            <span>{{ indexSectionSubtitle }}</span>
          </div>
          <ElButton
            :loading="loadingIndexQuotes"
            size="small"
            @click="refreshIndexQuotes"
          >
            刷新指数
          </ElButton>
        </div>
        <div
          v-if="relatedIndexQuotes.length > 0"
          v-loading="loadingIndexQuotes"
          class="index-card-grid"
        >
          <button
            v-for="item in relatedIndexQuotes"
            :key="item.secid"
            class="index-card"
            type="button"
            @click="openIndexMarket(item)"
          >
            <span class="index-card-name">{{ item.indexName }}</span>
            <strong :class="changeClass(item.changePercent)">
              {{ formatPrice(item.latestPrice) }}
            </strong>
            <span
              :class="['index-card-change', changeClass(item.changePercent)]"
            >
              {{ formatChangePercent(item.changePercent) }}
            </span>
            <small
              >{{ item.exchangeCode }} ·
              {{ formatMoney(item.turnoverAmount) }}</small
            >
          </button>
        </div>
        <ElEmpty v-else description="暂无指数数据" />
      </section>

      <section class="overview-band">
        <div>
          <div class="stock-title">
            {{ selectedQuote?.stockName ?? '行情看板' }}
            <span v-if="selectedQuote" class="stock-code">
              {{ selectedQuote.stockCode }}
            </span>
          </div>
          <div class="stock-meta">
            {{ formatMarket(selectedQuote?.marketCode) }} · 更新于
            {{ formatDateTime(syncedAt) }}
          </div>
        </div>
        <div class="overview-stats">
          <div class="overview-stat">
            <span>最新价</span>
            <strong :class="changeClass(selectedQuote?.changePercent)">
              {{ formatPrice(selectedQuote?.latestPrice) }}
            </strong>
          </div>
          <div class="overview-stat">
            <span>上涨</span>
            <strong class="text-red-500">{{ riseCount }}</strong>
          </div>
          <div class="overview-stat">
            <span>下跌</span>
            <strong class="text-emerald-500">{{ fallCount }}</strong>
          </div>
        </div>
      </section>

      <div class="content-grid">
        <ElCard class="quote-panel" shadow="never">
          <template #header>
            <div class="panel-header">
              <span>行情列表</span>
              <div class="header-actions">
                <ElSelect
                  v-model="marketCode"
                  class="market-select"
                  size="small"
                  @change="refreshQuotes"
                >
                  <ElOption
                    v-for="item in marketOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </ElSelect>
                <ElButton
                  :loading="loadingSync"
                  size="small"
                  @click="manualSyncStocks"
                >
                  手动同步
                </ElButton>
                <ElButton size="small" type="primary" @click="refreshQuotes">
                  刷新
                </ElButton>
              </div>
            </div>
          </template>

          <ElTable
            v-loading="loadingQuotes"
            :data="quotes"
            :default-sort="{ order: 'descending', prop: 'changePercent' }"
            height="620"
            highlight-current-row
            row-key="stockCode"
            @row-click="selectQuote"
            @sort-change="sortQuotes"
          >
            <ElTableColumn
              label="名称"
              min-width="150"
              prop="stockCode"
              sortable="custom"
            >
              <template #default="{ row }">
                <div class="stock-name-cell">
                  <span>{{ row.stockName }}</span>
                  <small>{{ row.stockCode }}</small>
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn
              label="最新"
              min-width="100"
              align="right"
              prop="latestPrice"
              sortable="custom"
            >
              <template #default="{ row }">
                <span :class="changeClass(row.changePercent)">
                  {{ formatPrice(row.latestPrice) }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn
              label="涨幅"
              min-width="100"
              align="right"
              prop="changePercent"
              sortable="custom"
            >
              <template #default="{ row }">
                <span :class="changeClass(row.changePercent)">
                  {{ formatChangePercent(row.changePercent) }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn
              label="成交量"
              min-width="110"
              align="right"
              prop="volume"
              sortable="custom"
            >
              <template #default="{ row }">
                {{ formatVolume(row.volume) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="市场" min-width="110">
              <template #default="{ row }">
                <ElTag effect="plain" size="small">
                  {{ formatMarket(row.marketCode) }}
                </ElTag>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElCard>

        <div class="detail-column">
          <ElCard class="quote-detail" shadow="never">
            <template #header>
              <div class="panel-header">
                <span>盘口数据</span>
                <span class="muted">{{
                  selectedQuote?.exchangeCode ?? '-'
                }}</span>
              </div>
            </template>
            <div v-if="selectedQuote" class="metric-grid">
              <div class="metric-item">
                <span>今开</span>
                <strong>{{ formatPrice(selectedQuote.openPrice) }}</strong>
              </div>
              <div class="metric-item">
                <span>昨收</span>
                <strong>{{
                  formatPrice(selectedQuote.previousClosePrice)
                }}</strong>
              </div>
              <div class="metric-item">
                <span>最高</span>
                <strong class="text-red-500">
                  {{ formatPrice(selectedQuote.highPrice) }}
                </strong>
              </div>
              <div class="metric-item">
                <span>最低</span>
                <strong class="text-emerald-500">
                  {{ formatPrice(selectedQuote.lowPrice) }}
                </strong>
              </div>
              <div class="metric-item">
                <span>换手率</span>
                <strong>{{
                  formatChangePercent(selectedQuote.turnoverRate)
                }}</strong>
              </div>
              <div class="metric-item">
                <span>振幅</span>
                <strong>{{
                  formatChangePercent(selectedQuote.amplitude)
                }}</strong>
              </div>
              <div class="metric-item">
                <span>成交额</span>
                <strong>{{ formatMoney(selectedQuote.turnoverAmount) }}</strong>
              </div>
              <div class="metric-item">
                <span>总市值</span>
                <strong>{{
                  formatMoney(selectedQuote.totalMarketValue)
                }}</strong>
              </div>
            </div>
            <ElEmpty v-else description="暂无行情数据" />
          </ElCard>

          <ElCard class="trend-panel" shadow="never">
            <template #header>
              <div class="panel-header">
                <span>分时走势</span>
                <span class="muted trend-status">{{ trendStatusText }}</span>
                <ElButton
                  :disabled="!selectedStockCode"
                  :loading="loadingTrends"
                  size="small"
                  @click="refreshTrends"
                >
                  更新走势
                </ElButton>
              </div>
            </template>
            <div v-loading="loadingTrends" class="chart-wrap">
              <EchartsUI
                v-if="trends.length > 0"
                ref="chartRef"
                height="100%"
              />
              <ElEmpty v-else description="暂无分时数据" />
            </div>
          </ElCard>
        </div>
      </div>
    </div>
  </Page>
</template>

<style scoped>
.stock-workspace {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.overview-band {
  align-items: center;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  padding: 20px 24px;
}

.stock-title {
  align-items: baseline;
  display: flex;
  flex-wrap: wrap;
  font-size: 28px;
  font-weight: 700;
  gap: 10px;
  line-height: 1.2;
}

.stock-code,
.stock-meta,
.muted {
  color: var(--el-text-color-secondary);
}

.stock-code {
  font-size: 15px;
  font-weight: 500;
}

.stock-meta {
  font-size: 13px;
  margin-top: 8px;
}

.market-index-section {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 16px 18px;
}

.section-header {
  align-items: center;
  display: flex;
  justify-content: space-between;
  margin-bottom: 14px;
}

.section-header h2 {
  font-size: 16px;
  font-weight: 700;
  line-height: 1.2;
  margin: 0;
}

.section-header span {
  color: var(--el-text-color-secondary);
  display: block;
  font-size: 12px;
  margin-top: 4px;
}

.index-card-grid {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 4px;
  scrollbar-width: thin;
}

.index-card {
  background: var(--el-fill-color-lighter);
  border: 1px solid transparent;
  border-radius: 6px;
  color: inherit;
  cursor: pointer;
  display: grid;
  flex: 0 0 240px;
  gap: 6px;
  min-height: 116px;
  padding: 14px;
  text-align: left;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease;
}

.index-card:hover {
  background: var(--el-fill-color-light);
  border-color: var(--el-color-primary-light-5);
}

.index-card-name {
  color: var(--el-text-color-regular);
  font-size: 14px;
  font-weight: 600;
}

.index-card strong {
  font-size: 24px;
  line-height: 1.1;
}

.index-card-change {
  font-size: 13px;
  font-weight: 600;
}

.index-card small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.overview-stats {
  display: grid;
  gap: 24px;
  grid-template-columns: repeat(3, minmax(84px, 1fr));
  min-width: 360px;
}

.overview-stat {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.overview-stat span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.overview-stat strong {
  font-size: 24px;
  line-height: 1.15;
}

.content-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(520px, 1.08fr) minmax(420px, 0.92fr);
}

.detail-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.panel-header {
  align-items: center;
  display: flex;
  gap: 10px;
  font-weight: 600;
  justify-content: space-between;
  min-width: 0;
}

.trend-status {
  flex: 1;
  font-size: 12px;
  font-weight: 400;
  overflow: hidden;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-actions {
  align-items: center;
  display: flex;
  gap: 10px;
}

.market-select {
  width: 128px;
}

.stock-name-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.25;
}

.stock-name-cell small {
  color: var(--el-text-color-secondary);
}

.metric-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.metric-item {
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 72px;
  padding: 12px;
}

.metric-item span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.metric-item strong {
  font-size: 18px;
  line-height: 1.2;
}

.chart-wrap {
  height: 378px;
  min-width: 0;
}

@media (max-width: 1200px) {
  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .overview-band {
    align-items: stretch;
    flex-direction: column;
    gap: 16px;
  }

  .overview-stats {
    min-width: 0;
  }

  .index-card {
    flex-basis: 210px;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
