<script lang="ts" setup>
import type { MarketQuote } from './market';
import type { WorkbenchTargetType } from './types';

import { onMounted, ref, watch } from 'vue';

import { ElEmpty } from 'element-plus';

import { findMarketQuote } from './market';

const props = defineProps<{
  targetCode?: string;
  targetName?: string;
  targetType?: WorkbenchTargetType;
}>();

const loading = ref(false);
const quote = ref<MarketQuote>();

onMounted(loadQuote);

watch(
  () => [props.targetType, props.targetCode],
  () => {
    void loadQuote();
  },
);

async function loadQuote() {
  if (!props.targetType || !props.targetCode) {
    quote.value = undefined;
    return;
  }
  loading.value = true;
  try {
    quote.value = await findMarketQuote(props.targetType, props.targetCode);
  } finally {
    loading.value = false;
  }
}

function formatValue(label: string, value?: number | string) {
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  if (['总市值', '成交额', '流通市值'].includes(label)) {
    return formatMoney(value);
  }
  if (['振幅', '换手率', '涨跌幅'].includes(label)) {
    return formatPercent(value);
  }
  if (typeof value === 'number') {
    return Number.isInteger(value) ? String(value) : value.toFixed(3);
  }
  return value;
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

function formatPercent(value?: number | string) {
  const numberValue = toNullableNumber(value);
  if (numberValue === null) {
    return '-';
  }
  return `${numberValue > 0 ? '+' : ''}${numberValue.toFixed(3)}%`;
}

function toNullableNumber(value?: number | string) {
  if (value === undefined || value === '') {
    return null;
  }
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
}
</script>

<template>
  <div v-loading="loading" class="market-quote-widget">
    <template v-if="quote">
      <header class="widget-summary">
        <div>
          <strong>{{ quote.name }} {{ quote.code }}</strong>
          <span>{{ quote.exchangeCode || quote.targetType }}</span>
        </div>
      </header>
      <div class="metric-grid">
        <div
          v-for="item in quote.quoteFields"
          :key="item.label"
          class="metric-item"
        >
          <span>{{ item.label }}</span>
          <strong>{{ formatValue(item.label, item.value) }}</strong>
        </div>
      </div>
    </template>
    <ElEmpty v-else description="请选择标的" />
  </div>
</template>

<style scoped>
.market-quote-widget {
  height: 100%;
  min-height: 0;
  overflow: auto;
}

.widget-summary {
  padding-bottom: 10px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.widget-summary div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.widget-summary span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(116px, 1fr));
  gap: 10px;
}

.metric-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 68px;
  padding: 10px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}

.metric-item span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.metric-item strong {
  font-size: 16px;
}
</style>
