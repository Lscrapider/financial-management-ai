<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { AiTokenUsageTrend } from '#/api/console';

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

  renderEcharts({
    color: ['#5ab1ef', '#019680'],
    grid: {
      bottom: 12,
      containLabel: true,
      left: '1.5%',
      right: '1.5%',
      top: 36,
    },
    legend: {
      data: ['输入 Token', '输出 Token'],
      top: 0,
    },
    series: [
      {
        areaStyle: {},
        data: promptTokens,
        name: '输入 Token',
        showSymbol: false,
        smooth: true,
        type: 'line',
      },
      {
        areaStyle: {},
        data: completionTokens,
        name: '输出 Token',
        showSymbol: false,
        smooth: true,
        type: 'line',
      },
    ],
    tooltip: {
      axisPointer: {
        lineStyle: {
          color: '#019680',
          width: 1,
        },
      },
      trigger: 'axis',
    },
    xAxis: {
      axisTick: {
        show: false,
      },
      boundaryGap: false,
      data: labels,
      splitLine: {
        lineStyle: {
          type: 'solid',
          width: 1,
        },
        show: true,
      },
      type: 'category',
    },
    yAxis: [
      {
        axisTick: {
          show: false,
        },
        splitArea: {
          show: true,
        },
        splitNumber: 4,
        type: 'value',
      },
    ],
  });
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
