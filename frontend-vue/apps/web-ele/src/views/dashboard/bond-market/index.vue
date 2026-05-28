<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { BondDailyKline, BondQuote } from '#/api/bond';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

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

import {
  getBondMarketSyncStatus,
  listBondDailyKlines,
  listBondQuotes,
  syncBondMarketData,
} from '#/api/bond';

const rangeOptions = [
  { label: '近60日', value: 60 },
  { label: '近120日', value: 120 },
  { label: '近250日', value: 250 },
  { label: '近500日', value: 500 },
];

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);
const route = useRoute();

const klineLimit = ref(250);
const klines = ref<BondDailyKline[]>([]);
const loadingKlines = ref(false);
const loadingQuotes = ref(false);
const loadingSync = ref(false);
const quotes = ref<BondQuote[]>([]);
const selectedSecid = ref('');
const sortField = ref('bondCode');
const sortOrder = ref<'asc' | 'desc'>('asc');

const selectedQuote = computed(() => {
  return quotes.value.find((item) => item.secid === selectedSecid.value);
});

const latestKline = computed(() => {
  return klines.value.at(-1);
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

const delay = (ms: number) =>
  new Promise((resolve) => {
    setTimeout(resolve, ms);
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
    klines.value = await listBondDailyKlines({
      limit: klineLimit.value,
      secid: selectedSecid.value,
    });
    await nextTick();
    renderKlineChart();
  } finally {
    loadingKlines.value = false;
  }
}

async function manualSyncBonds() {
  if (loadingSync.value) return;
  loadingSync.value = true;
  try {
    const status = await syncBondMarketData();
    ElMessage.info(
      status.started ? '可转债行情同步已开始' : '可转债行情同步正在执行',
    );
    const completed = await waitBondSyncCompleted();
    await refreshQuotes();
    if (completed) {
      ElMessage.success('可转债行情同步完成，数据已刷新');
    } else {
      ElMessage.warning('同步仍在后台执行，可稍后刷新查看');
    }
  } finally {
    loadingSync.value = false;
  }
}

async function waitBondSyncCompleted() {
  for (let i = 0; i < 120; i += 1) {
    await delay(3000);
    const status = await getBondMarketSyncStatus();
    if (!status.running) {
      return true;
    }
  }
  return false;
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
    color: ['#ef4444', '#089981', '#64748b'],
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
        name: '日K',
        type: 'candlestick',
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

function normalizeRouteSecid(value: unknown) {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : '';
  }
  return typeof value === 'string' ? value : '';
}
</script>

<template>
  <Page title="可转债行情">
    <div class="bond-market-page">
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
                <ElButton
                  :loading="loadingSync"
                  size="small"
                  @click="manualSyncBonds"
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
          </ElTable>
        </ElCard>

        <div class="detail-column">
          <ElCard class="quote-detail" shadow="never">
            <template #header>
              <div class="panel-header">
                <span>可转债数据</span>
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
            <ElEmpty v-else description="暂无可转债数据" />
          </ElCard>

          <ElCard class="kline-panel" shadow="never">
            <template #header>
              <div class="panel-header">
                <span>日K线</span>
                <div class="header-actions">
                  <ElSelect
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
                v-if="klines.length > 0"
                ref="chartRef"
                height="100%"
              />
              <ElEmpty v-else description="暂无日K数据" />
            </div>
          </ElCard>
        </div>
      </div>
    </div>
  </Page>
</template>

<style scoped>
.bond-market-page {
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

.bond-title {
  align-items: baseline;
  display: flex;
  flex-wrap: wrap;
  font-size: 28px;
  font-weight: 700;
  gap: 10px;
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
