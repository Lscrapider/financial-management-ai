<script lang="ts" setup>
import type { IndexQuote } from '#/api/index-market';
import type { StockQuote } from '#/api/stock';
import type { WatchGroup, WatchTargetType } from '#/api/watch-pool';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElCard,
  ElDialog,
  ElEmpty,
  ElInput,
  ElMessage,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { listIndexQuotes } from '#/api/index-market';
import { listStockQuotes } from '#/api/stock';
import {
  deleteWatchGroup,
  deleteWatchItem,
  listWatchGroups,
  saveWatchGroup,
  saveWatchItem,
} from '#/api/watch-pool';

interface TargetOption {
  code: string;
  name: string;
  secid: string;
}

const groups = ref<WatchGroup[]>([]);
const selectedGroupId = ref('');
const stockQuotes = ref<StockQuote[]>([]);
const indexQuotes = ref<IndexQuote[]>([]);
const loadingGroups = ref(false);
const loadingQuotes = ref(false);

const groupDialogVisible = ref(false);
const editingGroupId = ref('');
const groupName = ref('');

const targetDialogVisible = ref(false);
const targetType = ref<WatchTargetType>('STOCK');
const selectedTargetKeys = ref<string[]>([]);
const manualTargetCode = ref('');
const manualTargetName = ref('');
const manualSecid = ref('');
const targetRemark = ref('');

const selectedGroup = computed(() => {
  return groups.value.find((group) => group.id === selectedGroupId.value);
});

const stockOptions = computed<TargetOption[]>(() => {
  return stockQuotes.value.map((item) => ({
    code: item.stockCode,
    name: item.stockName,
    secid: item.secid,
  }));
});

const indexOptions = computed<TargetOption[]>(() => {
  return indexQuotes.value.map((item) => ({
    code: item.indexCode,
    name: item.indexName,
    secid: item.secid,
  }));
});

const currentTargetOptions = computed<TargetOption[]>(() => {
  return targetType.value === 'STOCK' ? stockOptions.value : indexOptions.value;
});

const stocks = computed(() => {
  return (selectedGroup.value?.items ?? [])
    .filter((item) => item.targetType === 'STOCK')
    .map((item) => ({
      ...item,
      latestPrice:
        item.latestPrice ??
        stockQuotes.value.find((quote) => quote.stockCode === item.targetCode)
          ?.latestPrice,
      changePercent:
        item.changePercent ??
        stockQuotes.value.find((quote) => quote.stockCode === item.targetCode)
          ?.changePercent,
      turnoverAmount:
        item.turnoverAmount ??
        stockQuotes.value.find((quote) => quote.stockCode === item.targetCode)
          ?.turnoverAmount,
    }));
});

const indices = computed(() => {
  return (selectedGroup.value?.items ?? [])
    .filter((item) => item.targetType === 'INDEX')
    .map((item) => ({
      ...item,
      latestPrice:
        item.latestPrice ??
        indexQuotes.value.find((quote) => quote.indexCode === item.targetCode)
          ?.latestPrice,
      changePercent:
        item.changePercent ??
        indexQuotes.value.find((quote) => quote.indexCode === item.targetCode)
          ?.changePercent,
      turnoverAmount:
        item.turnoverAmount ??
        indexQuotes.value.find((quote) => quote.indexCode === item.targetCode)
          ?.turnoverAmount,
    }));
});

const bonds = computed(() => {
  return (selectedGroup.value?.items ?? []).filter(
    (item) => item.targetType === 'BOND',
  );
});

onMounted(async () => {
  await Promise.all([loadGroups(), loadQuotes()]);
});

async function loadGroups() {
  loadingGroups.value = true;
  try {
    const currentGroupId = selectedGroupId.value;
    groups.value = await listWatchGroups();
    selectedGroupId.value = groups.value.some(
      (group) => group.id === currentGroupId,
    )
      ? currentGroupId
      : (groups.value[0]?.id ?? '');
  } finally {
    loadingGroups.value = false;
  }
}

async function loadQuotes() {
  loadingQuotes.value = true;
  try {
    const [stocksData, indicesData] = await Promise.all([
      listStockQuotes({
        limit: 200,
        sortField: 'changePercent',
        sortOrder: 'desc',
      }),
      listIndexQuotes({
        limit: 50,
        marketCode: 'INDEX',
        sortField: 'indexCode',
        sortOrder: 'asc',
      }),
    ]);
    stockQuotes.value = stocksData;
    indexQuotes.value = indicesData;
  } finally {
    loadingQuotes.value = false;
  }
}

function openCreateGroup() {
  editingGroupId.value = '';
  groupName.value = '';
  groupDialogVisible.value = true;
}

function openRenameGroup() {
  if (!selectedGroup.value) return;
  editingGroupId.value = selectedGroup.value.id;
  groupName.value = selectedGroup.value.name;
  groupDialogVisible.value = true;
}

async function saveGroup() {
  const name = groupName.value.trim();
  if (!name) {
    ElMessage.warning('请输入分组名称');
    return;
  }

  const group = await saveWatchGroup({
    groupName: name,
    id: editingGroupId.value || undefined,
  });
  selectedGroupId.value = group.id;
  await loadGroups();
  groupDialogVisible.value = false;
}

async function deleteGroup() {
  if (!selectedGroup.value || groups.value.length <= 1) {
    ElMessage.warning('至少保留一个分组');
    return;
  }
  const groupId = selectedGroup.value.id;
  await deleteWatchGroup(groupId);
  selectedGroupId.value = '';
  await loadGroups();
}

function openAddTarget() {
  targetType.value = 'STOCK';
  resetTargetForm();
  targetDialogVisible.value = true;
}

function resetTargetForm() {
  selectedTargetKeys.value = [];
  manualTargetCode.value = '';
  manualTargetName.value = '';
  manualSecid.value = '';
  targetRemark.value = '';
}

async function saveTarget() {
  const group = selectedGroup.value;
  if (!group) return;
  const options = selectedOptions();
  if (options.length === 0) {
    ElMessage.warning('请选择或填写标的');
    return;
  }
  const availableOptions = options.filter(
    (option) =>
      !group.items.some(
        (item) =>
          item.targetType === targetType.value &&
          item.targetCode === option.code,
      ),
  );
  if (availableOptions.length === 0) {
    ElMessage.warning('当前分组已存在该标的');
    return;
  }

  await Promise.all(
    availableOptions.map((option) =>
      saveWatchItem({
        groupId: group.id,
        remark: targetRemark.value.trim(),
        secid: option.secid,
        targetCode: option.code,
        targetName: option.name,
        targetType: targetType.value,
      }),
    ),
  );
  await loadGroups();
  targetDialogVisible.value = false;
}

function selectedOptions(): TargetOption[] {
  if (targetType.value === 'BOND') {
    const code = manualTargetCode.value.trim();
    const name = manualTargetName.value.trim();
    if (!code || !name) {
      return [];
    }
    return [
      {
        code,
        name,
        secid: manualSecid.value.trim(),
      },
    ];
  }

  return currentTargetOptions.value.filter((item) =>
    selectedTargetKeys.value.includes(optionKey(item)),
  );
}

function onTargetTypeChange() {
  resetTargetForm();
}

async function removeTarget(itemId: string) {
  await deleteWatchItem(itemId);
  await loadGroups();
}

function optionKey(option: TargetOption) {
  return `${option.code}::${option.secid}`;
}

function typeLabel(type: WatchTargetType) {
  if (type === 'STOCK') return '股票';
  if (type === 'INDEX') return '指数';
  return '债券';
}

function changeClass(value?: number | string) {
  const numberValue = toNumber(value);
  if (numberValue > 0) return 'text-red-500';
  if (numberValue < 0) return 'text-emerald-500';
  return '';
}

function formatChangePercent(value?: number | string) {
  const numberValue = toNullableNumber(value);
  if (numberValue === null) return '-';
  return `${numberValue > 0 ? '+' : ''}${numberValue.toFixed(2)}%`;
}

function formatMoney(value?: number | string) {
  const numberValue = toNullableNumber(value);
  if (numberValue === null) return '-';
  if (Math.abs(numberValue) >= 100_000_000) {
    return `${(numberValue / 100_000_000).toFixed(2)}亿`;
  }
  if (Math.abs(numberValue) >= 10_000) {
    return `${(numberValue / 10_000).toFixed(2)}万`;
  }
  return numberValue.toFixed(2);
}

function formatPrice(value?: number | string) {
  const numberValue = toNullableNumber(value);
  return numberValue === null ? '-' : numberValue.toFixed(2);
}

function toNullableNumber(value?: number | string | null) {
  if (value === null || value === undefined || value === '') return null;
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
}

function toNumber(value?: number | string | null) {
  return toNullableNumber(value) ?? 0;
}
</script>

<template>
  <Page title="投资观察池">
    <div class="watch-pool">
      <section v-loading="loadingGroups" class="group-toolbar">
        <div class="group-tabs">
          <button
            v-for="group in groups"
            :key="group.id"
            :class="['group-tab', { active: group.id === selectedGroupId }]"
            type="button"
            @click="selectedGroupId = group.id"
          >
            <span>{{ group.name }}</span>
            <small>{{ group.items.length }}</small>
          </button>
          <ElButton size="small" type="primary" @click="openCreateGroup">
            新建分组
          </ElButton>
        </div>

        <div class="toolbar-actions">
          <ElButton size="small" @click="openRenameGroup">重命名</ElButton>
          <ElButton size="small" type="danger" plain @click="deleteGroup">
            删除分组
          </ElButton>
          <ElButton size="small" type="primary" @click="openAddTarget">
            添加标的
          </ElButton>
        </div>
      </section>

      <section class="group-summary">
        <div>
          <h2>{{ selectedGroup?.name ?? '-' }}</h2>
          <span
            >股票 {{ stocks.length }} · 指数 {{ indices.length }} · 债券
            {{ bonds.length }}</span
          >
        </div>
        <ElButton :loading="loadingQuotes" size="small" @click="loadQuotes">
          刷新行情
        </ElButton>
      </section>

      <div class="asset-grid">
        <ElCard class="asset-card" shadow="never">
          <template #header>
            <div class="asset-header">
              <span>关注股票</span>
              <ElTag effect="plain" size="small">{{ stocks.length }}</ElTag>
            </div>
          </template>
          <ElTable v-if="stocks.length > 0" :data="stocks" height="300">
            <ElTableColumn label="名称" min-width="150">
              <template #default="{ row }">
                <div class="target-name">
                  <span>{{ row.targetName }}</span>
                  <small>{{ row.targetCode }}</small>
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn label="最新" align="right" min-width="90">
              <template #default="{ row }">
                {{ formatPrice(row.latestPrice) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="涨跌幅" align="right" min-width="90">
              <template #default="{ row }">
                <span :class="changeClass(row.changePercent)">
                  {{ formatChangePercent(row.changePercent) }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="成交额" align="right" min-width="100">
              <template #default="{ row }">
                {{ formatMoney(row.turnoverAmount) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="备注" min-width="120" prop="remark" />
            <ElTableColumn label="操作" width="72" align="right">
              <template #default="{ row }">
                <ElButton link type="danger" @click="removeTarget(row.id)">
                  移除
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
          <ElEmpty v-else description="暂无关注股票" />
        </ElCard>

        <ElCard class="asset-card" shadow="never">
          <template #header>
            <div class="asset-header">
              <span>关注指数</span>
              <ElTag effect="plain" size="small">{{ indices.length }}</ElTag>
            </div>
          </template>
          <ElTable v-if="indices.length > 0" :data="indices" height="300">
            <ElTableColumn label="名称" min-width="150">
              <template #default="{ row }">
                <div class="target-name">
                  <span>{{ row.targetName }}</span>
                  <small>{{ row.targetCode }}</small>
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn label="最新" align="right" min-width="90">
              <template #default="{ row }">
                {{ formatPrice(row.latestPrice) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="涨跌幅" align="right" min-width="90">
              <template #default="{ row }">
                <span :class="changeClass(row.changePercent)">
                  {{ formatChangePercent(row.changePercent) }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="成交额" align="right" min-width="100">
              <template #default="{ row }">
                {{ formatMoney(row.turnoverAmount) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="备注" min-width="120" prop="remark" />
            <ElTableColumn label="操作" width="72" align="right">
              <template #default="{ row }">
                <ElButton link type="danger" @click="removeTarget(row.id)">
                  移除
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
          <ElEmpty v-else description="暂无关注指数" />
        </ElCard>

        <ElCard class="asset-card asset-card--wide" shadow="never">
          <template #header>
            <div class="asset-header">
              <span>关注债券</span>
              <ElTag effect="plain" size="small">{{ bonds.length }}</ElTag>
            </div>
          </template>
          <ElTable v-if="bonds.length > 0" :data="bonds" height="260">
            <ElTableColumn label="名称" min-width="180">
              <template #default="{ row }">
                <div class="target-name">
                  <span>{{ row.targetName }}</span>
                  <small>{{ row.targetCode }}</small>
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn label="类型" width="90">
              <template #default="{ row }">
                <ElTag size="small" type="warning">{{
                  typeLabel(row.targetType)
                }}</ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="正股/备注" min-width="180">
              <template #default="{ row }">
                {{ row.remark || '-' }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="72" align="right">
              <template #default="{ row }">
                <ElButton link type="danger" @click="removeTarget(row.id)">
                  移除
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
          <ElEmpty v-else description="暂无关注债券" />
        </ElCard>
      </div>

      <ElDialog v-model="groupDialogVisible" title="分组设置" width="420px">
        <div class="dialog-form">
          <span>分组名称</span>
          <ElInput
            v-model="groupName"
            maxlength="20"
            placeholder="例如：短线跟踪"
          />
        </div>
        <template #footer>
          <ElButton @click="groupDialogVisible = false">取消</ElButton>
          <ElButton type="primary" @click="saveGroup">保存</ElButton>
        </template>
      </ElDialog>

      <ElDialog v-model="targetDialogVisible" title="添加标的" width="520px">
        <div class="dialog-form">
          <span>标的类型</span>
          <ElRadioGroup v-model="targetType" @change="onTargetTypeChange">
            <ElRadioButton value="STOCK">股票</ElRadioButton>
            <ElRadioButton value="INDEX">指数</ElRadioButton>
            <ElRadioButton value="BOND">债券</ElRadioButton>
          </ElRadioGroup>

          <template v-if="targetType !== 'BOND'">
            <span>选择标的</span>
            <ElSelect
              :key="targetType"
              v-model="selectedTargetKeys"
              collapse-tags
              collapse-tags-tooltip
              filterable
              multiple
              placeholder="可多选，输入代码或名称搜索"
              style="width: 100%"
            >
              <ElOption
                v-for="option in currentTargetOptions"
                :key="optionKey(option)"
                :label="`${option.name} ${option.code}`"
                :value="optionKey(option)"
              />
            </ElSelect>
          </template>

          <template v-else>
            <span>债券代码</span>
            <ElInput v-model="manualTargetCode" placeholder="例如：110xxx" />
            <span>债券名称</span>
            <ElInput v-model="manualTargetName" placeholder="例如：汇通转债" />
            <span>Secid</span>
            <ElInput
              v-model="manualSecid"
              placeholder="债券接口接入后可自动回填"
            />
          </template>

          <span>备注</span>
          <ElInput
            v-model="targetRemark"
            maxlength="50"
            placeholder="例如：观察转股溢价率"
          />
        </div>
        <template #footer>
          <ElButton @click="targetDialogVisible = false">取消</ElButton>
          <ElButton type="primary" @click="saveTarget">添加</ElButton>
        </template>
      </ElDialog>
    </div>
  </Page>
</template>

<style scoped>
.watch-pool {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.group-toolbar,
.group-summary {
  align-items: center;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  display: flex;
  gap: 16px;
  justify-content: space-between;
  padding: 16px 18px;
}

.group-tabs,
.toolbar-actions {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.group-tab {
  align-items: center;
  background: var(--el-fill-color-lighter);
  border: 1px solid transparent;
  border-radius: 6px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  display: inline-flex;
  gap: 8px;
  min-height: 32px;
  padding: 0 12px;
}

.group-tab.active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
}

.group-tab small {
  color: var(--el-text-color-secondary);
}

.group-summary h2 {
  font-size: 20px;
  line-height: 1.2;
  margin: 0 0 6px;
}

.group-summary span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.asset-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.asset-card {
  min-width: 0;
}

.asset-card--wide {
  grid-column: 1 / -1;
}

.asset-header {
  align-items: center;
  display: flex;
  font-weight: 600;
  justify-content: space-between;
}

.target-name {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.25;
}

.target-name small {
  color: var(--el-text-color-secondary);
}

.dialog-form {
  display: grid;
  gap: 10px;
}

.dialog-form > span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

@media (max-width: 1100px) {
  .asset-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .group-toolbar,
  .group-summary {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-actions {
    justify-content: flex-end;
  }
}
</style>
