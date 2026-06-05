<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type {
  MarketKlinePoint,
  MarketTrendPoint,
  WorkbenchKlineAdjustType,
  WorkbenchTrendPeriod,
} from './market';
import type { WorkbenchTargetType } from './types';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { ElEmpty, ElOption, ElSelect } from 'element-plus';

import {
  findMarketQuote,
  listMarketKlines,
  listMarketTrends,
} from './market';

const props = defineProps<{
  targetCode?: string;
  targetName?: string;
  targetType?: WorkbenchTargetType;
}>();

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

const loading = ref(false);
const periodType = ref<WorkbenchTrendPeriod>('intraday');
const adjustType = ref<WorkbenchKlineAdjustType>('hfq');
const trends = ref<MarketTrendPoint[]>([]);
const klines = ref<MarketKlinePoint[]>([]);

const adjustTypeOptions: Array<{
  label: string;
  value: WorkbenchKlineAdjustType;
}> = [
  { label: '后复权', value: 'hfq' },
  { label: '前复权', value: 'qfq' },
  { label: '不复权', value: 'none' },
];

const periodOptions = computed(() => {
  if (props.targetType === 'INDEX') {
    return [
      { label: '分时', value: 'intraday' as const },
      { label: '日K', value: 'daily' as const },
      { label: '周K', value: 'weekly' as const },
      { label: '月K', value: 'monthly' as const },
    ];
  }
  if (props.targetType === 'CONVERTIBLE_BOND') {
    return [
      { label: '分时', value: 'intraday' as const },
      { label: '日K', value: 'daily' as const },
      { label: '周K', value: 'weekly' as const },
      { label: '月K', value: 'monthly' as const },
    ];
  }
  return [
    { label: '分时', value: 'intraday' as const },
    { label: '日K', value: 'daily' as const },
    { label: '周K', value: 'weekly' as const },
    { label: '月K', value: 'monthly' as const },
  ];
});

const hasChartData = computed(() => trends.value.length > 0 || klines.value.length > 0);
const showAdjustTypeSelect = computed(
  () => props.targetType === 'STOCK' && periodType.value !== 'intraday',
);

onMounted(loadChart);

watch(
  () => [props.targetType, props.targetCode],
  () => {
    normalizePeriod();
    void loadChart();
  },
);

watch(adjustType, () => {
  if (showAdjustTypeSelect.value) {
    void loadChart();
  }
});

async function loadChart() {
  if (!props.targetType || !props.targetCode) {
    trends.value = [];
    klines.value = [];
    await nextTick();
    renderChart();
    return;
  }
  normalizePeriod();
  loading.value = true;
  try {
    if (periodType.value === 'intraday') {
      trends.value = await listMarketTrends(props.targetType, props.targetCode);
      klines.value = [];
    } else {
      const quote = await findMarketQuote(props.targetType, props.targetCode);
      klines.value = await listMarketKlines(
        props.targetType,
        props.targetCode,
        periodType.value,
        quote?.secid,
        adjustType.value,
      );
      trends.value = [];
    }
    await nextTick();
    renderChart();
  } finally {
    loading.value = false;
  }
}

function normalizePeriod() {
  const validValues = periodOptions.value.map((item) => item.value);
  if (!validValues.includes(periodType.value)) {
    periodType.value = validValues[0] ?? 'daily';
  }
}

function renderChart() {
  if (periodType.value === 'intraday') {
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
  const averages = trends.value.map((item) => toNullableNumber(item.averagePrice));
  const hasAverage = averages.some((item) => item !== null);

  renderEcharts({
    color: ['#089981', '#f59e0b'],
    grid: {
      bottom: 32,
      left: 44,
      right: 16,
      top: 30,
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

function renderKlineChart() {
  const dates = klines.value.map((item) => item.tradeDate);
  const candleData = klines.value.map((item) => [
    toNumber(item.openPrice),
    toNumber(item.closePrice),
    toNumber(item.lowPrice),
    toNumber(item.highPrice),
  ]);
  const volumes = klines.value.map((item) => {
    const openPrice = toNumber(item.openPrice);
    const closePrice = toNumber(item.closePrice);
    return {
      itemStyle: {
        color: closePrice >= openPrice ? '#ef4444' : '#089981',
      },
      value: toNumber(item.volume),
    };
  });

  renderEcharts({
    color: ['#ef4444', '#f59e0b', '#3b82f6', '#8b5cf6'],
    dataZoom: [
      {
        bottom: 8,
        height: 18,
        xAxisIndex: [0, 1],
        type: 'slider',
      },
      {
        type: 'inside',
        xAxisIndex: [0, 1],
      },
    ],
    grid: [
      {
        bottom: 96,
        left: 44,
        right: 16,
        top: 30,
      },
      {
        bottom: 36,
        height: 42,
        left: 44,
        right: 16,
      },
    ],
    legend: {
      data: [periodLabel(periodType.value), 'MA5', 'MA10', 'MA20'],
      top: 0,
    },
    series: [
      {
        data: candleData,
        itemStyle: {
          borderColor: '#ef4444',
          borderColor0: '#089981',
          color: '#ef4444',
          color0: '#089981',
        },
        name: periodLabel(periodType.value),
        type: 'candlestick',
        xAxisIndex: 0,
        yAxisIndex: 0,
      },
      {
        data: klines.value.map((item) => toLineNumber(item.ma5)),
        name: 'MA5',
        showSymbol: false,
        smooth: true,
        type: 'line',
        xAxisIndex: 0,
        yAxisIndex: 0,
      },
      {
        data: klines.value.map((item) => toLineNumber(item.ma10)),
        name: 'MA10',
        showSymbol: false,
        smooth: true,
        type: 'line',
        xAxisIndex: 0,
        yAxisIndex: 0,
      },
      {
        data: klines.value.map((item) => toLineNumber(item.ma20)),
        name: 'MA20',
        showSymbol: false,
        smooth: true,
        type: 'line',
        xAxisIndex: 0,
        yAxisIndex: 0,
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

function periodLabel(value: WorkbenchTrendPeriod) {
  const labels: Record<WorkbenchTrendPeriod, string> = {
    daily: '日K',
    intraday: '分时',
    monthly: '月K',
    weekly: '周K',
  };
  return labels[value];
}

function toNullableNumber(value?: null | number | string) {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
}

function toLineNumber(value?: null | number | string) {
  const numberValue = toNullableNumber(value);
  return numberValue && numberValue > 0 ? numberValue : null;
}

function toNumber(value?: null | number | string) {
  return toNullableNumber(value) ?? 0;
}
</script>

<template>
  <div v-loading="loading" class="market-trend-widget">
    <div class="trend-toolbar">
      <span>{{ targetName || targetCode || '走势图' }}</span>
      <div class="trend-controls">
        <ElSelect v-model="periodType" size="small" @change="loadChart">
          <ElOption
            v-for="item in periodOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
        <ElSelect
          v-if="showAdjustTypeSelect"
          v-model="adjustType"
          size="small"
        >
          <ElOption
            v-for="item in adjustTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </div>
    </div>
    <div class="chart-wrap">
      <EchartsUI v-if="hasChartData" ref="chartRef" height="100%" />
      <ElEmpty v-else description="请选择标的或暂无走势数据" />
    </div>
  </div>
</template>

<style scoped>
.market-trend-widget {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.trend-toolbar {
  align-items: center;
  display: flex;
  flex: 0 0 auto;
  gap: 10px;
  justify-content: space-between;
}

.trend-toolbar span {
  font-weight: 600;
}

.trend-controls {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.trend-controls :deep(.el-select) {
  width: 92px;
}

.chart-wrap {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
</style>
