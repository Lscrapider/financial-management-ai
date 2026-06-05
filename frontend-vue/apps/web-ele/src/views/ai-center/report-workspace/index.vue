<script lang="ts" setup>
import type {
  WorkbenchComponentType,
  WorkbenchItem,
  WorkbenchLayout,
  WorkbenchTargetType,
} from './components/types';

import type {
  SceneAnalysisReportHistory,
  SceneAnalysisTargetOption,
} from '#/api/scene-analysis';

import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElCard,
  ElDialog,
  ElEmpty,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';
import { GridItem, GridLayout } from 'grid-layout-plus';

import {
  listSceneReportHistory,
  searchSceneAnalysisTargets,
} from '#/api/scene-analysis';

import MarketDetailWidget from './components/MarketDetailWidget.vue';
import MarketQuoteWidget from './components/MarketQuoteWidget.vue';
import MarketTrendWidget from './components/MarketTrendWidget.vue';
import ReportBodyWidget from './components/ReportBodyWidget.vue';

interface ComponentOption {
  description: string;
  label: string;
  value: WorkbenchComponentType;
}

interface DraftConfig {
  componentType?: WorkbenchComponentType;
  reportId?: number;
  targetCode?: string;
  targetName?: string;
  targetType: WorkbenchTargetType;
}

const route = useRoute();

const layout = ref<WorkbenchLayout>('2x2');
const gridColumnCount = 12;
const gridRowHeight = 70;
const gridMargin: [number, number] = [14, 14];
const defaultItemHeight = 6;
const minItemHeight = 3;
const minItemWidth = 3;
const workbenchItems = ref<WorkbenchItem[]>(createTemplateItems('2x2'));
const configDialogVisible = ref(false);
const activeItemId = ref('');
const targetOptions = ref<SceneAnalysisTargetOption[]>([]);
const loadingTargets = ref(false);
const reportHistories = ref<SceneAnalysisReportHistory[]>([]);
const loadingHistories = ref(false);
const draft = ref<DraftConfig>({
  targetType: 'STOCK',
});

const layoutOptions: Array<{ label: string; value: WorkbenchLayout }> = [
  { label: '2 x 1', value: '2x1' },
  { label: '2 x 2', value: '2x2' },
  { label: '2 x 3', value: '2x3' },
  { label: '3 x 2', value: '3x2' },
  { label: '3 x 3', value: '3x3' },
];

const componentOptions: ComponentOption[] = [
  {
    description: '展示某个标的的一份历史分析报告正文',
    label: '报告主体',
    value: 'report',
  },
  {
    description: '展示标的分时或 K 线走势',
    label: '走势图',
    value: 'trend',
  },
  {
    description: '展示行情页面同口径盘口指标',
    label: '盘口数据',
    value: 'quote',
  },
  {
    description: '展示行情页面“更多”详情数据',
    label: '详情数据',
    value: 'detail',
  },
];

const targetTypeOptions: Array<{ label: string; value: WorkbenchTargetType }> = [
  { label: '股票', value: 'STOCK' },
  { label: '指数', value: 'INDEX' },
  { label: '可转债', value: 'CONVERTIBLE_BOND' },
];

onMounted(() => {
  const reportId = Number(route.query.reportId);
  if (Number.isFinite(reportId) && reportId > 0) {
    workbenchItems.value[0] = {
      ...workbenchItems.value[0],
      componentType: 'report',
      reportId,
    };
  }
});

function applyLayout(value: WorkbenchLayout) {
  layout.value = value;
  workbenchItems.value = createTemplateItems(value, workbenchItems.value);
}

function componentLabel(value?: WorkbenchComponentType) {
  return componentOptions.find((item) => item.value === value)?.label ?? '空组件';
}

function displayTarget(item?: WorkbenchItem) {
  if (!item?.targetCode) {
    return '未选择标的';
  }
  return item.targetName
    ? `${item.targetName} ${item.targetCode}`
    : item.targetCode;
}

function openConfig(itemId: string) {
  activeItemId.value = itemId;
  const current = findItem(itemId);
  draft.value = {
    componentType: current?.componentType,
    reportId: current?.reportId,
    targetCode: current?.targetCode,
    targetName: current?.targetName,
    targetType: current?.targetType ?? 'STOCK',
  };
  targetOptions.value = current?.targetCode
    ? [
        {
          targetCode: current.targetCode,
          targetName: current.targetName,
          targetType: current.targetType ?? 'STOCK',
        },
      ]
    : [];
  reportHistories.value = [];
  configDialogVisible.value = true;
  if (draft.value.componentType === 'report' && draft.value.targetCode) {
    void loadReportHistories();
  }
}

function clearCell(itemId = activeItemId.value) {
  const current = findItem(itemId);
  if (current) {
    delete current.componentType;
    delete current.reportId;
    delete current.targetCode;
    delete current.targetName;
    delete current.targetType;
  }
  configDialogVisible.value = false;
}

function saveCell() {
  const current = findItem(activeItemId.value);
  if (!current) {
    return;
  }
  if (!draft.value.componentType) {
    return;
  }
  if (draft.value.componentType === 'report') {
    if (!draft.value.reportId) {
      return;
    }
    Object.assign(current, {
      componentType: 'report',
      reportId: draft.value.reportId,
      targetCode: draft.value.targetCode,
      targetName: draft.value.targetName,
      targetType: draft.value.targetType,
    });
    configDialogVisible.value = false;
    return;
  }
  if (!draft.value.targetCode) {
    return;
  }
  Object.assign(current, {
    componentType: draft.value.componentType,
    targetCode: draft.value.targetCode,
    targetName: draft.value.targetName,
    targetType: draft.value.targetType,
  });
  configDialogVisible.value = false;
}

async function searchTargets(keyword: string) {
  loadingTargets.value = true;
  try {
    targetOptions.value = await searchSceneAnalysisTargets({
      keyword,
      limit: 30,
      targetType: draft.value.targetType,
    });
  } finally {
    loadingTargets.value = false;
  }
}

async function onTargetTypeChange() {
  draft.value.targetCode = undefined;
  draft.value.targetName = undefined;
  draft.value.reportId = undefined;
  targetOptions.value = [];
  reportHistories.value = [];
  await searchTargets('');
}

async function onTargetChange(targetCode: string) {
  const selected = targetOptions.value.find((item) => item.targetCode === targetCode);
  draft.value.targetName = selected?.targetName ?? targetCode;
  draft.value.reportId = undefined;
  if (draft.value.componentType === 'report') {
    await loadReportHistories();
  }
}

async function loadReportHistories() {
  if (!draft.value.targetCode) {
    reportHistories.value = [];
    return;
  }
  loadingHistories.value = true;
  try {
    reportHistories.value = await listSceneReportHistory(
      draft.value.targetType,
      draft.value.targetCode,
    );
  } finally {
    loadingHistories.value = false;
  }
}

function formatDateTime(value?: null | string) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 19);
}

function createTemplateItems(
  value: WorkbenchLayout,
  previousItems: WorkbenchItem[] = [],
) {
  const [columns = 2, rows = 2] = value.split('x').map(Number);
  const itemWidth = gridColumnCount / columns;
  return Array.from({ length: columns * rows }, (_, index) => {
    const previous = previousItems[index];
    return {
      componentType: previous?.componentType,
      h: defaultItemHeight,
      i: `cell-${index + 1}`,
      minH: minItemHeight,
      minW: minItemWidth,
      reportId: previous?.reportId,
      targetCode: previous?.targetCode,
      targetName: previous?.targetName,
      targetType: previous?.targetType,
      w: itemWidth,
      x: (index % columns) * itemWidth,
      y: Math.floor(index / columns) * defaultItemHeight,
    };
  });
}

function findItem(itemId: string) {
  return workbenchItems.value.find((item) => item.i === itemId);
}
</script>

<template>
  <Page title="报告工作台">
    <div class="report-workbench">
      <section class="workbench-toolbar">
        <div>
          <h2>报告工作台</h2>
          <span>选择初始布局后，可拖动卡片位置并拖拽右下角调整大小</span>
        </div>
        <ElRadioGroup :model-value="layout" size="small" @change="applyLayout">
          <ElRadioButton
            v-for="item in layoutOptions"
            :key="item.value"
            :value="item.value"
          >
            {{ item.label }}
          </ElRadioButton>
        </ElRadioGroup>
      </section>

      <GridLayout
        v-model:layout="workbenchItems"
        :col-num="gridColumnCount"
        :is-bounded="true"
        :is-draggable="true"
        :is-resizable="true"
        :margin="gridMargin"
        :prevent-collision="false"
        :row-height="gridRowHeight"
        :vertical-compact="true"
        class="workbench-grid"
      >
        <GridItem
          v-for="item in workbenchItems"
          :key="item.i"
          :h="item.h"
          :i="item.i"
          :min-h="item.minH"
          :min-w="item.minW"
          :w="item.w"
          :x="item.x"
          :y="item.y"
          drag-allow-from=".cell-drag-handle"
        >
          <ElCard class="workbench-cell" shadow="never">
            <template #header>
              <div class="cell-header cell-drag-handle">
                <div>
                  <strong>{{ componentLabel(item.componentType) }}</strong>
                  <span>{{ displayTarget(item) }}</span>
                </div>
                <ElButton link type="primary" @click.stop="openConfig(item.i)">
                  {{ item.componentType ? '配置' : '添加组件' }}
                </ElButton>
              </div>
            </template>

            <ReportBodyWidget
              v-if="item.componentType === 'report'"
              :report-id="item.reportId"
            />
            <MarketTrendWidget
              v-else-if="item.componentType === 'trend'"
              :target-code="item.targetCode"
              :target-name="item.targetName"
              :target-type="item.targetType"
            />
            <MarketQuoteWidget
              v-else-if="item.componentType === 'quote'"
              :target-code="item.targetCode"
              :target-name="item.targetName"
              :target-type="item.targetType"
            />
            <MarketDetailWidget
              v-else-if="item.componentType === 'detail'"
              :target-code="item.targetCode"
              :target-name="item.targetName"
              :target-type="item.targetType"
            />
            <button v-else class="empty-cell" type="button" @click="openConfig(item.i)">
              <strong>添加组件</strong>
              <span>报告主体 / 走势图 / 盘口数据 / 详情数据</span>
            </button>
          </ElCard>
        </GridItem>
      </GridLayout>

      <ElDialog v-model="configDialogVisible" title="配置工作台组件" width="760px">
        <div class="config-panel">
          <section>
            <h3>选择组件</h3>
            <div class="component-options">
              <button
                v-for="item in componentOptions"
                :key="item.value"
                :class="{ active: draft.componentType === item.value }"
                type="button"
                @click="draft.componentType = item.value"
              >
                <strong>{{ item.label }}</strong>
                <span>{{ item.description }}</span>
              </button>
            </div>
          </section>

          <section v-if="draft.componentType">
            <h3>选择标的</h3>
            <div class="target-config">
              <ElSelect
                v-model="draft.targetType"
                size="small"
                @change="onTargetTypeChange"
              >
                <ElOption
                  v-for="item in targetTypeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
              <ElSelect
                v-model="draft.targetCode"
                :loading="loadingTargets"
                filterable
                remote
                reserve-keyword
                size="small"
                @change="onTargetChange"
                @focus="searchTargets('')"
                @remote-method="searchTargets"
              >
                <ElOption
                  v-for="item in targetOptions"
                  :key="item.targetCode"
                  :label="
                    item.targetName
                      ? `${item.targetName} ${item.targetCode}`
                      : item.targetCode
                  "
                  :value="item.targetCode"
                />
              </ElSelect>
            </div>
          </section>

          <section v-if="draft.componentType === 'report'">
            <h3>选择历史报告</h3>
            <ElTable
              v-loading="loadingHistories"
              :data="reportHistories"
              height="260"
              highlight-current-row
              @row-click="(row) => (draft.reportId = row.reportId)"
            >
              <ElTableColumn width="70">
                <template #default="{ row }">
                  <ElButton
                    link
                    type="primary"
                    @click.stop="draft.reportId = row.reportId"
                  >
                    {{ draft.reportId === row.reportId ? '已选' : '选择' }}
                  </ElButton>
                </template>
              </ElTableColumn>
              <ElTableColumn label="版本" width="90">
                <template #default="{ row }">#{{ row.versionNo }}</template>
              </ElTableColumn>
              <ElTableColumn label="类型" prop="reportType" min-width="140" />
              <ElTableColumn label="模型" prop="model" min-width="120" />
              <ElTableColumn label="生成时间" width="170">
                <template #default="{ row }">
                  {{ formatDateTime(row.generatedAt || row.createdAt) }}
                </template>
              </ElTableColumn>
            </ElTable>
            <ElEmpty
              v-if="draft.targetCode && reportHistories.length === 0 && !loadingHistories"
              description="暂无历史报告"
            />
          </section>
        </div>

        <template #footer>
          <ElButton @click="configDialogVisible = false">取消</ElButton>
          <ElButton type="danger" @click="clearCell()">清空格子</ElButton>
          <ElButton type="primary" @click="saveCell">保存</ElButton>
        </template>
      </ElDialog>
    </div>
  </Page>
</template>

<style scoped>
.report-workbench {
  background: var(--el-bg-color-page);
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 100%;
}

.workbench-toolbar {
  align-items: center;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  padding: 14px 16px;
}

.workbench-toolbar h2 {
  font-size: 18px;
  font-weight: 700;
  margin: 0;
}

.workbench-toolbar span,
.cell-header span,
.empty-cell span,
.component-options span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.workbench-grid {
  background: var(--el-bg-color-page);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  min-height: 520px;
  --vgl-placeholder-bg: var(--el-color-primary-light-7);
}

.workbench-cell {
  background: var(--el-bg-color);
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
  overflow: hidden;
}

.workbench-cell :deep(.el-card__header) {
  background: var(--el-fill-color-light);
  flex: 0 0 auto;
}

.workbench-cell :deep(.el-card__body) {
  background: var(--el-bg-color);
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.cell-header {
  align-items: center;
  cursor: move;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  user-select: none;
}

.cell-header div {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.empty-cell {
  align-items: center;
  background: var(--el-fill-color-lighter);
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  color: inherit;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: 100%;
  justify-content: center;
  width: 100%;
}

.config-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.config-panel h3 {
  font-size: 14px;
  font-weight: 700;
  margin: 0 0 10px;
}

.component-options {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.component-options button {
  background: var(--el-fill-color-lighter);
  border: 1px solid transparent;
  border-radius: 6px;
  color: inherit;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 82px;
  padding: 12px;
  text-align: left;
}

.component-options button.active {
  border-color: var(--el-color-primary);
}

.target-config {
  display: grid;
  gap: 10px;
  grid-template-columns: 140px 1fr;
}

@media (max-width: 1200px) {
  .workbench-grid,
  .component-options,
  .target-config {
    grid-template-columns: 1fr !important;
  }

  .workbench-toolbar {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }
}
</style>
