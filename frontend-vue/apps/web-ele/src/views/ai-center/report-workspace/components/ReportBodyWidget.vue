<script lang="ts" setup>
import type { SceneAnalysisReportDetail } from '#/api/scene-analysis';

import { computed, onMounted, ref, watch } from 'vue';

import { ElEmpty, ElTag } from 'element-plus';

import { getSceneReportDetail } from '#/api/scene-analysis';

const props = defineProps<{
  reportId?: number;
}>();

const loading = ref(false);
const report = ref<SceneAnalysisReportDetail>();

const reportTitle = computed(() => {
  if (!report.value) {
    return '报告主体';
  }
  return report.value.targetName
    ? `${report.value.targetName} ${report.value.targetCode}`
    : report.value.targetCode;
});

const renderedReportHtml = computed(() => renderMarkdown(report.value?.reportText));

onMounted(loadReport);

watch(
  () => props.reportId,
  () => {
    void loadReport();
  },
);

async function loadReport() {
  if (!props.reportId) {
    report.value = undefined;
    return;
  }
  loading.value = true;
  try {
    report.value = await getSceneReportDetail(props.reportId);
  } finally {
    loading.value = false;
  }
}

function formatDateTime(value?: null | string) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 19);
}

function renderMarkdown(markdown?: null | string) {
  if (!markdown) {
    return '<p>暂无报告正文</p>';
  }
  const blocks: string[] = [];
  let listItems: string[] = [];

  const flushList = () => {
    if (listItems.length === 0) {
      return;
    }
    blocks.push(`<ul>${listItems.join('')}</ul>`);
    listItems = [];
  };

  markdown.split(/\r?\n/).forEach((line) => {
    const text = line.trim();
    if (!text) {
      flushList();
      return;
    }
    if (text.startsWith('# ')) {
      flushList();
      blocks.push(`<h1>${renderInlineMarkdown(text.slice(2))}</h1>`);
      return;
    }
    if (text.startsWith('## ')) {
      flushList();
      blocks.push(`<h2>${renderInlineMarkdown(text.slice(3))}</h2>`);
      return;
    }
    if (text.startsWith('### ')) {
      flushList();
      blocks.push(`<h3>${renderInlineMarkdown(text.slice(4))}</h3>`);
      return;
    }
    if (text.startsWith('- ')) {
      listItems.push(`<li>${renderInlineMarkdown(text.slice(2))}</li>`);
      return;
    }
    flushList();
    blocks.push(`<p>${renderInlineMarkdown(text)}</p>`);
  });
  flushList();
  return blocks.join('');
}

function renderInlineMarkdown(text: string) {
  return escapeHtml(text).replaceAll(
    /（引用：[^）]+）/g,
    (reference) => `<strong class="report-reference">${reference}</strong>`,
  );
}

function escapeHtml(value: string) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}
</script>

<template>
  <div v-loading="loading" class="report-body-widget">
    <template v-if="report">
      <header class="widget-summary">
        <div>
          <strong>{{ reportTitle }}</strong>
          <span>{{ report.reportType }} · #{{ report.versionNo }}</span>
        </div>
        <ElTag effect="plain" size="small">
          {{ formatDateTime(report.generatedAt || report.createdAt) }}
        </ElTag>
      </header>
      <article class="markdown-report" v-html="renderedReportHtml"></article>
    </template>
    <ElEmpty v-else description="请选择具体报告" />
  </div>
</template>

<style scoped>
.report-body-widget {
  height: 100%;
  min-height: 0;
  overflow: auto;
}

.widget-summary {
  align-items: flex-start;
  border-bottom: 1px solid var(--el-border-color-lighter);
  display: flex;
  gap: 12px;
  justify-content: space-between;
  margin-bottom: 12px;
  padding-bottom: 10px;
}

.widget-summary div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.widget-summary span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.markdown-report {
  color: var(--el-text-color-primary);
  font-size: 14px;
  line-height: 1.75;
}

.markdown-report :deep(h1),
.markdown-report :deep(h2),
.markdown-report :deep(h3) {
  font-weight: 700;
  margin: 14px 0 8px;
}

.markdown-report :deep(p),
.markdown-report :deep(ul) {
  margin: 8px 0;
}

.markdown-report :deep(.report-reference) {
  color: var(--el-color-primary);
  font-weight: 600;
}
</style>
