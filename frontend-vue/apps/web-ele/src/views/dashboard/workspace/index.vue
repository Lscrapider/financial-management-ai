<script lang="ts" setup>
import type { Sort } from 'element-plus';

import type { EchartsUIType } from '@vben/plugins/echarts';

import type { IndexQuote } from '#/api/index-market';
import type { StockIntradayTrend, StockKline, StockQuote } from '#/api/stock';

import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

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

import { listIndexQuotes } from '#/api/index-market';
import {
  listStockIntradayTrends,
  listStockKlines,
  listStockQuotes,
} from '#/api/stock';

type KlineAdjustType = 'hfq' | 'none' | 'qfq';
type TrendPeriod = 'daily' | 'intraday' | 'monthly' | 'weekly';

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

const trendPeriodOptions: Array<{ label: string; value: TrendPeriod }> = [
  { label: '分时', value: 'intraday' },
  { label: '日K', value: 'daily' },
  { label: '周K', value: 'weekly' },
  { label: '月K', value: 'monthly' },
];

const adjustTypeOptions: Array<{ label: string; value: KlineAdjustType }> = [
  { label: '后复权', value: 'hfq' },
  { label: '前复权', value: 'qfq' },
  { label: '不复权', value: 'none' },
];

const MARKET_CHART_COLORS = {
  compare: '#8a929f',
  fall: '#57d188',
  price: '#006be6',
  reference: '#efbd48',
  rise: '#dc4446',
} as const;

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);
const router = useRouter();

const detailColumnRef = ref<HTMLElement>();
const quotePanelRef = ref<InstanceType<typeof ElCard>>();
const indexQuotes = ref<IndexQuote[]>([]);
const loadingIndexQuotes = ref(false);
const loadingQuotes = ref(false);
const loadingTrends = ref(false);
const marketCode = ref('');
const quotes = ref<StockQuote[]>([]);
const sortField = ref('changePercent');
const sortOrder = ref<'asc' | 'desc'>('desc');
const trends = ref<StockIntradayTrend[]>([]);
const klines = ref<StockKline[]>([]);
const selectedStockCode = ref('');
const trendPeriod = ref<TrendPeriod>('intraday');
const adjustType = ref<KlineAdjustType>('hfq');
const klineLimit = ref(250);
const quotePanelHeight = ref(0);
const quoteTableBodyHeight = ref(580);
const quoteTableHeight = ref(620);
const quoteDetailVisible = ref(false);
const quoteDetailTitle = ref('');
const quoteDetailRows = ref<StockQuote['quoteDetails']>([]);

const selectedQuote = computed(() => {
  return quotes.value.find(
    (item) => item.stockCode === selectedStockCode.value,
  );
});

const quotePanelStyle = computed(() => {
  return quotePanelHeight.value > 0
    ? {
        '--quote-table-body-height': `${quoteTableBodyHeight.value}px`,
        height: `${quotePanelHeight.value}px`,
      }
    : {};
});

const relatedIndexQuotes = computed(() => {
  const option = marketOptions.find((item) => item.value === marketCode.value);
  const relatedSecids = option?.relatedIndexSecids ?? [];
  if (relatedSecids.length === 0) {
    return indexQuotes.value;
  }
  const matched = relatedSecids.flatMap((secid) => {
    const quote = indexQuotes.value.find((item) => item.secid === secid);
    return quote ? [quote] : [];
  });
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

const latestKline = computed(() => {
  return klines.value.at(-1);
});

const selectedTrendPeriodLabel = computed(() => {
  return (
    trendPeriodOptions.find((item) => item.value === trendPeriod.value)
      ?.label ?? '走势'
  );
});

const trendStatusText = computed(() => {
  if (trendPeriod.value !== 'intraday') {
    if (!latestKline.value) {
      return `暂无${selectedTrendPeriodLabel.value}数据`;
    }
    return `${selectedTrendPeriodLabel.value}截至 ${latestKline.value.tradeDate} · 同步于 ${formatDateTime(latestKline.value.syncedAt)}`;
  }
  if (!latestTrend.value) {
    return '暂无分时数据';
  }
  return `分时截至 ${formatDateTime(latestTrend.value.trendTime)} · 同步于 ${formatDateTime(latestTrend.value.syncedAt)}`;
});

let detailColumnResizeObserver: ResizeObserver | undefined;

onMounted(() => {
  refreshIndexQuotes();
  refreshQuotes();
  nextTick(() => {
    syncQuoteTableHeights();
    if (detailColumnRef.value) {
      detailColumnResizeObserver = new ResizeObserver(syncQuoteTableHeights);
      detailColumnResizeObserver.observe(detailColumnRef.value);
    }
  });
});

onBeforeUnmount(() => {
  detailColumnResizeObserver?.disconnect();
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
      limit: 500,
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
    await refreshTrendData();
    await nextTick();
    syncQuoteTableHeights();
  } finally {
    loadingQuotes.value = false;
  }
}

async function refreshTrendData() {
  if (trendPeriod.value === 'intraday') {
    await refreshIntradayTrends();
    await nextTick();
    syncQuoteTableHeights();
    return;
  }
  await refreshKlines();
  await nextTick();
  syncQuoteTableHeights();
}

function syncQuoteTableHeights() {
  const detailHeight = detailColumnRef.value?.offsetHeight ?? 0;
  const quotePanelElement = quotePanelRef.value?.$el as HTMLElement | undefined;
  if (detailHeight <= 0 || !quotePanelElement) {
    return;
  }

  const cardHeaderHeight =
    quotePanelElement.querySelector<HTMLElement>('.el-card__header')
      ?.offsetHeight ?? 56;
  const cardBodyElement =
    quotePanelElement.querySelector<HTMLElement>('.el-card__body');
  const cardBodyStyle = cardBodyElement
    ? getComputedStyle(cardBodyElement)
    : undefined;
  const cardBodyVerticalPadding =
    Number.parseFloat(cardBodyStyle?.paddingTop ?? '0') +
    Number.parseFloat(cardBodyStyle?.paddingBottom ?? '0');
  const tableHeaderHeight =
    quotePanelElement.querySelector<HTMLElement>(
      '.quote-table .el-table__header-wrapper',
    )?.offsetHeight ?? 40;

  quotePanelHeight.value = detailHeight;
  quoteTableHeight.value = Math.max(
    Math.floor(detailHeight - cardHeaderHeight - cardBodyVerticalPadding),
    320,
  );
  quoteTableBodyHeight.value = Math.max(
    Math.floor(quoteTableHeight.value - tableHeaderHeight),
    280,
  );
}

async function refreshIntradayTrends() {
  if (!selectedStockCode.value) {
    trends.value = [];
    klines.value = [];
    renderTrendChart();
    return;
  }

  loadingTrends.value = true;
  try {
    trends.value = await listStockIntradayTrends(selectedStockCode.value);
    klines.value = [];
    await nextTick();
    renderIntradayChart();
  } finally {
    loadingTrends.value = false;
  }
}

async function refreshKlines() {
  if (!selectedStockCode.value) {
    trends.value = [];
    klines.value = [];
    renderTrendChart();
    return;
  }

  loadingTrends.value = true;
  try {
    klines.value = await listStockKlines({
      adjustType: adjustType.value,
      limit: klineLimit.value,
      periodType:
        trendPeriod.value === 'intraday' ? 'daily' : trendPeriod.value,
      stockCode: selectedStockCode.value,
    });
    trends.value = [];
    await nextTick();
    renderKlineChart();
  } finally {
    loadingTrends.value = false;
  }
}

function selectQuote(row: StockQuote) {
  selectedStockCode.value = row.stockCode;
  refreshTrendData();
}

function openQuoteDetails(row: StockQuote) {
  quoteDetailTitle.value = `${row.stockName} ${row.stockCode}`;
  quoteDetailRows.value = row.quoteDetails ?? [];
  quoteDetailVisible.value = true;
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
  if (trendPeriod.value === 'intraday') {
    renderIntradayChart();
    return;
  }
  renderKlineChart();
}

function renderIntradayChart() {
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
    color: [
      MARKET_CHART_COLORS.price,
      MARKET_CHART_COLORS.reference,
      MARKET_CHART_COLORS.compare,
    ],
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

function renderKlineChart() {
  const dates = klines.value.map((item) => item.tradeDate);
  const candleData = klines.value.map((item) => [
    toNumber(item.openPrice),
    toNumber(item.closePrice),
    toNumber(item.lowPrice),
    toNumber(item.highPrice),
  ]);
  const ma5 = klines.value.map((item) => toNullableNumber(item.ma5));
  const ma10 = klines.value.map((item) => toNullableNumber(item.ma10));
  const ma20 = klines.value.map((item) => toNullableNumber(item.ma20));
  const volumes = klines.value.map((item) => {
    const openPrice = toNumber(item.openPrice);
    const closePrice = toNumber(item.closePrice);
    return {
      itemStyle: {
        color:
          closePrice >= openPrice
            ? MARKET_CHART_COLORS.rise
            : MARKET_CHART_COLORS.fall,
      },
      value: toNumber(item.volume),
    };
  });

  renderEcharts({
    color: [
      MARKET_CHART_COLORS.rise,
      MARKET_CHART_COLORS.reference,
      MARKET_CHART_COLORS.price,
      MARKET_CHART_COLORS.compare,
    ],
    axisPointer: {
      link: [
        {
          xAxisIndex: [0, 1],
        },
      ],
    },
    dataZoom: [
      {
        bottom: 10,
        height: 20,
        xAxisIndex: [0, 1],
        type: 'slider',
      },
      {
        xAxisIndex: [0, 1],
        type: 'inside',
      },
    ],
    grid: [
      {
        bottom: 124,
        left: 48,
        right: 24,
        top: 36,
      },
      {
        bottom: 48,
        height: 52,
        left: 48,
        right: 24,
      },
    ],
    legend: {
      data: [selectedTrendPeriodLabel.value, 'MA5', 'MA10', 'MA20'],
      top: 0,
    },
    series: [
      {
        data: candleData,
        itemStyle: {
          borderColor: MARKET_CHART_COLORS.rise,
          borderColor0: MARKET_CHART_COLORS.fall,
          color: MARKET_CHART_COLORS.rise,
          color0: MARKET_CHART_COLORS.fall,
        },
        name: selectedTrendPeriodLabel.value,
        type: 'candlestick',
        xAxisIndex: 0,
        yAxisIndex: 0,
      },
      {
        data: ma5,
        name: 'MA5',
        showSymbol: false,
        smooth: true,
        type: 'line',
        xAxisIndex: 0,
        yAxisIndex: 0,
      },
      {
        data: ma10,
        name: 'MA10',
        showSymbol: false,
        smooth: true,
        type: 'line',
        xAxisIndex: 0,
        yAxisIndex: 0,
      },
      {
        data: ma20,
        name: 'MA20',
        showSymbol: false,
        smooth: true,
        type: 'line',
        xAxisIndex: 0,
        yAxisIndex: 0,
      },
      {
        barWidth: '60%',
        data: volumes,
        name: '成交量',
        type: 'bar',
        xAxisIndex: 1,
        yAxisIndex: 1,
      },
    ],
    tooltip: {
      axisPointer: {
        type: 'cross',
      },
      trigger: 'axis',
    },
    xAxis: [
      {
        axisLabel: {
          show: false,
        },
        data: dates,
        type: 'category',
      },
      {
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
        splitNumber: 2,
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
  return `${numberValue > 0 ? '+' : ''}${numberValue.toFixed(3)}%`;
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
    return `${(numberValue / 100_000_000).toFixed(3)}亿`;
  }
  if (Math.abs(numberValue) >= 10_000) {
    return `${(numberValue / 10_000).toFixed(3)}万`;
  }
  return numberValue.toFixed(3);
}

function formatPrice(value?: number | string) {
  const numberValue = toNullableNumber(value);
  return numberValue === null ? '-' : numberValue.toFixed(3);
}

function formatTime(value?: string) {
  if (!value) {
    return '';
  }
  return value.replace('T', ' ').slice(11, 16);
}

function formatVolumeAxis(value?: number) {
  if (!value) {
    return '0';
  }
  if (value >= 100_000_000) {
    return `${(value / 100_000_000).toFixed(1)}亿`;
  }
  if (value >= 10_000) {
    return `${(value / 10_000).toFixed(1)}万`;
  }
  return String(value);
}

function formatVolume(value?: number) {
  if (!value) {
    return '-';
  }
  if (value >= 10_000) {
    return `${(value / 10_000).toFixed(3)}万`;
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
</script>

<template>
  <Page title="股票行情">
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
              class="index-card-change"
              :class="changeClass(item.changePercent)"
            >
              {{ formatChangePercent(item.changePercent) }}
            </span>
            <small>
              {{ item.exchangeCode }} · {{ formatMoney(item.turnoverAmount) }}
            </small>
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
        <ElCard
          ref="quotePanelRef"
          class="quote-panel"
          shadow="never"
          :style="quotePanelStyle"
        >
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
            class="quote-table"
            :height="quoteTableHeight"
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
            <ElTableColumn
              label="均价"
              min-width="100"
              align="right"
              prop="averagePrice"
            >
              <template #default="{ row }">
                {{ formatPrice(row.averagePrice) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="更多" width="90" align="right">
              <template #default="{ row }">
                <ElButton
                  link
                  size="small"
                  type="primary"
                  @click.stop="openQuoteDetails(row)"
                >
                  更多
                </ElButton>
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

        <div ref="detailColumnRef" class="detail-column">
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
                <span>均价</span>
                <strong>{{ formatPrice(selectedQuote.averagePrice) }}</strong>
              </div>
              <div class="metric-item">
                <span>现手</span>
                <strong>{{ formatVolume(selectedQuote.currentVolume) }}</strong>
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
              <div class="metric-item">
                <span>TTM市盈率</span>
                <strong>{{ formatPrice(selectedQuote.peTtm) }}</strong>
              </div>
              <div class="metric-item">
                <span>动态市盈率</span>
                <strong>{{ formatPrice(selectedQuote.peDynamic) }}</strong>
              </div>
              <div class="metric-item">
                <span>静态市盈率</span>
                <strong>{{ formatPrice(selectedQuote.peStatic) }}</strong>
              </div>
              <div class="metric-item">
                <span>量比</span>
                <strong>{{ formatPrice(selectedQuote.volumeRatio) }}</strong>
              </div>
            </div>
            <ElEmpty v-else description="暂无行情数据" />
          </ElCard>

          <ElCard class="trend-panel" shadow="never">
            <template #header>
              <div class="panel-header">
                <span>走势</span>
                <span class="muted trend-status">{{ trendStatusText }}</span>
                <ElSelect
                  v-model="trendPeriod"
                  class="trend-period-select"
                  size="small"
                  @change="refreshTrendData"
                >
                  <ElOption
                    v-for="item in trendPeriodOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </ElSelect>
                <ElSelect
                  v-if="trendPeriod !== 'intraday'"
                  v-model="adjustType"
                  class="adjust-type-select"
                  size="small"
                  @change="refreshTrendData"
                >
                  <ElOption
                    v-for="item in adjustTypeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </ElSelect>
                <ElButton
                  :disabled="!selectedStockCode"
                  :loading="loadingTrends"
                  size="small"
                  @click="refreshTrendData"
                >
                  更新走势
                </ElButton>
              </div>
            </template>
            <div v-loading="loadingTrends" class="chart-wrap">
              <EchartsUI
                v-if="
                  trendPeriod === 'intraday'
                    ? trends.length > 0
                    : klines.length > 0
                "
                ref="chartRef"
                height="100%"
              />
              <ElEmpty
                v-else
                :description="`暂无${selectedTrendPeriodLabel}数据`"
              />
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
.stock-workspace {
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

.stock-title {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: baseline;
  font-size: 28px;
  font-weight: 700;
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
  margin-top: 8px;
  font-size: 13px;
}

.market-index-section {
  padding: 16px 18px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.section-header h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.2;
}

.section-header span {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.index-card-grid {
  display: flex;
  gap: 12px;
  padding-bottom: 4px;
  overflow-x: auto;
  scrollbar-width: thin;
}

.index-card {
  display: grid;
  flex: 0 0 240px;
  gap: 6px;
  min-height: 116px;
  padding: 14px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: var(--el-fill-color-lighter);
  border: 1px solid transparent;
  border-radius: 6px;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease;
}

.index-card:hover {
  background: var(--el-fill-color-light);
  border-color: var(--el-color-primary-light-5);
}

.index-card-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-regular);
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
  font-size: 12px;
  color: var(--el-text-color-secondary);
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
  grid-template-columns: minmax(470px, 0.92fr) minmax(500px, 1.08fr);
  gap: 16px;
  align-items: start;
}

.quote-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.quote-panel :deep(.el-card__body) {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.quote-table {
  flex: 1;
  width: 100%;
  height: 100%;
  min-height: 0;
}

.quote-table :deep(.el-table__inner-wrapper) {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.quote-table :deep(.el-table__body-wrapper),
.quote-table :deep(.el-table__body-wrapper .el-scrollbar) {
  flex: 1;
  height: var(--quote-table-body-height) !important;
  min-height: 0;
}

.quote-table :deep(.el-scrollbar__wrap) {
  height: 100% !important;
}

.detail-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.panel-header {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  font-weight: 600;
}

.trend-status {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  font-weight: 400;
  text-align: right;
  white-space: nowrap;
}

.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.market-select {
  width: 128px;
}

.adjust-type-select,
.trend-period-select {
  flex: 0 0 auto;
  width: 96px;
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
  height: 460px;
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

  .index-card {
    flex-basis: 210px;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
