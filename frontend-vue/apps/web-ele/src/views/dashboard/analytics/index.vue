<script lang="ts" setup>
import type { AnalysisOverviewItem } from '@vben/common-ui';

import { computed, onMounted, ref } from 'vue';

import { AnalysisChartCard, AnalysisOverview } from '@vben/common-ui';
import { SvgCakeIcon, SvgCardIcon, SvgDownloadIcon } from '@vben/icons';

import {
  getAiConsoleOverview,
  listAiTokenUsageTrends,
  type AiConsoleOverview,
  type AiTokenUsageTrend,
} from '#/api/console';

import AnalyticsTrends from './analytics-trends.vue';

const overview = ref<AiConsoleOverview>();
const tokenTrends = ref<AiTokenUsageTrend[]>([]);

const overviewItems = computed<AnalysisOverviewItem[]>(() => [
  {
    icon: SvgCardIcon,
    title: '用户量',
    totalTitle: '总用户量',
    totalValue: overview.value?.user.totalUserCount ?? 0,
    value: overview.value?.user.totalUserCount ?? 0,
  },
  {
    icon: SvgCakeIcon,
    title: '访问量',
    totalTitle: '总访问量',
    totalValue: overview.value?.visit.totalVisitCount ?? 0,
    value: overview.value?.visit.periodVisitCount ?? 0,
  },
  {
    icon: SvgDownloadIcon,
    title: 'Token 用量',
    totalTitle: '近7天 Token',
    totalValue: overview.value?.tokenUsage.totalTokens ?? 0,
    value: overview.value?.tokenUsage.totalTokens ?? 0,
  },
]);

onMounted(() => {
  loadConsoleMetrics();
});

async function loadConsoleMetrics() {
  const [overviewResult, trendResult] = await Promise.all([
    getAiConsoleOverview(7),
    listAiTokenUsageTrends(7),
  ]);
  overview.value = overviewResult;
  tokenTrends.value = trendResult;
}
</script>

<template>
  <div class="p-5">
    <AnalysisOverview :items="overviewItems" />
    <AnalysisChartCard class="mt-5" title="Token 用量趋势">
      <AnalyticsTrends :trends="tokenTrends" />
    </AnalysisChartCard>
  </div>
</template>
