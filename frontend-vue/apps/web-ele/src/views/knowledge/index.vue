<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElCard,
  ElCol,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElInput,
  ElPagination,
  ElRow,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  getKnowledgeChunks,
  getKnowledgeStats,
  updateKnowledgeChunk,
  type KnowledgeChunk,
  type KnowledgeStats,
} from '#/api/knowledge';

const stats = ref<KnowledgeStats>({
  chunkCount: 0,
  latestCreatedAt: null,
  taskCount: 0,
  totalTextLength: 0,
});

const chunks = ref<KnowledgeChunk[]>([]);
const selectedChunk = ref<KnowledgeChunk | null>(null);
const loading = ref(false);
const pageNum = ref(1);
const pageSize = ref(20);
const total = ref(0);

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

async function fetchStats() {
  const data = await getKnowledgeStats();
  if (data) stats.value = data;
}

async function fetchChunks() {
  loading.value = true;
  try {
    const data = await getKnowledgeChunks(pageNum.value, pageSize.value);
    if (data) {
      chunks.value = data.records;
      total.value = data.total;
    }
  } finally {
    loading.value = false;
  }
}

function selectChunk(chunk: KnowledgeChunk | undefined) {
  if (!chunk) return;
  editing.value = false;
  editText.value = '';
  selectedChunk.value = chunk;
}

async function onPageChange(pn: number) {
  pageNum.value = pn;
  await fetchChunks();
}

async function onPageSizeChange(ps: number) {
  pageSize.value = ps;
  pageNum.value = 1;
  await fetchChunks();
}

function textPreview(text: string, maxLen = 60) {
  if (!text) return '';
  const cleaned = text.replace(/\s+/g, ' ');
  return cleaned.length > maxLen ? `${cleaned.slice(0, maxLen)}...` : cleaned;
}

const editing = ref(false);
const editText = ref('');
const saving = ref(false);

function startEdit() {
  if (!selectedChunk.value) return;
  editText.value = selectedChunk.value.text;
  editing.value = true;
}

function cancelEdit() {
  editing.value = false;
  editText.value = '';
}

async function saveEdit() {
  if (!selectedChunk.value || saving.value) return;
  saving.value = true;
  try {
    const updated = await updateKnowledgeChunk(
      selectedChunk.value.id,
      editText.value,
    );
    if (updated) {
      selectedChunk.value = updated;
      const idx = chunks.value.findIndex((c) => c.id === updated.id);
      if (idx !== -1) chunks.value[idx] = updated;
    }
    editing.value = false;
    editText.value = '';
  } finally {
    saving.value = false;
  }
}

onMounted(async () => {
  await Promise.all([fetchStats(), fetchChunks()]);
});
</script>

<template>
  <Page title="知识库">
    <div class="knowledge-page">
      <ElRow :gutter="16" class="stats-row">
        <ElCol :xs="12" :sm="6">
          <ElCard shadow="hover" class="stat-card">
            <div class="stat-value">{{ formatNumber(stats.taskCount) }}</div>
            <div class="stat-label">文档数</div>
          </ElCard>
        </ElCol>
        <ElCol :xs="12" :sm="6">
          <ElCard shadow="hover" class="stat-card">
            <div class="stat-value">{{ formatNumber(stats.chunkCount) }}</div>
            <div class="stat-label">知识条目</div>
          </ElCard>
        </ElCol>
        <ElCol :xs="12" :sm="6">
          <ElCard shadow="hover" class="stat-card">
            <div class="stat-value">
              {{ formatFileSize(stats.totalTextLength) }}
            </div>
            <div class="stat-label">总文本量</div>
          </ElCard>
        </ElCol>
        <ElCol :xs="12" :sm="6">
          <ElCard shadow="hover" class="stat-card">
            <div class="stat-value stat-value--small">
              {{ formatDate(stats.latestCreatedAt) }}
            </div>
            <div class="stat-label">最近更新</div>
          </ElCard>
        </ElCol>
      </ElRow>

      <ElRow :gutter="16" class="content-row">
        <ElCol :span="6">
          <ElCard header="知识条目" class="chunk-list-card">
            <div v-loading="loading">
              <ElTable
                :data="chunks"
                highlight-current-row
                stripe
                style="width: 100%"
                @current-change="selectChunk"
              >
                <ElTableColumn label="文档" min-width="140" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="task-no">{{ row.taskNo }}</span>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="页码" width="80" align="center">
                  <template #default="{ row }">
                    <ElTag v-if="row.pageNos?.length" size="small" type="info">
                      {{ row.pageNos.join(',') }}
                    </ElTag>
                    <span v-else>-</span>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="内容" min-width="160" show-overflow-tooltip>
                  <template #default="{ row }">
                    {{ textPreview(row.text) }}
                  </template>
                </ElTableColumn>
              </ElTable>
              <div class="pagination-wrapper">
                <ElPagination
                  v-model:current-page="pageNum"
                  v-model:page-size="pageSize"
                  :page-sizes="[10, 20, 50, 100]"
                  :total="total"
                  layout="total, sizes, prev, pager, next"
                  small
                  @current-change="onPageChange"
                  @size-change="onPageSizeChange"
                />
              </div>
            </div>
          </ElCard>
        </ElCol>

        <ElCol :span="18">
          <ElCard v-if="selectedChunk" class="detail-card">
            <template #header>
              <div class="detail-header">
                <span>{{ selectedChunk.taskNo }}</span>
                <span class="detail-header-sep">/</span>
                <span>片段 #{{ selectedChunk.chunkIndex }}</span>
                <ElTag
                  v-if="selectedChunk.avgConfidence != null"
                  size="small"
                  :type="selectedChunk.avgConfidence >= 0.8 ? 'success' : 'warning'"
                  class="confidence-tag"
                >
                  置信度 {{ (selectedChunk.avgConfidence * 100).toFixed(0) }}%
                </ElTag>
                <div class="header-actions">
                  <template v-if="!editing">
                    <ElButton size="small" type="primary" @click="startEdit">
                      编辑
                    </ElButton>
                  </template>
                  <template v-else>
                    <ElButton
                      size="small"
                      type="success"
                      :loading="saving"
                      @click="saveEdit"
                    >
                      保存
                    </ElButton>
                    <ElButton size="small" @click="cancelEdit">
                      取消
                    </ElButton>
                  </template>
                </div>
              </div>
            </template>

            <ElInput
              v-if="editing"
              v-model="editText"
              type="textarea"
              :rows="20"
              class="chunk-editor"
            />
            <div v-else class="chunk-text">{{ selectedChunk.text }}</div>

            <ElDescriptions :column="2" border size="small" class="chunk-meta">
              <ElDescriptionsItem label="文档编号">
                {{ selectedChunk.taskNo }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="片段序号">
                {{ selectedChunk.chunkIndex }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="来源页码">
                {{ selectedChunk.pageNos?.length ? selectedChunk.pageNos.join(', ') : '-' }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="来源段落">
                {{ selectedChunk.paragraphNos?.length ? selectedChunk.paragraphNos.join(', ') : '-' }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="置信度">
                {{ selectedChunk.avgConfidence != null ? (selectedChunk.avgConfidence * 100).toFixed(1) + '%' : '-' }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="版本">
                {{ selectedChunk.version ?? '-' }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="入库时间">
                {{ formatDate(selectedChunk.createdAt) }}
              </ElDescriptionsItem>
            </ElDescriptions>
          </ElCard>

          <ElCard v-else class="detail-card empty-detail">
            <ElEmpty description="请选择左侧条目查看详情" />
          </ElCard>
        </ElCol>
      </ElRow>
    </div>
  </Page>
</template>

<style scoped>
.knowledge-page {
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

.content-row {
  flex: 1;
  min-height: 0;
}

.chunk-list-card {
  height: 100%;
}

.chunk-list-card :deep(.el-card__body) {
  padding: 0;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding: 12px 0;
}

.task-no {
  font-family: monospace;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.detail-card {
  height: 100%;
}

.detail-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  flex: 1;
  overflow: hidden;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.detail-header-sep {
  color: var(--el-text-color-placeholder);
}

.confidence-tag {
  margin-left: 8px;
}

.header-actions {
  margin-left: auto;
  display: flex;
  gap: 6px;
}

.chunk-editor {
  margin-bottom: 16px;
  flex: 1;
}

.chunk-editor :deep(.el-textarea__inner) {
  height: 100%;
}

.chunk-text {
  padding: 16px;
  margin-bottom: 16px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  font-size: 15px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  flex: 1;
  overflow-y: auto;
  min-height: 300px;
}

.chunk-meta {
  margin-top: 0;
}

.empty-detail {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}
</style>
