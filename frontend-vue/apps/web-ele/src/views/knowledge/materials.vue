<script lang="ts" setup>
import type {
  KnowledgeMaterialChunk,
  KnowledgeMaterialSearchMode,
  KnowledgeMaterialTask,
} from '#/api/knowledge-material';
import type {
  SceneAnalysisConfigProfile,
  SceneAnalysisReportType,
  SceneAnalysisTargetOption,
} from '#/api/scene-analysis';

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import {
  ElButton,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElSkeleton,
  ElSlider,
  ElTag,
  ElTooltip,
} from 'element-plus';

import {
  getKnowledgeMaterialTask,
  submitKnowledgeMaterialTask,
} from '#/api/knowledge-material';
import {
  getSceneAnalysisConfigParameterSchema,
  getSceneAnalysisReportTypes,
  listSceneAnalysisConfigProfiles,
  searchSceneAnalysisTargets,
} from '#/api/scene-analysis';
import PageHero from '#/components/page-hero/index.vue';

interface ParameterField {
  defaultValue: number;
  description: string;
  key: string;
  label: string;
  max: number;
  min: number;
  path: string[];
  recommended: string;
  step: number;
  unit?: null | string;
}

interface ParameterGroup {
  fields: ParameterField[];
  label: string;
  name: string;
}

interface DisplayParameterGroup {
  label: string;
  name: string;
  sections: DisplayParameterSection[];
}

interface DisplayParameterSection {
  fields: ParameterField[];
  key: string;
  label: string;
}

const DEFAULT_TOTAL_CHUNKS = 10;
const DEFAULT_DAILY_KLINE_LIMIT = 90;
const DEFAULT_WEEKLY_KLINE_LIMIT = 52;
const DEFAULT_MONTHLY_KLINE_LIMIT = 60;
const MIN_DAILY_KLINE_LIMIT = 60;
const CORE_PARAMETER_KEYS = new Set([
  'attentionRiseCenter',
  'gapThreshold',
  'lowAttentionScale',
  'lowPriceThreshold',
  'priceDropCenter',
  'priceDropScale',
  'priceRiseCenter',
  'priceRiseScale',
  'pullbackThreshold',
  'supportDistanceThreshold',
  'volumeExpandCenter',
  'volumeExpandScale',
  'volumeSpikeCenter',
]);

const searchMode = ref<KnowledgeMaterialSearchMode>('target');
const submitting = ref(false);
const loadingTask = ref(false);
const loadingTargets = ref(false);
const loadingProfiles = ref(false);
const loadingParameterSchema = ref(false);
const activeTaskNo = ref('');
const materialTask = ref<KnowledgeMaterialTask>();
const targetOptions = ref<SceneAnalysisTargetOption[]>([]);
const configProfiles = ref<SceneAnalysisConfigProfile[]>([]);
const selectedConfigGroup = ref('');
const selectedProfileId = ref<null | number>(null);
const parameterGroups = ref<ParameterGroup[]>([]);
const parameterValues = ref<Record<string, number>>({});
const advancedParameterVisible = ref(false);
const selectedSceneFilter = ref('');
const selectedTagFilter = ref('');
const filenameFilter = ref('');
let pollTimer: number | undefined;

const form = ref({
  configProfile: 'system_recommended',
  dailyKlineLimit: DEFAULT_DAILY_KLINE_LIMIT,
  monthlyKlineLimit: DEFAULT_MONTHLY_KLINE_LIMIT,
  queryText: '',
  reportType: 'quick_analysis',
  targetCode: '',
  targetName: '',
  targetType: 'STOCK',
  totalChunks: DEFAULT_TOTAL_CHUNKS,
  weeklyKlineLimit: DEFAULT_WEEKLY_KLINE_LIMIT,
});

const reportTypeOptions = ref<SceneAnalysisReportType[]>([
  { code: 'quick_analysis', label: '快速分析' },
  { code: 'risk_check', label: '风险检查' },
  { code: 'valuation_report', label: '估值报告' },
]);

const targetTypeOptions = [
  { label: '股票', value: 'STOCK' },
  { label: '指数', value: 'INDEX' },
  { label: '可转债', value: 'CONVERTIBLE_BOND' },
];

const sceneLabels: Record<string, string> = {
  knowledge: '自然语言',
  price: '价格',
  risk_strategy: '风险策略',
  sentiment: '情绪',
  trend: '趋势',
  valuation: '估值',
  volume: '量能',
};

const tagLabels: Record<string, string> = {
  active_fund: '主动基金',
  avoid_emotional_trade: '避免情绪交易',
  bank_stock: '银行股',
  bond_fund: '债券基金',
  breakdown_from_range: '区间破位',
  breakout: '突破',
  breakout_from_range: '横盘突破',
  chase_high_risk: '追高风险',
  continuation: '趋势延续',
  convertible_bond: '可转债',
  convertible_debt_floor_support: '债底支撑',
  convertible_forced_redeem_risk: '强赎风险',
  convertible_high_conversion_value: '转股价值较高',
  convertible_high_premium: '转股高溢价',
  convertible_high_price_risk: '转债高价风险',
  convertible_high_ytm: '到期收益率较高',
  convertible_independent_strength: '转债独立走强',
  convertible_liquidity_risk: '转债流动性风险',
  convertible_low_premium: '转股低溢价',
  convertible_low_price_defensive: '转债低价防御',
  convertible_low_rating_risk: '低评级风险',
  convertible_low_ytm: '到期收益率较低或为负',
  convertible_premium_compression: '溢价压缩',
  convertible_premium_expansion: '溢价扩张',
  convertible_putback_risk: '回售相关风险',
  convertible_small_balance_risk: '剩余规模过小风险',
  convertible_stock_linkage: '正股联动',
  downtrend: '下降趋势',
  drawdown_risk: '回撤风险',
  etf: 'ETF',
  failed_breakout: '突破失败',
  false_breakout_risk: '假突破风险',
  fund: '基金',
  fund_concentration_risk: '基金持仓集中风险',
  fund_credit_risk: '债基信用风险',
  fund_discount: '基金场内折价',
  fund_duration_risk: '债基久期风险',
  fund_flow_in: '基金资金流入',
  fund_flow_out: '基金资金流出',
  fund_high_drawdown: '基金高回撤',
  fund_high_fee: '费率偏高',
  fund_large_scale: '基金规模较大',
  fund_liquidity_risk: '基金流动性风险',
  fund_nav_downtrend: '基金净值下降趋势',
  fund_nav_drop: '基金净值下跌',
  fund_nav_rise: '基金净值上涨',
  fund_nav_uptrend: '基金净值上升趋势',
  fund_premium: '基金场内溢价',
  fund_qdii_fx_risk: 'QDII 汇率风险',
  fund_share_growth: '基金份额增长',
  fund_share_shrink: '基金份额缩水',
  fund_small_scale: '基金规模较小',
  fund_stable_nav: '净值稳定',
  fund_tracking_deviation_risk: '基金跟踪偏离风险',
  fund_tracking_error: '跟踪误差',
  fundamental_risk: '基本面风险',
  gap_down: '跳空低开',
  gap_up: '跳空高开',
  general: '通用投资经验',
  herding_effect: '羊群效应 / 从众行为',
  high_dividend: '高股息',
  high_pb: '高 PB',
  high_pe: '高 PE',
  high_turnover: '高换手',
  index: '指数',
  index_fund: '指数基金',
  institutional_behavior: '机构 / 基金行为',
  large_cap_stock: '大盘股',
  liquidity_risk: '流动性风险',
  lof: 'LOF',
  low_pb: '低 PB',
  low_pe: '低 PE',
  low_price_stock: '低价股',
  low_turnover: '低换手',
  market_attention_rise: '关注度上升',
  money_fund: '货币基金',
  near_recent_high: '接近近期高位',
  near_recent_low: '接近近期低位',
  news_driven: '消息驱动',
  observe_next_day: '观察次日表现',
  overheated_risk: '过热风险',
  panic_selling: '恐慌抛售',
  policy_driven: '政策驱动',
  position_control: '仓位控制',
  price_drop: '价格下跌',
  price_rise: '价格上涨',
  pullback: '回调',
  qdii_fund: 'QDII 基金',
  range_bound: '区间震荡',
  rebound: '反弹',
  repair: '修复',
  risk_control: '风险控制',
  sector_rotation: '板块轮动',
  short_term_emotion: '短线情绪升温',
  sideways: '横盘',
  small_cap_stock: '小盘股',
  stock: '股票',
  stop_loss_plan: '止损计划',
  take_profit_plan: '止盈计划',
  trend_reversal: '趋势反转',
  turn_strong: '转强',
  turn_weak: '转弱',
  uptrend: '上升趋势',
  valuation_repair: '估值修复',
  valuation_trap: '低估值陷阱',
  valuation_trap_risk: '估值陷阱风险',
  volume_dry_up: '成交枯竭',
  volume_expand: '放量',
  volume_price_confirm: '量价配合',
  volume_price_divergence: '量价背离',
  volume_shrink: '缩量',
  volume_spike: '成交量突然放大',
  wait_confirm: '等待确认',
  weak_sentiment: '情绪偏弱',
};

const statusMeta: Record<
  string,
  { label: string; type: 'danger' | 'info' | 'primary' | 'success' | 'warning' }
> = {
  current_scenes_ready: { label: '场景已计算', type: 'warning' },
  failed: { label: '失败', type: 'danger' },
  pending: { label: '等待中', type: 'info' },
  processing_current_scenes: { label: '计算场景', type: 'warning' },
  retrieving_knowledge: { label: '检索中', type: 'primary' },
  success: { label: '完成', type: 'success' },
};

const chunks = computed(() => materialTask.value?.chunks ?? []);
const sceneFilterOptions = computed(() => {
  const counts = new Map<string, number>();
  for (const chunk of chunks.value) {
    const scene = chunk.scene || '';
    counts.set(scene, (counts.get(scene) ?? 0) + 1);
  }
  return [...counts.entries()].map(([scene, count]) => ({
    count,
    label: sceneLabel(scene),
    value: scene,
  }));
});
const tagFilterOptions = computed(() => {
  const counts = new Map<string, number>();
  for (const chunk of chunks.value) {
    if (selectedSceneFilter.value && chunk.scene !== selectedSceneFilter.value) {
      continue;
    }
    if (
      filenameFilter.value.trim() &&
      !matchesKnowledgeName(chunk, filenameFilter.value)
    ) {
      continue;
    }
    for (const tag of chunk.matchedTags || []) {
      counts.set(tag, (counts.get(tag) ?? 0) + 1);
    }
  }
  return [...counts.entries()].map(([tag, count]) => ({
    count,
    label: tagLabel(tag),
    value: tag,
  }));
});
const filteredChunks = computed(() =>
  chunks.value.filter((chunk) => {
    if (selectedSceneFilter.value && chunk.scene !== selectedSceneFilter.value) {
      return false;
    }
    if (
      selectedTagFilter.value &&
      !(chunk.matchedTags || []).includes(selectedTagFilter.value)
    ) {
      return false;
    }
    if (
      filenameFilter.value.trim() &&
      !matchesKnowledgeName(chunk, filenameFilter.value)
    ) {
      return false;
    }
    return true;
  }),
);
const groupedChunks = computed(() => {
  const groups = new Map<string, KnowledgeMaterialChunk[]>();
  for (const chunk of filteredChunks.value) {
    const scene = chunk.scene || '';
    groups.set(scene, [...(groups.get(scene) || []), chunk]);
  }
  return [...groups.entries()].map(([scene, items]) => ({
    chunks: items,
    label: sceneLabel(scene),
    scene,
  }));
});
const selectedProfile = computed(() =>
  configProfiles.value.find((profile) => profile.id === selectedProfileId.value),
);
const configGroupOptions = computed(() => [
  ...new Set(configProfiles.value.map((profile) => profile.configGroup)),
]);
const filteredConfigProfiles = computed(() =>
  selectedConfigGroup.value
    ? configProfiles.value.filter(
        (profile) => profile.configGroup === selectedConfigGroup.value,
      )
    : configProfiles.value,
);
const displayParameterGroups = computed<DisplayParameterGroup[]>(() =>
  parameterGroups.value.map((group) => ({
    label: group.label,
    name: group.name,
    sections: parameterSections(group),
  })),
);
const coreParameterGroups = computed<DisplayParameterGroup[]>(() =>
  parameterGroups.value
    .map((group) => ({
      ...group,
      fields: group.fields.filter((field) =>
        CORE_PARAMETER_KEYS.has(field.key),
      ),
    }))
    .filter((group) => group.fields.length > 0)
    .map((group) => ({
      label: group.label,
      name: group.name,
      sections: parameterSections(group),
    })),
);
const visibleParameterGroups = computed(() =>
  advancedParameterVisible.value
    ? displayParameterGroups.value
    : coreParameterGroups.value,
);
const submitDisabled = computed(() => {
  if (submitting.value) {
    return true;
  }
  if (searchMode.value === 'target') {
    return !form.value.targetCode;
  }
  return !form.value.queryText.trim();
});
const currentStatus = computed(() =>
  materialTask.value?.status
    ? statusMeta[materialTask.value.status]
    : undefined,
);
const taskTitle = computed(() => {
  if (!materialTask.value) {
    return '暂无任务';
  }
  if (materialTask.value.searchMode === 'target') {
    return displayTarget(materialTask.value);
  }
  return materialTask.value.queryText || '自然语言检索';
});

onMounted(async () => {
  await Promise.all([loadParameterSchema(), loadReportTypes()]);
  await loadProfiles();
});

watch(
  () => form.value.targetType,
  () => {
    form.value.targetCode = '';
    form.value.targetName = '';
    targetOptions.value = [];
  },
);

onBeforeUnmount(() => {
  stopPolling();
});

async function handleSubmit() {
  if (submitDisabled.value) {
    return;
  }
  submitting.value = true;
  try {
    const payload =
      searchMode.value === 'target'
        ? {
            configProfile: form.value.configProfile,
            dailyKlineLimit: normalizedDailyKlineLimit(),
            monthlyKlineLimit: normalizedMonthlyKlineLimit(),
            reportType: form.value.reportType,
            searchMode: searchMode.value,
            targetCode: form.value.targetCode,
            targetName: form.value.targetName,
            targetType: form.value.targetType,
            totalChunks: form.value.totalChunks,
            weeklyKlineLimit: normalizedWeeklyKlineLimit(),
            userOverrides: buildUserOverrides(),
          }
        : {
            queryText: form.value.queryText.trim(),
            searchMode: searchMode.value,
            totalChunks: form.value.totalChunks,
          };
    const result = await submitKnowledgeMaterialTask(payload);
    activeTaskNo.value = result.taskNo;
    resetResultFilters();
    materialTask.value = {
      chunks: [],
      queryText: result.queryText,
      rewrittenQuery: result.rewrittenQuery,
      searchMode: result.searchMode,
      status: result.status,
      targetCode: result.targetCode,
      targetName: result.targetName,
      targetType: result.targetType,
      taskNo: result.taskNo,
    };
    await refreshTask();
    startPolling();
    ElMessage.success('已提交材料检索');
  } finally {
    submitting.value = false;
  }
}

async function loadProfiles() {
  loadingProfiles.value = true;
  try {
    configProfiles.value = await listSceneAnalysisConfigProfiles();
    selectProfile(selectedProfileId.value);
  } finally {
    loadingProfiles.value = false;
  }
}

async function loadReportTypes() {
  const types = await getSceneAnalysisReportTypes();
  if (types.length > 0) {
    reportTypeOptions.value = types;
  }
}

async function loadParameterSchema() {
  loadingParameterSchema.value = true;
  try {
    parameterGroups.value = await getSceneAnalysisConfigParameterSchema();
    parameterValues.value = defaultParameterValues();
  } finally {
    loadingParameterSchema.value = false;
  }
}

function changeConfigGroup(configGroup: string) {
  selectedConfigGroup.value = configGroup;
  const profile = filteredConfigProfiles.value[0];
  selectedProfileId.value = profile?.id ?? null;
  applyProfile(profile);
}

function changeProfile(profileId: number) {
  selectProfile(profileId);
}

function selectProfile(profileId?: null | number) {
  const profile =
    configProfiles.value.find((item) => item.id === profileId) ??
    configProfiles.value.find(
      (item) => item.configProfile === 'system_recommended',
    ) ??
    configProfiles.value[0];
  selectedProfileId.value = profile?.id ?? null;
  selectedConfigGroup.value = profile?.configGroup ?? '';
  applyProfile(profile);
}

function applyProfile(profile?: SceneAnalysisConfigProfile) {
  if (!profile) {
    return;
  }
  const config = profile.configJson || {};
  const profileTargetType = textValue(
    config.targetType,
    profile.targetType || 'STOCK',
  );
  form.value = {
    ...form.value,
    configProfile: textValue(config.configProfile, profile.configProfile),
    dailyKlineLimit: numberValue(
      config.dailyKlineLimit,
      DEFAULT_DAILY_KLINE_LIMIT,
    ),
    monthlyKlineLimit: numberValue(
      config.monthlyKlineLimit,
      DEFAULT_MONTHLY_KLINE_LIMIT,
    ),
    reportType: textValue(
      config.reportType,
      profile.reportType || 'quick_analysis',
    ),
    targetType: form.value.targetCode
      ? form.value.targetType
      : profileTargetType,
    totalChunks: numberValue(config.totalChunks, DEFAULT_TOTAL_CHUNKS),
    weeklyKlineLimit: numberValue(
      config.weeklyKlineLimit,
      DEFAULT_WEEKLY_KLINE_LIMIT,
    ),
  };
  parameterValues.value = parameterValuesFromOverrides(config.userOverrides);
}

async function refreshTask(showMessage = false) {
  if (!activeTaskNo.value) {
    return;
  }
  loadingTask.value = true;
  try {
    materialTask.value = await getKnowledgeMaterialTask(activeTaskNo.value);
    if (isTerminal(materialTask.value.status)) {
      stopPolling();
    }
    if (showMessage) {
      ElMessage.success('材料任务已刷新');
    }
  } finally {
    loadingTask.value = false;
  }
}

function startPolling() {
  stopPolling();
  pollTimer = window.setInterval(() => {
    if (activeTaskNo.value) {
      void refreshTask();
    }
  }, 1600);
}

function stopPolling() {
  if (pollTimer) {
    window.clearInterval(pollTimer);
    pollTimer = undefined;
  }
}

async function loadTargetOptions(keyword: string) {
  loadingTargets.value = true;
  try {
    const options = await searchSceneAnalysisTargets({
      keyword: keyword?.trim() || undefined,
      limit: 20,
      targetType: form.value.targetType,
    });
    targetOptions.value = withSelectedTargetOption(options);
  } finally {
    loadingTargets.value = false;
  }
}

function handleTargetChange(targetCode: string) {
  const selected = targetOptions.value.find(
    (item) => item.targetCode === targetCode,
  );
  form.value.targetName = selected?.targetName ?? '';
  targetOptions.value = withSelectedTargetOption(targetOptions.value);
}

function targetOptionLabel(option: SceneAnalysisTargetOption) {
  return option.targetName
    ? `${option.targetName} ${option.targetCode}`
    : option.targetCode;
}

function withSelectedTargetOption(options: SceneAnalysisTargetOption[]) {
  if (!form.value.targetCode) {
    return options;
  }
  const exists = options.some(
    (item) =>
      item.targetType === form.value.targetType &&
      item.targetCode === form.value.targetCode,
  );
  if (exists) {
    return options;
  }
  return [
    {
      targetCode: form.value.targetCode,
      targetName: form.value.targetName || undefined,
      targetType: form.value.targetType,
    },
    ...options,
  ];
}

function normalizedDailyKlineLimit() {
  return Math.max(
    numberValue(form.value.dailyKlineLimit, DEFAULT_DAILY_KLINE_LIMIT),
    MIN_DAILY_KLINE_LIMIT,
  );
}

function normalizedWeeklyKlineLimit() {
  return numberValue(form.value.weeklyKlineLimit, DEFAULT_WEEKLY_KLINE_LIMIT);
}

function normalizedMonthlyKlineLimit() {
  return numberValue(form.value.monthlyKlineLimit, DEFAULT_MONTHLY_KLINE_LIMIT);
}

function buildUserOverrides() {
  const overrides: Record<string, unknown> = {
    asset_type: assetTypeByTargetType(form.value.targetType),
  };
  parameterGroups.value
    .flatMap((group) => group.fields)
    .forEach((field) => {
      setNestedValue(
        overrides,
        field.path,
        parameterValues.value[field.key] ?? field.defaultValue,
      );
    });
  return overrides;
}

function parameterValuesFromOverrides(value: unknown) {
  const overrides = objectValue(value);
  const result = defaultParameterValues();
  parameterGroups.value
    .flatMap((group) => group.fields)
    .forEach((field) => {
      const nestedValue = getNestedValue(overrides, field.path);
      if (typeof nestedValue === 'number' && Number.isFinite(nestedValue)) {
        result[field.key] = nestedValue;
      }
    });
  return result;
}

function defaultParameterValues() {
  const result: Record<string, number> = {};
  for (const group of parameterGroups.value) {
    for (const field of group.fields) {
      result[field.key] = field.defaultValue;
    }
  }
  return result;
}

function objectValue(value: unknown) {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function getNestedValue(source: Record<string, unknown>, path: string[]) {
  let current: unknown = source;
  for (const key of path) {
    if (!current || typeof current !== 'object' || Array.isArray(current)) {
      return undefined;
    }
    current = (current as Record<string, unknown>)[key];
  }
  return current;
}

function setNestedValue(
  target: Record<string, unknown>,
  path: string[],
  value: unknown,
) {
  let cursor = target;
  path.forEach((key, index) => {
    if (index === path.length - 1) {
      cursor[key] = value;
      return;
    }
    if (
      !cursor[key] ||
      typeof cursor[key] !== 'object' ||
      Array.isArray(cursor[key])
    ) {
      cursor[key] = {};
    }
    cursor = cursor[key] as Record<string, unknown>;
  });
}

function assetTypeByTargetType(targetType: string) {
  if (targetType === 'INDEX') {
    return 'index';
  }
  if (targetType === 'CONVERTIBLE_BOND') {
    return 'convertible_bond';
  }
  return 'stock';
}

function textValue(value: unknown, fallback: string) {
  return typeof value === 'string' && value.trim() ? value.trim() : fallback;
}

function numberValue(value: unknown, fallback: number) {
  return typeof value === 'number' && value > 0 ? value : fallback;
}

function parameterTooltip(field: ParameterField) {
  return `${field.description} 推荐范围：${field.recommended}`;
}

function parameterValueLabel(field: ParameterField) {
  const value = parameterValues.value[field.key] ?? field.defaultValue;
  return `${value}${field.unit || ''}`;
}

function parameterSectionKey(field: ParameterField) {
  return field.path.length >= 3 ? (field.path[1] ?? 'basic') : 'basic';
}

function parameterSectionLabel(group: ParameterGroup, key: string) {
  const labels: Record<string, string> = {
    basic: '基础参数',
    chase_high_risk_weights: '追高风险权重',
    drawdown_risk_weights: '回撤风险权重',
    false_breakout_risk_weights: '假突破风险权重',
    liquidity_risk_weights: '流动性风险权重',
    market_proxy_emotion_weights: '短线情绪权重',
    market_proxy_herding_weights: '羊群效应权重',
    market_proxy_panic_weights: '恐慌抛售权重',
    market_proxy_weak_weights: '弱势情绪权重',
    overheated_risk_weights: '过热风险权重',
    position_control_weights: '仓位控制权重',
    stop_loss_plan_weights: '止损计划权重',
    take_profit_plan_weights: '止盈计划权重',
    uncertainty_weights: '不确定性权重',
  };
  return labels[key] || group.label;
}

function parameterSections(group: ParameterGroup): DisplayParameterSection[] {
  const sectionMap = new Map<string, ParameterField[]>();
  for (const field of group.fields) {
    const key = parameterSectionKey(field);
    sectionMap.set(key, [...(sectionMap.get(key) || []), field]);
  }
  return [...sectionMap.entries()].map(([key, fields]) => ({
    fields,
    key,
    label: parameterSectionLabel(group, key),
  }));
}

function isTerminal(status?: string) {
  return status === 'success' || status === 'failed';
}

function displayTarget(task: {
  targetCode?: null | string;
  targetName?: null | string;
  targetType?: null | string;
}) {
  if (!task.targetCode) {
    return '未选择标的';
  }
  const typeLabel =
    targetTypeOptions.find((item) => item.value === task.targetType)?.label ??
    task.targetType;
  return task.targetName
    ? `${task.targetName} ${task.targetCode} · ${typeLabel}`
    : `${task.targetCode} · ${typeLabel}`;
}

function sceneLabel(scene?: string) {
  if (!scene) {
    return '未分类';
  }
  return sceneLabels[scene] ?? scene;
}

function tagLabel(tag?: string) {
  if (!tag) {
    return '';
  }
  return tagLabels[tag] ?? tag;
}

function matchesKnowledgeName(chunk: KnowledgeMaterialChunk, keyword: string) {
  const normalizedKeyword = keyword.trim().toLowerCase();
  if (!normalizedKeyword) {
    return true;
  }
  return [chunk.filename, chunk.taskNo, chunkReference(chunk)].some((value) =>
    (value || '').toLowerCase().includes(normalizedKeyword),
  );
}

function resetResultFilters() {
  selectedSceneFilter.value = '';
  selectedTagFilter.value = '';
  filenameFilter.value = '';
}

function scoreText(score?: null | number) {
  if (typeof score !== 'number') {
    return '--';
  }
  return `${Math.round(score * 1000) / 10}%`;
}

function chunkReference(chunk: KnowledgeMaterialChunk) {
  const chunkIndex =
    typeof chunk.chunkIndex === 'number' ? `#${chunk.chunkIndex}` : '';
  return [chunk.filename || chunk.taskNo, chunkIndex].filter(Boolean).join(' ');
}
</script>

<template>
  <Page>
    <PageHero
      description="按标的或自然语言检索知识库原文，展示召回材料和检索理解，不生成报告。"
      title="知识库材料"
    >
      <template #actions>
        <ElButton
          :disabled="submitDisabled"
          :loading="submitting"
          type="primary"
          @click="handleSubmit"
        >
          <IconifyIcon icon="lucide:search" />
          检索材料
        </ElButton>
        <ElButton
          :disabled="!activeTaskNo"
          :loading="loadingTask"
          @click="refreshTask(true)"
        >
          <IconifyIcon icon="lucide:refresh-cw" />
          刷新结果
        </ElButton>
      </template>
    </PageHero>

    <div class="materials-layout">
      <section class="search-panel">
        <div class="panel-title">检索条件</div>
        <ElForm label-position="top">
          <ElFormItem label="检索方式">
            <ElRadioGroup v-model="searchMode">
              <ElRadioButton value="target">按标的</ElRadioButton>
              <ElRadioButton value="natural_language">自然语言</ElRadioButton>
            </ElRadioGroup>
          </ElFormItem>

          <template v-if="searchMode === 'target'">
            <div class="config-block">
              <div class="config-block-title">报告配置</div>
              <ElFormItem label="配置分组">
                <ElSelect
                  v-model="selectedConfigGroup"
                  :loading="loadingProfiles"
                  class="full-control"
                  placeholder="选择配置分组"
                  @change="changeConfigGroup"
                >
                  <ElOption
                    v-for="group in configGroupOptions"
                    :key="group"
                    :label="group"
                    :value="group"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="配置模板">
                <ElSelect
                  :model-value="selectedProfileId"
                  :loading="loadingProfiles"
                  class="full-control"
                  filterable
                  placeholder="选择配置模板"
                  @change="changeProfile"
                >
                  <ElOption
                    v-for="profile in filteredConfigProfiles"
                    :key="profile.id"
                    :label="`${profile.name}${profile.systemDefault ? '（系统）' : ''}`"
                    :value="profile.id"
                  />
                </ElSelect>
              </ElFormItem>
            </div>

            <ElFormItem label="标的类型">
              <ElSelect v-model="form.targetType" class="full-control">
                <ElOption
                  v-for="option in targetTypeOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="选择标的">
              <ElSelect
                v-model="form.targetCode"
                :loading="loadingTargets"
                class="full-control"
                filterable
                remote
                reserve-keyword
                placeholder="输入名称或代码"
                :remote-method="loadTargetOptions"
                @change="handleTargetChange"
                @focus="loadTargetOptions('')"
              >
                <ElOption
                  v-for="option in targetOptions"
                  :key="`${option.targetType}-${option.targetCode}`"
                  :label="targetOptionLabel(option)"
                  :value="option.targetCode"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="场景口径">
              <ElSelect v-model="form.reportType" class="full-control">
                <ElOption
                  v-for="option in reportTypeOptions"
                  :key="option.code"
                  :label="option.label"
                  :value="option.code"
                />
              </ElSelect>
            </ElFormItem>

            <div class="form-grid three-columns">
              <ElFormItem label="日线数">
                <ElInputNumber
                  v-model="form.dailyKlineLimit"
                  :max="250"
                  :min="MIN_DAILY_KLINE_LIMIT"
                  class="full-control"
                  controls-position="right"
                />
              </ElFormItem>
              <ElFormItem label="周线数">
                <ElInputNumber
                  v-model="form.weeklyKlineLimit"
                  :max="250"
                  :min="1"
                  class="full-control"
                  controls-position="right"
                />
              </ElFormItem>
              <ElFormItem label="月线数">
                <ElInputNumber
                  v-model="form.monthlyKlineLimit"
                  :max="250"
                  :min="1"
                  class="full-control"
                  controls-position="right"
                />
              </ElFormItem>
            </div>

            <div class="parameter-panel">
              <div class="parameter-panel-head">
                <div>
                  <div class="config-block-title">参数覆盖</div>
                  <div class="config-block-meta">
                    {{ selectedProfile?.name || '未选择模板' }}
                  </div>
                </div>
                <ElButton
                  :disabled="displayParameterGroups.length === 0"
                  :loading="loadingParameterSchema"
                  link
                  type="primary"
                  @click="advancedParameterVisible = !advancedParameterVisible"
                >
                  {{ advancedParameterVisible ? '收起参数' : '展开全部参数' }}
                </ElButton>
              </div>

              <ElEmpty
                v-if="!loadingParameterSchema && visibleParameterGroups.length === 0"
                description="暂无可覆盖参数"
              />
              <div v-else class="parameter-groups">
                <section
                  v-for="group in visibleParameterGroups"
                  :key="group.name"
                  class="parameter-group"
                >
                  <div class="parameter-group-title">{{ group.label }}</div>
                  <div
                    v-for="section in group.sections"
                    :key="`${group.name}-${section.key}`"
                    class="parameter-section"
                  >
                    <div class="parameter-section-title">
                      {{ section.label }}
                    </div>
                    <div class="parameter-grid">
                      <div
                        v-for="field in section.fields"
                        :key="field.key"
                        class="parameter-field"
                      >
                        <div class="parameter-field-head">
                          <ElTooltip
                            :content="parameterTooltip(field)"
                            placement="top"
                          >
                            <span>{{ field.label }}</span>
                          </ElTooltip>
                          <strong>{{ parameterValueLabel(field) }}</strong>
                        </div>
                        <ElSlider
                          v-model="parameterValues[field.key]"
                          :max="field.max"
                          :min="field.min"
                          :step="field.step"
                          show-input
                        />
                      </div>
                    </div>
                  </div>
                </section>
              </div>
            </div>
          </template>

          <ElFormItem v-else label="检索问题">
            <ElInput
              v-model="form.queryText"
              :autosize="{ minRows: 4, maxRows: 7 }"
              maxlength="500"
              placeholder="例如：低估值银行股有哪些风险控制材料"
              show-word-limit
              type="textarea"
            />
          </ElFormItem>

          <ElFormItem label="召回数量">
            <ElInputNumber
              v-model="form.totalChunks"
              :max="20"
              :min="1"
              class="full-control"
            />
          </ElFormItem>
        </ElForm>
      </section>

      <section class="result-panel">
        <div class="result-head">
          <div class="result-title-group">
            <div class="panel-title">检索结果</div>
            <div class="task-title">{{ taskTitle }}</div>
          </div>
          <ElTag v-if="currentStatus" :type="currentStatus.type">
            {{ currentStatus.label }}
          </ElTag>
        </div>

        <ElSkeleton v-if="loadingTask && !materialTask" :rows="6" animated />

        <ElEmpty
          v-else-if="!materialTask"
          description="提交检索后展示知识库材料"
        />

        <template v-else>
          <div v-if="materialTask.status === 'failed'" class="error-block">
            {{ materialTask.errorMessage || '材料检索失败' }}
          </div>

          <ElEmpty
            v-else-if="materialTask.status === 'success' && chunks.length === 0"
            description="没有匹配到知识库材料"
          />

          <template v-else>
            <div v-if="chunks.length > 0" class="result-filters">
              <ElSelect
                v-model="selectedSceneFilter"
                class="filter-control"
                clearable
                filterable
                placeholder="筛选场景"
              >
                <ElOption
                  v-for="option in sceneFilterOptions"
                  :key="option.value || 'unclassified'"
                  :label="`${option.label}（${option.count}）`"
                  :value="option.value"
                />
              </ElSelect>
              <ElSelect
                v-model="selectedTagFilter"
                class="filter-control"
                clearable
                filterable
                placeholder="筛选标签"
              >
                <ElOption
                  v-for="option in tagFilterOptions"
                  :key="option.value"
                  :label="`${option.label}（${option.count}）`"
                  :value="option.value"
                />
              </ElSelect>
              <ElInput
                v-model="filenameFilter"
                class="filter-control"
                clearable
                placeholder="知识库名称"
              />
              <ElButton class="filter-reset" text @click="resetResultFilters">
                <IconifyIcon icon="lucide:rotate-ccw" />
                重置
              </ElButton>
            </div>

            <ElEmpty
              v-if="chunks.length > 0 && filteredChunks.length === 0"
              description="当前筛选条件下没有匹配材料"
            />

            <div v-else-if="chunks.length > 0" class="scene-groups">
              <section
                v-for="group in groupedChunks"
                :key="group.scene || 'unclassified'"
                class="scene-group"
              >
                <div class="scene-group-head">
                  <span class="scene-group-title">{{ group.label }}</span>
                  <ElTag size="small" type="info">
                    {{ group.chunks.length }} 条
                  </ElTag>
                </div>

                <div class="result-list">
                  <article
                    v-for="chunk in group.chunks"
                    :key="`${chunk.taskNo}-${chunk.chunkId}`"
                    class="result-item"
                  >
                    <div class="chunk-head">
                      <div class="chunk-source">
                        <span class="source-title">
                          {{ chunkReference(chunk) }}
                        </span>
                        <ElTag size="small">{{ sceneLabel(chunk.scene) }}</ElTag>
                      </div>
                      <div class="score-group">
                        <span>综合 {{ scoreText(chunk.finalScore) }}</span>
                        <span>语义 {{ scoreText(chunk.semanticScore) }}</span>
                      </div>
                    </div>

                    <div v-if="chunk.matchedTags.length > 0" class="tag-row">
                      <ElTag
                        v-for="tag in chunk.matchedTags"
                        :key="tag"
                        effect="plain"
                        size="small"
                        type="info"
                      >
                        {{ tagLabel(tag) }}
                      </ElTag>
                    </div>

                    <div class="original-text">{{ chunk.text }}</div>
                  </article>
                </div>
              </section>
            </div>

            <ElEmpty v-else description="等待检索结果" />
          </template>
        </template>
      </section>
    </div>
  </Page>
</template>

<style scoped>
.materials-layout {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 16px;
  margin-top: 16px;
}

.search-panel,
.result-panel {
  min-width: 0;
  padding: 20px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.panel-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.full-control {
  width: 100%;
}

.config-block,
.parameter-panel {
  padding-top: 14px;
  margin-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.config-block-title {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.config-block-meta {
  margin-top: -4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0;
}

.parameter-panel-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}

.parameter-groups {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.parameter-group {
  padding: 12px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.parameter-group-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.parameter-section {
  margin-top: 12px;
}

.parameter-section-title {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.parameter-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
}

.parameter-field {
  min-width: 0;
}

.parameter-field-head {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.parameter-field-head span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.parameter-field-head strong {
  flex: 0 0 auto;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.parameter-field :deep(.el-slider__input) {
  width: 92px;
}

.result-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.result-title-group {
  min-width: 0;
}

.task-title {
  margin-top: 6px;
  overflow-wrap: anywhere;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.error-block {
  padding: 12px 14px;
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  border: 1px solid var(--el-color-danger-light-7);
  border-radius: 8px;
}

.result-filters {
  display: grid;
  grid-template-columns:
    minmax(140px, 180px) minmax(150px, 220px)
    minmax(180px, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 12px;
  margin-bottom: 14px;
  background: transparent;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.filter-control {
  width: 100%;
}

.filter-reset {
  justify-self: end;
}

.scene-groups {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.scene-group {
  padding-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.scene-group:first-child {
  padding-top: 0;
  border-top: 0;
}

.scene-group-head {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.scene-group-title {
  min-width: 0;
  overflow-wrap: anywhere;
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-item {
  padding: 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.chunk-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.chunk-source {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
  align-items: center;
}

.source-title {
  overflow-wrap: anywhere;
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.score-group {
  display: flex;
  flex: 0 0 auto;
  gap: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.original-text {
  padding: 12px;
  margin-top: 12px;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--el-text-color-primary);
}

@media (max-width: 1024px) {
  .materials-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .search-panel,
  .result-panel {
    padding: 16px;
  }

  .chunk-head,
  .result-head {
    flex-direction: column;
  }

  .result-filters {
    grid-template-columns: 1fr;
  }

  .filter-reset {
    justify-self: start;
  }

  .score-group {
    flex-wrap: wrap;
  }
}
</style>
