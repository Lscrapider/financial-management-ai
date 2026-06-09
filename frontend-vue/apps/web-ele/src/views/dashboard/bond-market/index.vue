<script lang="ts" setup>
import type { Sort } from 'element-plus';

import type { EchartsUIType } from '@vben/plugins/echarts';

import type { BondIntradayTrend, BondKline, BondQuote } from '#/api/bond';
import type { StockQuoteDetail } from '#/api/stock';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { Page } from '@vben/common-ui';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import {
  ElButton,
  ElCard,
  ElDialog,
  ElEmpty,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  listBondIntradayTrends,
  listBondKlines,
  listBondQuotes,
} from '#/api/bond';
import PageHero from '#/components/page-hero/index.vue';

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

const MARKET_CHART_COLORS = {
  compare: '#8a929f',
  fall: '#57d188',
  price: '#006be6',
  reference: '#efbd48',
  rise: '#dc4446',
} as const;

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);
const route = useRoute();

const klineLimit = ref(250);
const klinePeriodType =
  ref<(typeof periodOptions)[number]['value']>('intraday');
const klines = ref<BondKline[]>([]);
const trends = ref<BondIntradayTrend[]>([]);
const loadingKlines = ref(false);
const loadingQuotes = ref(false);
const quotes = ref<BondQuote[]>([]);
const selectedSecid = ref('');
const sortField = ref('bondCode');
const sortOrder = ref<'asc' | 'desc'>('asc');
const quoteDetailVisible = ref(false);
const quoteDetailTitle = ref('');
const quoteDetailRows = ref<StockQuoteDetail[]>([]);

const selectedQuote = computed(() => {
  return quotes.value.find((item) => item.secid === selectedSecid.value);
});

const latestKline = computed(() => {
  return klines.value.at(-1);
});

const klinePeriodLabel = computed(() => {
  return (
    periodOptions.find((item) => item.value === klinePeriodType.value)?.label ??
    '走势'
  );
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
    quotes.value = await listBondQuotes({
      limit: 100,
      sortField: sortField.value,
      sortOrder: sortOrder.value,
    });
    const firstSecid = quotes.value[0]?.secid ?? '';
    const querySecid = normalizeRouteSecid(route.query.secid);
    selectedSecid.value = nextSelectedSecid(querySecid, firstSecid);
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
      trends.value = selectedQuote.value?.bondCode
        ? await listBondIntradayTrends(selectedQuote.value.bondCode)
        : [];
      klines.value = [];
    } else {
      klines.value = await listBondKlines({
        bondCode: selectedQuote.value?.bondCode,
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

function selectQuote(row: BondQuote) {
  selectedSecid.value = row.secid;
  refreshKlines();
}

function sortQuotes(sort: Sort) {
  sortField.value = String(sort.prop || 'bondCode');
  sortOrder.value = sort.order === 'descending' ? 'desc' : 'asc';
  refreshQuotes();
}

function openQuoteDetails(row: BondQuote) {
  quoteDetailTitle.value = `${row.bondName} ${row.bondCode}`;
  quoteDetailRows.value = quoteDetailsWithConversionPremium(row);
  quoteDetailVisible.value = true;
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
    color: [
      MARKET_CHART_COLORS.rise,
      MARKET_CHART_COLORS.reference,
      MARKET_CHART_COLORS.price,
      MARKET_CHART_COLORS.compare,
      MARKET_CHART_COLORS.fall,
    ],
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
          borderColor: MARKET_CHART_COLORS.rise,
          borderColor0: MARKET_CHART_COLORS.fall,
          color: MARKET_CHART_COLORS.rise,
          color0: MARKET_CHART_COLORS.fall,
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
  const averages = trends.value.map((item) =>
    toNullableNumber(item.averagePrice),
  );
  const hasAverage = averages.some((item) => item !== null);

  renderEcharts({
    animation: false,
    color: [MARKET_CHART_COLORS.price, MARKET_CHART_COLORS.reference],
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

function quoteDetailsWithConversionPremium(row: BondQuote) {
  const details = [...(row.quoteDetails ?? [])];
  if (toNullableNumber(row.conversionPremiumRate) === null) {
    return details;
  }
  details.push({
    fieldIndex: -1,
    fieldName: '转股溢价率',
    fieldValue: formatChangePercent(row.conversionPremiumRate),
  });
  return details;
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

function toLineNumber(value?: null | number | string) {
  const numberValue = toNullableNumber(value);
  return numberValue && numberValue > 0 ? numberValue : null;
}

function normalizeRouteSecid(value: unknown) {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : '';
  }
  return typeof value === 'string' ? value : '';
}

function nextSelectedSecid(querySecid: string, firstSecid: string) {
  if (quotes.value.some((item) => item.secid === querySecid)) {
    return querySecid;
  }
  if (quotes.value.some((item) => item.secid === selectedSecid.value)) {
    return selectedSecid.value;
  }
  return firstSecid;
}
</script>

<template>
  <Page>
    <div class="bond-market-page">
      <PageHero
        description="查看可转债快照、盘口和 K 线趋势。"
        title="可转债行情"
      />

      <section class="overview-band">
        <div>
          <div class="bond-title">
            {{ selectedQuote?.bondName ?? '可转债行情' }}
            <span v-if="selectedQuote" class="bond-code">
              {{ selectedQuote.bondCode }}
            </span>
          </div>
          <div class="bond-meta">
            {{ selectedQuote?.exchangeCode ?? '-' }} · 更新于
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
              <span>可转债快照</span>
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
            :default-sort="{ order: 'ascending', prop: 'bondCode' }"
            height="620"
            highlight-current-row
            row-key="secid"
            @row-click="selectQuote"
            @sort-change="sortQuotes"
          >
            <ElTableColumn
              label="名称"
              min-width="160"
              prop="bondCode"
              sortable="custom"
            >
              <template #default="{ row }">
                <div class="bond-name-cell">
                  <span>{{ row.bondName }}</span>
                  <small>{{ row.bondCode }}</small>
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
            <ElTableColumn label="评级" min-width="80">
              <template #default="{ row }">
                <ElTag v-if="row.bondRating" effect="plain" size="small">
                  {{ row.bondRating }}
                </ElTag>
                <span v-else>-</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="交易所" min-width="90">
              <template #default="{ row }">
                <ElTag effect="plain" size="small">
                  {{ row.exchangeCode }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="80" align="right">
              <template #default="{ row }">
                <ElButton
                  link
                  size="small"
                  type="primary"
                  @click="openQuoteDetails(row)"
                >
                  更多
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElCard>

        <div class="detail-column">
          <ElCard class="quote-detail" shadow="never">
            <template #header>
              <div class="panel-header">
                <span>盘口数据</span>
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
                <span>换手率</span>
                <strong>{{
                  formatChangePercent(selectedQuote.turnoverRate)
                }}</strong>
              </div>
              <div class="metric-item">
                <span>转股溢价率</span>
                <strong>{{
                  formatChangePercent(selectedQuote.conversionPremiumRate)
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
              <div v-if="selectedQuote.bondRating" class="metric-item">
                <span>债券评级</span>
                <strong>{{ selectedQuote.bondRating }}</strong>
              </div>
            </div>
            <ElEmpty v-else description="暂无盘口数据" />
          </ElCard>

          <ElCard class="kline-panel" shadow="never">
            <template #header>
              <div class="panel-header">
                <span>{{
                  klinePeriodLabel === '分时'
                    ? '分时走势'
                    : `${klinePeriodLabel}线`
                }}</span>
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
                v-if="
                  klinePeriodType === 'intraday'
                    ? trends.length > 0
                    : klines.length > 0
                "
                ref="chartRef"
                height="100%"
              />
              <ElEmpty v-else :description="`暂无${klinePeriodLabel}数据`" />
            </div>
          </ElCard>
        </div>
      </div>

      <ElDialog
        v-model="quoteDetailVisible"
        :title="`${quoteDetailTitle} 更多行情`"
        width="720px"
      >
        <div
          v-if="quoteDetailRows && quoteDetailRows.length > 0"
          class="quote-detail-grid"
        >
          <div
            v-for="item in quoteDetailRows"
            :key="item.fieldIndex"
            class="quote-detail-cell"
          >
            <span class="detail-label">{{ item.fieldName }}</span>
            <span class="detail-value">{{ item.fieldValue || '-' }}</span>
          </div>
        </div>
        <ElEmpty v-else description="暂无更多行情数据" />
      </ElDialog>
    </div>
  </Page>
</template>

<style scoped>
.quote-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 32px;
  max-height: 520px;
  overflow: auto;
}

.quote-detail-cell {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 9px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.quote-detail-cell .detail-label {
  flex-shrink: 0;
  margin-right: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.quote-detail-cell .detail-value {
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  text-align: right;
  overflow-wrap: anywhere;
}

.bond-market-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.overview-band {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.bond-title {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: baseline;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}

.bond-code,
.bond-meta,
.muted {
  color: var(--el-text-color-secondary);
}

.bond-code {
  font-size: 15px;
  font-weight: 500;
}

.bond-meta {
  margin-top: 8px;
  font-size: 13px;
}

.overview-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(84px, 1fr));
  gap: 24px;
  min-width: 360px;
}

.overview-stat {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.overview-stat span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.overview-stat strong {
  font-size: 24px;
  line-height: 1.15;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(520px, 0.95fr) minmax(480px, 1.05fr);
  gap: 16px;
}

.detail-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.panel-header {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.range-select {
  width: 104px;
}

.period-select {
  width: 88px;
}

.bond-name-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.25;
}

.bond-name-cell small {
  color: var(--el-text-color-secondary);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 72px;
  padding: 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}

.metric-item span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.metric-item strong {
  font-size: 18px;
  line-height: 1.2;
}

.chart-wrap {
  min-width: 0;
  height: 430px;
}

@media (max-width: 1200px) {
  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .overview-band {
    flex-direction: column;
    gap: 16px;
    align-items: stretch;
  }

  .overview-stats {
    min-width: 0;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
