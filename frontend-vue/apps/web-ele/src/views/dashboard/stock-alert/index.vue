<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { AlertTargetOption, StockAlertConfig } from '#/api/stock-alert';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import {
  ElAlert,
  ElButton,
  ElCard,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSpace,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  checkStockAlerts,
  deleteStockAlert,
  listAlertTargetOptions,
  listStockAlerts,
  saveStockAlert,
} from '#/api/stock-alert';

interface AlertFormState {
  enabled: boolean;
  id?: string;
  stockCode: string;
  targetType: string;
  thresholdPercent: number;
}

const TARGET_TYPES = [
  { label: '股票', value: 'STOCK' },
  { label: '指数', value: 'INDEX' },
  { label: '可转债', value: 'BOND' },
];

const userStore = useUserStore();
const alerts = ref<StockAlertConfig[]>([]);
const targetOptions = ref<AlertTargetOption[]>([]);
const loading = ref(false);
const saving = ref(false);
const checking = ref(false);
const dialogVisible = ref(false);
const filterTargetType = ref('');
const formRef = ref<FormInstance>();
const formState = reactive<AlertFormState>({
  enabled: true,
  stockCode: '',
  targetType: 'STOCK',
  thresholdPercent: 5,
});

const formRules: FormRules<AlertFormState> = {
  targetType: [{ message: '请选择类型', required: true, trigger: 'change' }],
  stockCode: [{ message: '请选择目标', required: true, trigger: 'change' }],
  thresholdPercent: [
    { message: '请输入涨跌幅阈值', required: true, trigger: 'blur' },
  ],
};

const isAdmin = computed(() => userStore.userInfo?.roles?.includes('admin'));

const notificationTip = computed(() => {
  const userInfo = userStore.userInfo as
    | null
    | (typeof userStore.userInfo & {
        email?: string;
        emailNotification?: boolean;
      });
  if (isAdmin.value) {
    return '';
  }
  if (!userInfo?.email) {
    return '当前账号尚未配置邮箱，越界条目仍会高亮，但不会发送邮件。';
  }
  if (userInfo.emailNotification === false) {
    return '当前账号已关闭邮件通知，越界条目仍会高亮，但不会发送邮件。';
  }
  return '';
});

const targetOptionList = computed(() =>
  targetOptions.value.map((item) => ({
    label: `${item.targetName}(${item.targetCode})`,
    value: item.targetCode,
  })),
);

onMounted(() => {
  refreshData();
});

watch(filterTargetType, () => {
  refreshData();
});

async function refreshData() {
  loading.value = true;
  try {
    alerts.value = await listStockAlerts(filterTargetType.value || undefined);
  } finally {
    loading.value = false;
  }
}

async function loadTargetOptions(targetType: string) {
  if (!targetType) {
    targetOptions.value = [];
    return;
  }
  targetOptions.value = await listAlertTargetOptions(targetType);
}

function onFormTypeChange(type: string) {
  formState.stockCode = '';
  loadTargetOptions(type);
}

function openCreateDialog() {
  Object.assign(formState, {
    enabled: true,
    id: undefined,
    stockCode: '',
    targetType: 'STOCK',
    thresholdPercent: 5,
  });
  loadTargetOptions('STOCK');
  dialogVisible.value = true;
}

function openEditDialog(row: StockAlertConfig) {
  Object.assign(formState, {
    enabled: row.enabled,
    id: row.id,
    stockCode: row.stockCode,
    targetType: row.targetType,
    thresholdPercent: toNumber(row.thresholdPercent),
  });
  loadTargetOptions(row.targetType);
  dialogVisible.value = true;
}

async function submitForm() {
  await formRef.value?.validate();
  saving.value = true;
  try {
    await saveStockAlert({
      enabled: formState.enabled,
      id: formState.id,
      targetType: formState.targetType,
      stockCode: formState.stockCode,
      thresholdPercent: formState.thresholdPercent,
    });
    ElMessage.success('提醒配置已保存');
    dialogVisible.value = false;
    await refreshData();
  } finally {
    saving.value = false;
  }
}

async function removeAlert(row: StockAlertConfig) {
  await ElMessageBox.confirm(
    `确定删除 ${typeLabel(row.targetType)} ${row.stockName}(${row.stockCode}) 的提醒配置吗？`,
    '删除确认',
    {
      confirmButtonText: '删除',
      type: 'warning',
    },
  );
  await deleteStockAlert(row.id);
  ElMessage.success('提醒配置已删除');
  await refreshData();
}

async function triggerCheck() {
  checking.value = true;
  try {
    await checkStockAlerts();
    ElMessage.success('已触发提醒检查');
    await refreshData();
  } finally {
    checking.value = false;
  }
}

function tableRowClassName({ row }: { row: StockAlertConfig }) {
  if (!row.outOfThreshold) {
    return '';
  }
  return toNumber(row.changePercent) >= 0
    ? 'stock-alert-row-up'
    : 'stock-alert-row-down';
}

function changeClass(value?: number | string) {
  const numberValue = toNumber(value);
  if (numberValue > 0) {
    return 'text-red-500';
  }
  if (numberValue < 0) {
    return 'text-emerald-500';
  }
  return '';
}

function changeTagType(value?: number | string) {
  const numberValue = toNumber(value);
  if (numberValue > 0) {
    return 'danger';
  }
  if (numberValue < 0) {
    return 'success';
  }
  return 'info';
}

function typeLabel(targetType?: string) {
  const found = TARGET_TYPES.find((t) => t.value === targetType);
  return found?.label ?? targetType ?? '-';
}

function typeTagType(targetType?: string) {
  switch (targetType) {
    case 'BOND': {
      return 'success';
    }
    case 'INDEX': {
      return 'warning';
    }
    case 'STOCK': {
      return 'primary';
    }
    default: {
      return 'info';
    }
  }
}

function formatPercent(value?: number | string) {
  const numberValue = toNullableNumber(value);
  if (numberValue === null) {
    return '-';
  }
  return `${numberValue > 0 ? '+' : ''}${numberValue.toFixed(2)}%`;
}

function formatThreshold(value?: number | string) {
  const numberValue = toNullableNumber(value);
  return numberValue === null ? '-' : `${numberValue.toFixed(2)}%`;
}

function formatPrice(value?: number | string) {
  const numberValue = toNullableNumber(value);
  return numberValue === null ? '-' : numberValue.toFixed(2);
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 19);
}

function toNullableNumber(value?: null | number | string) {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
}

function toNumber(value?: null | number | string) {
  return toNullableNumber(value) ?? 0;
}
</script>

<template>
  <Page title="涨跌幅提醒">
    <div class="stock-alert-page">
      <section class="overview-band">
        <div>
          <div class="page-title">涨跌幅提醒</div>
          <div class="page-meta">
            关注股票/指数/可转债并配置阈值，越界时按用户邮箱通知发送提醒。
          </div>
        </div>
        <div class="overview-stats">
          <div class="overview-stat">
            <span>关注数量</span>
            <strong>{{ alerts.length }}</strong>
          </div>
          <div class="overview-stat">
            <span>越界数量</span>
            <strong class="text-red-500">
              {{ alerts.filter((item) => item.outOfThreshold).length }}
            </strong>
          </div>
          <div class="overview-stat">
            <span>启用数量</span>
            <strong>
              {{ alerts.filter((item) => item.enabled).length }}
            </strong>
          </div>
        </div>
      </section>

      <ElAlert
        v-if="notificationTip"
        :closable="false"
        show-icon
        type="warning"
      >
        {{ notificationTip }}
      </ElAlert>

      <ElCard shadow="never">
        <template #header>
          <div class="panel-header">
            <div class="header-left">
              <span>关注配置</span>
              <ElSelect
                v-model="filterTargetType"
                class="type-filter"
                clearable
                placeholder="全部类型"
                size="small"
              >
                <ElOption label="全部" value="" />
                <ElOption
                  v-for="item in TARGET_TYPES"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </div>
            <ElSpace>
              <ElButton :loading="loading" @click="refreshData">
                <IconifyIcon icon="lucide:refresh-cw" />
                刷新
              </ElButton>
              <ElButton
                v-if="isAdmin"
                :loading="checking"
                @click="triggerCheck"
              >
                <IconifyIcon icon="lucide:send" />
                触发检查
              </ElButton>
              <ElButton type="primary" @click="openCreateDialog">
                <IconifyIcon icon="lucide:plus" />
                新增关注
              </ElButton>
            </ElSpace>
          </div>
        </template>

        <ElTable
          v-loading="loading"
          :data="alerts"
          :row-class-name="tableRowClassName"
          row-key="id"
          stripe
        >
          <ElTableColumn
            v-if="isAdmin"
            label="用户"
            min-width="160"
            prop="username"
          >
            <template #default="{ row }">
              <div class="stack-cell">
                <span>{{ row.realName || row.username || '-' }}</span>
                <small>{{ row.username }}</small>
              </div>
            </template>
          </ElTableColumn>

          <ElTableColumn
            v-if="isAdmin"
            label="邮箱通知"
            min-width="220"
            prop="email"
          >
            <template #default="{ row }">
              <div class="stack-cell">
                <span>{{ row.email || '-' }}</span>
                <ElTag
                  :type="row.emailNotification ? 'success' : 'info'"
                  effect="plain"
                  size="small"
                >
                  {{ row.emailNotification ? '已开启' : '未开启' }}
                </ElTag>
              </div>
            </template>
          </ElTableColumn>

          <ElTableColumn label="类型" min-width="90" prop="targetType">
            <template #default="{ row }">
              <ElTag
                :type="typeTagType(row.targetType)"
                effect="plain"
                size="small"
              >
                {{ typeLabel(row.targetType) }}
              </ElTag>
            </template>
          </ElTableColumn>

          <ElTableColumn label="名称" min-width="170" prop="stockName">
            <template #default="{ row }">
              <div class="stack-cell">
                <span>{{ row.stockName }}</span>
                <small>{{ row.stockCode }}</small>
              </div>
            </template>
          </ElTableColumn>

          <ElTableColumn align="right" label="最新价" min-width="100">
            <template #default="{ row }">
              <span :class="changeClass(row.changePercent)">
                {{ formatPrice(row.latestPrice) }}
              </span>
            </template>
          </ElTableColumn>

          <ElTableColumn align="right" label="涨跌幅" min-width="150">
            <template #default="{ row }">
              <ElTag :type="changeTagType(row.changePercent)" effect="plain">
                {{ formatPercent(row.changePercent) }}
              </ElTag>
              <ElTag v-if="row.outOfThreshold" class="ml-2" type="warning">
                越界
              </ElTag>
            </template>
          </ElTableColumn>

          <ElTableColumn align="right" label="阈值" min-width="100">
            <template #default="{ row }">
              {{ formatThreshold(row.thresholdPercent) }}
            </template>
          </ElTableColumn>

          <ElTableColumn label="配置状态" min-width="100">
            <template #default="{ row }">
              <ElTag :type="row.enabled ? 'primary' : 'info'" effect="plain">
                {{ row.enabled ? '启用' : '停用' }}
              </ElTag>
            </template>
          </ElTableColumn>

          <ElTableColumn label="行情时间" min-width="170">
            <template #default="{ row }">
              {{ formatDateTime(row.syncedAt) }}
            </template>
          </ElTableColumn>

          <ElTableColumn label="最近提醒" min-width="170">
            <template #default="{ row }">
              {{ formatDateTime(row.lastAlertedAt) }}
            </template>
          </ElTableColumn>

          <ElTableColumn fixed="right" label="操作" width="150">
            <template #default="{ row }">
              <ElButton link type="primary" @click="openEditDialog(row)">
                编辑
              </ElButton>
              <ElButton link type="danger" @click="removeAlert(row)">
                删除
              </ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
      </ElCard>
    </div>

    <ElDialog
      v-model="dialogVisible"
      :title="formState.id ? '编辑关注' : '新增关注'"
      width="460px"
    >
      <ElForm
        ref="formRef"
        :model="formState"
        :rules="formRules"
        label-position="top"
      >
        <ElFormItem label="类型" prop="targetType">
          <ElSelect
            v-model="formState.targetType"
            :disabled="!!formState.id"
            class="w-full"
            @change="onFormTypeChange"
          >
            <ElOption
              v-for="item in TARGET_TYPES"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>

        <ElFormItem label="目标" prop="stockCode">
          <ElSelect
            v-model="formState.stockCode"
            :disabled="!!formState.id"
            filterable
            placeholder="请选择目标"
            class="w-full"
          >
            <ElOption
              v-for="item in targetOptionList"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>

        <ElFormItem label="涨跌幅阈值" prop="thresholdPercent">
          <ElInputNumber
            v-model="formState.thresholdPercent"
            :max="100"
            :min="0.01"
            :precision="2"
            :step="0.5"
            class="w-full"
          />
        </ElFormItem>

        <ElFormItem label="启用提醒" prop="enabled">
          <ElSwitch v-model="formState.enabled" />
        </ElFormItem>
      </ElForm>

      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton :loading="saving" type="primary" @click="submitForm">
          保存
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>

<style scoped>
.stock-alert-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.overview-band {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}

.page-meta {
  margin-top: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.overview-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(84px, 1fr));
  gap: 24px;
  min-width: 360px;
}

.overview-stat {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.overview-stat span,
.stack-cell small {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.overview-stat strong {
  font-size: 24px;
  line-height: 1.15;
}

.panel-header {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.header-left {
  display: flex;
  gap: 12px;
  align-items: center;
}

.type-filter {
  width: 120px;
}

.stack-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.25;
}

.stock-alert-page :deep(.stock-alert-row-up) {
  --el-table-row-hover-bg-color: rgb(220 68 70 / 28%);
  --el-table-tr-bg-color: rgb(220 68 70 / 18%);
  --stock-alert-row-border: rgb(220 68 70 / 55%);

  color: var(--el-text-color-primary);
  outline: 1px solid var(--stock-alert-row-border);
  outline-offset: -1px;
}

.stock-alert-page :deep(.stock-alert-row-down) {
  --el-table-row-hover-bg-color: rgb(87 209 136 / 24%);
  --el-table-tr-bg-color: rgb(87 209 136 / 16%);
  --stock-alert-row-border: rgb(87 209 136 / 48%);

  color: var(--el-text-color-primary);
  outline: 1px solid var(--stock-alert-row-border);
  outline-offset: -1px;
}

.stock-alert-page :deep(.stock-alert-row-up .el-table__cell),
.stock-alert-page :deep(.stock-alert-row-down .el-table__cell) {
  color: inherit;
}

.stock-alert-page :deep(.stock-alert-row-up small),
.stock-alert-page :deep(.stock-alert-row-down small) {
  color: var(--el-text-color-secondary);
}

.stock-alert-page :deep(.stock-alert-row-up .el-tag--warning),
.stock-alert-page :deep(.stock-alert-row-down .el-tag--warning) {
  color: var(--el-text-color-primary);
  background-color: rgb(239 189 72 / 16%);
  border-color: rgb(239 189 72 / 58%);
}

@media (max-width: 768px) {
  .overview-band,
  .panel-header {
    flex-direction: column;
    align-items: stretch;
  }

  .overview-stats {
    min-width: 0;
  }
}
</style>
