<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElInput,
  ElMessage,
  ElOption,
  ElPagination,
  ElPopconfirm,
  ElProgress,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  createManualKnowledgeDraft,
  deleteManualKnowledgeTask,
  getManualKnowledgeTask,
  pageManualKnowledgeTasks,
  saveManualKnowledgeDraft,
  submitManualKnowledgeTask,
} from '#/api/manual-knowledge';
import type { OcrTask, OcrTaskStatus } from '#/api/ocr-task';

type ManualTask = {
  chunks: number;
  currentStage: string;
  id: string;
  progress: number;
  status: OcrTaskStatus;
  title: string;
  updatedAt: string;
};

const loadingTasks = ref(false);
const saving = ref(false);
const submitting = ref(false);
const deletingTaskNo = ref('');
const dialogVisible = ref(false);
const dialogReadonly = ref(false);
const editingTaskNo = ref('');
const title = ref('');
const chunks = ref<string[]>(['']);
const tasks = ref<ManualTask[]>([]);
const statusFilter = ref<OcrTaskStatus | 'all'>('all');
const pageNum = ref(1);
const pageSize = ref(20);
const totalTasks = ref(0);

const statusFilterOptions: Array<{
  label: string;
  value: OcrTaskStatus | 'all';
}> = [
  { label: '全部状态', value: 'all' },
  { label: '草稿待提交', value: 'manual_review_required' },
  { label: '处理中', value: 'running' },
  { label: '已完成', value: 'finished' },
  { label: '失败', value: 'failed' },
];

const summary = computed(() => {
  return {
    draft: tasks.value.filter(
      (item) => item.status === 'manual_review_required',
    ).length,
    finished: tasks.value.filter((item) => item.status === 'finished').length,
    running: tasks.value.filter((item) => item.status === 'running').length,
    totalChunks: tasks.value.reduce((total, item) => total + item.chunks, 0),
  };
});

const validChunkCount = computed(() => {
  return chunks.value.map((item) => item.trim()).filter(Boolean).length;
});

const dialogTitle = computed(() => {
  if (!editingTaskNo.value) {
    return '新增手动知识';
  }
  return dialogReadonly.value ? '查看手动知识' : '编辑手动知识';
});

onMounted(() => {
  void loadTasks();
});

async function loadTasks() {
  loadingTasks.value = true;
  try {
    const page = await pageManualKnowledgeTasks({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      status: statusFilter.value === 'all' ? undefined : statusFilter.value,
    });
    tasks.value = page.records.map(toManualTask);
    totalTasks.value = page.total;
  } finally {
    loadingTasks.value = false;
  }
}

function openCreate() {
  editingTaskNo.value = '';
  title.value = '';
  chunks.value = [''];
  dialogReadonly.value = false;
  dialogVisible.value = true;
}

async function openEditor(row: ManualTask) {
  editingTaskNo.value = row.id;
  title.value = row.title;
  dialogReadonly.value = row.status !== 'manual_review_required';
  const detail = await getManualKnowledgeTask(row.id);
  chunks.value =
    detail.draftContent.paragraphs.length > 0
      ? detail.draftContent.paragraphs.map((item) => item.text)
      : [''];
  dialogVisible.value = true;
}

async function saveDraft() {
  const draft = currentDraft();
  if (!draft) {
    return;
  }
  saving.value = true;
  try {
    if (editingTaskNo.value) {
      await saveManualKnowledgeDraft(editingTaskNo.value, draft);
    } else {
      const task = await createManualKnowledgeDraft(draft);
      editingTaskNo.value = task.taskNo;
    }
    ElMessage.success('草稿已保存');
    await loadTasks();
  } finally {
    saving.value = false;
  }
}

async function submitDraft() {
  const draft = currentDraft();
  if (!draft) {
    return;
  }
  submitting.value = true;
  try {
    let taskNo = editingTaskNo.value;
    if (!taskNo) {
      const task = await createManualKnowledgeDraft(draft);
      taskNo = task.taskNo;
      editingTaskNo.value = taskNo;
    }
    await submitManualKnowledgeTask(taskNo, draft);
    ElMessage.success('已提交，开始打标入库');
    dialogVisible.value = false;
    await loadTasks();
  } finally {
    submitting.value = false;
  }
}

async function removeTask(row: ManualTask) {
  deletingTaskNo.value = row.id;
  try {
    await deleteManualKnowledgeTask(row.id);
    ElMessage.success('已删除任务');
    if (tasks.value.length === 1 && pageNum.value > 1) {
      pageNum.value -= 1;
    }
    await loadTasks();
  } finally {
    deletingTaskNo.value = '';
  }
}

function currentDraft() {
  const normalizedChunks = chunks.value.map((item) => item.trim()).filter(Boolean);
  if (normalizedChunks.length === 0) {
    ElMessage.warning('至少需要一个非空文本分段');
    return undefined;
  }
  return {
    chunks: normalizedChunks,
    title: title.value.trim() || undefined,
  };
}

function addChunk() {
  chunks.value.push('');
}

function removeChunk(index: number) {
  chunks.value.splice(index, 1);
  if (chunks.value.length === 0) {
    chunks.value.push('');
  }
}

function changeStatusFilter() {
  pageNum.value = 1;
  void loadTasks();
}

function changePageNum(value: number) {
  pageNum.value = value;
  void loadTasks();
}

function changePageSize(value: number) {
  pageNum.value = 1;
  pageSize.value = value;
  void loadTasks();
}

function toManualTask(task: OcrTask): ManualTask {
  return {
    chunks: task.segmentCount,
    currentStage: task.currentStage,
    id: task.taskNo,
    progress: task.progress,
    status: task.status,
    title: task.originalFilename,
    updatedAt: formatDateTime(task.updatedAt ?? task.submittedAt),
  };
}

function statusLabel(status: OcrTaskStatus) {
  const labels: Record<OcrTaskStatus, string> = {
    failed: '失败',
    finished: '已完成',
    manual_review_required: '草稿待提交',
    ready: '等待中',
    running: '处理中',
  };
  return labels[status] ?? status;
}

function statusType(status: OcrTaskStatus) {
  if (status === 'finished') {
    return 'success';
  }
  if (status === 'failed') {
    return 'danger';
  }
  if (status === 'manual_review_required') {
    return 'warning';
  }
  return 'primary';
}

function stageLabel(stage: string) {
  if (stage === 'quality.validate') {
    return '草稿编辑';
  }
  if (stage?.startsWith('chunk.tag')) {
    return '场景打标';
  }
  if (stage === 'embedding.index') {
    return '向量入库';
  }
  return stage || '-';
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 19);
}
</script>

<template>
  <Page title="手动知识导入">
    <div class="manual-page">
      <section class="toolbar-band">
        <div>
          <h2>手动文本入库</h2>
          <span>按 chunk 手动录入文本，提交后复用现有场景打标和向量入库流程</span>
        </div>
        <div class="toolbar-actions">
          <ElSelect
            v-model="statusFilter"
            class="status-filter"
            @change="changeStatusFilter"
          >
            <ElOption
              v-for="item in statusFilterOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
          <ElButton :loading="loadingTasks" @click="loadTasks">刷新</ElButton>
          <ElButton type="primary" @click="openCreate">新增</ElButton>
        </div>
      </section>

      <section class="summary-band">
        <div class="metric-item">
          <span>草稿</span>
          <strong>{{ summary.draft }}</strong>
        </div>
        <div class="metric-item">
          <span>处理中</span>
          <strong>{{ summary.running }}</strong>
        </div>
        <div class="metric-item">
          <span>已完成</span>
          <strong>{{ summary.finished }}</strong>
        </div>
        <div class="metric-item">
          <span>当前页 chunk</span>
          <strong>{{ summary.totalChunks }}</strong>
        </div>
      </section>

      <section class="table-band">
        <ElTable
          v-loading="loadingTasks"
          :data="tasks"
          border
          height="560"
          row-key="id"
        >
          <ElTableColumn label="任务编号" min-width="260" prop="id" />
          <ElTableColumn label="标题" min-width="220" prop="title" />
          <ElTableColumn label="状态" width="130">
            <template #default="{ row }: { row: ManualTask }">
              <ElTag :type="statusType(row.status)">
                {{ statusLabel(row.status) }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="当前阶段" min-width="130">
            <template #default="{ row }: { row: ManualTask }">
              {{ stageLabel(row.currentStage) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="进度" min-width="160">
            <template #default="{ row }: { row: ManualTask }">
              <ElProgress :percentage="row.progress" :stroke-width="8" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="Chunk数" prop="chunks" width="100" />
          <ElTableColumn label="更新时间" prop="updatedAt" width="180" />
          <ElTableColumn fixed="right" label="操作" width="210">
            <template #default="{ row }: { row: ManualTask }">
              <ElButton link type="primary" @click="openEditor(row)">
                {{ row.status === 'manual_review_required' ? '编辑' : '查看' }}
              </ElButton>
              <ElPopconfirm title="确认删除该手动任务？" @confirm="removeTask(row)">
                <template #reference>
                  <ElButton
                    :loading="deletingTaskNo === row.id"
                    link
                    type="danger"
                  >
                    删除
                  </ElButton>
                </template>
              </ElPopconfirm>
            </template>
          </ElTableColumn>
        </ElTable>
        <ElEmpty v-if="!loadingTasks && tasks.length === 0" description="暂无手动任务" />
        <div class="pagination-row">
          <ElPagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="totalTasks"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="changePageNum"
            @size-change="changePageSize"
          />
        </div>
      </section>

      <ElDialog
        v-model="dialogVisible"
        :title="dialogTitle"
        class="manual-dialog"
        width="980px"
      >
        <div class="editor-form">
          <label>
            <span>标题</span>
            <ElInput
              v-model="title"
              :disabled="dialogReadonly"
              maxlength="120"
              placeholder="不填则使用第一个 chunk 的前 5 个字"
              show-word-limit
            />
          </label>
          <div class="editor-header">
            <span>Chunk 列表</span>
            <span>有效 {{ validChunkCount }} 条</span>
          </div>
          <div class="chunk-list">
            <div v-for="(_, index) in chunks" :key="index" class="chunk-item">
              <div class="chunk-title">
                <strong>Chunk {{ index + 1 }}</strong>
                <ElButton
                  v-if="!dialogReadonly"
                  :disabled="chunks.length === 1"
                  link
                  type="danger"
                  @click="removeChunk(index)"
                >
                  删除
                </ElButton>
              </div>
              <ElInput
                v-model="chunks[index]"
                :autosize="{ minRows: 5, maxRows: 12 }"
                :disabled="dialogReadonly"
                placeholder="输入这一段 chunk 的文本"
                type="textarea"
              />
            </div>
          </div>
          <ElButton v-if="!dialogReadonly" plain type="primary" @click="addChunk">
            添加 Chunk
          </ElButton>
        </div>
        <template #footer>
          <ElButton @click="dialogVisible = false">关闭</ElButton>
          <ElButton
            v-if="!dialogReadonly"
            :loading="saving"
            type="primary"
            @click="saveDraft"
          >
            保存草稿
          </ElButton>
          <ElButton
            v-if="!dialogReadonly"
            :loading="submitting"
            type="success"
            @click="submitDraft"
          >
            提交入库
          </ElButton>
        </template>
      </ElDialog>
    </div>
  </Page>
</template>

<style scoped>
.manual-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar-band,
.summary-band,
.table-band {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.toolbar-band {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
}

.toolbar-band h2 {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 650;
}

.toolbar-band span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-filter {
  width: 160px;
}

.summary-band {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.metric-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px 20px;
}

.metric-item span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.metric-item strong {
  font-size: 24px;
  line-height: 1.2;
}

.table-band {
  padding: 16px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}

.editor-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.editor-form label {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.editor-form label span,
.editor-header {
  color: var(--el-text-color-regular);
  font-size: 13px;
  font-weight: 600;
}

.editor-header {
  display: flex;
  justify-content: space-between;
}

.chunk-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 560px;
  overflow: auto;
  padding-right: 4px;
}

.chunk-item {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 12px;
}

.chunk-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

@media (max-width: 900px) {
  .toolbar-band {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-actions {
    flex-wrap: wrap;
  }

  .summary-band {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
