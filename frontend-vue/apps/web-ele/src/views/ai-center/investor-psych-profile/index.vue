<script lang="ts" setup>
import type {
  InvestorPsychProfile,
  InvestorPsychProfileQuestion,
} from '#/api/investor-psych-profile';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import {
  ElButton,
  ElEmpty,
  ElMessage,
  ElProgress,
  ElSkeleton,
  ElTag,
} from 'element-plus';

import {
  getInvestorPsychProfile,
  getInvestorPsychProfileQuestionnaire,
  submitInvestorPsychProfile,
  updateInvestorPsychProfile,
} from '#/api/investor-psych-profile';
import PageHero from '#/components/page-hero/index.vue';

const questions = ref<InvestorPsychProfileQuestion[]>([]);
const profile = ref<InvestorPsychProfile>();
const answers = reactive<Record<string, string>>({});
const loading = ref(false);
const saving = ref(false);

const answeredCount = computed(() => {
  return questions.value.filter((question) => answers[question.code]).length;
});

const completed = computed(() => answeredCount.value === questions.value.length);
const profileCompleted = computed(() => Boolean(profile.value?.profileCompleted));
const progressPercentage = computed(() => {
  if (questions.value.length === 0) {
    return 0;
  }
  return Math.round((answeredCount.value / questions.value.length) * 100);
});

const questionGroups = computed(() => [
  questions.value.slice(0, 5),
  questions.value.slice(5),
]);

const profileHighlights = computed(() => {
  if (!profile.value?.profileCompleted) {
    return [];
  }
  return [
    {
      label: '波动情绪',
      value: labelOf('riskEmotion', profile.value.riskEmotion),
    },
    {
      label: '决策风格',
      value: labelOf('decisionStyle', profile.value.decisionStyle),
    },
    {
      label: '操作节奏',
      value: labelOf('tradingTempo', profile.value.tradingTempo),
    },
    {
      label: '回答偏好',
      value: labelOf('explanationPreference', profile.value.explanationPreference),
    },
  ];
});

onMounted(() => {
  void loadPageData();
});

async function loadPageData() {
  loading.value = true;
  try {
    const [questionnaire, currentProfile] = await Promise.all([
      getInvestorPsychProfileQuestionnaire(),
      getInvestorPsychProfile(),
    ]);
    questions.value = questionnaire.questions;
    profile.value = currentProfile;
  } finally {
    loading.value = false;
  }
}

function selectAnswer(questionCode: string, optionCode: string) {
  answers[questionCode] = optionCode;
}

async function saveProfile() {
  if (!completed.value) {
    ElMessage.warning('请先完成全部 10 题');
    return;
  }
  saving.value = true;
  try {
    const payload = {
      answers: questions.value.map((question) => ({
        optionCode: answers[question.code] || '',
        questionCode: question.code,
      })),
    };
    profile.value = profileCompleted.value
      ? await updateInvestorPsychProfile(payload)
      : await submitInvestorPsychProfile(payload);
    ElMessage.success('投资心理画像已更新');
  } finally {
    saving.value = false;
  }
}

function resetAnswers() {
  for (const question of questions.value) {
    delete answers[question.code];
  }
}

function labelOf(dimension: string, value?: null | string) {
  if (!value) {
    return '未识别';
  }
  return LABELS[dimension]?.[value] ?? value;
}

function adviceStyleTone(value?: null | string) {
  if (value === 'explicit_trade_light_position') {
    return 'success';
  }
  if (value === 'conditional_trade') {
    return 'warning';
  }
  return 'info';
}

const LABELS: Record<string, Record<string, string>> = {
  adviceStyle: {
    conditional_trade: '条件建议型',
    explicit_trade_light_position: '轻仓明确建议型',
    explicit_trade_with_position: '仓位建议型',
    risk_first: '风险优先型',
  },
  decisionStyle: {
    clear_conclusion: '喜欢明确结论',
    clear_conclusion_with_conditions: '结论 + 条件触发',
    condition_trigger: '条件触发',
    data_driven: '数据逻辑',
    risk_first: '风险优先',
  },
  explanationPreference: {
    conditional_branches: '条件分支',
    conclusion_with_key_reason: '结论 + 关键理由',
    full_data_logic: '完整数据逻辑',
    short_conclusion: '简短结论',
  },
  holdingMindset: {
    chase_high_tendency: '容易追高',
    decision_hesitation: '压力下犹豫',
    entry_patience: '有等待耐心',
    hard_to_stop_loss: '止损容易犹豫',
    plan_based: '能按计划执行',
    profit_taking_early: '盈利容易拿不住',
    stop_loss_discipline: '止损纪律较好',
    trend_holding_ability: '能顺势持有',
  },
  riskEmotion: {
    average_down_impulse: '亏损后有补仓冲动',
    can_accept_volatility: '能接受波动',
    high_volatility_seeking: '偏好高弹性',
    loss_anxiety: '亏损焦虑',
    volatility_averse: '波动回避',
  },
  tradingTempo: {
    fast_short_term: '短线快进快出',
    long_term_holding: '中长期持有',
    market_driven_uncertain: '容易被行情带着走',
    patient_wait: '等待确定性',
    swing_holding: '波段持有',
    trial_position: '小仓位试错',
  },
};
</script>

<template>
  <Page auto-content-height>
    <div class="profile-page">
      <PageHero
        description="用 10 个真实交易场景推导心理画像，AI Chat 会据此调整买卖建议、仓位边界和风险提醒。"
        title="投资心理画像"
      >
        <template #actions>
          <ElButton :disabled="saving" @click="resetAnswers">
            <IconifyIcon icon="lucide:rotate-ccw" />
            重置答案
          </ElButton>
          <ElButton
            :disabled="!completed"
            :loading="saving"
            type="primary"
            @click="saveProfile"
          >
            <IconifyIcon icon="lucide:save" />
            保存画像
          </ElButton>
        </template>
      </PageHero>

      <ElSkeleton v-if="loading" :rows="10" animated />

      <template v-else>
        <section class="profile-layout">
          <div class="question-panel">
            <div class="panel-header">
              <div>
                <h2>问卷</h2>
                <p>只记录行为反应，不让用户直接选择建议强度。</p>
              </div>
              <div class="progress-block">
                <span>{{ answeredCount }}/{{ questions.length }}</span>
                <ElProgress
                  :percentage="progressPercentage"
                  :show-text="false"
                  :stroke-width="8"
                />
              </div>
            </div>

            <div
              v-for="(group, groupIndex) in questionGroups"
              :key="groupIndex"
              class="question-group"
            >
              <div class="group-title">
                {{ groupIndex === 0 ? '交易反应' : '执行与表达' }}
              </div>
              <article
                v-for="question in group"
                :key="question.code"
                class="question-item"
              >
                <div class="question-title">
                  <span>{{ question.code }}</span>
                  <strong>{{ question.title }}</strong>
                </div>
                <div class="option-grid">
                  <button
                    v-for="option in question.options"
                    :key="option.code"
                    class="option-button"
                    :class="{ selected: answers[question.code] === option.code }"
                    type="button"
                    @click="selectAnswer(question.code, option.code)"
                  >
                    <span class="option-code">{{ option.code }}</span>
                    <span class="option-label">{{ option.label }}</span>
                  </button>
                </div>
              </article>
            </div>
          </div>

          <aside class="result-panel">
            <div class="result-card primary">
              <div class="result-icon">
                <IconifyIcon icon="lucide:brain" />
              </div>
              <div>
                <h2>当前画像</h2>
                <p v-if="profileCompleted">
                  第 {{ profile?.profileVersion }} 版，AI Chat 会按这个画像约束最终建议。
                </p>
                <p v-else>还没有保存画像，AI Chat 会保持通用研究口径。</p>
              </div>
            </div>

            <div v-if="profileCompleted" class="result-stack">
              <div class="advice-band">
                <span>建议强度</span>
                <ElTag
                  :type="adviceStyleTone(profile?.adviceStyle)"
                  effect="plain"
                >
                  {{ labelOf('adviceStyle', profile?.adviceStyle) }}
                </ElTag>
              </div>

              <div class="highlight-grid">
                <div
                  v-for="item in profileHighlights"
                  :key="item.label"
                  class="highlight-item"
                >
                  <span>{{ item.label }}</span>
                  <strong>{{ item.value }}</strong>
                </div>
              </div>

              <div class="mindset-block">
                <div class="block-title">
                  <IconifyIcon icon="lucide:clipboard-list" />
                  持仓心态
                </div>
                <div class="tag-list">
                  <ElTag
                    v-for="item in profile?.holdingMindset"
                    :key="item"
                    effect="plain"
                  >
                    {{ labelOf('holdingMindset', item) }}
                  </ElTag>
                </div>
              </div>

              <div class="summary-block">
                <div class="block-title">
                  <IconifyIcon icon="lucide:check-circle-2" />
                  AI 回答策略
                </div>
                <p>{{ profile?.summary }}</p>
              </div>
            </div>

            <ElEmpty
              v-else
              description="完成问卷后会在这里看到画像推导结果"
            />
          </aside>
        </section>
      </template>
    </div>
  </Page>
</template>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.profile-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  align-items: start;
}

.question-panel,
.result-panel {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.question-panel {
  padding: 18px;
}

.panel-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;
}

.panel-header h2,
.result-card h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.25;
}

.panel-header p,
.result-card p {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.progress-block {
  flex: 0 0 180px;
}

.progress-block span {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
  color: var(--el-text-color-secondary);
  text-align: right;
}

.question-group + .question-group {
  margin-top: 22px;
}

.group-title {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.question-item {
  padding: 14px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.question-title {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 10px;
}

.question-title span {
  flex: 0 0 auto;
  min-width: 34px;
  padding: 3px 7px;
  font-size: 12px;
  font-weight: 700;
  color: var(--el-color-primary);
  text-align: center;
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 6px;
}

.question-title strong {
  font-size: 14px;
  line-height: 1.6;
  color: var(--el-text-color-primary);
}

.option-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.option-button {
  display: flex;
  gap: 9px;
  align-items: flex-start;
  min-height: 46px;
  padding: 10px 12px;
  color: var(--el-text-color-regular);
  text-align: left;
  cursor: pointer;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  transition:
    background-color 160ms ease,
    border-color 160ms ease,
    color 160ms ease;
}

.option-button:hover {
  color: var(--el-text-color-primary);
  border-color: var(--el-color-primary-light-5);
}

.option-button.selected {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.option-code {
  flex: 0 0 auto;
  font-size: 13px;
  font-weight: 700;
}

.option-label {
  min-width: 0;
  font-size: 13px;
  line-height: 1.45;
}

.result-panel {
  position: sticky;
  top: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
}

.result-card {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.result-icon {
  display: flex;
  flex: 0 0 34px;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 8px;
}

.result-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.advice-band,
.mindset-block,
.summary-block {
  padding: 12px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.advice-band {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.advice-band span,
.highlight-item span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.highlight-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.highlight-item {
  min-height: 72px;
  padding: 11px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.highlight-item strong {
  display: block;
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.35;
  color: var(--el-text-color-primary);
}

.block-title {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.summary-block p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
  white-space: pre-line;
}

@media (max-width: 1120px) {
  .profile-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .result-panel {
    position: static;
  }
}

@media (max-width: 720px) {
  .panel-header {
    flex-direction: column;
  }

  .progress-block {
    flex: 0 0 auto;
    width: 100%;
  }

  .progress-block span {
    text-align: left;
  }

  .option-grid,
  .highlight-grid {
    grid-template-columns: 1fr;
  }
}
</style>
