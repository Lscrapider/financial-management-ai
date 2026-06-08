<script lang="ts" setup>
import type { UploadFile, UploadInstance, UploadRawFile } from 'element-plus';

import type { OcrReviewDetail, OcrReviewDraftContent } from '#/api/ocr-review';
import type {
  OcrChunkTagChunk,
  OcrChunkTagDetail,
  OcrStageDetail,
  OcrTask,
} from '#/api/ocr-task';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElCard,
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
  ElTimeline,
  ElTimelineItem,
  ElUpload,
} from 'element-plus';

import { getOcrReview, submitOcrReview } from '#/api/ocr-review';
import {
  deleteOcrTask,
  getOcrChunkTagDetail,
  getOcrStageDetail,
  pageOcrTasks,
  submitOcrTask,
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
const statusFilter = ref<'all' | ProcessingStatus>('all');
const pageNum = ref(1);
const pageSize = ref(20);
const totalTasks = ref(0);

const pipelineStages = [
  { key: 'document.normalize', label: '格式校验' },
  { key: 'ocr.recognize', label: 'OCR识别' },
  { key: 'text.clean', label: '文本清洗' },
  { key: 'quality.validate', label: '质量校验' },
  { key: 'chunk.tag', label: '场景打标' },
  { key: 'embedding.index', label: '向量入库' },
];

const processingTasks = ref<ProcessingTask[]>([]);
const chunkTagDialogVisible = ref(false);
const loadingChunkTags = ref(false);
const chunkTagDetail = ref<OcrChunkTagDetail>();
const stageDialogVisible = ref(false);
const loadingStageDetail = ref(false);
const stageDetail = ref<OcrStageDetail>();
const selectedStageKey = ref('');
const reviewDialogVisible = ref(false);
const loadingReview = ref(false);
const submittingReview = ref(false);
const reviewTask = ref<ProcessingTask>();
const reviewDetail = ref<OcrReviewDetail>();

const statusFilterOptions: Array<{
  label: string;
  value: 'all' | ProcessingStatus;
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

const canSubmit = computed(
  () => selectedFiles.value.length > 0 && !submitting.value,
);

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
    processingTasks.value = page.records.map((item) => toProcessingTask(item));
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
    const newProcessingTasks = tasks.map((item) => toProcessingTask(item));
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
  const types: Record<
    ProcessingStatus,
    'danger' | 'info' | 'success' | 'warning'
  > = {
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
    pipelineStages.find((item) => item.key === normalizeStage(stage))?.label ??
    '未知阶段'
  );
}

function stageClass(index: number) {
  const task = selectedTask.value;
  if (!task) {
    return '';
  }
  const currentIndex = pipelineStages.findIndex(
    (item) => item.key === normalizeStage(task.currentStage),
  );
  if (currentIndex === -1) {
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

function normalizeStage(stage: ProcessingStage) {
  return isChunkTagStage(stage) ? 'chunk.tag' : stage;
}

function isChunkTagStage(stage: string) {
  return ['chunk.tag.correct', 'chunk.tag.llm', 'chunk.tag.rule'].includes(
    stage,
  );
}

function canOpenStage(stageKey: string) {
  if (!selectedTask.value) {
    return false;
  }
  const stageIndex = pipelineStages.findIndex((item) => item.key === stageKey);
  const currentStage = selectedTask.value.currentStage;
  const currentIndex = pipelineStages.findIndex(
    (item) => item.key === normalizeStage(currentStage),
  );
  return stageIndex !== -1 && currentIndex >= stageIndex;
}

async function openStage(stageKey: string) {
  if (!selectedTask.value || !canOpenStage(stageKey)) {
    return;
  }
  if (stageKey === 'chunk.tag') {
    await openChunkTags();
    return;
  }
  selectedStageKey.value = stageKey;
  stageDialogVisible.value = true;
  await loadStageDetail();
}

function canOpenRetagDialog(row: ProcessingTask) {
  return row.status === 'finished';
}

async function openRetagDialog(row: ProcessingTask) {
  reviewTask.value = row;
  reviewDetail.value = undefined;
  reviewDialogVisible.value = true;
  loadingReview.value = true;
  try {
    reviewDetail.value = await getOcrReview(row.id);
  } finally {
    loadingReview.value = false;
  }
}

async function submitReviewDialog() {
  if (!reviewTask.value || !reviewDetail.value) {
    return;
  }
  submittingReview.value = true;
  try {
    renumberReviewParagraphs(reviewDetail.value.draftContent);
    await submitOcrReview(reviewTask.value.id, reviewDetail.value.draftContent);
    ElMessage.success('已提交复核内容，将重新打标签并重建向量');
    reviewDialogVisible.value = false;
    await loadTasks();
  } finally {
    submittingReview.value = false;
  }
}

function renumberReviewParagraphs(content: OcrReviewDraftContent) {
  content.paragraphs.forEach((paragraph, index) => {
    paragraph.paragraphNo = index + 1;
  });
  content.paragraphCount = content.paragraphs.length;
  if (content.metrics) {
    content.metrics.paragraphCount = content.paragraphs.length;
    content.metrics.warningCount = content.paragraphs.reduce(
      (total, item) => total + item.warnings.length,
      0,
    );
  }
}

async function loadStageDetail() {
  if (!selectedTask.value) {
    return;
  }
  loadingStageDetail.value = true;
  try {
    stageDetail.value = await getOcrStageDetail(selectedTask.value.id);
  } finally {
    loadingStageDetail.value = false;
  }
}

async function openChunkTags() {
  if (!selectedTask.value) {
    return;
  }
  chunkTagDialogVisible.value = true;
  await loadChunkTags();
}

async function loadChunkTags() {
  if (!selectedTask.value) {
    return;
  }
  loadingChunkTags.value = true;
  try {
    chunkTagDetail.value = await getOcrChunkTagDetail(selectedTask.value.id);
  } finally {
    loadingChunkTags.value = false;
  }
}

const selectedStageRecord = computed(() => {
  return stageDetail.value?.stages.find(
    (item) => item.stage === selectedStageKey.value,
  );
});

function chunkStatusLabel(status: string) {
  const labels: Record<string, string> = {
    failed: '失败',
    finished: '完成',
    pending: '待处理',
    running: '处理中',
  };
  return labels[status] ?? status;
}

function chunkStatusType(status: string) {
  const types: Record<string, 'danger' | 'info' | 'success' | 'warning'> = {
    failed: 'danger',
    finished: 'success',
    pending: 'info',
    running: 'warning',
  };
  return types[status] ?? 'info';
}

function formatNumberList(values: number[]) {
  return values.length > 0 ? values.join(', ') : '-';
}

function sourcePagesText(values: number[]) {
  return `第 ${formatNumberList(values)} 页`;
}

function formatScenes(chunk: OcrChunkTagChunk) {
  if (!chunk.scenes) {
    return '-';
  }
  const tags = Object.values(chunk.scenes).flat();
  return tags.length > 0 ? tags.join(' / ') : '-';
}

function formatJson(value?: Record<string, unknown>) {
  if (!value || Object.keys(value).length === 0) {
    return '-';
  }
  return JSON.stringify(value, null, 2);
}

function stageStatusType(status?: string) {
  return chunkStatusType(status ?? 'pending');
}

function stageStatusLabel(status?: string) {
  return chunkStatusLabel(status ?? 'pending');
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
          class="pipeline-step"
          :class="[
            stageClass(index),
            { 'is-clickable': canOpenStage(stage.key) },
          ]"
          @click="openStage(stage.key)"
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
              width="220"
            >
              <template #default="{ row }">
                <div class="table-actions" @click.stop>
                  <ElButton
                    v-if="canOpenRetagDialog(row)"
                    link
                    type="primary"
                    @click="openRetagDialog(row)"
                  >
                    重新打标
                  </ElButton>
                  <ElButton
                    v-if="row.status === 'manual_review_required'"
                    link
                    type="primary"
                    @click="openReview(row)"
                  >
                    复核页
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
              <ElButton
                :disabled="!canOpenStage(stage.key)"
                link
                type="primary"
                @click="openStage(stage.key)"
              >
                {{ stage.label }}
              </ElButton>
            </ElTimelineItem>
          </ElTimeline>
        </aside>
      </div>

      <ElDialog
        v-model="stageDialogVisible"
        :title="`${stageLabel(selectedStageKey)}详情`"
        width="840px"
      >
        <template #header>
          <div class="dialog-header">
            <span>{{ stageLabel(selectedStageKey) }}详情</span>
            <ElButton
              :loading="loadingStageDetail"
              size="small"
              type="primary"
              @click="loadStageDetail"
            >
              刷新
            </ElButton>
          </div>
        </template>
        <div v-loading="loadingStageDetail" class="stage-detail">
          <div class="stage-detail-row">
            <span>阶段</span>
            <strong>{{ stageLabel(selectedStageKey) }}</strong>
          </div>
          <div class="stage-detail-row">
            <span>状态</span>
            <ElTag
              :type="stageStatusType(selectedStageRecord?.status)"
              effect="plain"
            >
              {{ stageStatusLabel(selectedStageRecord?.status) }}
            </ElTag>
          </div>
          <div class="stage-detail-row">
            <span>尝试</span>
            <strong>
              {{ selectedStageRecord?.attemptCount ?? 0 }} /
              {{ selectedStageRecord?.maxAttempts ?? 0 }}
            </strong>
          </div>
          <div class="stage-detail-row">
            <span>开始</span>
            <strong>{{
              formatDateTime(selectedStageRecord?.startedAt ?? '')
            }}</strong>
          </div>
          <div class="stage-detail-row">
            <span>结束</span>
            <strong>{{
              formatDateTime(selectedStageRecord?.finishedAt ?? '')
            }}</strong>
          </div>
          <div class="stage-detail-block">
            <span>指标</span>
            <pre>{{ formatJson(selectedStageRecord?.metrics) }}</pre>
          </div>
          <div class="stage-detail-block">
            <span>输入引用</span>
            <pre>{{ formatJson(selectedStageRecord?.inputRef) }}</pre>
          </div>
          <div class="stage-detail-block">
            <span>输出引用</span>
            <pre>{{ formatJson(selectedStageRecord?.outputRef) }}</pre>
          </div>
          <div class="stage-detail-block">
            <span>错误</span>
            <pre>{{ selectedStageRecord?.errorMessage ?? '-' }}</pre>
          </div>
        </div>
      </ElDialog>

      <ElDialog
        v-model="chunkTagDialogVisible"
        title="场景打标明细"
        width="1180px"
      >
        <template #header>
          <div class="dialog-header">
            <span>场景打标明细</span>
            <ElButton
              :loading="loadingChunkTags"
              size="small"
              type="primary"
              @click="loadChunkTags"
            >
              刷新
            </ElButton>
          </div>
        </template>
        <div v-if="chunkTagDetail" class="chunk-summary">
          <div>
            <span>总数</span>
            <strong>{{ chunkTagDetail.totalChunkCount }}</strong>
          </div>
          <div>
            <span>完成</span>
            <strong>{{ chunkTagDetail.finishedChunkCount }}</strong>
          </div>
          <div>
            <span>待处理</span>
            <strong>{{ chunkTagDetail.pendingChunkCount }}</strong>
          </div>
          <div>
            <span>失败</span>
            <strong>{{ chunkTagDetail.failedChunkCount }}</strong>
          </div>
          <div>
            <span>需 LLM</span>
            <strong>{{ chunkTagDetail.llmChunkCount }}</strong>
          </div>
          <div>
            <span>已删除</span>
            <strong>{{ chunkTagDetail.deletedChunkCount }}</strong>
          </div>
        </div>
        <ElTable
          :data="chunkTagDetail?.chunks ?? []"
          v-loading="loadingChunkTags"
          border
          height="620"
          row-key="chunkId"
        >
          <ElTableColumn align="right" label="#" prop="chunkIndex" width="72" />
          <ElTableColumn label="页码" width="100">
            <template #default="{ row }">
              {{ formatNumberList(row.pageNos) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="段落" width="100">
            <template #default="{ row }">
              {{ formatNumberList(row.paragraphNos) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="状态" width="96">
            <template #default="{ row }">
              <ElTag :type="chunkStatusType(row.status)" effect="plain">
                {{ chunkStatusLabel(row.status) }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="LLM" width="76">
            <template #default="{ row }">
              <ElTag :type="row.needLlm ? 'warning' : 'info'" effect="plain">
                {{ row.needLlm ? '是' : '否' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="删除" width="76">
            <template #default="{ row }">
              <ElTag :type="row.deleted ? 'danger' : 'success'" effect="plain">
                {{ row.deleted ? '是' : '否' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="标签" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              {{ formatScenes(row) }}
            </template>
          </ElTableColumn>
          <ElTableColumn
            label="内容"
            min-width="260"
            prop="text"
            show-overflow-tooltip
          />
          <ElTableColumn
            label="错误"
            min-width="180"
            prop="errorMessage"
            show-overflow-tooltip
          />
        </ElTable>
      </ElDialog>

      <ElDialog
        v-model="reviewDialogVisible"
        :close-on-click-modal="false"
        width="980px"
      >
        <template #header>
          <div class="dialog-header">
            <span>复核并重新打标签</span>
            <ElPopconfirm
              title="确认使用当前复核内容重新打标签并重建向量？"
              @confirm="submitReviewDialog"
            >
              <template #reference>
                <ElButton
                  :disabled="!reviewDetail"
                  :loading="submittingReview"
                  size="small"
                  type="primary"
                >
                  确认重新打标签
                </ElButton>
              </template>
            </ElPopconfirm>
          </div>
        </template>

        <div v-loading="loadingReview" class="review-dialog-body">
          <template v-if="reviewDetail">
            <div class="review-summary">
              <div>
                <span>任务</span>
                <strong>{{
                  reviewTask?.fileName ?? reviewTask?.id ?? '-'
                }}</strong>
              </div>
              <div>
                <span>段落</span>
                <strong>{{ reviewDetail.draftContent.paragraphCount }}</strong>
              </div>
              <div>
                <span>警告</span>
                <strong>{{ reviewDetail.warningCount }}</strong>
              </div>
              <div>
                <span>复核状态</span>
                <strong>{{ reviewDetail.status }}</strong>
              </div>
            </div>

            <div class="review-paragraphs">
              <article
                v-for="paragraph in reviewDetail.draftContent.paragraphs"
                :key="paragraph.paragraphNo"
                class="review-paragraph"
              >
                <div class="paragraph-meta">
                  <strong>#{{ paragraph.paragraphNo }}</strong>
                  <span>{{ sourcePagesText(paragraph.sourcePages) }}</span>
                  <span>{{ paragraph.text.length }} 字</span>
                  <ElTag
                    v-if="paragraph.warnings.length > 0"
                    effect="plain"
                    size="small"
                    type="warning"
                  >
                    {{ paragraph.warnings.length }} 个警告
                  </ElTag>
                </div>
                <ElInput
                  v-model="paragraph.text"
                  :autosize="{ minRows: 3, maxRows: 8 }"
                  type="textarea"
                />
              </article>
            </div>
          </template>

          <ElEmpty v-else description="暂无复核内容" />
        </div>
      </ElDialog>
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
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
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
  font-size: 13px;
  color: hsl(var(--muted-foreground));
}

.metric-item {
  padding: 18px;
}

.metric-item span,
.panel-header span {
  font-size: 13px;
  color: hsl(var(--muted-foreground));
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
  gap: 10px;
  align-items: center;
  min-height: 54px;
  padding: 10px;
  color: hsl(var(--muted-foreground));
  background: hsl(var(--muted) / 35%);
  border-radius: 6px;
}

.pipeline-step.is-active {
  color: rgb(217 119 6);
  background: rgb(245 158 11 / 14%);
}

.pipeline-step.is-done {
  color: rgb(5 150 105);
  background: rgb(16 185 129 / 14%);
}

.pipeline-step.is-clickable {
  cursor: pointer;
}

.pipeline-step.is-clickable:hover {
  outline: 1px solid currentcolor;
}

.step-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  font-size: 13px;
  font-weight: 700;
  color: hsl(var(--background));
  background: currentcolor;
  border-radius: 999px;
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
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
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
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
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

.chunk-summary {
  display: grid;
  grid-template-columns: repeat(6, minmax(80px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.chunk-summary div {
  padding: 10px;
  border: 1px solid hsl(var(--border));
  border-radius: 6px;
}

.chunk-summary span {
  display: block;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}

.chunk-summary strong {
  display: block;
  margin-top: 6px;
  font-size: 20px;
}

.dialog-header {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 38px;
}

.stage-detail {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.stage-detail-row,
.stage-detail-block {
  min-width: 0;
  padding: 12px;
  border: 1px solid hsl(var(--border));
  border-radius: 6px;
}

.stage-detail-row {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
}

.stage-detail-block {
  grid-column: 1 / -1;
}

.stage-detail-block span {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}

.stage-detail-row span {
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}

.stage-detail-row strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stage-detail-row :deep(.el-tag) {
  max-width: 100%;
}

.stage-detail-row :deep(.el-tag__content) {
  overflow: hidden;
  text-overflow: ellipsis;
}

.stage-detail pre {
  max-height: 180px;
  padding: 10px;
  margin: 0;
  overflow: auto;
  font-size: 12px;
  white-space: pre-wrap;
  background: hsl(var(--muted) / 45%);
  border-radius: 6px;
}

.review-dialog-body {
  min-height: 220px;
}

.review-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.review-summary div {
  min-width: 0;
  padding: 10px;
  border: 1px solid hsl(var(--border));
  border-radius: 6px;
}

.review-summary span {
  display: block;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}

.review-summary strong {
  display: block;
  min-width: 0;
  margin-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 16px;
  white-space: nowrap;
}

.review-paragraphs {
  display: grid;
  gap: 10px;
  max-height: 620px;
  overflow: auto;
}

.review-paragraph {
  min-width: 0;
  padding: 12px;
  background: hsl(var(--background));
  border: 1px solid hsl(var(--border));
  border-radius: 6px;
}

.paragraph-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
  font-size: 13px;
  color: hsl(var(--muted-foreground));
}

.paragraph-meta strong {
  color: hsl(var(--foreground));
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
  .pipeline-band,
  .review-summary {
    grid-template-columns: 1fr;
  }
}
</style>
