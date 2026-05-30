# 投资分析报告知识库检索设计文档

## 1. 设计目标

本项目中的投资分析报告不应只是简单地把行情数据、知识库片段和用户问题直接交给 LLM 生成答案。

报告生成的核心目标是：

```text
先由系统分析当前标的的市场场景，
再根据场景决定应该检索哪些知识，
最后由 LLM 基于事实数据、场景信号和知识库依据组织成结构化报告。
```

因此，本系统的检索重点不是单纯寻找“语义最相似”的文本，而是寻找：

```text
对当前市场场景最有参考价值的经验内容。
```

最终检索逻辑应结合：

```text
1. 内容相似度 semantic_score
2. 场景相似度 scene_score
3. 召回后的综合重排序 rerank
```

第一版暂不单独引入质量权重，因为未经过人工复核的 OCR 数据不会进入知识库。

---

## 2. 整体报告生成流程

推荐报告生成流程如下：

```text
用户选择标的
  ↓
后端查询行情数据
  ↓
后端计算指标和信号
  ↓
生成当前标的 currentScenes
  ↓
根据 reportType + currentScenes 生成检索计划
  ↓
多路召回知识库 chunk
  ↓
计算 semantic_score 和 scene_score
  ↓
综合 rerank
  ↓
取 TopK 知识片段作为 knowledgeContext
  ↓
LLM 基于 marketContext + signalContext + knowledgeContext 生成结构化报告
  ↓
保存报告、上下文和引用依据
```

关键思想：

```text
不要做“LLM 直接生成报告”；
而是做“系统生成分析上下文，LLM 负责解释和组织报告”。
```

---

## 3. 检索评分设计

第一版推荐使用两个核心分数：

```text
semantic_score：内容相似度
scene_score：场景相似度
```

综合分：

```text
final_score = semantic_score * 0.4 + scene_score * 0.6
```

如果后续发现场景标签质量不够稳定，可以调整为：

```text
final_score = semantic_score * 0.5 + scene_score * 0.5
```

如果场景标签体系成熟，可以调整为：

```text
final_score = semantic_score * 0.35 + scene_score * 0.65
```

---

## 4. 内容相似度 semantic_score

内容相似度使用 embedding cosine similarity 或 pgvector 向量距离计算。

它回答的问题是：

```text
当前 query 和 chunk 文本内容在语义上像不像？
```

例如：

```text
query:
放量上涨 高换手 追高风险

chunk:
低价股突然放量时，不要只看涨幅，要观察成交量持续性和换手率。
```

这类内容会有较高 semantic_score。

但是 semantic_score 不能解决以下问题：

```text
当前标的是股票、指数还是可转债？
当前是低位反弹还是高位放量？
当前是趋势突破还是情绪异动？
当前更需要风险控制经验还是估值判断经验？
```

因此 semantic_score 只作为基础分，不应单独决定检索结果。

---

## 5. 场景相似度 scene_score

场景相似度是本系统检索的核心。

每个知识库 chunk 入库时，需要被分析出 scenes 标签；生成某个标的报告时，系统也会根据行情数据分析出 currentScenes。

然后比较：

```text
当前标的 currentScenes
和
知识库 chunk scenes
```

匹配程度越高，说明该 chunk 越适合当前报告。

---

## 6. Scene 分类体系

第一版建议将 scene 分成 7 大类：

```text
1. asset_scene：资产类型场景
2. price_scene：价格位置场景
3. volume_scene：成交量 / 换手场景
4. trend_scene：趋势结构场景
5. valuation_scene：估值 / 基本面场景
6. sentiment_scene：情绪 / 异动场景
7. risk_strategy_scene：风险 / 策略场景
```

这 7 类基本可以覆盖股票、指数、可转债的第一版分析报告。

---

## 7. asset_scene：资产类型场景

### 作用

asset_scene 用来判断知识片段是否适合当前标的类型。

它主要解决：

```text
这段知识是不是适用于当前资产？
```

例如：

```text
分析股票时，不应大量召回可转债专用知识。
分析可转债时，不应只召回普通股票技术分析知识。
```

### 推荐标签

```text
stock                  股票
index                  指数
bond                   可转债
bank_stock             银行股
low_price_stock        低价股
large_cap_stock        大盘股
small_cap_stock        小盘股
convertible_bond       可转债
```

### 示例

分析青农商行时：

```json
{
  "asset_scene": ["stock", "bank_stock", "low_price_stock"]
}
```

### 设计理解

asset_scene 更多是过滤和基础匹配，不是最终观点来源。

它适合低权重参与 scene_score。

---

## 8. price_scene：价格位置场景

### 作用

price_scene 描述当前价格状态。

它回答：

```text
当前价格是在涨、跌、横盘、高位、低位、突破还是回调？
```

### 推荐标签

```text
price_rise              明显上涨
price_drop              明显下跌
sideways                横盘
near_recent_high        接近近期高位
near_recent_low         接近近期低位
breakout                突破
pullback                回调
gap_up                  跳空高开
gap_down                跳空低开
limit_up                涨停
limit_down              跌停
```

### 示例

```json
{
  "price_scene": ["price_rise", "near_recent_high"]
}
```

### 可检索知识方向

```text
上涨后是否追高
接近高位如何处理
突破后的确认条件
回调是否健康
低位反弹是否可靠
```

### 设计理解

price_scene 很重要，但不能单独判断。价格必须结合成交量、趋势和风险策略一起分析。

---

## 9. volume_scene：成交量 / 换手场景

### 作用

volume_scene 描述资金活跃度和交易强度。

它回答：

```text
当前成交量是否异常？
换手率是否过高？
量价是否配合？
```

### 推荐标签

```text
volume_expand            放量
volume_shrink            缩量
high_turnover            高换手
low_turnover             低换手
volume_price_confirm     量价配合
volume_price_divergence  量价背离
volume_spike             成交量突然放大
volume_dry_up            成交枯竭
```

### 示例

```json
{
  "volume_scene": ["volume_expand", "high_turnover"]
}
```

### 可检索知识方向

```text
放量上涨怎么看
高换手是否有风险
量价配合是否说明趋势有效
放量后第二天如何观察
放量滞涨是否危险
```

### 设计理解

volume_scene 在短线异动、观察池、风险检查中非常重要。

对于股票和可转债报告，volume_scene 应该是高权重类别之一。

---

## 10. trend_scene：趋势结构场景

### 作用

trend_scene 描述最近一段时间的走势结构。

它和 price_scene 不同：

```text
price_scene 看当前价格位置；
trend_scene 看一段时间内的结构。
```

例如，今天上涨 3% 是 price_rise；但最近 20 天可能是横盘突破、下跌反弹或趋势加速，这属于 trend_scene。

### 推荐标签

```text
uptrend                  上升趋势
downtrend                下降趋势
range_bound              区间震荡
trend_reversal           趋势反转
rebound                  反弹
breakout_from_range      横盘突破
failed_breakout          突破失败
higher_high              创阶段新高
lower_low                创阶段新低
```

### 示例

```json
{
  "trend_scene": ["range_bound", "breakout_from_range"]
}
```

### 可检索知识方向

```text
横盘突破后的确认
突破失败风险
趋势反转是否成立
下跌反弹和真正反转的区别
区间震荡中的观察纪律
```

### 设计理解

trend_scene 可以让报告不只看当天行情，而是具备结构分析能力。

对于复盘报告和观察池报告，trend_scene 权重可以更高。

---

## 11. valuation_scene：估值 / 基本面场景

### 作用

valuation_scene 用于描述估值和基础财务特征。

它适合股票、银行股、低价股、指数类分析，也适合中长期观察。

### 推荐标签

```text
low_pe                   低 PE
high_pe                  高 PE
low_pb                   低 PB
high_pb                  高 PB
valuation_repair         估值修复
valuation_trap           低估值陷阱
profit_pressure          盈利压力
high_dividend            高股息
fundamental_uncertain    基本面不确定
```

### 示例

分析银行股时：

```json
{
  "valuation_scene": ["low_pb", "low_pe"]
}
```

### 可检索知识方向

```text
低 PB 银行股怎么看
低估值是否一定安全
低估值修复需要什么条件
低估值陷阱如何判断
高股息策略的风险
```

### 设计理解

valuation_scene 不适合单独决定短线判断，但适合为报告提供估值背景。

如果当前系统财务数据不完整，第一版可以只使用 PE、PB、股息率等简单字段。

---

## 12. sentiment_scene：情绪 / 异动场景

### 作用

sentiment_scene 描述市场关注度、情绪热度和消息驱动特征。

它不完全来自财务数据，可以来自：

```text
涨跌幅
成交量变化
换手率
涨停/大跌
新闻/公告
板块联动
观察池热度
```

### 推荐标签

```text
market_attention_rise    关注度上升
short_term_emotion       短线情绪升温
panic_selling            恐慌抛售
speculation_heat         投机热度
weak_sentiment           情绪偏弱
news_driven              消息驱动
policy_driven            政策驱动
sector_rotation          板块轮动
```

### 示例

```json
{
  "sentiment_scene": ["short_term_emotion", "market_attention_rise"]
}
```

### 可检索知识方向

```text
情绪上来时如何避免追高
消息驱动行情是否持续
短线热度和真实趋势的区别
大跌时如何区分恐慌和趋势破坏
板块轮动时个股是否有持续性
```

### 设计理解

sentiment_scene 是比较软的分类，但对个人投资系统非常重要。

很多错误并不是因为数据没看到，而是情绪影响了判断。

第一版可以用规则粗略生成：

```text
涨幅较大 + 放量 + 高换手 = short_term_emotion
涨停 / 大涨 = market_attention_rise
大跌 + 放量 = panic_selling
```

后续可以接新闻和公告增强判断。

---

## 13. risk_strategy_scene：风险 / 策略场景

### 作用

risk_strategy_scene 是个人知识库最有价值的一类。

前面几类更多描述“市场状态”，而 risk_strategy_scene 描述：

```text
当前应该注意什么风险？
当前应该采取什么观察或处理策略？
```

### 推荐标签：风险类

```text
chase_high_risk          追高风险
false_breakout_risk      假突破风险
liquidity_risk           流动性风险
drawdown_risk            回撤风险
valuation_trap_risk      估值陷阱风险
overheated_risk          过热风险
```

### 推荐标签：策略类

```text
risk_control             风险控制
position_control         仓位控制
wait_confirm             等待确认
observe_next_day         观察次日表现
avoid_emotional_trade    避免情绪交易
take_profit_plan         止盈计划
stop_loss_plan           止损计划
```

### 示例

当前标的是：

```text
放量上涨 + 高换手 + 接近近期高位
```

可以生成：

```json
{
  "risk_strategy_scene": [
    "chase_high_risk",
    "wait_confirm",
    "observe_next_day"
  ]
}
```

### 可检索知识方向

```text
追高风险如何处理
高位放量后怎么观察
突破后是否需要等待确认
如何设置观察条件
如何避免情绪交易
什么时候需要控制仓位
```

### 设计理解

risk_strategy_scene 适合报告的风险提示、后续观察点和非买卖建议结论。

如果报告目标是帮助用户少犯错，这一类应该是高权重。

---

## 14. Scene Metadata 存储结构

建议在 `knowledge_vector.metadata` 中存储 scenes。

示例：

```json
{
  "sourceType": "ocr_note",
  "reviewed": true,
  "scenes": {
    "asset": ["stock", "bank_stock"],
    "price": ["price_rise"],
    "volume": ["volume_expand", "high_turnover"],
    "trend": ["breakout_from_range"],
    "valuation": ["low_pb"],
    "sentiment": ["short_term_emotion"],
    "risk_strategy": ["chase_high_risk", "wait_confirm"]
  },
  "keywords": ["放量", "换手率", "追高", "风险控制"]
}
```

生成报告时，系统也生成同样结构的 `currentScenes`：

```json
{
  "asset": ["stock", "bank_stock", "low_price_stock"],
  "price": ["price_rise", "near_recent_high"],
  "volume": ["volume_expand", "high_turnover"],
  "trend": ["breakout_from_range"],
  "valuation": ["low_pb"],
  "sentiment": ["short_term_emotion"],
  "risk_strategy": ["chase_high_risk", "wait_confirm"]
}
```

---

## 15. scene_score 计算方式

每个大类分别计算匹配分，然后按报告类型加权。

### 单类匹配分

简单版本：

```text
category_score = 当前类别 scenes 与 chunk 类别 scenes 的交集数量 / 当前类别 scenes 数量
```

例如：

```text
current volume_scene:
volume_expand, high_turnover

chunk volume_scene:
volume_expand, volume_price_confirm

交集：
volume_expand

volume_score = 1 / 2 = 0.5
```

### 加权版本

如果不同 scene 重要性不同，可以给 scene 设置权重。

例如：

```json
{
  "volume_expand": 1.0,
  "high_turnover": 0.8,
  "volume_price_divergence": 1.2
}
```

则：

```text
category_score =
匹配到的 scene 权重之和 / 当前类别 scene 权重之和
```

第一版可以先用简单版本。

---

## 16. 普通快速报告权重

普通 quick_analysis 报告推荐权重：

```json
{
  "asset_scene": 0.10,
  "price_scene": 0.15,
  "volume_scene": 0.20,
  "trend_scene": 0.15,
  "valuation_scene": 0.10,
  "sentiment_scene": 0.10,
  "risk_strategy_scene": 0.20
}
```

解释：

```text
成交量 / 换手：20%
风险 / 策略：20%
价格位置：15%
趋势结构：15%
资产类型：10%
估值基本面：10%
情绪异动：10%
```

适合大多数股票和指数快速分析。

---

## 17. 风险检查报告权重

risk_check 报告推荐权重：

```json
{
  "asset_scene": 0.10,
  "price_scene": 0.10,
  "volume_scene": 0.20,
  "trend_scene": 0.10,
  "valuation_scene": 0.10,
  "sentiment_scene": 0.15,
  "risk_strategy_scene": 0.25
}
```

解释：

```text
风险 / 策略权重最高；
成交量 / 换手和情绪异动也较高；
价格、趋势、估值作为辅助。
```

适合判断：

```text
是否追高
是否情绪过热
是否放量滞涨
是否需要等待确认
是否应该控制仓位
```

---

## 18. 观察池复盘报告权重

watch_review 报告推荐权重：

```json
{
  "asset_scene": 0.10,
  "price_scene": 0.15,
  "volume_scene": 0.15,
  "trend_scene": 0.20,
  "valuation_scene": 0.10,
  "sentiment_scene": 0.10,
  "risk_strategy_scene": 0.20
}
```

解释：

```text
复盘类报告更关注趋势结构和风险策略；
需要判断加入观察池后是否符合原来的观察逻辑。
```

适合分析：

```text
加入观察池后是否继续走强
是否触发过预警
是否符合最初观察理由
是否需要继续观察或移出观察池
```

---

## 19. 可转债报告扩展

可转债建议后续单独增加 `bond_scene`。

推荐标签：

```text
high_premium             高溢价率
low_premium              低溢价率
high_bond_price          转债价格较高
low_bond_price           转债价格较低
redemption_risk          强赎风险
stock_bond_linkage       正股联动
rating_risk              评级风险
small_remaining_size     剩余规模较小
```

可转债报告权重示例：

```json
{
  "asset_scene": 0.10,
  "bond_scene": 0.25,
  "price_scene": 0.10,
  "volume_scene": 0.15,
  "sentiment_scene": 0.10,
  "risk_strategy_scene": 0.30
}
```

解释：

```text
可转债报告中，风险 / 策略和 bond_scene 权重最高。
因为可转债不仅要看价格和成交量，还要看溢价率、强赎、正股联动等专有因素。
```

---

## 20. 多路召回设计

报告生成时不要只执行一次 embedding 检索。

推荐使用多路召回：

```text
1. 内容相似召回
2. 风险召回
3. 策略召回
4. 资产类型召回
```

示例：

当前标的：

```json
{
  "asset": ["stock", "bank_stock"],
  "price": ["price_rise", "near_recent_high"],
  "volume": ["volume_expand", "high_turnover"],
  "risk_strategy": ["chase_high_risk", "wait_confirm"]
}
```

可以生成以下 query：

```text
内容相似召回：
银行股 放量上涨 高换手 低估值

风险召回：
放量上涨 接近高位 追高风险 高换手

策略召回：
放量后 如何观察 是否等待确认 风险控制

资产类型召回：
低价银行股 低 PB 估值修复 风险
```

每路召回 topK，例如 top 10，合并去重后进入 rerank。

---

## 21. Rerank 设计

召回后对所有候选 chunk 计算：

```text
semantic_score
scene_score
final_score
```

推荐第一版公式：

```text
final_score = semantic_score * 0.4 + scene_score * 0.6
```

然后按 final_score 排序。

最终取：

```text
Top 5 ～ Top 8
```

进入报告的 knowledgeContext。

---

## 22. 报告中知识引用的组织方式

不要把所有知识片段混在一起。

推荐按引用目的分类：

```json
{
  "knowledgeContext": {
    "supportingEvidence": [],
    "riskWarnings": [],
    "watchPoints": [],
    "strategyReferences": []
  }
}
```

对应报告展示：

```text
1. 知识库支持点
2. 知识库风险提醒
3. 后续观察点
4. 策略参考
```

这样可以避免报告只引用支持当前观点的内容，而忽略反向风险。

---

## 23. 第一版落地建议

第一版不要追求复杂，可以按以下步骤实现：

```text
1. 给 knowledge_vector.metadata 增加 scenes 字段
2. OCR 人工复核入库时，为每个 chunk 生成 scenes
3. 报告生成前，根据行情数据生成 currentScenes
4. 根据 reportType 设置 scene 类别权重
5. 用多路 query 召回候选 chunk
6. 对候选 chunk 计算 semantic_score 和 scene_score
7. 用 final_score rerank
8. 将 TopK chunk 写入 report.knowledgeContext
9. LLM 基于 marketContext + signalContext + knowledgeContext 生成结构化报告
```

---

## 24. 推荐类设计

可以增加以下服务类：

```text
AnalysisSignalService
负责从行情、K 线、分时数据中计算基础信号。

AnalysisSceneService
负责把基础信号转换成 currentScenes。

KnowledgeRetrievalPlanService
负责根据 reportType 和 currentScenes 生成多路检索计划。

KnowledgeHybridRetrievalService
负责执行 embedding 召回、场景相似度计算和 rerank。

AnalysisReportService
负责组装 marketContext、signalContext、knowledgeContext，并调用 LLM 生成报告。
```

---

## 25. 最终设计总结

本系统的报告生成不应是普通 RAG，而应是：

```text
Hybrid Retrieval + Scenario Reranking
```

核心逻辑：

```text
内容相似度：文本语义像不像？
场景相似度：这段经验适不适合当前行情场景？
重排序：综合内容和场景，选出真正有参考价值的知识。
```

最终目标：

```text
让报告不是简单引用几段相似文本，
而是基于当前标的的市场状态，
主动寻找对应的经验、风险和观察策略。
```
