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
  ElOption,
  ElPagination,
  ElRow,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  getKnowledgeChunks,
  getKnowledgeStats,
  updateKnowledgeChunk,
} from '#/api/knowledge';
import type {
  KnowledgeChunk,
  KnowledgeChunkUpdateParam,
  KnowledgeStats,
  ScenesData,
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
const searchFilename = ref('');

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
    const data = await getKnowledgeChunks(
      pageNum.value,
      pageSize.value,
      searchFilename.value || undefined,
    );
    if (data) {
      chunks.value = data.records;
      total.value = data.total;
    }
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  pageNum.value = 1;
  void fetchChunks();
}

function handleClearSearch() {
  searchFilename.value = '';
  pageNum.value = 1;
  void fetchChunks();
}

function selectChunk(chunk: KnowledgeChunk | undefined) {
  if (!chunk) return;
  editing.value = false;
  editText.value = '';
  editingTags.value = false;
  draftScenes.value = null;
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

function chunkDocumentName(chunk: KnowledgeChunk) {
  return chunk.originalFilename || chunk.taskNo;
}

function chunkNoLabel(chunk: KnowledgeChunk) {
  return `Chunk #${chunk.chunkIndex}`;
}

function sourcePageLabel(chunk: KnowledgeChunk) {
  return chunk.pageNos?.length
    ? `来源页 ${chunk.pageNos.join(', ')}`
    : '来源页 -';
}

const SCENE_CATEGORY_LABELS: Record<string, string> = {
  asset: '资产类型',
  price: '价格位置',
  volume: '成交量/换手',
  trend: '趋势结构',
  valuation: '估值/基本面',
  sentiment: '情绪/异动',
  risk_strategy: '风险/策略',
};

const TAG_LABELS: Record<string, string> = {
  general: '通用',
  stock: '股票',
  index: '指数',
  convertible_bond: '可转债',
  fund: '基金',
  bank_stock: '银行股',
  low_price_stock: '低价股',
  large_cap_stock: '大盘股',
  small_cap_stock: '小盘股',
  price_rise: '上涨',
  price_drop: '下跌',
  sideways: '横盘',
  near_recent_high: '接近高位',
  near_recent_low: '接近低位',
  breakout: '突破',
  pullback: '回调',
  gap_up: '跳空高开',
  gap_down: '跳空低开',
  volume_expand: '放量',
  volume_shrink: '缩量',
  high_turnover: '高换手',
  low_turnover: '低换手',
  volume_price_confirm: '量价配合',
  volume_price_divergence: '量价背离',
  volume_spike: '成交量突增',
  volume_dry_up: '成交枯竭',
  uptrend: '上升趋势',
  downtrend: '下降趋势',
  range_bound: '区间震荡',
  rebound: '反弹',
  trend_reversal: '趋势反转',
  breakout_from_range: '横盘突破',
  failed_breakout: '突破失败',
  low_pe: '低PE',
  high_pe: '高PE',
  low_pb: '低PB',
  high_pb: '高PB',
  high_dividend: '高股息',
  valuation_repair: '估值修复',
  valuation_trap: '低估值陷阱',
  fundamental_risk: '基本面风险',
  market_attention_rise: '关注度上升',
  short_term_emotion: '短线情绪',
  panic_selling: '恐慌抛售',
  news_driven: '消息驱动',
  policy_driven: '政策驱动',
  sector_rotation: '板块轮动',
  weak_sentiment: '情绪偏弱',
  herding_effect: '羊群效应',
  institutional_behavior: '机构行为',
  chase_high_risk: '追高风险',
  false_breakout_risk: '假突破风险',
  liquidity_risk: '流动性风险',
  drawdown_risk: '回撤风险',
  valuation_trap_risk: '估值陷阱风险',
  overheated_risk: '过热风险',
  risk_control: '风险控制',
  position_control: '仓位控制',
  wait_confirm: '等待确认',
  observe_next_day: '观察次日',
  avoid_emotional_trade: '避免情绪交易',
  take_profit_plan: '止盈计划',
  stop_loss_plan: '止损计划',
};

const CATEGORY_TAG_TYPES: Record<string, 'danger' | 'info' | 'primary' | 'success' | 'warning'> = {
  asset: 'primary',
  price: 'success',
  volume: 'warning',
  valuation: 'info',
  sentiment: 'danger',
  risk_strategy: 'danger',
};

const VALID_TAGS_BY_CATEGORY: Record<string, string[]> = {
  asset: ['general', 'stock', 'index', 'convertible_bond', 'fund', 'bank_stock', 'low_price_stock', 'large_cap_stock', 'small_cap_stock'],
  price: ['price_rise', 'price_drop', 'sideways', 'near_recent_high', 'near_recent_low', 'breakout', 'pullback', 'gap_up', 'gap_down'],
  volume: ['volume_expand', 'volume_shrink', 'high_turnover', 'low_turnover', 'volume_price_confirm', 'volume_price_divergence', 'volume_spike', 'volume_dry_up'],
  trend: ['uptrend', 'downtrend', 'range_bound', 'rebound', 'trend_reversal', 'breakout_from_range', 'failed_breakout'],
  valuation: ['low_pe', 'high_pe', 'low_pb', 'high_pb', 'high_dividend', 'valuation_repair', 'valuation_trap', 'fundamental_risk'],
  sentiment: ['market_attention_rise', 'short_term_emotion', 'panic_selling', 'news_driven', 'policy_driven', 'sector_rotation', 'weak_sentiment', 'herding_effect', 'institutional_behavior'],
  risk_strategy: ['chase_high_risk', 'false_breakout_risk', 'liquidity_risk', 'drawdown_risk', 'valuation_trap_risk', 'overheated_risk', 'risk_control', 'position_control', 'wait_confirm', 'observe_next_day', 'avoid_emotional_trade', 'take_profit_plan', 'stop_loss_plan'],
};

function emptyScenes(): ScenesData {
  return {
    asset: [], price: [], volume: [], trend: [],
    valuation: [], sentiment: [], risk_strategy: [],
  };
}

function currentScenes(chunk: KnowledgeChunk): ScenesData | null {
  const scenes = chunk.metadata?.scenes;
  if (!scenes || typeof scenes !== 'object') return null;
  const hasAny = Object.values(scenes).some(
    (v) => Array.isArray(v) && v.length > 0,
  );
  return hasAny ? (scenes as ScenesData) : null;
}

function categoryLabel(category: string) {
  return SCENE_CATEGORY_LABELS[category] ?? category;
}

function tagLabel(tag: string) {
  return TAG_LABELS[tag] ?? tag;
}

function categoryTagType(category: string) {
  return CATEGORY_TAG_TYPES[category];
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
    const reembed = editText.value !== selectedChunk.value.text;
    const param: KnowledgeChunkUpdateParam = {
      text: editText.value,
      reembed,
    };
    const updated = await updateKnowledgeChunk(
      selectedChunk.value.id,
      param,
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

// ---- 标签编辑 ----
const editingTags = ref(false);
const draftScenes = ref<ScenesData | null>(null);
const savingTags = ref(false);

function startEditTags() {
  if (!selectedChunk.value) return;
  const scenes = currentScenes(selectedChunk.value);
  draftScenes.value = scenes
    ? JSON.parse(JSON.stringify(scenes))
    : emptyScenes();
  editingTags.value = true;
}

function cancelEditTags() {
  editingTags.value = false;
  draftScenes.value = null;
}

function removeTag(category: string, tag: string) {
  if (!draftScenes.value) return;
  const arr = draftScenes.value[category];
  if (arr) {
    draftScenes.value[category] = arr.filter((t) => t !== tag);
  }
}

function addTag(category: string, tag: string) {
  if (!draftScenes.value || !tag) return;
  const arr = draftScenes.value[category];
  if (arr && !arr.includes(tag)) {
    arr.push(tag);
  }
}

function availableTags(category: string): string[] {
  if (!draftScenes.value) return [];
  const existing = draftScenes.value[category] ?? [];
  return (VALID_TAGS_BY_CATEGORY[category] ?? []).filter(
    (t) => !existing.includes(t),
  );
}

async function saveEditTags() {
  if (!selectedChunk.value || savingTags.value || !draftScenes.value) return;
  savingTags.value = true;
  try {
    const param: KnowledgeChunkUpdateParam = {
      scenes: draftScenes.value,
      reembed: false,
    };
    const updated = await updateKnowledgeChunk(
      selectedChunk.value.id,
      param,
    );
    if (updated) {
      selectedChunk.value = updated;
      const idx = chunks.value.findIndex((c) => c.id === updated.id);
      if (idx !== -1) chunks.value[idx] = updated;
    }
    editingTags.value = false;
    draftScenes.value = null;
  } finally {
    savingTags.value = false;
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
        <ElCol :xs="24" :lg="10">
          <ElCard class="chunk-list-card">
            <template #header>
              <div class="chunk-list-header">
                <span>知识条目</span>
                <ElInput
                  v-model="searchFilename"
                  placeholder="搜索文档名称..."
                  clearable
                  size="small"
                  style="width: 200px"
                  @clear="handleClearSearch"
                  @keyup.enter="handleSearch"
                />
              </div>
            </template>
            <div v-loading="loading">
              <ElTable
                :data="chunks"
                highlight-current-row
                stripe
                style="width: 100%"
                @current-change="selectChunk"
              >
                <ElTableColumn label="文档片段" min-width="260">
                  <template #default="{ row }">
                    <div class="chunk-doc-cell">
                      <div class="chunk-title-line">
                        <span class="file-name" :title="chunkDocumentName(row)">
                          {{ chunkDocumentName(row) }}
                        </span>
                        <ElTag size="small" type="primary">
                          {{ chunkNoLabel(row) }}
                        </ElTag>
                      </div>
                      <div class="chunk-submeta">
                        <span class="task-no">{{ row.taskNo }}</span>
                        <span>{{ sourcePageLabel(row) }}</span>
                      </div>
                    </div>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="内容" min-width="260">
                  <template #default="{ row }">
                    <div class="chunk-preview">
                      {{ textPreview(row.text, 90) }}
                    </div>
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

        <ElCol :xs="24" :lg="14">
          <ElCard v-if="selectedChunk" class="detail-card">
            <template #header>
              <div class="detail-header">
                <span
                  class="detail-title"
                  :title="chunkDocumentName(selectedChunk)"
                >
                  {{ chunkDocumentName(selectedChunk) }}
                </span>
                <span class="detail-header-sep">/</span>
                <span>{{ chunkNoLabel(selectedChunk) }}</span>
                <ElTag
                  v-if="selectedChunk.avgConfidence != null"
                  size="small"
                  :type="
                    selectedChunk.avgConfidence >= 0.8 ? 'success' : 'warning'
                  "
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
                    <ElButton size="small" @click="cancelEdit"> 取消 </ElButton>
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

            <div v-if="currentScenes(selectedChunk) || editingTags" class="scenes-section">
              <div class="scenes-header">
                <span class="scenes-title">场景标签</span>
                <template v-if="!editingTags">
                  <ElButton size="small" type="primary" plain @click="startEditTags">
                    编辑标签
                  </ElButton>
                </template>
                <template v-else>
                  <div class="scenes-header-actions">
                    <ElButton
                      size="small"
                      type="success"
                      :loading="savingTags"
                      @click="saveEditTags"
                    >
                      保存
                    </ElButton>
                    <ElButton size="small" @click="cancelEditTags"> 取消 </ElButton>
                  </div>
                </template>
              </div>

              <!-- View mode -->
              <template v-if="!editingTags">
                <div
                  v-for="category in Object.keys(SCENE_CATEGORY_LABELS)"
                  :key="category"
                  class="scene-category"
                >
                  <template v-if="currentScenes(selectedChunk)?.[category]?.length">
                    <span class="scene-category-label">
                      {{ categoryLabel(category) }}
                    </span>
                    <ElTag
                      v-for="tag in currentScenes(selectedChunk)?.[category]"
                      :key="tag"
                      size="small"
                      :type="categoryTagType(category)"
                      effect="plain"
                      class="scene-tag"
                    >
                      {{ tagLabel(tag) }}
                    </ElTag>
                  </template>
                </div>
              </template>

              <!-- Edit mode -->
              <template v-else>
                <div
                  v-for="category in Object.keys(VALID_TAGS_BY_CATEGORY)"
                  :key="category"
                  class="scene-category scene-category--edit"
                >
                  <span class="scene-category-label">
                    {{ categoryLabel(category) }}
                  </span>
                  <div class="scene-tags-row">
                    <ElTag
                      v-for="tag in draftScenes?.[category]"
                      :key="tag"
                      size="small"
                      :type="categoryTagType(category)"
                      effect="plain"
                      closable
                      class="scene-tag"
                      @close="removeTag(category, tag)"
                    >
                      {{ tagLabel(tag) }}
                    </ElTag>
                    <ElSelect
                      v-if="availableTags(category).length > 0"
                      :model-value="''"
                      size="small"
                      placeholder="添加标签"
                      class="scene-tag-select"
                      @change="(val: string) => addTag(category, val)"
                    >
                      <ElOption
                        v-for="tag in availableTags(category)"
                        :key="tag"
                        :label="tagLabel(tag)"
                        :value="tag"
                      />
                    </ElSelect>
                  </div>
                </div>
              </template>
            </div>

            <ElDescriptions :column="2" border size="small" class="chunk-meta">
              <ElDescriptionsItem label="上传文件名">
                {{ selectedChunk.originalFilename || '-' }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="任务编号">
                {{ selectedChunk.taskNo }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="片段序号">
                {{ chunkNoLabel(selectedChunk) }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="来源页码">
                {{
                  selectedChunk.pageNos?.length
                    ? selectedChunk.pageNos.join(', ')
                    : '-'
                }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="来源段落">
                {{
                  selectedChunk.paragraphNos?.length
                    ? selectedChunk.paragraphNos.join(', ')
                    : '-'
                }}
              </ElDescriptionsItem>
              <ElDescriptionsItem label="置信度">
                {{
                  selectedChunk.avgConfidence != null
                    ? (selectedChunk.avgConfidence * 100).toFixed(1) + '%'
                    : '-'
                }}
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
  row-gap: 16px;
}

.chunk-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
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

.chunk-list-card :deep(.el-table__row) {
  cursor: pointer;
}

.chunk-doc-cell {
  min-width: 0;
  padding: 4px 0;
}

.chunk-title-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.file-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chunk-title-line :deep(.el-tag) {
  flex: none;
}

.chunk-submeta {
  display: flex;
  gap: 10px;
  margin-top: 4px;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.task-no {
  font-family: monospace;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.chunk-preview {
  color: var(--el-text-color-regular);
  line-height: 1.5;
  word-break: break-word;
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
  min-width: 0;
}

.detail-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.scenes-section {
  padding: 12px 16px;
  margin-bottom: 16px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}

.scenes-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.scenes-title {
  margin-bottom: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.scenes-header-actions {
  display: flex;
  gap: 6px;
}

.scene-category {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.scene-category:last-child {
  margin-bottom: 0;
}

.scene-category--edit {
  align-items: flex-start;
}

.scene-category-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  min-width: 80px;
  flex: none;
  line-height: 24px;
}

.scene-tags-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  flex: 1;
}

.scene-tag {
  margin: 0;
}

.scene-tag-select {
  width: 140px;
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
