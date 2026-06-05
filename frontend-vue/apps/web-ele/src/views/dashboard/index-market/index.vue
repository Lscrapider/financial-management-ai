<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type {
  IndexIntradayTrend,
  IndexKline,
  IndexQuote,
} from '#/api/index-market';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

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

import {
  listIndexIntradayTrends,
  listIndexKlines,
  listIndexQuotes,
} from '#/api/index-market';

const rangeOptions = [
  { label: '近60日', value: 60 },
  { label: '近120日', value: 120 },
  { label: '近250日', value: 250 },
  { label: '近500日', value: 500 },
];
const periodOptions = [
  { label: '分时', value: 'intraday' },
  { label: '日K', value: 'daily' },
  { label: '周K', value: 'weekly' },
  { label: '月K', value: 'monthly' },
] as const;

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);
const route = useRoute();

const klineLimit = ref(250);
const klinePeriodType = ref<(typeof periodOptions)[number]['value']>('intraday');
const klines = ref<IndexKline[]>([]);
const trends = ref<IndexIntradayTrend[]>([]);
const loadingKlines = ref(false);
const loadingQuotes = ref(false);
const quotes = ref<IndexQuote[]>([]);
const selectedSecid = ref('');
const sortField = ref('indexCode');
const sortOrder = ref<'asc' | 'desc'>('asc');

const selectedQuote = computed(() => {
  return quotes.value.find((item) => item.secid === selectedSecid.value);
});

const latestKline = computed(() => {
  return klines.value.at(-1);
});

const klinePeriodLabel = computed(() => {
  return periodOptions.find((item) => item.value === klinePeriodType.value)?.label ?? '走势';
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
    quotes.value = await listIndexQuotes({
      limit: 100,
      marketCode: 'INDEX',
      sortField: sortField.value,
      sortOrder: sortOrder.value,
    });
    const firstSecid = quotes.value[0]?.secid ?? '';
    const querySecid = normalizeRouteSecid(route.query.secid);
    selectedSecid.value = quotes.value.some((item) => item.secid === querySecid)
      ? querySecid
      : quotes.value.some((item) => item.secid === selectedSecid.value)
        ? selectedSecid.value
        : firstSecid;
    await refreshKlines();
  } finally {
    loadingQuotes.value = false;
  }
}

async function refreshKlines() {
  if (!selectedSecid.value) {
    klines.value = [];
    renderKlineChart();
    return;
  }

  loadingKlines.value = true;
  try {
    if (klinePeriodType.value === 'intraday') {
      trends.value = selectedQuote.value?.indexCode
        ? await listIndexIntradayTrends(selectedQuote.value.indexCode)
        : [];
      klines.value = [];
    } else {
      klines.value = await listIndexKlines({
        indexCode: selectedQuote.value?.indexCode,
        limit: klineLimit.value,
        periodType: klinePeriodType.value,
        secid: selectedSecid.value,
      });
      trends.value = [];
    }
    await nextTick();
    renderKlineChart();
  } finally {
    loadingKlines.value = false;
  }
}

function selectQuote(row: IndexQuote) {
  selectedSecid.value = row.secid;
  refreshKlines();
}

function sortQuotes(sort: Sort) {
  sortField.value = String(sort.prop || 'indexCode');
  sortOrder.value = sort.order === 'descending' ? 'desc' : 'asc';
  refreshQuotes();
}

watch(
  () => route.query.secid,
  (value) => {
    const secid = normalizeRouteSecid(value);
    if (!secid || secid === selectedSecid.value) {
      return;
    }
    if (!quotes.value.some((item) => item.secid === secid)) {
      return;
    }
    selectedSecid.value = secid;
    refreshKlines();
  },
);

function renderKlineChart() {
  if (klinePeriodType.value === 'intraday') {
    renderIntradayChart();
    return;
  }
  const dates = klines.value.map((item) => item.tradeDate);
  const candleData = klines.value.map((item) => [
    toNumber(item.openPrice),
    toNumber(item.closePrice),
    toNumber(item.lowPrice),
    toNumber(item.highPrice),
  ]);
  const volumes = klines.value.map((item) => toNumber(item.volume));

  renderEcharts({
    animation: false,
    axisPointer: {
      link: [{ xAxisIndex: 'all' }],
    },
    color: ['#ef4444', '#f59e0b', '#3b82f6', '#8b5cf6', '#64748b'],
    dataZoom: [
      {
        bottom: 12,
        height: 20,
        start: 65,
        type: 'slider',
      },
      {
        type: 'inside',
      },
    ],
    grid: [
      {
        bottom: 112,
        left: 56,
        right: 24,
        top: 32,
      },
      {
        bottom: 44,
        height: 48,
        left: 56,
        right: 24,
      },
    ],
    series: [
      {
        data: candleData,
        itemStyle: {
          borderColor: '#ef4444',
          borderColor0: '#089981',
          color: '#ef4444',
          color0: '#089981',
        },
        name: klinePeriodLabel.value,
        type: 'candlestick',
      },
      {
        data: klines.value.map((item) => toLineNumber(item.ma5)),
        name: 'MA5',
        showSymbol: false,
        smooth: true,
        type: 'line',
      },
      {
        data: klines.value.map((item) => toLineNumber(item.ma10)),
        name: 'MA10',
        showSymbol: false,
        smooth: true,
        type: 'line',
      },
      {
        data: klines.value.map((item) => toLineNumber(item.ma20)),
        name: 'MA20',
        showSymbol: false,
        smooth: true,
        type: 'line',
      },
      {
        data: volumes,
        name: '成交量',
        type: 'bar',
        xAxisIndex: 1,
        yAxisIndex: 1,
      },
    ],
    tooltip: {
      trigger: 'axis',
    },
    legend: {
      data: [klinePeriodLabel.value, 'MA5', 'MA10', 'MA20'],
      top: 0,
    },
    xAxis: [
      {
        boundaryGap: true,
        data: dates,
        type: 'category',
      },
      {
        axisLabel: {
          show: false,
        },
        axisTick: {
          show: false,
        },
        data: dates,
        gridIndex: 1,
        type: 'category',
      },
    ],
    yAxis: [
      {
        scale: true,
        type: 'value',
      },
      {
        axisLabel: {
          formatter: formatVolumeAxis,
        },
        gridIndex: 1,
        type: 'value',
      },
    ],
  });
}

function renderIntradayChart() {
  const times = trends.value.map(
    (item) => item.trendMinute || formatTime(item.trendTime),
  );
  const prices = trends.value.map((item) => toNullableNumber(item.closePrice));
  const averages = trends.value.map((item) => toNullableNumber(item.averagePrice));
  const hasAverage = averages.some((item) => item !== null);

  renderEcharts({
    animation: false,
    color: ['#089981', '#f59e0b'],
    grid: {
      bottom: 32,
      left: 56,
      right: 24,
      top: 32,
    },
    legend: {
      data: hasAverage ? ['价格', '均价'] : ['价格'],
      top: 0,
    },
    series: [
      {
        data: prices,
        name: '价格',
        showSymbol: false,
        smooth: true,
        type: 'line',
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
    ],
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

function formatTime(value?: string) {
  if (!value) {
    return '';
  }
  return value.replace('T', ' ').slice(11, 16);
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

function formatVolume(value?: number) {
  if (!value) {
    return '-';
  }
  if (value >= 100_000_000) {
    return `${(value / 100_000_000).toFixed(2)}亿`;
  }
  if (value >= 10_000) {
    return `${(value / 10_000).toFixed(2)}万`;
  }
  return String(value);
}

function formatVolumeAxis(value: number) {
  if (value >= 100_000_000) {
    return `${(value / 100_000_000).toFixed(1)}亿`;
  }
  if (value >= 10_000) {
    return `${(value / 10_000).toFixed(0)}万`;
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

function toLineNumber(value?: number | string | null) {
  const numberValue = toNullableNumber(value);
  return numberValue && numberValue > 0 ? numberValue : null;
}

function normalizeRouteSecid(value: unknown) {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : '';
  }
  return typeof value === 'string' ? value : '';
}
</script>

<template>
  <Page title="指数行情">
    <div class="index-market-page">
      <section class="overview-band">
        <div>
          <div class="index-title">
            {{ selectedQuote?.indexName ?? '指数行情' }}
            <span v-if="selectedQuote" class="index-code">
              {{ selectedQuote.indexCode }}
            </span>
          </div>
          <div class="index-meta">
            {{ selectedQuote?.exchangeCode ?? '-' }} · 更新于
            {{ formatDateTime(syncedAt) }}
          </div>
        </div>
        <div class="overview-stats">
          <div class="overview-stat">
            <span>最新点位</span>
            <strong :class="changeClass(selectedQuote?.changePercent)">
              {{ formatPrice(selectedQuote?.latestPrice) }}
            </strong>
          </div>
          <div class="overview-stat">
            <span>上涨指数</span>
            <strong class="text-red-500">{{ riseCount }}</strong>
          </div>
          <div class="overview-stat">
            <span>下跌指数</span>
            <strong class="text-emerald-500">{{ fallCount }}</strong>
          </div>
        </div>
      </section>

      <div class="content-grid">
        <ElCard class="quote-panel" shadow="never">
          <template #header>
            <div class="panel-header">
              <span>指数快照</span>
              <div class="header-actions">
                <ElButton size="small" type="primary" @click="refreshQuotes">
                  刷新
                </ElButton>
              </div>
            </div>
          </template>

          <ElTable
            v-loading="loadingQuotes"
            :current-row-key="selectedSecid"
            :data="quotes"
            :default-sort="{ order: 'ascending', prop: 'indexCode' }"
            height="620"
            highlight-current-row
            row-key="secid"
            @row-click="selectQuote"
            @sort-change="sortQuotes"
          >
            <ElTableColumn
              label="名称"
              min-width="150"
              prop="indexCode"
              sortable="custom"
            >
              <template #default="{ row }">
                <div class="index-name-cell">
                  <span>{{ row.indexName }}</span>
                  <small>{{ row.indexCode }}</small>
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn
              align="right"
              label="最新"
              min-width="100"
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
              align="right"
              label="涨幅"
              min-width="100"
              prop="changePercent"
              sortable="custom"
            >
              <template #default="{ row }">
                <span :class="changeClass(row.changePercent)">
                  {{ formatChangePercent(row.changePercent) }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn align="right" label="成交额" min-width="120">
              <template #default="{ row }">
                {{ formatMoney(row.turnoverAmount) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="交易所" min-width="90">
              <template #default="{ row }">
                <ElTag effect="plain" size="small">
                  {{ row.exchangeCode }}
                </ElTag>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElCard>

        <div class="detail-column">
          <ElCard class="quote-detail" shadow="never">
            <template #header>
              <div class="panel-header">
                <span>指数数据</span>
                <span class="muted">
                  {{ latestKline?.tradeDate ?? '-' }}
                </span>
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
                <span>涨跌额</span>
                <strong :class="changeClass(selectedQuote.changeAmount)">
                  {{ formatPrice(selectedQuote.changeAmount) }}
                </strong>
              </div>
              <div class="metric-item">
                <span>振幅</span>
                <strong>{{
                  formatChangePercent(selectedQuote.amplitude)
                }}</strong>
              </div>
              <div class="metric-item">
                <span>成交量</span>
                <strong>{{ formatVolume(selectedQuote.volume) }}</strong>
              </div>
              <div class="metric-item">
                <span>成交额</span>
                <strong>{{ formatMoney(selectedQuote.turnoverAmount) }}</strong>
              </div>
            </div>
            <ElEmpty v-else description="暂无指数数据" />
          </ElCard>

          <ElCard class="kline-panel" shadow="never">
            <template #header>
              <div class="panel-header">
                <span>{{ klinePeriodLabel === '分时' ? '分时走势' : `${klinePeriodLabel}线` }}</span>
                <div class="header-actions">
                  <ElSelect
                    v-model="klinePeriodType"
                    class="period-select"
                    size="small"
                    @change="refreshKlines"
                  >
                    <ElOption
                      v-for="item in periodOptions"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </ElSelect>
                  <ElSelect
                    v-if="klinePeriodType !== 'intraday'"
                    v-model="klineLimit"
                    class="range-select"
                    size="small"
                    @change="refreshKlines"
                  >
                    <ElOption
                      v-for="item in rangeOptions"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </ElSelect>
                  <ElButton
                    :disabled="!selectedSecid"
                    :loading="loadingKlines"
                    size="small"
                    @click="refreshKlines"
                  >
                    更新
                  </ElButton>
                </div>
              </div>
            </template>
            <div v-loading="loadingKlines" class="chart-wrap">
              <EchartsUI
                v-if="klinePeriodType === 'intraday' ? trends.length > 0 : klines.length > 0"
                ref="chartRef"
                height="100%"
              />
              <ElEmpty v-else :description="`暂无${klinePeriodLabel}数据`" />
            </div>
          </ElCard>
        </div>
      </div>
    </div>
  </Page>
</template>

<style scoped>
.index-market-page {
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

.index-title {
  align-items: baseline;
  display: flex;
  flex-wrap: wrap;
  font-size: 28px;
  font-weight: 700;
  gap: 10px;
  line-height: 1.2;
}

.index-code,
.index-meta,
.muted {
  color: var(--el-text-color-secondary);
}

.index-code {
  font-size: 15px;
  font-weight: 500;
}

.index-meta {
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
  grid-template-columns: minmax(520px, 0.95fr) minmax(480px, 1.05fr);
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
  gap: 12px;
  justify-content: space-between;
}

.header-actions {
  align-items: center;
  display: flex;
  gap: 10px;
}

.range-select {
  width: 104px;
}

.period-select {
  width: 88px;
}

.index-name-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.25;
}

.index-name-cell small {
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
  height: 430px;
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
