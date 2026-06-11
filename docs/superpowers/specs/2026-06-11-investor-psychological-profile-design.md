# AI Chat 投资心理画像设计

## 背景

AI Chat 已具备行情查询、工具调用、知识库检索、最终答案流式输出和对话保存能力。下一步希望让 Agent 在回答买卖相关问题时，不只基于标的客观数据，还能结合用户的投资心理偏好，给出更贴近用户行为模式的建议。

本设计聚焦“投资心理画像”，不做完整券商适当性评估，也不接入默认 Report 生成链路。Report 保持客观研究底稿；用户需要个性化买卖建议时，通过 AI Chat 基于 Report、行情数据和心理画像进行二次分析。

## 目标

1. 通过 10 题场景化问卷推导用户投资心理画像。
2. 推导 6 个画像维度：波动情绪、决策风格、持仓心态、操作节奏、信息偏好、建议强度。
3. 用户只能回答行为和心理场景题，不能直接选择“建议强度”。
4. AI Chat 在最终回答阶段读取画像，并据此调整买卖建议、仓位表达、风险提醒和回答结构。
5. 问卷原始回答和推导后的画像保存在同一张用户画像表里。
6. 不影响现有对话保存、Token 统计、流式输出、工具调用和 Report 生成。

## 非目标

1. 不把心理画像接入默认 Report 生成。
2. 不做真实资产、收入、年龄、职业等强适当性字段。
3. 不让用户直接选择“明确买卖 + 高仓位”。
4. 不允许模型绕过系统规则给出无限制仓位建议。
5. 不在第一版做自动交易、下单、组合持仓管理。

## 产品边界

### Report

Report 是客观分析底稿：

- 可以分析行情、估值、趋势、情绪、风险、知识库依据。
- 可以输出通用观察条件和风险提醒。
- 不读取用户心理画像。
- 不输出“基于你的画像，建议买入 20%”这类个人化建议。

### AI Chat

AI Chat 是个性化二次分析入口：

- 可以读取用户心理画像。
- 可以读取当前对话、工具结果、行情数据、知识库结果和 Report 上下文。
- 可以根据画像给出买入、卖出、持有、减仓、观望等建议。
- 可以给出仓位区间，但必须受系统推导出的建议强度和仓位上限约束。

推荐用户路径：

```text
用户生成 Report
  -> Report 输出客观研究结论
用户在 AI Chat 追问“结合我的偏好，这个能买吗”
  -> Chat 读取 Report + 行情/知识库 + 心理画像
  -> Chat 输出个性化买卖建议
```

## 画像维度

### 1. riskEmotion：波动/亏损情绪

描述用户面对亏损、回撤、剧烈波动时的心理反应。

候选值：

- `loss_anxiety`：亏损焦虑型
- `volatility_averse`：波动回避型
- `can_accept_volatility`：可接受波动
- `high_volatility_seeking`：偏好高弹性
- `average_down_impulse`：亏损后补仓冲动

影响：

- 决定是否优先提示回撤风险。
- 决定仓位建议是否需要打折。
- 决定回答是否需要强调退出条件。

### 2. decisionStyle：决策风格

描述用户希望如何得到交易建议。

候选值：

- `clear_conclusion`：喜欢明确结论
- `risk_first`：先看风险
- `condition_trigger`：喜欢条件触发
- `data_driven`：偏好数据逻辑
- `multi_plan`：喜欢多方案对比

影响：

- 决定回答开头是先结论、先风险、先条件还是先数据。
- 决定最终答案结构。

### 3. holdingMindset：持仓心态

描述用户持仓后容易出现的行为偏差。

候选标签，可多选：

- `chase_high_tendency`：容易追高
- `entry_patience`：有等待回调耐心
- `hard_to_stop_loss`：不愿止损
- `stop_loss_discipline`：止损纪律较好
- `profit_taking_early`：盈利拿不住
- `trend_holding_ability`：能顺势持有
- `decision_hesitation`：压力下犹豫
- `plan_based`：能按计划执行

影响：

- 容易追高时，禁止输出“现在追入”式建议。
- 不愿止损时，买入建议必须附带更明确的止损和仓位限制。
- 盈利拿不住时，优先给分批止盈方案。

### 4. tradingTempo：操作节奏

描述用户偏好的交易周期和动作频率。

候选值：

- `patient_wait`：等待确定性
- `trial_position`：小仓位试错
- `fast_short_term`：短线快进快出
- `swing_holding`：波段持有
- `long_term_holding`：中长期持有
- `market_driven_uncertain`：容易被行情带着走

影响：

- 短线型回答要给入场、止损、止盈、失效条件。
- 波段型回答要给趋势确认和加减仓条件。
- 中长期型回答少受日内波动影响，更多看估值和基本面。

### 5. explanationPreference：信息偏好

描述用户希望 AI 用什么信息密度回答。

候选值：

- `short_conclusion`：简短明确
- `conclusion_with_key_reason`：结论 + 关键理由
- `conditional_branches`：条件分支
- `full_data_logic`：完整数据逻辑

影响：

- 控制最终回答长度和结构。
- 影响是否展示多个方案。

### 6. adviceStyle：建议强度

系统推导，不允许用户直接选择。

候选值：

- `risk_first`：只给风险提示和观察条件。
- `conditional_trade`：给条件建议，不直接给强买卖。
- `explicit_trade_light_position`：可以给明确买卖 + 轻仓比例。
- `explicit_trade_with_position`：可以给明确买卖 + 仓位区间，第一版仅内部预留。

第一版产品开放：

- 开放 `risk_first`
- 开放 `conditional_trade`
- 开放 `explicit_trade_light_position`
- 暂不开放 `explicit_trade_with_position`

## 10 题问卷

问卷题目全部是场景反应题，不直接问“你是什么风险偏好”，也不直接问“你要不要明确买卖建议”。

### Q1. 买入后两天亏了 6%，但原本逻辑没明显变，你通常会？

- A. 很焦虑，想先卖掉
- B. 继续观察，不急着处理
- C. 想补仓摊低成本
- D. 看是否跌破原计划止损位

### Q2. 一个你关注过的标的连续涨了 20%，但你没买，你更容易？

- A. 等回调，不追
- B. 小仓位试一下
- C. 越看越想买，怕错过
- D. 放弃，觉得已经晚了

### Q3. 如果买入理由被破坏，但账面已经亏损，你更可能？

- A. 按计划止损
- B. 再等等，希望反弹
- C. 补仓降低成本
- D. 不知道怎么处理，会重新找理由

### Q4. 持仓盈利 12%，但趋势还没明显结束，你通常会？

- A. 先卖掉，落袋为安
- B. 卖一部分，剩下继续看
- C. 继续持有，按趋势走
- D. 反复纠结，怕利润回吐

### Q5. 你问 AI “这个能买吗”时，最希望它先给什么？

- A. 直接结论：买、不买、观望
- B. 先告诉我风险
- C. 告诉我满足什么条件可以买
- D. 先给数据和逻辑依据

### Q6. 如果 AI 给了“跌破某价格就止损”的计划，你实际执行时更接近？

- A. 基本能执行
- B. 会犹豫，但大多能执行
- C. 经常舍不得止损
- D. 到时候会再问一遍

### Q7. 你更舒服的操作方式是？

- A. 等机会明确后再出手
- B. 小仓位先试，再根据走势加减
- C. 看到机会就快速参与
- D. 更喜欢长期拿着，不频繁处理

### Q8. 面对一个波动很大的机会，你通常会？

- A. 不碰，波动太大影响心态
- B. 可以小仓位参与
- C. 越波动越觉得有机会
- D. 先看风险收益比和退出条件

### Q9. 当市场突然大跌，你更容易？

- A. 想赶紧降低仓位
- B. 观察是否破坏趋势
- C. 想找便宜机会加仓
- D. 不太知道该怎么判断

### Q10. AI 给你建议时，你更能接受哪种表达？

- A. 简短明确，别太长
- B. 结论 + 关键理由
- C. 条件分支：如果怎样就怎样
- D. 完整分析，数据、逻辑、风险都要有

## 问卷计分模型

### 标签分

每个选项不直接产出最终维度，而是给若干标签加分。最终再根据标签分聚合画像维度。

建议基础标签：

```text
loss_anxiety
volatility_averse
can_accept_volatility
high_volatility_seeking
average_down_tendency
fomo_level
chase_high_tendency
entry_patience
stop_loss_discipline
hard_to_stop_loss
decision_hesitation
profit_taking_early
trend_holding_ability
partial_position_comfort
clear_conclusion_preference
risk_first_preference
condition_trigger_preference
data_driven_preference
execution_discipline
plan_dependence
trial_position_preference
fast_trade_preference
long_term_preference
full_logic_preference
short_answer_preference
```

### 选项加分表

| 题目 | 选项 | 标签加分 |
| --- | --- | --- |
| Q1 | A | `loss_anxiety +2`, `volatility_averse +1` |
| Q1 | B | `can_accept_volatility +2`, `holding_stability +1` |
| Q1 | C | `average_down_tendency +2`, `hard_to_stop_loss +1` |
| Q1 | D | `stop_loss_discipline +2`, `plan_based +1` |
| Q2 | A | `entry_patience +2`, `chase_high_tendency -1` |
| Q2 | B | `trial_position_preference +2`, `can_accept_volatility +1` |
| Q2 | C | `fomo_level +2`, `chase_high_tendency +2` |
| Q2 | D | `volatility_averse +1`, `loss_anxiety +1` |
| Q3 | A | `stop_loss_discipline +3`, `execution_discipline +1` |
| Q3 | B | `hard_to_stop_loss +2`, `decision_hesitation +1` |
| Q3 | C | `average_down_tendency +3`, `hard_to_stop_loss +1` |
| Q3 | D | `decision_hesitation +2`, `plan_dependence +1` |
| Q4 | A | `profit_taking_early +2`, `loss_anxiety +1` |
| Q4 | B | `partial_position_comfort +2`, `plan_based +1` |
| Q4 | C | `trend_holding_ability +3`, `can_accept_volatility +1` |
| Q4 | D | `profit_taking_early +1`, `decision_hesitation +2` |
| Q5 | A | `clear_conclusion_preference +3`, `short_answer_preference +1` |
| Q5 | B | `risk_first_preference +3` |
| Q5 | C | `condition_trigger_preference +3`, `plan_based +1` |
| Q5 | D | `data_driven_preference +3`, `full_logic_preference +1` |
| Q6 | A | `execution_discipline +3`, `stop_loss_discipline +2` |
| Q6 | B | `execution_discipline +1`, `decision_hesitation +1` |
| Q6 | C | `hard_to_stop_loss +3`, `execution_discipline -1` |
| Q6 | D | `plan_dependence +2`, `decision_hesitation +1` |
| Q7 | A | `entry_patience +2`, `patient_wait_preference +2` |
| Q7 | B | `trial_position_preference +3`, `plan_based +1` |
| Q7 | C | `fast_trade_preference +3`, `chase_high_tendency +1` |
| Q7 | D | `long_term_preference +3` |
| Q8 | A | `volatility_averse +3`, `loss_anxiety +1` |
| Q8 | B | `trial_position_preference +2`, `can_accept_volatility +1` |
| Q8 | C | `high_volatility_seeking +3`, `fast_trade_preference +1` |
| Q8 | D | `risk_first_preference +2`, `plan_based +1` |
| Q9 | A | `loss_anxiety +2`, `volatility_averse +1` |
| Q9 | B | `plan_based +2`, `can_accept_volatility +1` |
| Q9 | C | `average_down_tendency +2`, `high_volatility_seeking +1` |
| Q9 | D | `decision_hesitation +2`, `plan_dependence +1` |
| Q10 | A | `short_answer_preference +3`, `clear_conclusion_preference +1` |
| Q10 | B | `clear_conclusion_preference +2`, `data_driven_preference +1` |
| Q10 | C | `condition_trigger_preference +3` |
| Q10 | D | `full_logic_preference +3`, `data_driven_preference +2` |

说明：

- 负分只用于抑制明显相反标签，第一版可只支持少量负分。
- 未出现的标签默认 0。
- 分值相同则按保守原则选择更稳健的画像值。

## 维度聚合规则

### riskEmotion

```text
if loss_anxiety >= 4:
  riskEmotion = loss_anxiety
elif volatility_averse >= 4:
  riskEmotion = volatility_averse
elif high_volatility_seeking >= 3:
  riskEmotion = high_volatility_seeking
elif average_down_tendency >= 4:
  riskEmotion = average_down_impulse
else:
  riskEmotion = can_accept_volatility
```

### decisionStyle

```text
top = max(
  clear_conclusion_preference,
  risk_first_preference,
  condition_trigger_preference,
  data_driven_preference
)

clear_conclusion_preference top -> clear_conclusion
risk_first_preference top -> risk_first
condition_trigger_preference top -> condition_trigger
data_driven_preference top -> data_driven

if clear_conclusion_preference >= 3 and condition_trigger_preference >= 3:
  decisionStyle = clear_conclusion_with_conditions
```

### holdingMindset

多标签输出：

```text
if chase_high_tendency >= 3 or fomo_level >= 3:
  add chase_high_tendency
if entry_patience >= 3:
  add entry_patience
if hard_to_stop_loss >= 3:
  add hard_to_stop_loss
if stop_loss_discipline >= 4:
  add stop_loss_discipline
if profit_taking_early >= 3:
  add profit_taking_early
if trend_holding_ability >= 3:
  add trend_holding_ability
if decision_hesitation >= 4:
  add decision_hesitation
if plan_based >= 3:
  add plan_based
```

### tradingTempo

```text
if fast_trade_preference >= 3:
  tradingTempo = fast_short_term
elif trial_position_preference >= 3:
  tradingTempo = trial_position
elif long_term_preference >= 3:
  tradingTempo = long_term_holding
elif patient_wait_preference >= 3 or entry_patience >= 3:
  tradingTempo = patient_wait
elif decision_hesitation >= 4:
  tradingTempo = market_driven_uncertain
else:
  tradingTempo = swing_holding
```

### explanationPreference

```text
if short_answer_preference >= 3:
  explanationPreference = short_conclusion
elif condition_trigger_preference >= 3:
  explanationPreference = conditional_branches
elif full_logic_preference >= 3 or data_driven_preference >= 4:
  explanationPreference = full_data_logic
else:
  explanationPreference = conclusion_with_key_reason
```

### adviceStyle

`adviceStyle` 由系统推导，规则优先级从严到宽。

```text
if loss_anxiety >= 4
   or hard_to_stop_loss >= 4
   or decision_hesitation >= 4:
  adviceStyle = risk_first

elif chase_high_tendency >= 3
     or average_down_tendency >= 4
     or execution_discipline <= 1:
  adviceStyle = conditional_trade

elif stop_loss_discipline >= 4
     and execution_discipline >= 3
     and clear_conclusion_preference >= 3
     and chase_high_tendency < 3
     and hard_to_stop_loss < 3:
  adviceStyle = explicit_trade_light_position

elif stop_loss_discipline >= 5
     and execution_discipline >= 4
     and data_driven_preference >= 3
     and chase_high_tendency < 2
     and average_down_tendency < 3:
  adviceStyle = explicit_trade_with_position

else:
  adviceStyle = conditional_trade
```

第一版如果推导出 `explicit_trade_with_position`，产品侧降级为 `explicit_trade_light_position`，但可在后端保留原始推导值。

## 仓位和建议约束

画像不直接决定标的好坏，只决定“这个建议怎么给”和“仓位是否需要限制”。

基础上限：

```text
risk_first: 不给买入仓位
conditional_trade: 只给条件，不给当前直接买入仓位；可给“若满足条件，轻仓观察”
explicit_trade_light_position: 5%-15%
explicit_trade_with_position: 10%-25%，第一版不对用户开放
```

风险打折：

```text
chase_high_tendency: 仓位上限 * 0.7
hard_to_stop_loss: 仓位上限 * 0.6
loss_anxiety: 仓位上限 * 0.6
average_down_tendency: 禁止建议补仓摊低成本，除非工具结果有明确反转证据
high_volatility_seeking: 必须附带止损线和失效条件
```

输出规则：

1. 没有行情或 Report 上下文时，不能给明确买卖和仓位。
2. 没有退出条件时，不能给买入建议。
3. 画像为 `risk_first` 时，只能输出风险、观察条件和等待确认。
4. 画像为 `conditional_trade` 时，只能输出“如果满足条件，则可以考虑”。
5. 画像为 `explicit_trade_light_position` 时，可以给明确买卖，但仓位必须是轻仓区间。
6. 如果用户有明显追高倾向，回答必须明确提醒“不要追入式买入”。
7. 如果用户不愿止损，回答必须把止损和退出条件放在建议主体里。

## 用户可见画像结果

不要向用户展示标签名和 L1/L2/L3。展示自然语言摘要。

示例：

```text
你的投资心理画像：
- 你偏好明确结论，但更适合配合条件触发来执行。
- 你有一定追高倾向，所以 AI 会避免给你追入式建议。
- 你适合小仓位试错，并设置明确止损条件。
- AI 回答会优先给：结论、触发条件、仓位上限、退出条件。
```

用户可以修正心理描述，例如“我不是容易追高”。系统可以重新计算或让用户重做相关题目。用户不能直接修改 `adviceStyle`。

## 数据模型

### ai_investor_psych_profile

保存用户当前生效心理画像。

```sql
CREATE TABLE ai_investor_psych_profile (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    profile_version BIGINT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    risk_emotion VARCHAR(64) NOT NULL,
    decision_style VARCHAR(64) NOT NULL,
    holding_mindset_json TEXT NOT NULL,
    trading_tempo VARCHAR(64) NOT NULL,
    explanation_preference VARCHAR(64) NOT NULL,
    advice_style VARCHAR(64) NOT NULL,
    raw_advice_style VARCHAR(64),
    tag_scores_json TEXT NOT NULL,
    questionnaire_answers_json TEXT NOT NULL,
    summary TEXT,
    confirmed_by_user BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_ai_investor_psych_profile_user
    ON ai_investor_psych_profile (user_id)
    WHERE status = 'active';
```

说明：

- `questionnaire_answers_json` 保存用户 10 题问卷原始答案。
- 画像维度字段保存由问卷推导出的当前生效画像。
- 不额外建立画像使用快照表。
- AI Chat 每次最终回答都读取当前最新画像；用户重做问卷后，后续回答自然跟随新画像。

## 后端 Java 设计

### 模块职责

Java 负责：

1. 保存问卷答案。
2. 计算画像标签分。
3. 聚合 6 个画像维度。
4. 生成用户可见画像摘要。
5. 提供画像查询接口给前端和 Python。
6. 在 AI Chat 最终回答阶段提供当前用户画像。
7. 保存 assistant message 使用画像时的摘要元数据。

Python 负责：

1. 消费 Java 提供的当前画像。
2. 在最终答案阶段把画像作为回答约束。
3. 根据画像调整表达和建议强度。
4. 不负责持久化画像主数据。

### Java 类建议

```text
finance-data
- AiInvestorPsychProfilePO
- AiInvestorPsychProfileMapper
- AiInvestorPsychProfileManage

finance-ai
- InvestorPsychProfileController
- InvestorPsychProfileService
- InvestorPsychProfileScoringHandler
- InvestorPsychProfileQuestionnaireVO
- InvestorPsychProfileSubmitParam
- InvestorPsychProfileVO
```

### API 设计

#### 获取问卷

```http
GET /api/ai/investor-psych-profile/questionnaire
```

返回：

```json
{
  "questions": [
    {
      "code": "Q1",
      "title": "买入后两天亏了 6%，但原本逻辑没明显变，你通常会？",
      "options": [
        {"code": "A", "label": "很焦虑，想先卖掉"},
        {"code": "B", "label": "继续观察，不急着处理"}
      ]
    }
  ]
}
```

#### 提交问卷

```http
POST /api/ai/investor-psych-profile
```

请求：

```json
{
  "answers": [
    {"questionCode": "Q1", "optionCode": "D"},
    {"questionCode": "Q2", "optionCode": "A"}
  ]
}
```

返回：

```json
{
  "profileVersion": 1,
  "riskEmotion": "can_accept_volatility",
  "decisionStyle": "clear_conclusion_with_conditions",
  "holdingMindset": ["entry_patience", "stop_loss_discipline"],
  "tradingTempo": "trial_position",
  "explanationPreference": "conclusion_with_key_reason",
  "adviceStyle": "explicit_trade_light_position",
  "summary": "你偏好明确结论，也能按条件执行，适合小仓位试错并设置止损。"
}
```

#### 查询当前画像

```http
GET /api/ai/investor-psych-profile
```

如果未填写，返回 `profileCompleted=false`。

#### 重做问卷

```http
PUT /api/ai/investor-psych-profile
```

语义和提交问卷一致，`profile_version + 1`。

### Python 内部数据网关

现有 Agent 通过 Java 数据网关读取对话历史、工具数据。增加内部 action：

```text
investor.psych_profile
```

返回当前用户画像：

```json
{
  "profileExists": true,
  "profileVersion": 3,
  "riskEmotion": "loss_anxiety",
  "decisionStyle": "clear_conclusion_with_conditions",
  "holdingMindset": ["chase_high_tendency"],
  "tradingTempo": "trial_position",
  "explanationPreference": "conclusion_with_key_reason",
  "adviceStyle": "conditional_trade",
  "summary": "你偏好明确结论，但更适合条件触发型建议。"
}
```

如果用户未填写画像：

```json
{
  "profileExists": false
}
```

## Python Agent 接入设计

### 接入位置

只在最终回答阶段接入画像，不在 planner 阶段接入。

推荐流程：

```text
用户提问
  -> planner 判断工具
  -> 工具查询行情、知识库、Report
  -> scratchpad 汇总工具结果
  -> 读取当前心理画像
  -> answer_from_scratchpad 生成最终回答
  -> answer_delta 流式输出
  -> final_answer 保存完整回答和 Token 用量
```

不建议：

```text
planner 阶段注入心理画像
```

原因：

- planner 应该决定查什么数据，不应该因为用户心理偏好改变事实查询。
- 避免 planner 阶段输出用户可见内容。
- 保持流式输出只发生在最终答案阶段。

### Prompt 约束

最终答案 prompt 增加一段“心理画像约束”：

```text
用户投资心理画像：
- 波动情绪：亏损焦虑
- 决策风格：喜欢明确结论 + 条件触发
- 持仓心态：容易追高
- 操作节奏：小仓位试错
- 信息偏好：结论 + 关键理由
- 建议强度：conditional_trade

回答规则：
1. 不要因为用户偏好而改变行情事实。
2. 只在工具结果支持时给买卖建议。
3. 当前建议强度为 conditional_trade，只能给条件建议，不能直接说“现在买入 20%”。
4. 用户有追高倾向，必须提醒不要追入式买入。
5. 买入相关建议必须包含触发条件和退出条件。
```

### 无画像降级

如果用户未填写心理画像：

- AI Chat 可以正常回答。
- 只给通用分析和风险提示。
- 不给个性化仓位建议。
- 可在回答末尾轻提示：“如果你完善投资心理画像，我可以把建议调整得更贴合你的交易习惯。”

### 对话保存

不为画像新增 assistant message metadata。问卷答案和画像保存在 `ai_investor_psych_profile`，AI Chat 每次最终回答实时读取当前画像。

Java `final_answer` 继续按原有逻辑保存 assistant message 完整内容。`answer_delta` 仍只负责 WebSocket 推送，不入库、不记 Token。

## 前端设计

### 入口

第一版建议放两个入口：

1. AI Chat 空状态或设置入口：“完善投资心理画像”
2. AI Chat 用户要求个性化买卖建议但未填写画像时，弹出引导

### 问卷形态

10 题可以分 2 页，每页 5 题：

- 第 1 页：亏损、追高、止损、盈利处理、建议偏好
- 第 2 页：执行力、操作节奏、高波动机会、市场大跌、回答偏好

每题单选。提交前必须答完。

### 结果页

展示自然语言摘要，不展示内部标签：

```text
你的投资心理画像

你偏好明确结论，但更适合配合条件触发来执行。
你有一定追高倾向，所以 AI 会避免给你追入式建议。
你适合小仓位试错，并设置明确止损条件。

AI 回答会优先给：
- 结论
- 触发条件
- 仓位上限
- 退出条件
```

操作：

- 保存画像
- 重新填写
- 进入 AI Chat

### Chat 提示

当 Chat 使用画像时，可以在回答顶部或底部轻量显示：

```text
已按你的投资心理画像调整建议：条件触发型、小仓位、重视退出条件。
```

不要在每次回答都大段解释画像，避免噪音。

## 与现有能力的关系

### 对话保存

不改变现有 user/assistant message 保存逻辑。

- 用户消息照常保存。
- `answer_delta` 仍不入库。
- `final_answer` 保存完整 assistant 内容。
- 不在 assistant message 中保存画像快照或画像摘要。

### 流式输出

不改变流式边界。

- planner 不流式。
- 工具调用不流式。
- 最终答案阶段流式。
- 心理画像只注入最终答案阶段。

### Token 统计

新增画像 prompt 会增加最终答案阶段输入 Token。需要在 token usage 阶段归因到：

- `final_answer`

不新增独立 token 阶段，避免统计变复杂。

### Report

默认 Report 不读取画像。

如未来需要个性化报告，应新增 `personalized_report` 模式，而不是改变默认 report。

## 实现计划建议

### 阶段一：画像问卷和画像保存

1. 新增数据库迁移：
   - `ai_investor_psych_profile`
2. 新增 Java PO、Mapper、Manage。
3. 实现问卷配置常量。
4. 实现 `InvestorPsychProfileScoringHandler`。
5. 实现问卷查询、提交、查询当前画像 API。
6. 前端实现问卷页和结果页。

验证：

- 10 题必须全部提交。
- 同一用户重复提交后 `profile_version + 1`。
- 标签分和画像维度符合规则。
- 用户不能直接提交 `adviceStyle`。

### 阶段二：AI Chat 接入画像

1. Java 内部 Agent 数据网关增加当前画像 action。
2. Python AgentRunHandler 或 data gateway client 读取当前画像。
3. `answer_from_scratchpad` 最终答案 prompt 注入画像约束。
4. Java final_answer 继续保存完整回答和 Token 用量。
5. 前端 Chat 展示轻量画像使用提示。

验证：

- 未填写画像时 Chat 正常回答。
- 已填写画像时最终答案包含符合画像的建议结构。
- planner 阶段不读取画像、不流式输出画像相关内容。
- `answer_delta` 不入库。
- `final_answer` 正常保存完整回答，不额外保存画像摘要。

### 阶段三：建议强度和仓位规则加强

1. 在 Python 最终回答前构建 `AdviceConstraint`。
2. 将 `adviceStyle` 映射为允许的动作和仓位范围。
3. 对高风险心理标签进行仓位打折。
4. Prompt 中强约束不允许越界。
5. 可选：最终回答后做一次轻量规则检查，发现越界则重写或降级。

验证：

- `risk_first` 不出现明确买入和仓位。
- `conditional_trade` 不出现“现在买入 20%”。
- `explicit_trade_light_position` 仓位不超过轻仓上限。
- 有追高倾向时必须出现追高风险提醒。
- 不愿止损时必须出现退出条件。

## 测试建议

### Java 单元测试

- 问卷答案完整性校验。
- 标签计分正确。
- 维度聚合正确。
- `adviceStyle` 不能由前端提交覆盖。
- 重做问卷版本递增。

### Python 单元测试

- 无画像时最终答案 prompt 不注入画像约束。
- 有画像时只在最终答案阶段注入。
- `risk_first` 画像不会生成明确买入仓位。
- `conditional_trade` 输出为条件建议。
- 流式输出仍只发生在最终答案阶段。

### 集成测试

- 用户提交问卷 -> `ai_investor_psych_profile.questionnaire_answers_json` 保存原始答案 -> Chat 读取最新画像。
- 未提交问卷 -> Chat 正常回答且不读取画像。
- Report 生成不读取画像。

## 风险和取舍

### 风险一：模型仍然可能越过建议强度

缓解：

- Prompt 约束。
- 仓位规则前置。
- 后续增加回答后校验。

### 风险二：10 题可能无法准确刻画复杂心理

缓解：

- 第一版允许重新填写。
- 后续通过对话行为生成 `profile_signal`，但只提示用户确认，不自动覆盖画像。

### 风险三：用户不认可系统推导的建议强度

缓解：

- 展示自然语言原因。
- 允许用户修正心理描述或重做问卷。
- 不允许直接修改 adviceStyle。

### 风险四：画像导致回答过度个人化

缓解：

- 明确规则：画像不改变事实判断，只影响建议表达和执行边界。
- Report 不接画像，保持客观底稿。

## 待确认点

1. 第一版是否只开放到 `explicit_trade_light_position`，暂不开放更高仓位建议。
2. 问卷入口放在 AI Chat 内，还是用户设置页也提供入口。
3. Chat 回答中是否需要展示“已使用心理画像”的轻量提示。
4. 是否需要允许用户删除画像，删除后 Chat 回到通用建议模式。
