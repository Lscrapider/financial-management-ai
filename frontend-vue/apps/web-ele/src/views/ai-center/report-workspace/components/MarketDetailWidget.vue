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
</script>

<template>
  <div v-loading="loading" class="market-detail-widget">
    <template v-if="quote">
      <header class="widget-summary">
        <div>
          <strong>{{ quote.name }} {{ quote.code }}</strong>
          <span>{{ quote.exchangeCode || quote.targetType }}</span>
        </div>
      </header>
      <div class="detail-grid">
        <div
          v-for="item in quote.detailRows"
          :key="item.label"
          class="detail-cell"
        >
          <span>{{ item.label }}</span>
          <strong>{{ item.value || '-' }}</strong>
        </div>
      </div>
    </template>
    <ElEmpty v-else description="请选择标的" />
  </div>
</template>

<style scoped>
.market-detail-widget {
  height: 100%;
  min-height: 0;
  overflow: auto;
}

.widget-summary {
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-bottom: 12px;
  padding-bottom: 10px;
}

.widget-summary div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.widget-summary span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.detail-grid {
  display: grid;
  gap: 0 18px;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.detail-cell {
  align-items: baseline;
  border-bottom: 1px dashed var(--el-border-color-lighter);
  display: flex;
  gap: 10px;
  justify-content: space-between;
  min-height: 40px;
  padding: 9px 0;
}

.detail-cell span {
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
  font-size: 13px;
}

.detail-cell strong {
  font-size: 13px;
  overflow-wrap: anywhere;
  text-align: right;
}
</style>
