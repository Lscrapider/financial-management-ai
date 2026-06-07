<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { AppVisitTrend } from '#/api/console';

import { onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

const props = defineProps<{
  trends: AppVisitTrend[];
}>();

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

onMounted(renderVisitTrendChart);

watch(() => props.trends, renderVisitTrendChart, { deep: true });

function renderVisitTrendChart() {
  const labels = props.trends.map((item) => formatTime(item.timeBucket));
  const visits = props.trends.map((item) => item.visitCount ?? 0);
  const uniqueUsers = props.trends.map((item) => item.uniqueUserCount ?? 0);

  renderEcharts({
    color: ['#5ab1ef', '#efbd48'],
    grid: {
      bottom: 8,
      containLabel: true,
      left: 8,
      right: 10,
      top: 38,
    },
    legend: {
      data: ['访问次数', '独立用户'],
      icon: 'roundRect',
      top: 0,
    },
    series: [
      {
        barMaxWidth: 20,
        data: visits,
        name: '访问次数',
        type: 'bar',
      },
      {
        data: uniqueUsers,
        name: '独立用户',
        showSymbol: false,
        smooth: true,
        type: 'line',
      },
    ],
    tooltip: {
      axisPointer: {
        lineStyle: {
          color: '#5ab1ef',
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
    yAxis: {
      axisLabel: {
        color: '#94a3b8',
      },
      axisTick: {
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
  });
}

function formatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return `${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate(),
  ).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:00`;
}
</script>

<template>
  <EchartsUI ref="chartRef" />
</template>
