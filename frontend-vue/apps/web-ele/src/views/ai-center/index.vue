<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElCard,
  ElMessage,
  ElOption,
  ElPagination,
  ElPopconfirm,
  ElProgress,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTimeline,
  ElTimelineItem,
  ElUpload,
} from 'element-plus';
import type { UploadFile, UploadRawFile } from 'element-plus';
import type { UploadInstance } from 'element-plus';

import {
  deleteOcrTask,
  pageOcrTasks,
  submitOcrTask,
  type OcrTask,
} from '#/api/ocr-task';

type ProcessingStatus = OcrTask['status'];
type ProcessingStage = OcrTask['currentStage'];

interface ProcessingTask {
  currentStage: ProcessingStage;
  fileName: string;
  id: string;
  pages: number;
  progress: number;
  segments: number;
  status: ProcessingStatus;
  updatedAt: string;
}

const router = useRouter();
const selectedTaskId = ref('');
const selectedFiles = ref<File[]>([]);
const uploadRef = ref<UploadInstance>();
const submitting = ref(false);
const loadingTasks = ref(false);
const deletingTaskNo = ref('');
const statusFilter = ref<ProcessingStatus | 'all'>('all');
const pageNum = ref(1);
const pageSize = ref(20);
const totalTasks = ref(0);

const pipelineStages = [
  { key: 'document.normalize', label: '格式校验' },
  { key: 'ocr.recognize', label: 'OCR识别' },
  { key: 'text.clean', label: '文本清洗' },
  { key: 'quality.validate', label: '质量校验' },
  { key: 'chunk.tag.rule', label: '规则打标' },
  { key: 'chunk.tag.llm', label: 'LLM打标' },
  { key: 'chunk.tag.correct', label: '标签回正' },
  { key: 'embedding.index', label: '向量入库' },
];

const processingTasks = ref<ProcessingTask[]>([]);

const statusFilterOptions: Array<{
  label: string;
  value: ProcessingStatus | 'all';
}> = [
  { label: '全部状态', value: 'all' },
  { label: '处理中', value: 'running' },
  { label: '已完成', value: 'finished' },
  { label: '等待中', value: 'ready' },
  { label: '审核中', value: 'manual_review_required' },
  { label: '失败', value: 'failed' },
];

const selectedTask = computed(() => {
  return (
    processingTasks.value.find((item) => item.id === selectedTaskId.value) ??
    processingTasks.value[0]
  );
});

const finishedCount = computed(() => {
  return processingTasks.value.filter((item) => item.status === 'finished')
    .length;
});

const runningCount = computed(() => {
  return processingTasks.value.filter((item) => item.status === 'running')
    .length;
});

const readyCount = computed(() => {
  return processingTasks.value.filter((item) => item.status === 'ready').length;
});

const totalPages = computed(() => {
  return processingTasks.value.reduce((total, item) => total + item.pages, 0);
});

const reviewingCount = computed(() => {
  return processingTasks.value.filter(
    (item) => item.status === 'manual_review_required',
  ).length;
});

const canSubmit = computed(() => selectedFiles.value.length > 0 && !submitting.value);

onMounted(() => {
  void loadTasks();
});

async function loadTasks() {
  loadingTasks.value = true;
  try {
    const page = await pageOcrTasks({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      status: statusFilter.value === 'all' ? undefined : statusFilter.value,
    });
    processingTasks.value = page.records.map(toProcessingTask);
    totalTasks.value = page.total;
    selectedTaskId.value = processingTasks.value[0]?.id ?? '';
  } finally {
    loadingTasks.value = false;
  }
}

async function submitTask() {
  if (selectedFiles.value.length === 0) {
    ElMessage.warning('请选择 PDF 或图片文件');
    return;
  }
  submitting.value = true;
  try {
    const tasks = await submitOcrTask(selectedFiles.value);
    const newProcessingTasks = tasks.map(toProcessingTask);
    pageNum.value = 1;
    selectedTaskId.value = newProcessingTasks[0]?.id ?? selectedTaskId.value;
    selectedFiles.value = [];
    uploadRef.value?.clearFiles();
    ElMessage.success(`已提交 ${newProcessingTasks.length} 个识别任务`);
    await loadTasks();
  } finally {
    submitting.value = false;
  }
}

function beforeUpload(file: UploadRawFile) {
  if (!isSupportedFile(file)) {
    ElMessage.warning('仅支持 PDF、PNG、JPG、JPEG、WEBP 文件');
    return false;
  }
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.warning('上传文件不能超过50MB');
    return false;
  }
  return true;
}

function changeUploadFile(file: UploadFile) {
  const rawFile = file.raw;
  if (!rawFile || !beforeUpload(rawFile)) {
    selectedFiles.value = selectedFiles.value.filter(
      (item) => item.name !== file.name,
    );
    return;
  }
  selectedFiles.value = [
    ...selectedFiles.value.filter((item) => item.name !== rawFile.name),
    rawFile,
  ];
}

function removeUploadFile(file: UploadFile) {
  selectedFiles.value = selectedFiles.value.filter(
    (item) => item.name !== file.name,
  );
}

function isSupportedFile(file: File) {
  const fileName = file.name.toLowerCase();
  return ['.pdf', '.png', '.jpg', '.jpeg', '.webp'].some((suffix) =>
    fileName.endsWith(suffix),
  );
}

function toProcessingTask(task: OcrTask): ProcessingTask {
  return {
    currentStage: task.currentStage,
    fileName: task.originalFilename,
    id: task.taskNo,
    pages: task.pageCount,
    progress: task.progress,
    segments: task.segmentCount,
    status: task.status,
    updatedAt: formatDateTime(task.updatedAt ?? task.submittedAt),
  };
}

function selectTask(row: ProcessingTask) {
  selectedTaskId.value = row.id;
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

function openReview(row: ProcessingTask) {
  void router.push({
    name: 'AiOcrReview',
    params: { taskNo: row.id },
  });
}

async function removeTask(row: ProcessingTask) {
  deletingTaskNo.value = row.id;
  try {
    await deleteOcrTask(row.id);
    ElMessage.success('已删除任务');
    if (processingTasks.value.length === 1 && pageNum.value > 1) {
      pageNum.value -= 1;
    }
    await loadTasks();
  } finally {
    deletingTaskNo.value = '';
  }
}

function statusLabel(status: ProcessingStatus) {
  const labels: Record<ProcessingStatus, string> = {
    failed: '失败',
    finished: '完成',
    manual_review_required: '需人工介入',
    ready: '等待中',
    running: '处理中',
  };
  return labels[status];
}

function statusType(status: ProcessingStatus) {
  const types: Record<ProcessingStatus, 'danger' | 'info' | 'success' | 'warning'> =
    {
      failed: 'danger',
      finished: 'success',
      manual_review_required: 'warning',
      ready: 'info',
      running: 'warning',
    };
  return types[status];
}

function stageLabel(stage: ProcessingStage) {
  return (
    pipelineStages.find((item) => item.key === stage)?.label ?? '未知阶段'
  );
}

function stageClass(index: number) {
  const task = selectedTask.value;
  if (!task) {
    return '';
  }
  const currentIndex = pipelineStages.findIndex(
    (item) => item.key === task.currentStage,
  );
  if (currentIndex < 0) {
    return '';
  }
  if (task.status === 'finished' || index < currentIndex) {
    return 'is-done';
  }
  if (index === currentIndex) {
    return 'is-active';
  }
  return '';
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 19);
}
</script>

<template>
  <Page title="AI中心">
    <div class="ai-center-page">
      <section class="submit-panel">
        <div class="panel-header">
          <div>
            <h2>提交识别任务</h2>
            <span>上传 PDF 或图片后进入 OCR、清洗、切分和向量化队列</span>
          </div>
          <ElButton
            :disabled="!canSubmit"
            :loading="submitting"
            type="primary"
            @click="submitTask"
          >
            提交任务
          </ElButton>
        </div>

        <ElUpload
          ref="uploadRef"
          :auto-upload="false"
          :before-upload="beforeUpload"
          accept=".pdf,.png,.jpg,.jpeg,.webp"
          drag
          multiple
          @change="changeUploadFile"
          @remove="removeUploadFile"
        >
          <div class="upload-content">
            <strong>
              {{
                selectedFiles.length > 0
                  ? `已选择 ${selectedFiles.length} 个文件`
                  : '选择或拖入文件'
              }}
            </strong>
            <span>支持 PDF、PNG、JPG、JPEG、WEBP，单个文件不超过50MB</span>
          </div>
        </ElUpload>
      </section>

      <section class="overview-band">
        <div class="metric-item">
          <span>处理中</span>
          <strong>{{ runningCount }}</strong>
        </div>
        <div class="metric-item">
          <span>已完成</span>
          <strong>{{ finishedCount }}</strong>
        </div>
        <div class="metric-item">
          <span>等待中</span>
          <strong>{{ readyCount }}</strong>
        </div>
        <div class="metric-item">
          <span>扫描页数</span>
          <strong>{{ totalPages }}</strong>
        </div>
        <div class="metric-item">
          <span>审核中</span>
          <strong>{{ reviewingCount }}</strong>
        </div>
      </section>

      <section class="pipeline-band">
        <div
          v-for="(stage, index) in pipelineStages"
          :key="stage.key"
          :class="['pipeline-step', stageClass(index)]"
        >
          <span class="step-index">{{ index + 1 }}</span>
          <strong>{{ stage.label }}</strong>
        </div>
      </section>

      <div class="content-grid">
        <ElCard class="queue-panel" shadow="never">
          <template #header>
            <div class="panel-header">
              <div>
                <h2>处理队列</h2>
                <span>{{ selectedTask?.updatedAt ?? '-' }}</span>
              </div>
              <div class="queue-tools">
                <ElSelect
                  v-model="statusFilter"
                  size="small"
                  @change="changeStatusFilter"
                >
                  <ElOption
                    v-for="option in statusFilterOptions"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  />
                </ElSelect>
              </div>
            </div>
          </template>

          <ElTable
            :data="processingTasks"
            v-loading="loadingTasks"
            border
            height="560"
            highlight-current-row
            row-key="id"
            @row-click="selectTask"
          >
            <ElTableColumn
              label="文件"
              min-width="220"
              prop="fileName"
              resizable
              show-overflow-tooltip
            />
            <ElTableColumn label="阶段" min-width="120" resizable>
              <template #default="{ row }">
                {{ stageLabel(row.currentStage) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="状态" resizable width="132">
              <template #default="{ row }">
                <ElTag :type="statusType(row.status)" effect="plain">
                  {{ statusLabel(row.status) }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="进度" min-width="190" resizable>
              <template #default="{ row }">
                <ElProgress
                  :percentage="row.progress"
                  :status="row.status === 'finished' ? 'success' : undefined"
                />
              </template>
            </ElTableColumn>
            <ElTableColumn
              align="right"
              label="页数"
              prop="pages"
              resizable
              width="80"
            />
            <ElTableColumn
              align="right"
              label="分段"
              prop="segments"
              resizable
              width="90"
            />
            <ElTableColumn
              align="right"
              fixed="right"
              label="操作"
              resizable
              width="150"
            >
              <template #default="{ row }">
                <div class="table-actions" @click.stop>
                  <ElButton
                    v-if="row.status === 'manual_review_required'"
                    link
                    type="primary"
                    @click="openReview(row)"
                  >
                    复核
                  </ElButton>
                  <ElPopconfirm
                    title="确认删除该任务？"
                    @confirm="removeTask(row)"
                  >
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
                </div>
              </template>
            </ElTableColumn>
          </ElTable>

          <div class="queue-pagination">
            <ElPagination
              :current-page="pageNum"
              :page-size="pageSize"
              :page-sizes="[10, 20, 50, 100]"
              :total="totalTasks"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="changePageNum"
              @size-change="changePageSize"
            />
          </div>
        </ElCard>

        <aside class="detail-panel">
          <div class="panel-header">
            <div>
              <h2>{{ selectedTask?.fileName ?? '-' }}</h2>
              <span>{{ selectedTask?.id ?? '-' }}</span>
            </div>
            <ElTag
              v-if="selectedTask"
              :type="statusType(selectedTask.status)"
              effect="plain"
            >
              {{ statusLabel(selectedTask.status) }}
            </ElTag>
          </div>

          <div class="detail-progress">
            <ElProgress
              :percentage="selectedTask?.progress ?? 0"
              :stroke-width="10"
            />
          </div>

          <ElTimeline>
            <ElTimelineItem
              v-for="(stage, index) in pipelineStages"
              :key="stage.key"
              :type="stageClass(index) === 'is-done' ? 'success' : undefined"
            >
              {{ stage.label }}
            </ElTimelineItem>
          </ElTimeline>
        </aside>
      </div>
    </div>
  </Page>
</template>

<style scoped>
.ai-center-page {
  padding: 4px 0 24px;
}

.overview-band {
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.metric-item,
.detail-panel,
.submit-panel,
.pipeline-band {
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
  background: hsl(var(--card));
}

.submit-panel {
  padding: 16px;
  margin-bottom: 16px;
}

.upload-content {
  display: grid;
  gap: 8px;
  padding: 18px 0;
}

.upload-content strong {
  font-size: 16px;
}

.upload-content span {
  color: hsl(var(--muted-foreground));
  font-size: 13px;
}

.metric-item {
  padding: 18px;
}

.metric-item span,
.panel-header span {
  color: hsl(var(--muted-foreground));
  font-size: 13px;
}

.metric-item strong {
  display: block;
  margin-top: 10px;
  font-size: 28px;
  font-weight: 700;
}

.pipeline-band {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
  padding: 14px;
  margin-bottom: 16px;
}

.pipeline-step {
  display: flex;
  align-items: center;
  min-height: 54px;
  gap: 10px;
  padding: 10px;
  border-radius: 6px;
  background: hsl(var(--muted) / 35%);
  color: hsl(var(--muted-foreground));
}

.pipeline-step.is-active {
  background: rgb(245 158 11 / 14%);
  color: rgb(217 119 6);
}

.pipeline-step.is-done {
  background: rgb(16 185 129 / 14%);
  color: rgb(5 150 105);
}

.step-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 999px;
  background: currentcolor;
  color: hsl(var(--background));
  font-size: 13px;
  font-weight: 700;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
}

.queue-panel {
  padding: 0;
}

.queue-panel :deep(.el-card__body) {
  padding: 0;
}

.detail-panel {
  padding: 16px;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 0;
}

.panel-header h2 {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 700;
}

.detail-panel .panel-header {
  margin-bottom: 16px;
}

.detail-progress {
  margin-bottom: 22px;
}

.table-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.queue-tools {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.queue-tools :deep(.el-select) {
  width: 132px;
}

.queue-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 12px;
}

@media (max-width: 1200px) {
  .overview-band {
    grid-template-columns: repeat(3, minmax(120px, 1fr));
  }

  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .overview-band,
  .pipeline-band {
    grid-template-columns: 1fr;
  }
}
</style>
