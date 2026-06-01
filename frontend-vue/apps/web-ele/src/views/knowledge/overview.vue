<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElCard,
  ElCol,
  ElRow,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getKnowledgeOverview } from '#/api/knowledge';
import type { KnowledgeOverview } from '#/api/knowledge';
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

const formatDate = (d: string | null) => {
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
  primary: '#409eff',
  success: '#67c23a',
  warning: '#e6a23c',
  danger: '#f56c6c',
  info: '#909399',
};

function categoryColor(key: string) {
  const type = CATEGORY_TAG_TYPES[key];
  return CATEGORY_COLORS[type ?? ''] ?? '#c0c4cc';
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

const flatRows = ref<FlatRow[]>([]);

async function fetchOverview() {
  loading.value = true;
  try {
    const data = await getKnowledgeOverview();
    if (data) {
      overview.value = data;
      flatRows.value = data.tagDistributions.flatMap((dist) =>
        dist.tags.map((tag) => ({
          categoryKey: dist.categoryKey,
          categoryLabel: categoryLabel(dist.categoryKey),
          tagKey: tag.tagKey,
          tagLabel: tagLabel(tag.tagKey),
          count: tag.count,
          categoryPercentage: tag.categoryPercentage,
          totalPercentage: tag.totalPercentage,
        })),
      );
    }
  } finally {
    loading.value = false;
  }
}

function categorySpanMethod({ row, column, rowIndex }: {
  row: FlatRow;
  column: { property: string };
  rowIndex: number;
}) {
  if (column.property !== 'categoryLabel') {
    return { rowspan: 1, colspan: 1 };
  }
  const data = flatRows.value;
  const cur = row.categoryKey;
  if (rowIndex === 0 || data[rowIndex - 1]!.categoryKey !== cur) {
    let span = 0;
    for (let i = rowIndex; i < data.length; i++) {
      if (data[i]!.categoryKey === cur) span++;
      else break;
    }
    return { rowspan: span, colspan: 1 };
  }
  return { rowspan: 0, colspan: 0 };
}

onMounted(() => {
  fetchOverview();
});
</script>

<template>
  <Page title="知识库概览">
    <div class="overview-page" v-loading="loading">
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
          <ElTableColumn
            prop="categoryLabel"
            label="场景类别"
            width="140"
          />
          <ElTableColumn prop="tagLabel" label="标签" width="140" />
          <ElTableColumn prop="count" label="条数" width="100" sortable>
            <template #default="{ row }">
              {{ formatNumber(row.count) }}
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="categoryPercentage"
            label="同类占比"
            width="100"
            sortable
          >
            <template #default="{ row }">
              {{ row.categoryPercentage }}%
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="totalPercentage"
            label="全局占比"
            width="100"
            sortable
          >
            <template #default="{ row }">
              {{ row.totalPercentage }}%
            </template>
          </ElTableColumn>
          <ElTableColumn label="分布" min-width="200">
            <template #default="{ row }">
              <div class="bar-wrapper">
                <div class="bar-track">
                  <div
                    class="bar-fill"
                    :style="{
                      width: `${Math.max(row.totalPercentage, 0.5)}%`,
                      backgroundColor: categoryColor(row.categoryKey),
                    }"
                  />
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
  text-align: center;
  padding: 8px 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--el-color-primary);
  line-height: 1.3;
  min-height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
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
  align-items: center;
  gap: 8px;
}

.bar-track {
  flex: 1;
  height: 8px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s ease;
}

.bar-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  min-width: 48px;
  text-align: right;
}
</style>
