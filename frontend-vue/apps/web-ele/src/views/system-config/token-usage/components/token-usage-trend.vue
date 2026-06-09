<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { AiTokenUsageTrend } from '#/api/ai-token-usage';

import { onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

const props = defineProps<{
  trends: AiTokenUsageTrend[];
}>();

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

onMounted(renderTokenTrendChart);

watch(() => props.trends, renderTokenTrendChart, { deep: true });

function renderTokenTrendChart() {
  const labels = props.trends.map((item) => formatDate(item.timeBucket));
  const promptTokens = props.trends.map((item) => item.promptTokens ?? 0);
  const completionTokens = props.trends.map(
    (item) => item.completionTokens ?? 0,
  );
  const totalTokens = props.trends.map((item) => item.totalTokens ?? 0);

  renderEcharts({
    color: ['#5ab1ef', '#57d188', '#efbd48'],
    grid: {
      bottom: 8,
      containLabel: true,
      left: 8,
      right: 10,
      top: 38,
    },
    legend: {
      data: ['输入 Token', '输出 Token', '总 Token'],
      icon: 'roundRect',
      top: 0,
    },
    series: [
      {
        data: promptTokens,
        name: '输入 Token',
        showSymbol: false,
        smooth: true,
        type: 'line',
      },
      {
        data: completionTokens,
        name: '输出 Token',
        showSymbol: false,
        smooth: true,
        type: 'line',
      },
      {
        data: totalTokens,
        name: '总 Token',
        showSymbol: false,
        smooth: true,
        type: 'line',
      },
    ],
    tooltip: {
      axisPointer: {
        lineStyle: {
          color: '#57d188',
          width: 1,
        },
      },
      valueFormatter: (value) => Number(value).toLocaleString(),
      trigger: 'axis',
    },
    xAxis: {
      axisLabel: {
        color: '#94a3b8',
      },
      axisTick: {
        show: false,
      },
      boundaryGap: false,
      data: labels,
      splitLine: {
        lineStyle: {
          color: 'rgba(148, 163, 184, 0.14)',
          type: 'solid',
          width: 1,
        },
        show: true,
      },
      type: 'category',
    },
    yAxis: [
      {
        axisLabel: {
          color: '#94a3b8',
          formatter: (value: number) => compactNumber(value),
        },
        axisTick: {
          show: false,
        },
        splitArea: {
          show: false,
        },
        splitLine: {
          lineStyle: {
            color: 'rgba(148, 163, 184, 0.14)',
          },
        },
        splitNumber: 4,
        type: 'value',
      },
    ],
  });
}

function compactNumber(value: number) {
  if (value >= 10_000) {
    return `${Math.round(value / 1000)}k`;
  }
  return `${value}`;
}

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return `${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate(),
  ).padStart(2, '0')}`;
}
</script>

<template>
  <EchartsUI ref="chartRef" />
</template>
