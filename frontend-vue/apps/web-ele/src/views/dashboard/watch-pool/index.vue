<script lang="ts" setup>
import type { BondQuote } from '#/api/bond';
import type { IndexQuote } from '#/api/index-market';
import type { StockQuote } from '#/api/stock';
import type { WatchGroup, WatchItem, WatchTargetType } from '#/api/watch-pool';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

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

import { listBondQuotes } from '#/api/bond';
import { listIndexQuotes } from '#/api/index-market';
import { listStockQuotes } from '#/api/stock';
import {
  deleteWatchGroup,
  deleteWatchItem,
  listWatchGroups,
  saveWatchGroup,
  saveWatchItem,
} from '#/api/watch-pool';

import { useColumnResize } from './useColumnResize';

interface TargetOption {
  code: string;
  name: string;
  secid: string;
}

const route = useRoute();
const router = useRouter();
const groups = ref<WatchGroup[]>([]);
const selectedGroupId = ref('');
const highlightedItemId = ref('');
const typeFilter = ref<'ALL' | WatchTargetType>('ALL');
const stockQuotes = ref<StockQuote[]>([]);
const indexQuotes = ref<IndexQuote[]>([]);
const bondQuotes = ref<BondQuote[]>([]);
const loadingGroups = ref(false);
const loadingQuotes = ref(false);

const groupDialogVisible = ref(false);
const editingGroupId = ref('');
const groupName = ref('');

const targetDialogVisible = ref(false);
const targetType = ref<WatchTargetType>('STOCK');
const selectedTargetKeys = ref<string[]>([]);
const targetRemark = ref('');
const targetBuyPrice = ref('');
const targetPosition = ref('');

const { onMouseDown, widths } = useColumnResize();

const editDialogVisible = ref(false);
const editingItem = ref<null | WatchItem>(null);
const editBuyPrice = ref('');
const editPosition = ref('');
const editRemark = ref('');
const quoteDetailVisible = ref(false);
const quoteDetailTitle = ref('');
const quoteDetailRows = ref<StockQuote['quoteDetails']>([]);

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

const bondOptions = computed<TargetOption[]>(() => {
  return bondQuotes.value.map((item) => ({
    code: item.bondCode,
    name: item.bondName,
    secid: item.secid,
  }));
});

const currentTargetOptions = computed<TargetOption[]>(() => {
  if (targetType.value === 'STOCK') return stockOptions.value;
  if (targetType.value === 'INDEX') return indexOptions.value;
  return bondOptions.value;
});

function supportsDetailQuote(targetType: WatchTargetType): boolean {
  return targetType === 'STOCK' || targetType === 'BOND';
}

const filteredItems = computed(() => {
  const items = selectedGroup.value?.items ?? [];
  const filtered =
    typeFilter.value === 'ALL'
      ? items
      : items.filter((item) => item.targetType === typeFilter.value);
  return filtered.map((item) => {
    const stockQuote = stockQuotes.value.find(
      (q) => q.stockCode === item.targetCode,
    );
    const indexQuote = indexQuotes.value.find(
      (q) => q.indexCode === item.targetCode,
    );
    const bondQuote = bondQuotes.value.find(
      (q) => q.bondCode === item.targetCode,
    );
    const quote = stockQuote ?? indexQuote ?? bondQuote;
    return {
      ...item,
      ...(stockQuote
        ? {
            amplitude: item.amplitude ?? stockQuote.amplitude,
            averagePrice: item.averagePrice ?? stockQuote.averagePrice,
            currentVolume: item.currentVolume ?? stockQuote.currentVolume,
            externalVolume: item.externalVolume ?? stockQuote.externalVolume,
            floatMarketValue:
              item.floatMarketValue ?? stockQuote.floatMarketValue,
            internalVolume: item.internalVolume ?? stockQuote.internalVolume,
            limitDownPrice: item.limitDownPrice ?? stockQuote.limitDownPrice,
            limitUpPrice: item.limitUpPrice ?? stockQuote.limitUpPrice,
            pbRatio: item.pbRatio ?? stockQuote.pbRatio,
            peDynamic: item.peDynamic ?? stockQuote.peDynamic,
            peStatic: item.peStatic ?? stockQuote.peStatic,
            peTtm: item.peTtm ?? stockQuote.peTtm,
            quoteDetails: item.quoteDetails ?? stockQuote.quoteDetails,
            totalMarketValue:
              item.totalMarketValue ?? stockQuote.totalMarketValue,
            turnoverRate: item.turnoverRate ?? stockQuote.turnoverRate,
            volume: item.volume ?? stockQuote.volume,
            volumeRatio: item.volumeRatio ?? stockQuote.volumeRatio,
          }
        : {}),
      latestPrice: item.latestPrice ?? quote?.latestPrice,
      changePercent: item.changePercent ?? quote?.changePercent,
      turnoverAmount: item.turnoverAmount ?? quote?.turnoverAmount,
    };
  });
});

function typeLabel(type: WatchTargetType) {
  const map: Record<WatchTargetType, string> = {
    BOND: '债券',
    FUND: '基金',
    INDEX: '指数',
    SECTOR: '板块',
    STOCK: '股票',
  };
  return map[type] ?? type;
}

onMounted(async () => {
  await Promise.all([loadGroups(), loadQuotes()]);
});

watch(
  () => [route.query.groupId, route.query.itemId],
  () => {
    applyRouteSelection();
  },
);

async function loadGroups() {
  loadingGroups.value = true;
  try {
    const currentGroupId = selectedGroupId.value;
    groups.value = await listWatchGroups();
    applyRouteSelection(currentGroupId);
  } finally {
    loadingGroups.value = false;
  }
}

function applyRouteSelection(fallbackGroupId = selectedGroupId.value) {
  const queryGroupId = firstQueryValue(route.query.groupId);
  const queryItemId = firstQueryValue(route.query.itemId);
  const fallbackGroup = groups.value.find(
    (group) => group.id === fallbackGroupId,
  );
  const routeGroup = groups.value.find((group) => group.id === queryGroupId);
  const nextGroup = routeGroup ?? fallbackGroup ?? groups.value[0];

  selectedGroupId.value = nextGroup?.id ?? '';
  highlightedItemId.value = nextGroup?.items.some(
    (item) => item.id === queryItemId,
  )
    ? queryItemId
    : '';
}

function selectGroup(groupId: string) {
  selectedGroupId.value = groupId;
  highlightedItemId.value = '';
  if (route.query.groupId || route.query.itemId) {
    void router.replace({
      name: 'WatchPool',
      query: {
        ...route.query,
        groupId,
        itemId: undefined,
      },
    });
  }
}

function firstQueryValue(value: unknown) {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : '';
  }
  return typeof value === 'string' ? value : '';
}

function tableRowClassName({ row }: { row: WatchItem }) {
  return row.id === highlightedItemId.value ? 'watch-row-highlight' : '';
}

async function loadQuotes() {
  loadingQuotes.value = true;
  try {
    const [stocksData, indicesData, bondsData] = await Promise.all([
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
      listBondQuotes({
        limit: 100,
        sortField: 'changePercent',
        sortOrder: 'desc',
      }),
    ]);
    stockQuotes.value = stocksData;
    indexQuotes.value = indicesData;
    bondQuotes.value = bondsData;
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
  targetRemark.value = '';
  targetBuyPrice.value = '';
  targetPosition.value = '';
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
        buyPrice: targetBuyPrice.value
          ? Number(targetBuyPrice.value)
          : undefined,
        groupId: group.id,
        position: targetPosition.value
          ? Number(targetPosition.value)
          : undefined,
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

function openEditTarget(item: WatchItem) {
  editingItem.value = item;
  editBuyPrice.value =
    item.buyPrice !== null && item.buyPrice !== undefined
      ? String(item.buyPrice)
      : '';
  editPosition.value =
    item.position !== null && item.position !== undefined
      ? String(item.position)
      : '';
  editRemark.value = item.remark ?? '';
  editDialogVisible.value = true;
}

function openQuoteDetails(item: WatchItem) {
  quoteDetailTitle.value = `${item.targetName} ${item.targetCode}`;
  quoteDetailRows.value = item.quoteDetails ?? [];
  quoteDetailVisible.value = true;
}

async function saveEditTarget() {
  if (!editingItem.value || !selectedGroup.value) return;
  await saveWatchItem({
    buyPrice: editBuyPrice.value ? Number(editBuyPrice.value) : undefined,
    groupId: selectedGroup.value.id,
    id: editingItem.value.id,
    position: editPosition.value ? Number(editPosition.value) : undefined,
    remark: editRemark.value.trim(),
    targetCode: editingItem.value.targetCode,
    targetName: editingItem.value.targetName,
    targetType: editingItem.value.targetType,
  });
  await loadGroups();
  editDialogVisible.value = false;
}

function optionKey(option: TargetOption) {
  return `${option.code}::${option.secid}`;
}

function changeClass(value?: null | number | string) {
  const numberValue = toNumber(value);
  if (numberValue > 0) return 'text-red-500';
  if (numberValue < 0) return 'text-emerald-500';
  return '';
}

function formatChangePercent(value?: null | number | string) {
  const numberValue = toNullableNumber(value);
  if (numberValue === null) return '-';
  return `${numberValue > 0 ? '+' : ''}${numberValue.toFixed(3)}%`;
}

function formatMoney(value?: null | number | string) {
  const numberValue = toNullableNumber(value);
  if (numberValue === null) return '-';
  if (Math.abs(numberValue) >= 100_000_000) {
    return `${(numberValue / 100_000_000).toFixed(3)}亿`;
  }
  if (Math.abs(numberValue) >= 10_000) {
    return `${(numberValue / 10_000).toFixed(3)}万`;
  }
  return numberValue.toFixed(3);
}

function formatPrice(value?: null | number | string) {
  const numberValue = toNullableNumber(value);
  return numberValue === null ? '-' : numberValue.toFixed(3);
}

function toNullableNumber(value?: null | number | string) {
  if (value === null || value === undefined || value === '') return null;
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
}

function toNumber(value?: null | number | string) {
  return toNullableNumber(value) ?? 0;
}

function costReturnRate(
  latestPrice?: number | string,
  buyPrice?: number | string,
) {
  const lp = toNullableNumber(latestPrice);
  const bp = toNullableNumber(buyPrice);
  if (lp === null || bp === null || bp === 0) return null;
  return ((lp - bp) / bp) * 100;
}

function positionMarketValue(
  latestPrice?: number | string,
  position?: number | string,
) {
  const lp = toNullableNumber(latestPrice);
  const pos = toNullableNumber(position);
  if (lp === null || pos === null) return null;
  return lp * pos;
}

function positionPnL(
  latestPrice?: number | string,
  buyPrice?: number | string,
  position?: number | string,
) {
  const lp = toNullableNumber(latestPrice);
  const bp = toNullableNumber(buyPrice);
  const pos = toNullableNumber(position);
  if (lp === null || bp === null || pos === null) return null;
  return (lp - bp) * pos;
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
            class="group-tab"
            :class="[{ active: group.id === selectedGroupId }]"
            type="button"
            @click="selectGroup(group.id)"
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
          <span>共 {{ selectedGroup?.items.length ?? 0 }} 个标的</span>
        </div>
        <ElButton :loading="loadingQuotes" size="small" @click="loadQuotes">
          刷新行情
        </ElButton>
      </section>

      <div class="filter-bar">
        <ElRadioGroup v-model="typeFilter" size="small">
          <ElRadioButton value="ALL">全部</ElRadioButton>
          <ElRadioButton value="STOCK">股票</ElRadioButton>
          <ElRadioButton value="INDEX">指数</ElRadioButton>
          <ElRadioButton value="BOND">债券</ElRadioButton>
        </ElRadioGroup>
      </div>

      <ElCard shadow="never">
        <ElTable
          v-if="filteredItems.length > 0"
          :data="filteredItems"
          :row-class-name="tableRowClassName"
          border
          height="400"
          row-key="id"
        >
          <ElTableColumn :width="widths['col-type']" min-width="70">
            <template #header>
              <div class="resize-header">
                <span>类型</span>
                <span
                  class="resize-handle"
                  @mousedown="onMouseDown('col-type', $event)"
                ></span>
              </div>
            </template>
            <template #default="{ row }">
              <ElTag effect="plain" size="small" type="info">
                {{ typeLabel(row.targetType) }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn :width="widths['col-name']" min-width="150">
            <template #header>
              <div class="resize-header">
                <span>名称</span>
                <span
                  class="resize-handle"
                  @mousedown="onMouseDown('col-name', $event)"
                ></span>
              </div>
            </template>
            <template #default="{ row }">
              <div class="target-name">
                <span>{{ row.targetName }}</span>
                <small>{{ row.targetCode }}</small>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn
            :width="widths['col-latest']"
            align="right"
            min-width="90"
          >
            <template #header>
              <div class="resize-header">
                <span>最新</span>
                <span
                  class="resize-handle"
                  @mousedown="onMouseDown('col-latest', $event)"
                ></span>
              </div>
            </template>
            <template #default="{ row }">
              {{ formatPrice(row.latestPrice) }}
            </template>
          </ElTableColumn>
          <ElTableColumn
            :width="widths['col-change']"
            align="right"
            min-width="90"
          >
            <template #header>
              <div class="resize-header">
                <span>涨跌幅</span>
                <span
                  class="resize-handle"
                  @mousedown="onMouseDown('col-change', $event)"
                ></span>
              </div>
            </template>
            <template #default="{ row }">
              <span :class="changeClass(row.changePercent)">
                {{ formatChangePercent(row.changePercent) }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn
            :width="widths['col-turnover']"
            align="right"
            min-width="100"
          >
            <template #header>
              <div class="resize-header">
                <span>成交额</span>
                <span
                  class="resize-handle"
                  @mousedown="onMouseDown('col-turnover', $event)"
                ></span>
              </div>
            </template>
            <template #default="{ row }">
              {{ formatMoney(row.turnoverAmount) }}
            </template>
          </ElTableColumn>
          <ElTableColumn align="right" min-width="90">
            <template #header>
              <div class="resize-header">
                <span>均价</span>
              </div>
            </template>
            <template #default="{ row }">
              {{
                supportsDetailQuote(row.targetType)
                  ? formatPrice(row.averagePrice)
                  : '-'
              }}
            </template>
          </ElTableColumn>
          <ElTableColumn align="right" min-width="90">
            <template #header>
              <div class="resize-header">
                <span>现手</span>
              </div>
            </template>
            <template #default="{ row }">
              {{
                supportsDetailQuote(row.targetType)
                  ? (row.currentVolume ?? '-')
                  : '-'
              }}
            </template>
          </ElTableColumn>
          <ElTableColumn
            :width="widths['col-buy']"
            align="right"
            min-width="90"
          >
            <template #header>
              <div class="resize-header">
                <span>买入价</span>
                <span
                  class="resize-handle"
                  @mousedown="onMouseDown('col-buy', $event)"
                ></span>
              </div>
            </template>
            <template #default="{ row }">
              {{ formatPrice(row.buyPrice) }}
            </template>
          </ElTableColumn>
          <ElTableColumn
            :width="widths['col-pos']"
            align="right"
            min-width="90"
          >
            <template #header>
              <div class="resize-header">
                <span>持仓数量</span>
                <span
                  class="resize-handle"
                  @mousedown="onMouseDown('col-pos', $event)"
                ></span>
              </div>
            </template>
            <template #default="{ row }">
              {{ row.position ?? '-' }}
            </template>
          </ElTableColumn>
          <ElTableColumn
            :width="widths['col-mv']"
            align="right"
            min-width="100"
          >
            <template #header>
              <div class="resize-header">
                <span>持仓市值</span>
                <span
                  class="resize-handle"
                  @mousedown="onMouseDown('col-mv', $event)"
                ></span>
              </div>
            </template>
            <template #default="{ row }">
              {{
                formatMoney(positionMarketValue(row.latestPrice, row.position))
              }}
            </template>
          </ElTableColumn>
          <ElTableColumn
            :width="widths['col-pnl']"
            align="right"
            min-width="100"
          >
            <template #header>
              <div class="resize-header">
                <span>浮动盈亏</span>
                <span
                  class="resize-handle"
                  @mousedown="onMouseDown('col-pnl', $event)"
                ></span>
              </div>
            </template>
            <template #default="{ row }">
              <span
                :class="
                  changeClass(
                    positionPnL(row.latestPrice, row.buyPrice, row.position),
                  )
                "
              >
                {{
                  formatMoney(
                    positionPnL(row.latestPrice, row.buyPrice, row.position),
                  )
                }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn
            :width="widths['col-rate']"
            align="right"
            min-width="90"
          >
            <template #header>
              <div class="resize-header">
                <span>盈亏比例</span>
                <span
                  class="resize-handle"
                  @mousedown="onMouseDown('col-rate', $event)"
                ></span>
              </div>
            </template>
            <template #default="{ row }">
              <span
                :class="
                  changeClass(costReturnRate(row.latestPrice, row.buyPrice))
                "
              >
                {{
                  formatChangePercent(
                    costReturnRate(row.latestPrice, row.buyPrice),
                  )
                }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn :width="widths['col-remark']" min-width="120">
            <template #header>
              <div class="resize-header">
                <span>备注</span>
                <span
                  class="resize-handle"
                  @mousedown="onMouseDown('col-remark', $event)"
                ></span>
              </div>
            </template>
            <template #default="{ row }">
              {{ row.remark }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="180" align="right">
            <template #default="{ row }">
              <ElButton
                :disabled="!supportsDetailQuote(row.targetType)"
                link
                size="small"
                type="primary"
                @click="openQuoteDetails(row)"
              >
                更多
              </ElButton>
              <ElButton
                link
                size="small"
                type="primary"
                @click="openEditTarget(row)"
              >
                编辑
              </ElButton>
              <ElButton
                link
                size="small"
                type="danger"
                @click="removeTarget(row.id)"
              >
                移除
              </ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
        <ElEmpty v-else description="暂无标的" />
      </ElCard>

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

          <span>备注</span>
          <ElInput
            v-model="targetRemark"
            maxlength="50"
            placeholder="例如：观察价格走势"
          />

          <span>买入价（选填）</span>
          <ElInput
            v-model="targetBuyPrice"
            placeholder="例如：12.50"
            type="number"
          />

          <span>持仓数量（选填）</span>
          <ElInput
            v-model="targetPosition"
            placeholder="例如：100"
            type="number"
          />
        </div>
        <template #footer>
          <ElButton @click="targetDialogVisible = false">取消</ElButton>
          <ElButton type="primary" @click="saveTarget">添加</ElButton>
        </template>
      </ElDialog>

      <ElDialog v-model="editDialogVisible" title="编辑标的" width="460px">
        <div class="dialog-form">
          <span>标的</span>
          <div class="edit-target-info">
            <span>{{ editingItem?.targetName }}</span>
            <small>{{ editingItem?.targetCode }}</small>
          </div>

          <span>买入价</span>
          <ElInput
            v-model="editBuyPrice"
            placeholder="买入价格"
            type="number"
          />

          <span>持仓数量</span>
          <ElInput
            v-model="editPosition"
            placeholder="持仓数量"
            type="number"
          />

          <span>备注</span>
          <ElInput v-model="editRemark" maxlength="50" placeholder="备注信息" />
        </div>
        <template #footer>
          <ElButton @click="editDialogVisible = false">取消</ElButton>
          <ElButton type="primary" @click="saveEditTarget">保存</ElButton>
        </template>
      </ElDialog>

      <ElDialog
        v-model="quoteDetailVisible"
        :title="`${quoteDetailTitle} 更多行情`"
        width="720px"
      >
        <div
          v-if="quoteDetailRows && quoteDetailRows.length > 0"
          class="quote-detail-grid"
        >
          <div
            v-for="item in quoteDetailRows"
            :key="item.fieldIndex"
            class="quote-detail-cell"
          >
            <span class="detail-label">{{ item.fieldName }}</span>
            <span class="detail-value">{{ item.fieldValue || '-' }}</span>
          </div>
        </div>
        <ElEmpty v-else description="暂无更多行情数据" />
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
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.group-tabs,
.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.group-tab {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: var(--el-fill-color-lighter);
  border: 1px solid transparent;
  border-radius: 6px;
}

.group-tab.active {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
}

.group-tab small {
  color: var(--el-text-color-secondary);
}

.group-summary h2 {
  margin: 0 0 6px;
  font-size: 20px;
  line-height: 1.2;
}

.group-summary span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.filter-bar {
  margin-bottom: 0;
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
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.resize-header {
  position: relative;
  word-break: keep-all;
}

.resize-handle {
  position: absolute;
  top: -4px;
  right: -6px;
  bottom: -4px;
  z-index: 1;
  width: 14px;
  cursor: col-resize;
}

:deep(.el-table__header th) {
  overflow: visible;
}

:deep(.watch-row-highlight > td) {
  background: var(--el-color-primary-light-9) !important;
}

:deep(.watch-row-highlight:hover > td) {
  background: var(--el-color-primary-light-8) !important;
}

.edit-target-info {
  display: flex;
  gap: 8px;
  align-items: baseline;
  padding: 8px 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
}

.edit-target-info small {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.quote-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 32px;
  max-height: 520px;
  overflow: auto;
}

.quote-detail-cell {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 9px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.quote-detail-cell .detail-label {
  flex-shrink: 0;
  margin-right: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.quote-detail-cell .detail-value {
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  text-align: right;
  overflow-wrap: anywhere;
}

@media (max-width: 768px) {
  .group-toolbar,
  .group-summary {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar-actions {
    justify-content: flex-end;
  }
}
</style>
