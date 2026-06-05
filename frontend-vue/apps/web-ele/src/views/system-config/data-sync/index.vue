<script lang="ts" setup>
import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElCard,
  ElInput,
  ElMessage,
  ElTag,
} from 'element-plus';

import {
  getBondMarketSyncStatus,
  syncBondMarketData,
  syncBondTrendData,
} from '#/api/bond';
import {
  getIndexMarketSyncStatus,
  syncIndexMarketData,
} from '#/api/index-market';
import {
  getStockMarketSyncStatus,
  syncStockMarketData,
  syncStockTrendData,
} from '#/api/stock';

type SyncKind = 'bond' | 'bondTrend' | 'index' | 'stock' | 'stockTrend';

interface SyncItem {
  description: string;
  key: SyncKind;
  title: string;
  scope: string;
}

const syncItems: SyncItem[] = [
  {
    description: '全量同步所有已启用股票的行情快照、分时数据和 K 线数据。',
    key: 'stock',
    title: '股票行情同步',
    scope: '股票快照 / 分时 / K线',
  },
  {
    description: '全量同步所有已启用指数的行情快照和日 K 数据。',
    key: 'index',
    title: '指数行情同步',
    scope: '指数快照 / 日K',
  },
  {
    description: '全量同步所有已启用可转债的行情快照和日 K 数据。',
    key: 'bond',
    title: '可转债行情同步',
    scope: '可转债快照 / 日K',
  },
];

const loadingMap = ref<Record<SyncKind, boolean>>({
  bond: false,
  bondTrend: false,
  index: false,
  stock: false,
  stockTrend: false,
});
const stockTrendCode = ref('');
const bondTrendCode = ref('');

const delay = (ms: number) =>
  new Promise((resolve) => {
    setTimeout(resolve, ms);
  });

async function runMarketSync(kind: 'bond' | 'index' | 'stock') {
  if (loadingMap.value[kind]) return;
  loadingMap.value[kind] = true;
  try {
    const status = await startMarketSync(kind);
    ElMessage.info(status.started ? `${labelOf(kind)}同步已开始` : `${labelOf(kind)}同步正在执行`);
    const completed = await waitMarketSyncCompleted(kind);
    if (completed) {
      ElMessage.success(`${labelOf(kind)}同步完成`);
    } else {
      ElMessage.warning(`${labelOf(kind)}同步仍在后台执行，可稍后到行情页面刷新查看`);
    }
  } finally {
    loadingMap.value[kind] = false;
  }
}

async function startMarketSync(kind: 'bond' | 'index' | 'stock') {
  if (kind === 'stock') {
    return syncStockMarketData();
  }
  if (kind === 'index') {
    return syncIndexMarketData();
  }
  return syncBondMarketData();
}

async function waitMarketSyncCompleted(kind: 'bond' | 'index' | 'stock') {
  for (let i = 0; i < 120; i += 1) {
    await delay(3000);
    const status = await getMarketSyncStatus(kind);
    if (!status.running) {
      return true;
    }
  }
  return false;
}

async function getMarketSyncStatus(kind: 'bond' | 'index' | 'stock') {
  if (kind === 'stock') {
    return getStockMarketSyncStatus();
  }
  if (kind === 'index') {
    return getIndexMarketSyncStatus();
  }
  return getBondMarketSyncStatus();
}

async function runStockTrendSync() {
  const code = stockTrendCode.value.trim();
  if (!code || loadingMap.value.stockTrend) return;
  loadingMap.value.stockTrend = true;
  try {
    await syncStockTrendData(code);
    ElMessage.success(`${code} 股票分时同步完成`);
  } finally {
    loadingMap.value.stockTrend = false;
  }
}

async function runBondTrendSync() {
  const code = bondTrendCode.value.trim();
  if (!code || loadingMap.value.bondTrend) return;
  loadingMap.value.bondTrend = true;
  try {
    await syncBondTrendData(code);
    ElMessage.success(`${code} 可转债分时同步完成`);
  } finally {
    loadingMap.value.bondTrend = false;
  }
}

function labelOf(kind: 'bond' | 'index' | 'stock') {
  if (kind === 'stock') {
    return '股票行情';
  }
  if (kind === 'index') {
    return '指数行情';
  }
  return '可转债行情';
}
</script>

<template>
  <Page title="数据同步">
    <div class="data-sync-page">
      <section class="sync-grid">
        <ElCard
          v-for="item in syncItems"
          :key="item.key"
          class="sync-card"
          shadow="never"
        >
          <div class="sync-card-body">
            <div class="sync-card-main">
              <div class="sync-card-title">
                <h2>{{ item.title }}</h2>
                <ElTag effect="plain" size="small">
                  {{ item.scope }}
                </ElTag>
              </div>
              <p>{{ item.description }}</p>
            </div>
            <ElButton
              :loading="loadingMap[item.key]"
              type="primary"
              @click="runMarketSync(item.key as 'bond' | 'index' | 'stock')"
            >
              执行同步
            </ElButton>
          </div>
        </ElCard>
      </section>

      <section class="trend-sync-section">
        <ElCard class="trend-card" shadow="never">
          <div class="trend-card-body">
            <div class="trend-card-main">
              <h2>单只股票分时同步</h2>
              <p>仅同步输入股票代码对应的分时数据，不执行全量股票同步。</p>
            </div>
            <div class="trend-actions">
              <ElInput
                v-model="stockTrendCode"
                clearable
                placeholder="股票代码"
                @keyup.enter="runStockTrendSync"
              />
              <ElButton
                :disabled="!stockTrendCode.trim()"
                :loading="loadingMap.stockTrend"
                type="primary"
                @click="runStockTrendSync"
              >
                同步分时
              </ElButton>
            </div>
          </div>
        </ElCard>

        <ElCard class="trend-card" shadow="never">
          <div class="trend-card-body">
            <div class="trend-card-main">
              <h2>单只可转债分时同步</h2>
              <p>仅同步输入可转债代码对应的分时数据，不执行全量可转债同步。</p>
            </div>
            <div class="trend-actions">
              <ElInput
                v-model="bondTrendCode"
                clearable
                placeholder="可转债代码"
                @keyup.enter="runBondTrendSync"
              />
              <ElButton
                :disabled="!bondTrendCode.trim()"
                :loading="loadingMap.bondTrend"
                type="primary"
                @click="runBondTrendSync"
              >
                同步分时
              </ElButton>
            </div>
          </div>
        </ElCard>
      </section>
    </div>
  </Page>
</template>

<style scoped>
.data-sync-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sync-grid,
.trend-sync-section {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
}

.sync-card,
.trend-card {
  border-radius: 8px;
}

.sync-card,
.trend-card {
  background: #1f2937;
  border-color: #374151;
}

.sync-card-body,
.trend-card-body {
  display: flex;
  gap: 16px;
  justify-content: space-between;
}

.sync-card-main,
.trend-card-main {
  min-width: 0;
}

.sync-card-title {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

h2 {
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
  margin: 0;
}

.sync-card h2,
.trend-card h2 {
  color: #fff;
}

p {
  color: #cbd5e1;
  font-size: 13px;
  line-height: 20px;
  margin: 8px 0 0;
}

.trend-card-body {
  align-items: flex-start;
  flex-direction: column;
}

.trend-actions {
  display: flex;
  gap: 10px;
  width: 100%;
}

.trend-actions :deep(.el-input) {
  max-width: 220px;
}

@media (max-width: 640px) {
  .sync-card-body,
  .trend-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .trend-actions :deep(.el-input) {
    max-width: none;
  }
}
</style>
