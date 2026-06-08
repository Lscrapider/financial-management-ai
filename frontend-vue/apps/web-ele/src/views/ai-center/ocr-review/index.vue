<script lang="ts" setup>
import type {
  OcrReviewDetail,
  OcrReviewDraftContent,
  OcrReviewParagraph,
} from '#/api/ocr-review';

import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElEmpty,
  ElInput,
  ElMessage,
  ElScrollbar,
  ElSkeleton,
  ElTag,
} from 'element-plus';

import {
  getOcrReview,
  saveOcrReviewDraft,
  submitOcrReview,
} from '#/api/ocr-review';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const saving = ref(false);
const submitting = ref(false);
const review = ref<OcrReviewDetail>();
const selectedParagraphNo = ref<number>();

const taskNo = computed(() => String(route.params.taskNo ?? ''));

const hasTaskNo = computed(() => taskNo.value.trim().length > 0);

const draft = computed(() => review.value?.draftContent);

const paragraphs = computed(() => draft.value?.paragraphs ?? []);

const selectedParagraph = computed(() => {
  return (
    paragraphs.value.find(
      (item) => item.paragraphNo === selectedParagraphNo.value,
    ) ?? paragraphs.value[0]
  );
});

const selectedPageNo = computed(() => {
  return (
    selectedParagraph.value?.sourcePages?.[0] ?? review.value?.pages[0]?.pageNo
  );
});

const selectedPage = computed(() => {
  return review.value?.pages.find(
    (item) => item.pageNo === selectedPageNo.value,
  );
});

const confidenceText = computed(() => {
  const value = Number(review.value?.overallConfidence ?? 0);
  return `${Math.round(value * 100)}%`;
});

const emptyDescription = computed(() => {
  return hasTaskNo.value ? '复核任务不存在' : '请从处理队列点击复核进入';
});

onMounted(() => {
  void loadReview();
});

async function loadReview() {
  if (!hasTaskNo.value) {
    return;
  }
  loading.value = true;
  try {
    review.value = await getOcrReview(taskNo.value);
    selectedParagraphNo.value =
      review.value.draftContent.paragraphs[0]?.paragraphNo;
  } finally {
    loading.value = false;
  }
}

async function saveDraft() {
  if (!review.value) {
    return;
  }
  saving.value = true;
  try {
    renumberParagraphs(review.value.draftContent);
    await saveOcrReviewDraft(taskNo.value, review.value.draftContent);
    ElMessage.success('草稿已保存');
  } finally {
    saving.value = false;
  }
}

async function submitReview() {
  if (!review.value) {
    return;
  }
  submitting.value = true;
  try {
    renumberParagraphs(review.value.draftContent);
    await submitOcrReview(taskNo.value, review.value.draftContent);
    ElMessage.success('已提交复核结果');
    await goBack();
  } finally {
    submitting.value = false;
  }
}

function selectParagraph(paragraph: OcrReviewParagraph) {
  selectedParagraphNo.value = paragraph.paragraphNo;
}

function removeParagraph(index: number) {
  const content = draft.value;
  if (!content) {
    return;
  }
  content.paragraphs.splice(index, 1);
  renumberParagraphs(content);
  selectedParagraphNo.value =
    content.paragraphs[
      Math.min(index, content.paragraphs.length - 1)
    ]?.paragraphNo;
}

function mergeWithNext(index: number) {
  const content = draft.value;
  if (!content || index >= content.paragraphs.length - 1) {
    return;
  }
  const current = content.paragraphs[index];
  const next = content.paragraphs[index + 1];
  if (!current || !next) {
    return;
  }
  current.text = `${current.text}\n${next.text}`.trim();
  current.sourcePages = [
    ...new Set([...current.sourcePages, ...next.sourcePages]),
  ];
  current.sourceSegments = [...current.sourceSegments, ...next.sourceSegments];
  current.avgConfidence = Number(
    ((current.avgConfidence + next.avgConfidence) / 2).toFixed(4),
  );
  current.warnings = [...current.warnings, ...next.warnings];
  content.paragraphs.splice(index + 1, 1);
  renumberParagraphs(content);
  selectedParagraphNo.value = current.paragraphNo;
}

function copyParagraph(index: number) {
  const content = draft.value;
  if (!content) {
    return;
  }
  const original = content.paragraphs[index];
  if (!original) {
    return;
  }
  const clone: OcrReviewParagraph = {
    ...original,
    sourcePages: [...original.sourcePages],
    sourceSegments: original.sourceSegments.map((seg) => ({ ...seg })),
    warnings: original.warnings.map((w) => ({ ...w })),
  };
  content.paragraphs.splice(index + 1, 0, clone);
  renumberParagraphs(content);
  selectedParagraphNo.value = clone.paragraphNo;
}

function moveParagraph(index: number, offset: -1 | 1) {
  const content = draft.value;
  if (!content) {
    return;
  }
  const targetIndex = index + offset;
  if (targetIndex < 0 || targetIndex >= content.paragraphs.length) {
    return;
  }
  const [item] = content.paragraphs.splice(index, 1);
  if (!item) {
    return;
  }
  content.paragraphs.splice(targetIndex, 0, item);
  renumberParagraphs(content);
  selectedParagraphNo.value = item.paragraphNo;
}

function renumberParagraphs(content: OcrReviewDraftContent) {
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

function confidenceType(value: number) {
  if (value < 0.7) {
    return 'danger';
  }
  if (value < 0.85) {
    return 'warning';
  }
  return 'success';
}

async function goBack() {
  await router.push({ name: 'AiKnowledgeProcessing' });
}
</script>

<template>
  <Page title="OCR人工复核">
    <div class="review-page">
      <section class="toolbar-band">
        <div>
          <h2>{{ hasTaskNo ? taskNo : '未选择复核任务' }}</h2>
          <span v-if="review">整体置信度 {{ confidenceText }}</span>
          <span v-else>请从知识库处理进度进入复核</span>
        </div>
        <div class="toolbar-actions">
          <ElButton @click="goBack">返回</ElButton>
          <ElButton
            v-if="review"
            :loading="saving"
            type="primary"
            @click="saveDraft"
          >
            保存草稿
          </ElButton>
          <ElButton
            v-if="review"
            :loading="submitting"
            type="success"
            @click="submitReview"
          >
            确认提交
          </ElButton>
        </div>
      </section>

      <ElSkeleton v-if="loading" :rows="8" animated />

      <template v-else-if="review">
        <section class="summary-band">
          <div class="metric-item">
            <span>段落</span>
            <strong>{{ draft?.paragraphCount ?? 0 }}</strong>
          </div>
          <div class="metric-item">
            <span>警告</span>
            <strong>{{ draft?.metrics?.warningCount ?? 0 }}</strong>
          </div>
          <div class="metric-item">
            <span>低置信度</span>
            <strong>{{
              draft?.metrics?.lowConfidenceParagraphCount ?? 0
            }}</strong>
          </div>
          <div class="metric-item">
            <span>状态</span>
            <strong>{{ review.status }}</strong>
          </div>
        </section>

        <div class="review-layout">
          <section class="paragraph-panel">
            <ElScrollbar height="calc(100vh - 300px)">
              <article
                v-for="(paragraph, index) in paragraphs"
                :key="paragraph.paragraphNo"
                class="paragraph-item"
                :class="[
                  selectedParagraph?.paragraphNo === paragraph.paragraphNo &&
                    'is-active',
                ]"
                @click="selectParagraph(paragraph)"
              >
                <div class="paragraph-meta">
                  <strong>#{{ paragraph.paragraphNo }}</strong>
                  <ElTag
                    :type="confidenceType(paragraph.avgConfidence)"
                    effect="plain"
                    size="small"
                  >
                    {{ Math.round(paragraph.avgConfidence * 100) }}%
                  </ElTag>
                  <span>第 {{ paragraph.sourcePages.join(', ') }} 页</span>
                  <span>{{ paragraph.text.length }} 字</span>
                </div>
                <ElInput
                  v-model="paragraph.text"
                  :autosize="{ minRows: 3, maxRows: 8 }"
                  type="textarea"
                />
                <div class="paragraph-footer">
                  <div class="warning-list">
                    <ElTag
                      v-for="warning in paragraph.warnings"
                      :key="warning.type"
                      effect="plain"
                      size="small"
                      type="warning"
                    >
                      {{ warning.type }}
                    </ElTag>
                  </div>
                  <div class="paragraph-actions">
                    <ElButton
                      :disabled="index === 0"
                      link
                      type="primary"
                      @click.stop="moveParagraph(index, -1)"
                    >
                      上移
                    </ElButton>
                    <ElButton
                      :disabled="index === paragraphs.length - 1"
                      link
                      type="primary"
                      @click.stop="moveParagraph(index, 1)"
                    >
                      下移
                    </ElButton>
                    <ElButton
                      :disabled="index === paragraphs.length - 1"
                      link
                      type="primary"
                      @click.stop="mergeWithNext(index)"
                    >
                      合并
                    </ElButton>
                    <ElButton
                      link
                      type="primary"
                      @click.stop="copyParagraph(index)"
                    >
                      复制
                    </ElButton>
                    <ElButton
                      link
                      type="danger"
                      @click.stop="removeParagraph(index)"
                    >
                      删除
                    </ElButton>
                  </div>
                </div>
              </article>
            </ElScrollbar>
          </section>

          <aside class="image-panel">
            <div class="image-header">
              <div>
                <h2>第 {{ selectedPageNo ?? '-' }} 页</h2>
                <span>{{ selectedPage?.imageRef.objectKey ?? '-' }}</span>
              </div>
            </div>
            <div class="image-stage">
              <img
                v-if="selectedPage"
                :alt="`page-${selectedPage.pageNo}`"
                :src="selectedPage.imageUrl"
              />
              <ElEmpty v-else description="暂无页面图片" />
            </div>
          </aside>
        </div>
      </template>

      <ElEmpty v-else :description="emptyDescription">
        <ElButton type="primary" @click="goBack">返回处理队列</ElButton>
      </ElEmpty>
    </div>
  </Page>
</template>

<style scoped>
.review-page {
  padding: 4px 0 24px;
}

.toolbar-band,
.summary-band,
.paragraph-panel,
.image-panel {
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
}

.toolbar-band {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  margin-bottom: 16px;
}

.toolbar-band h2,
.image-header h2 {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 700;
}

.toolbar-band span,
.image-header span,
.metric-item span,
.paragraph-meta span {
  font-size: 13px;
  color: hsl(var(--muted-foreground));
}

.toolbar-actions {
  display: flex;
  gap: 10px;
}

.summary-band {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 12px;
  padding: 14px;
  margin-bottom: 16px;
}

.metric-item strong {
  display: block;
  margin-top: 8px;
  font-size: 24px;
}

.review-layout {
  display: grid;
  grid-template-columns: minmax(420px, 0.9fr) minmax(520px, 1.1fr);
  gap: 16px;
}

.paragraph-panel,
.image-panel {
  min-height: calc(100vh - 260px);
  padding: 14px;
}

.paragraph-item {
  padding: 12px;
  margin-bottom: 10px;
  cursor: pointer;
  background: hsl(var(--background));
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
}

.paragraph-item.is-active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-5);
}

.paragraph-meta,
.paragraph-footer {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.paragraph-footer {
  align-items: flex-start;
  margin-top: 10px;
  margin-bottom: 0;
}

.warning-list,
.paragraph-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.image-header {
  margin-bottom: 12px;
}

.image-stage {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  height: calc(100vh - 340px);
  overflow: auto;
  background: hsl(var(--muted) / 35%);
  border-radius: 8px;
}

.image-stage img {
  width: 100%;
  max-width: 100%;
  height: auto;
  object-fit: contain;
}

@media (max-width: 1180px) {
  .review-layout,
  .summary-band {
    grid-template-columns: 1fr;
  }
}
</style>
