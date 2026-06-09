<script lang="ts" setup>
import type {
  CategoryTagDistribution,
  KnowledgeOverview,
} from '#/api/knowledge';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { ElCard, ElCol, ElRow, ElTable, ElTableColumn } from 'element-plus';

import { getKnowledgeOverview } from '#/api/knowledge';
import PageHero from '#/components/page-hero/index.vue';

import {
  CATEGORY_TAG_TYPES,
  SCENE_CATEGORY_LABELS,
  TAG_LABELS,
} from './constants';

const overview = ref<KnowledgeOverview>({
  taskCount: 0,
  chunkCount: 0,
  totalTextLength: 0,
  latestCreatedAt: null,
  tagDistributions: [],
});
const loading = ref(false);

const formatNumber = (n: number) => n.toLocaleString();

const formatDate = (d: null | string) => {
  if (!d) return '-';
  return new Date(d).toLocaleString('zh-CN');
};

const formatFileSize = (bytes: number) => {
  if (bytes < 1024) return `${bytes} 字`;
  if (bytes < 1_048_576) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1_048_576).toFixed(1)} MB`;
};

function categoryLabel(key: string) {
  return SCENE_CATEGORY_LABELS[key] ?? key;
}

function tagLabel(key: string) {
  return TAG_LABELS[key] ?? key;
}

const CATEGORY_COLORS: Record<string, string> = {
  danger: '#dc4446',
  info: '#8a929f',
  primary: '#006be6',
  success: '#57d188',
  warning: '#efbd48',
};

function categoryColor(key: string) {
  const type = CATEGORY_TAG_TYPES[key];
  return CATEGORY_COLORS[type ?? ''] ?? CATEGORY_COLORS.info;
}

interface FlatRow {
  categoryKey: string;
  categoryLabel: string;
  tagKey: string;
  tagLabel: string;
  count: number;
  categoryPercentage: number;
  totalPercentage: number;
}

const rawDistributions = ref<CategoryTagDistribution[]>([]);

const sortField = ref<string>('totalPercentage');
const sortDir = ref<'asc' | 'desc'>('desc');

type SortKey = 'categoryPercentage' | 'count' | 'totalPercentage';

function handleSort(field: SortKey) {
  if (sortField.value === field) {
    sortDir.value = sortDir.value === 'desc' ? 'asc' : 'desc';
  } else {
    sortField.value = field;
    sortDir.value = 'desc';
  }
}

const flatRows = computed<FlatRow[]>(() => {
  const key = sortField.value as '' | SortKey;
  const dir = sortDir.value;

  return rawDistributions.value.flatMap((dist) => {
    const tags = [...dist.tags];
    if (key) {
      tags.sort((a, b) => {
        const va = a[key];
        const vb = b[key];
        if (va === vb) {
          return b.totalPercentage - a.totalPercentage;
        }
        return dir === 'desc' ? vb - va : va - vb;
      });
    } else {
      tags.sort((a, b) => b.totalPercentage - a.totalPercentage);
    }
    return tags.map((tag) => ({
      categoryKey: dist.categoryKey,
      categoryLabel: categoryLabel(dist.categoryKey),
      tagKey: tag.tagKey,
      tagLabel: tagLabel(tag.tagKey),
      count: tag.count,
      categoryPercentage: tag.categoryPercentage,
      totalPercentage: tag.totalPercentage,
    }));
  });
});

async function fetchOverview() {
  loading.value = true;
  try {
    const data = await getKnowledgeOverview();
    if (data) {
      overview.value = data;
      rawDistributions.value = data.tagDistributions;
    }
  } finally {
    loading.value = false;
  }
}

function categorySpanMethod({
  row,
  column,
  rowIndex,
}: {
  column: { property: string };
  row: FlatRow;
  rowIndex: number;
}) {
  if (column.property !== 'categoryLabel') {
    return { rowspan: 1, colspan: 1 };
  }
  const data = flatRows.value;
  const cur = row.categoryKey;
  if (rowIndex === 0 || data[rowIndex - 1]?.categoryKey !== cur) {
    let span = 0;
    for (let i = rowIndex; i < data.length; i++) {
      if (data[i]?.categoryKey === cur) span++;
      else break;
    }
    return { rowspan: span, colspan: 1 };
  }
  return { rowspan: 0, colspan: 0 };
}

function sortIndicator(field: string): string {
  if (sortField.value !== field) return '';
  return sortDir.value === 'desc' ? ' ▼' : ' ▲';
}

function sortAriaLabel(field: SortKey, label: string): string {
  if (sortField.value !== field) {
    return `按${label}排序，点击后按降序排列`;
  }
  const currentDir = sortDir.value === 'desc' ? '降序' : '升序';
  const nextDir = sortDir.value === 'desc' ? '升序' : '降序';
  return `当前按${label}${currentDir}排列，点击切换为${nextDir}`;
}

onMounted(() => {
  fetchOverview();
});
</script>

<template>
  <Page>
    <div class="overview-page" v-loading="loading">
      <PageHero
        description="查看知识库来源、分块数量和最近入库情况。"
        title="知识库概览"
      />

      <ElRow :gutter="16" class="stats-row">
        <ElCol :xs="12" :sm="6">
          <ElCard shadow="hover" class="stat-card">
            <div class="stat-value">
              {{ formatNumber(overview.taskCount) }}
            </div>
            <div class="stat-label">文档数</div>
          </ElCard>
        </ElCol>
        <ElCol :xs="12" :sm="6">
          <ElCard shadow="hover" class="stat-card">
            <div class="stat-value">
              {{ formatNumber(overview.chunkCount) }}
            </div>
            <div class="stat-label">知识库条数</div>
          </ElCard>
        </ElCol>
        <ElCol :xs="12" :sm="6">
          <ElCard shadow="hover" class="stat-card">
            <div class="stat-value">
              {{ formatFileSize(overview.totalTextLength) }}
            </div>
            <div class="stat-label">文本量</div>
          </ElCard>
        </ElCol>
        <ElCol :xs="12" :sm="6">
          <ElCard shadow="hover" class="stat-card">
            <div class="stat-value stat-value--small">
              {{ formatDate(overview.latestCreatedAt) }}
            </div>
            <div class="stat-label">最近更新</div>
          </ElCard>
        </ElCol>
      </ElRow>

      <ElCard class="distribution-card">
        <template #header>
          <span>标签分布</span>
        </template>
        <ElTable
          :data="flatRows"
          border
          stripe
          :span-method="categorySpanMethod"
        >
          <ElTableColumn prop="categoryLabel" label="场景类别" width="140" />
          <ElTableColumn prop="tagLabel" label="标签" width="140" />
          <ElTableColumn prop="count" width="110">
            <template #header>
              <button
                :aria-label="sortAriaLabel('count', '条数')"
                class="sort-header"
                type="button"
                @click="handleSort('count')"
              >
                条数<span aria-hidden="true">{{ sortIndicator('count') }}</span>
              </button>
            </template>
            <template #default="{ row }">
              {{ formatNumber(row.count) }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="categoryPercentage" width="110">
            <template #header>
              <button
                :aria-label="sortAriaLabel('categoryPercentage', '同类占比')"
                class="sort-header"
                type="button"
                @click="handleSort('categoryPercentage')"
              >
                同类占比<span aria-hidden="true">{{
                  sortIndicator('categoryPercentage')
                }}</span>
              </button>
            </template>
            <template #default="{ row }">
              {{ row.categoryPercentage }}%
            </template>
          </ElTableColumn>
          <ElTableColumn prop="totalPercentage" width="110">
            <template #header>
              <button
                :aria-label="sortAriaLabel('totalPercentage', '全局占比')"
                class="sort-header"
                type="button"
                @click="handleSort('totalPercentage')"
              >
                全局占比<span aria-hidden="true">{{
                  sortIndicator('totalPercentage')
                }}</span>
              </button>
            </template>
            <template #default="{ row }"> {{ row.totalPercentage }}% </template>
          </ElTableColumn>
          <ElTableColumn label="分布" min-width="200">
            <template #default="{ row }">
              <div class="bar-wrapper">
                <div class="bar-track">
                  <div
                    class="bar-fill"
                    :style="{
                      backgroundColor: categoryColor(row.categoryKey),
                      transform: `scaleX(${Math.max(row.totalPercentage, 0.5) / 100})`,
                    }"
                  ></div>
                </div>
                <span class="bar-label">{{ row.totalPercentage }}%</span>
              </div>
            </template>
          </ElTableColumn>
        </ElTable>
      </ElCard>
    </div>
  </Page>
</template>

<style scoped>
.overview-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stats-row {
  margin-bottom: 0;
}

.stat-card {
  padding: 8px 0;
  text-align: center;
}

.stat-value {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--el-color-primary);
}

.stat-value--small {
  font-size: 14px;
  font-weight: 500;
}

.stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.distribution-card {
  flex: 1;
}

.bar-wrapper {
  display: flex;
  gap: 8px;
  align-items: center;
}

.bar-track {
  flex: 1;
  height: 8px;
  overflow: hidden;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.bar-fill {
  width: 100%;
  height: 100%;
  border-radius: 4px;
  transform-origin: left center;
  transition: transform 0.3s ease;
}

.bar-label {
  min-width: 48px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: right;
}

.sort-header {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0;
  font: inherit;
  color: inherit;
  cursor: pointer;
  user-select: none;
  background: transparent;
  border: 0;
}

.sort-header:hover {
  color: var(--el-color-primary);
}

.sort-header:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
  .bar-fill {
    transition: none;
  }
}
</style>
