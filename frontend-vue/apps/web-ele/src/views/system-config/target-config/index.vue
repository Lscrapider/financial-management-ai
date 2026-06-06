<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import { computed, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElPopconfirm,
  ElSelect,
  ElTag,
} from 'element-plus';

import { addBondConfig } from '#/api/bond';
import { deleteTargetConfig } from '#/api/system-config';
import { addStockConfig } from '#/api/stock';

type DeleteTargetType = 'BOND' | 'INDEX' | 'STOCK';

const addingBond = ref(false);
const addingStock = ref(false);
const deletingTarget = ref(false);
const bondAddFormRef = ref<FormInstance>();
const stockAddFormRef = ref<FormInstance>();
const deleteFormRef = ref<FormInstance>();

const bondAddForm = reactive({
  bondCode: '',
  bondName: '',
});
const stockAddForm = reactive({
  stockCode: '',
  stockName: '',
});
const deleteForm = reactive<{
  targetCode: string;
  targetType: DeleteTargetType;
}>({
  targetCode: '',
  targetType: 'STOCK',
});

const bondAddRules: FormRules = {
  bondCode: [
    { message: '请输入可转债代码', required: true, trigger: 'blur' },
    {
      message: '可转债代码必须是 6 位数字',
      pattern: /^\d{6}$/,
      trigger: 'blur',
    },
  ],
  bondName: [
    { message: '请输入可转债名称', required: true, trigger: 'blur' },
  ],
};
const stockAddRules: FormRules = {
  stockCode: [
    { message: '请输入股票代码', required: true, trigger: 'blur' },
    {
      message: '股票代码必须是 6 位数字',
      pattern: /^\d{6}$/,
      trigger: 'blur',
    },
  ],
  stockName: [
    { message: '请输入股票名称', required: true, trigger: 'blur' },
  ],
};
const deleteRules: FormRules = {
  targetCode: [
    { message: '请输入标的代码', required: true, trigger: 'blur' },
    {
      message: '标的代码必须是 6 位数字',
      pattern: /^\d{6}$/,
      trigger: 'blur',
    },
  ],
  targetType: [
    { message: '请选择标的类型', required: true, trigger: 'change' },
  ],
};

const deleteTypeLabel = computed(() => labelOf(deleteForm.targetType));

async function submitStockAdd() {
  if (addingStock.value) return;
  const valid = await stockAddFormRef.value?.validate();
  if (!valid) return;
  addingStock.value = true;
  try {
    const result = await addStockConfig({
      stockCode: stockAddForm.stockCode.trim(),
      stockName: stockAddForm.stockName.trim(),
    });
    const trendMessage = result.trendSynced ? '分时同步完成' : '分时同步未完成';
    ElMessage.success(`${result.stockName} 快照同步完成，${trendMessage}`);
    stockAddForm.stockCode = '';
    stockAddForm.stockName = '';
    stockAddFormRef.value?.clearValidate();
  } finally {
    addingStock.value = false;
  }
}

async function submitBondAdd() {
  if (addingBond.value) return;
  const valid = await bondAddFormRef.value?.validate();
  if (!valid) return;
  addingBond.value = true;
  try {
    const result = await addBondConfig({
      bondCode: bondAddForm.bondCode.trim(),
      bondName: bondAddForm.bondName.trim(),
    });
    const stockText = result.underlyingStockName
      ? `，正股 ${result.underlyingStockName} 已同步`
      : '';
    ElMessage.success(`${result.bondName} 同步完成${stockText}`);
    bondAddForm.bondCode = '';
    bondAddForm.bondName = '';
    bondAddFormRef.value?.clearValidate();
  } finally {
    addingBond.value = false;
  }
}

async function submitTargetDelete() {
  if (deletingTarget.value) return;
  const valid = await deleteFormRef.value?.validate();
  if (!valid) return;
  deletingTarget.value = true;
  try {
    await deleteTargetConfig({
      targetCode: deleteForm.targetCode.trim(),
      targetType: deleteForm.targetType,
    });
    ElMessage.success(`${deleteTypeLabel.value} ${deleteForm.targetCode.trim()} 已物理删除`);
    deleteForm.targetCode = '';
    deleteFormRef.value?.clearValidate();
  } finally {
    deletingTarget.value = false;
  }
}

function labelOf(targetType: DeleteTargetType) {
  if (targetType === 'STOCK') {
    return '股票';
  }
  if (targetType === 'BOND') {
    return '可转债';
  }
  return '指数';
}
</script>

<template>
  <Page title="标的配置">
    <div class="target-config-page">
      <section class="target-card-grid">
        <ElCard class="target-card" shadow="never">
          <div class="target-card-header">
            <div>
              <h2>新增股票</h2>
              <p>校验股票代码和名称，补齐腾讯快照与单只股票分时。</p>
            </div>
            <ElTag effect="plain" size="small">股票</ElTag>
          </div>

          <ElForm
            ref="stockAddFormRef"
            class="target-form"
            label-position="top"
            :model="stockAddForm"
            :rules="stockAddRules"
          >
            <ElFormItem label="股票代码" prop="stockCode">
              <ElInput
                v-model="stockAddForm.stockCode"
                clearable
                maxlength="6"
                placeholder="例如 000001"
              />
            </ElFormItem>
            <ElFormItem label="股票名称" prop="stockName">
              <ElInput
                v-model="stockAddForm.stockName"
                clearable
                placeholder="例如 平安银行"
                @keyup.enter="submitStockAdd"
              />
            </ElFormItem>
            <ElButton
              class="target-form-button"
              :loading="addingStock"
              type="primary"
              @click="submitStockAdd"
            >
              新增并同步
            </ElButton>
          </ElForm>
        </ElCard>

        <ElCard class="target-card" shadow="never">
          <div class="target-card-header">
            <div>
              <h2>新增可转债</h2>
              <p>同步 Tushare 基础资料、正股数据、转债行情和专属估值数据。</p>
            </div>
            <ElTag effect="plain" size="small">可转债</ElTag>
          </div>

          <ElForm
            ref="bondAddFormRef"
            class="target-form"
            label-position="top"
            :model="bondAddForm"
            :rules="bondAddRules"
          >
            <ElFormItem label="可转债代码" prop="bondCode">
              <ElInput
                v-model="bondAddForm.bondCode"
                clearable
                maxlength="6"
                placeholder="例如 113665"
              />
            </ElFormItem>
            <ElFormItem label="可转债名称" prop="bondName">
              <ElInput
                v-model="bondAddForm.bondName"
                clearable
                placeholder="例如 汇通转债"
                @keyup.enter="submitBondAdd"
              />
            </ElFormItem>
            <ElButton
              class="target-form-button"
              :loading="addingBond"
              type="primary"
              @click="submitBondAdd"
            >
              新增并同步
            </ElButton>
          </ElForm>
        </ElCard>

        <ElCard class="target-card reserved-card" shadow="never">
          <div class="target-card-header">
            <div>
              <h2>新增指数</h2>
              <p>预留指数新增入口，后续接入指数代码和市场校验。</p>
            </div>
            <ElTag effect="plain" size="small" type="info">预留</ElTag>
          </div>
        </ElCard>
      </section>

      <section>
        <ElCard class="target-card danger-card" shadow="never">
          <div class="target-card-header">
            <div>
              <h2>删除标的</h2>
              <p>按类型和代码物理删除该标的在配置、行情、分时、报告、提醒和观察池里的数据。</p>
            </div>
            <ElTag effect="plain" size="small" type="danger">物理删除</ElTag>
          </div>

          <ElForm
            ref="deleteFormRef"
            class="delete-form"
            label-position="top"
            :model="deleteForm"
            :rules="deleteRules"
          >
            <ElFormItem label="标的类型" prop="targetType">
              <ElSelect v-model="deleteForm.targetType">
                <ElOption label="股票" value="STOCK" />
                <ElOption label="可转债" value="BOND" />
                <ElOption label="指数" value="INDEX" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="标的代码" prop="targetCode">
              <ElInput
                v-model="deleteForm.targetCode"
                clearable
                maxlength="6"
                placeholder="输入 6 位代码"
              />
            </ElFormItem>
            <ElPopconfirm
              confirm-button-text="确认删除"
              cancel-button-text="取消"
              :title="`确认物理删除 ${deleteTypeLabel} ${deleteForm.targetCode || ''} 的全部数据？`"
              width="320"
              @confirm="submitTargetDelete"
            >
              <template #reference>
                <ElButton
                  class="target-form-button"
                  :disabled="!deleteForm.targetCode.trim()"
                  :loading="deletingTarget"
                  type="danger"
                >
                  删除标的
                </ElButton>
              </template>
            </ElPopconfirm>
          </ElForm>
        </ElCard>
      </section>
    </div>
  </Page>
</template>

<style scoped>
.target-config-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.target-card-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
}

.target-card {
  background: #1f2937;
  border-color: #374151;
  border-radius: 8px;
}

.danger-card {
  border-color: #7f1d1d;
}

.reserved-card {
  min-height: 168px;
}

.target-card-header {
  align-items: flex-start;
  display: flex;
  gap: 16px;
  justify-content: space-between;
}

.target-form,
.delete-form {
  display: grid;
  gap: 4px 12px;
  grid-template-columns: 1fr 1fr auto;
  margin-top: 18px;
}

.target-form :deep(.el-form-item),
.delete-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.target-form :deep(.el-form-item__label),
.delete-form :deep(.el-form-item__label) {
  color: #cbd5e1;
}

.target-form-button {
  align-self: end;
  margin-bottom: 1px;
}

h2 {
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
  margin: 0;
}

p {
  color: #cbd5e1;
  font-size: 13px;
  line-height: 20px;
  margin: 8px 0 0;
}

@media (max-width: 780px) {
  .delete-form,
  .target-form {
    grid-template-columns: 1fr;
  }
}
</style>
