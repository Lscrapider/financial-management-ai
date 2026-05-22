<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { StockIntradayTrend, StockQuote } from '#/api/stock';

import { computed, nextTick, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import {
  ElButton,
  ElCard,
  ElEmpty,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';
import type { Sort } from 'element-plus';

import { listStockIntradayTrends, listStockQuotes } from '#/api/stock';

const marketOptions = [
  { label: '全部市场', value: '' },
  { label: '科创板', value: 'STAR' },
  { label: '创业板', value: 'CHINEXT' },
  { label: '沪市主板', value: 'SH_MAIN' },
  { label: '深市主板', value: 'SZ_MAIN' },
];

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

const loadingQuotes = ref(false);
const loadingTrends = ref(false);
const marketCode = ref('');
const quotes = ref<StockQuote[]>([]);
const sortField = ref('changePercent');
const sortOrder = ref<'asc' | 'desc'>('desc');
const trends = ref<StockIntradayTrend[]>([]);
const selectedStockCode = ref('');

const selectedQuote = computed(() => {
  return quotes.value.find((item) => item.stockCode === selectedStockCode.value);
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

onMounted(() => {
  refreshQuotes();
});

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

function selectQuote(row: StockQuote) {
  selectedStockCode.value = row.stockCode;
  refreshTrends();
}

function sortQuotes(sort: Sort) {
  sortField.value = String(sort.prop || 'changePercent');
  sortOrder.value = sort.order === 'ascending' ? 'asc' : 'desc';
  refreshQuotes();
}

function renderTrendChart() {
  const times = trends.value.map((item) => formatTime(item.trendTime));
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
  <Page title="股票行情">
    <div class="stock-workspace">
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
                <span class="muted">{{ selectedQuote?.exchangeCode ?? '-' }}</span>
              </div>
            </template>
            <div v-if="selectedQuote" class="metric-grid">
              <div class="metric-item">
                <span>今开</span>
                <strong>{{ formatPrice(selectedQuote.openPrice) }}</strong>
              </div>
              <div class="metric-item">
                <span>昨收</span>
                <strong>{{ formatPrice(selectedQuote.previousClosePrice) }}</strong>
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
                <strong>{{ formatChangePercent(selectedQuote.turnoverRate) }}</strong>
              </div>
              <div class="metric-item">
                <span>振幅</span>
                <strong>{{ formatChangePercent(selectedQuote.amplitude) }}</strong>
              </div>
              <div class="metric-item">
                <span>成交额</span>
                <strong>{{ formatMoney(selectedQuote.turnoverAmount) }}</strong>
              </div>
              <div class="metric-item">
                <span>总市值</span>
                <strong>{{ formatMoney(selectedQuote.totalMarketValue) }}</strong>
              </div>
            </div>
            <ElEmpty v-else description="暂无行情数据" />
          </ElCard>

          <ElCard class="trend-panel" shadow="never">
            <template #header>
              <div class="panel-header">
                <span>分时走势</span>
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
              <EchartsUI v-if="trends.length > 0" ref="chartRef" height="100%" />
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
  font-weight: 600;
  justify-content: space-between;
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

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
