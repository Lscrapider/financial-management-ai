<script lang="ts" setup>
import { computed, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElMessage,
  ElProgress,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
  ElTag,
  ElTimeline,
  ElTimelineItem,
  ElUpload,
} from 'element-plus';
import type { UploadFile, UploadRawFile } from 'element-plus';

import { submitOcrTask, type OcrTask } from '#/api/ocr-task';

type ProcessingStatus = 'failed' | 'finished' | 'pending' | 'running';

interface ProcessingTask {
  currentStage: string;
  fileName: string;
  id: string;
  pages: number;
  progress: number;
  segments: number;
  status: ProcessingStatus;
  updatedAt: string;
}

const activeTab = ref('knowledgeProcessing');
const selectedTaskId = ref('scan-20260525-002');
const selectedFile = ref<File>();
const submitting = ref(false);

const pipelineStages = [
  { key: 'upload', label: '扫描件上传' },
  { key: 'ocr', label: 'OCR识别' },
  { key: 'clean', label: '文本清洗' },
  { key: 'split', label: '文本切分' },
  { key: 'vectorize', label: '向量化' },
  { key: 'indexed', label: '写入向量库' },
];

const processingTasks = ref<ProcessingTask[]>([
  {
    currentStage: '写入向量库',
    fileName: '手写副本-估值框架-001.pdf',
    id: 'scan-20260525-001',
    pages: 28,
    progress: 100,
    segments: 146,
    status: 'finished',
    updatedAt: '2026-05-25 15:48',
  },
  {
    currentStage: 'OCR识别',
    fileName: '手写副本-交易纪律-002.pdf',
    id: 'scan-20260525-002',
    pages: 16,
    progress: 42,
    segments: 0,
    status: 'running',
    updatedAt: '2026-05-25 16:08',
  },
  {
    currentStage: '文本清洗',
    fileName: '手写副本-行业笔记-003.pdf',
    id: 'scan-20260525-003',
    pages: 22,
    progress: 68,
    segments: 84,
    status: 'running',
    updatedAt: '2026-05-25 16:03',
  },
  {
    currentStage: '等待处理',
    fileName: '手写副本-财报摘要-004.pdf',
    id: 'scan-20260525-004',
    pages: 9,
    progress: 0,
    segments: 0,
    status: 'pending',
    updatedAt: '2026-05-25 15:57',
  },
]);

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

const pendingCount = computed(() => {
  return processingTasks.value.filter((item) => item.status === 'pending')
    .length;
});

const totalPages = computed(() => {
  return processingTasks.value.reduce((total, item) => total + item.pages, 0);
});

const totalSegments = computed(() => {
  return processingTasks.value.reduce(
    (total, item) => total + item.segments,
    0,
  );
});

const canSubmit = computed(() => Boolean(selectedFile.value) && !submitting.value);

async function submitTask() {
  if (!selectedFile.value) {
    ElMessage.warning('请选择 PDF 或图片文件');
    return;
  }
  submitting.value = true;
  try {
    const task = await submitOcrTask(selectedFile.value);
    const processingTask = toProcessingTask(task);
    processingTasks.value.unshift(processingTask);
    selectedTaskId.value = processingTask.id;
    selectedFile.value = undefined;
    ElMessage.success('识别任务已提交');
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
    selectedFile.value = undefined;
    return;
  }
  selectedFile.value = rawFile;
}

function removeUploadFile() {
  selectedFile.value = undefined;
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

function statusLabel(status: ProcessingStatus) {
  const labels: Record<ProcessingStatus, string> = {
    failed: '失败',
    finished: '完成',
    pending: '等待中',
    running: '处理中',
  };
  return labels[status];
}

function statusType(status: ProcessingStatus) {
  const types: Record<ProcessingStatus, 'danger' | 'info' | 'success' | 'warning'> =
    {
      failed: 'danger',
      finished: 'success',
      pending: 'info',
      running: 'warning',
    };
  return types[status];
}

function stageClass(index: number) {
  const task = selectedTask.value;
  if (!task) {
    return '';
  }
  const currentIndex = pipelineStages.findIndex(
    (item) => item.label === task.currentStage,
  );
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
      <ElTabs v-model="activeTab">
        <ElTabPane label="知识库处理进度" name="knowledgeProcessing">
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
              :auto-upload="false"
              :before-upload="beforeUpload"
              :limit="1"
              accept=".pdf,.png,.jpg,.jpeg,.webp"
              drag
              @change="changeUploadFile"
              @remove="removeUploadFile"
            >
              <div class="upload-content">
                <strong>{{ selectedFile?.name ?? '选择或拖入文件' }}</strong>
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
              <strong>{{ pendingCount }}</strong>
            </div>
            <div class="metric-item">
              <span>扫描页数</span>
              <strong>{{ totalPages }}</strong>
            </div>
            <div class="metric-item">
              <span>文本分段</span>
              <strong>{{ totalSegments }}</strong>
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
            <section class="queue-panel">
              <div class="panel-header">
                <div>
                  <h2>处理队列</h2>
                  <span>{{ selectedTask?.updatedAt ?? '-' }}</span>
                </div>
              </div>

              <ElTable
                :data="processingTasks"
                height="520"
                highlight-current-row
                row-key="id"
                @row-click="selectTask"
              >
                <ElTableColumn label="文件" min-width="220" prop="fileName" />
                <ElTableColumn label="阶段" min-width="120" prop="currentStage" />
                <ElTableColumn label="状态" width="100">
                  <template #default="{ row }">
                    <ElTag :type="statusType(row.status)" effect="plain">
                      {{ statusLabel(row.status) }}
                    </ElTag>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="进度" min-width="180">
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
                  width="80"
                />
                <ElTableColumn
                  align="right"
                  label="分段"
                  prop="segments"
                  width="90"
                />
              </ElTable>
            </section>

            <aside class="detail-panel">
              <div class="panel-header">
                <div>
                  <h2>{{ selectedTask?.fileName }}</h2>
                  <span>{{ selectedTask?.id }}</span>
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
        </ElTabPane>
      </ElTabs>
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
.queue-panel,
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
  grid-template-columns: repeat(6, minmax(120px, 1fr));
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

.queue-panel,
.detail-panel {
  padding: 16px;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-header h2 {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 700;
}

.detail-progress {
  margin-bottom: 22px;
}

@media (max-width: 1200px) {
  .overview-band,
  .pipeline-band {
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
