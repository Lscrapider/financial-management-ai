<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type {
  StockAlertConfig,
  StockAlertStockOption,
} from '#/api/stock-alert';

import { computed, onMounted, reactive, ref } from 'vue';

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
  listStockAlerts,
  listStockAlertStockOptions,
  saveStockAlert,
} from '#/api/stock-alert';

interface AlertFormState {
  enabled: boolean;
  id?: string;
  stockCode: string;
  thresholdPercent: number;
}

const userStore = useUserStore();
const alerts = ref<StockAlertConfig[]>([]);
const stockOptions = ref<StockAlertStockOption[]>([]);
const loading = ref(false);
const saving = ref(false);
const checking = ref(false);
const dialogVisible = ref(false);
const formRef = ref<FormInstance>();
const formState = reactive<AlertFormState>({
  enabled: true,
  stockCode: '',
  thresholdPercent: 5,
});

const formRules: FormRules<AlertFormState> = {
  stockCode: [{ message: '请选择股票', required: true, trigger: 'change' }],
  thresholdPercent: [
    { message: '请输入涨跌幅阈值', required: true, trigger: 'blur' },
  ],
};

const isAdmin = computed(() => userStore.userInfo?.roles?.includes('admin'));

const notificationTip = computed(() => {
  const userInfo = userStore.userInfo as
    | (typeof userStore.userInfo & {
        email?: string;
        emailNotification?: boolean;
      })
    | null;
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

const stockOptionList = computed(() =>
  stockOptions.value.map((item) => ({
    label: `${item.stockName}(${item.stockCode})`,
    value: item.stockCode,
  })),
);

onMounted(() => {
  refreshData();
});

async function refreshData() {
  loading.value = true;
  try {
    const [alertRows, options] = await Promise.all([
      listStockAlerts(),
      listStockAlertStockOptions(),
    ]);
    alerts.value = alertRows;
    stockOptions.value = options;
  } finally {
    loading.value = false;
  }
}

function openCreateDialog() {
  Object.assign(formState, {
    enabled: true,
    id: undefined,
    stockCode: '',
    thresholdPercent: 5,
  });
  dialogVisible.value = true;
}

function openEditDialog(row: StockAlertConfig) {
  Object.assign(formState, {
    enabled: row.enabled,
    id: row.id,
    stockCode: row.stockCode,
    thresholdPercent: toNumber(row.thresholdPercent),
  });
  dialogVisible.value = true;
}

async function submitForm() {
  await formRef.value?.validate();
  saving.value = true;
  try {
    await saveStockAlert({
      enabled: formState.enabled,
      id: formState.id,
      stockCode: formState.stockCode,
      thresholdPercent: formState.thresholdPercent,
    });
    ElMessage.success('股票提醒配置已保存');
    dialogVisible.value = false;
    await refreshData();
  } finally {
    saving.value = false;
  }
}

async function removeAlert(row: StockAlertConfig) {
  await ElMessageBox.confirm(
    `确定删除 ${row.stockName}(${row.stockCode}) 的提醒配置吗？`,
    '删除确认',
    {
      confirmButtonText: '删除',
      type: 'warning',
    },
  );
  await deleteStockAlert(row.id);
  ElMessage.success('股票提醒配置已删除');
  await refreshData();
}

async function triggerCheck() {
  checking.value = true;
  try {
    await checkStockAlerts();
    ElMessage.success('已触发股票提醒检查');
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

function toNullableNumber(value?: number | string | null) {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
}

function toNumber(value?: number | string | null) {
  return toNullableNumber(value) ?? 0;
}
</script>

<template>
  <Page title="股票提醒">
    <div class="stock-alert-page">
      <section class="overview-band">
        <div>
          <div class="page-title">股票涨跌幅提醒</div>
          <div class="page-meta">
            关注股票并配置阈值，越界时按用户邮箱通知设置发送提醒。
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
            <span>关注配置</span>
            <ElSpace>
              <ElButton :loading="loading" @click="refreshData">
                <IconifyIcon icon="lucide:refresh-cw" />
                刷新
              </ElButton>
              <ElButton v-if="isAdmin" :loading="checking" @click="triggerCheck">
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

          <ElTableColumn label="股票" min-width="170" prop="stockName">
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
        <ElFormItem label="股票" prop="stockCode">
          <ElSelect
            v-model="formState.stockCode"
            :disabled="!!formState.id"
            filterable
            placeholder="请选择股票"
            class="w-full"
          >
            <ElOption
              v-for="item in stockOptionList"
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
  align-items: center;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  padding: 20px 24px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}

.page-meta {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-top: 8px;
}

.overview-stats {
  display: grid;
  gap: 24px;
  grid-template-columns: repeat(3, minmax(84px, 1fr));
  min-width: 360px;
}

.overview-stat {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.overview-stat span,
.stack-cell small {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.overview-stat strong {
  font-size: 24px;
  line-height: 1.15;
}

.panel-header {
  align-items: center;
  display: flex;
  font-weight: 600;
  gap: 12px;
  justify-content: space-between;
}

.stack-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.25;
}

.stock-alert-page :deep(.stock-alert-row-up) {
  --el-table-tr-bg-color: #7f1d1d;
  color: #fff7ed;
  box-shadow: inset 3px 0 0 #f97316;
}

.stock-alert-page :deep(.stock-alert-row-down) {
  --el-table-tr-bg-color: #14532d;
  color: #f0fdf4;
  box-shadow: inset 3px 0 0 #22c55e;
}

.stock-alert-page :deep(.stock-alert-row-up .el-table__cell),
.stock-alert-page :deep(.stock-alert-row-down .el-table__cell) {
  color: inherit;
}

.stock-alert-page :deep(.stock-alert-row-up small),
.stock-alert-page :deep(.stock-alert-row-down small) {
  color: rgb(255 255 255 / 70%);
}

@media (max-width: 768px) {
  .overview-band,
  .panel-header {
    align-items: stretch;
    flex-direction: column;
  }

  .overview-stats {
    min-width: 0;
  }
}
</style>
