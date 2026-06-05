# 投资分析报告动态检索系统设计文档

## 1. 设计目标与核心思路

### 1.1 设计背景

本项目中的投资分析报告不应只是简单地把行情数据、知识库内容和用户问题交给 LLM 生成答案。

普通 RAG 通常是：

```text
用户问题
  ↓
Embedding 检索 TopK 知识库内容
  ↓
行情数据 + 检索结果 + LLM
  ↓
生成报告
```

这种方式适合普通文档问答，但不适合个人投资研究场景。

投资分析报告真正需要解决的问题不是：

```text
哪段文本和用户问题最相似？
```

而是：

```text
当前标的处于什么市场场景？
这个场景下应该参考哪些经验？
哪些知识应该重点召回？
哪些知识只是辅助背景？
```

因此，本系统需要一套基于场景标签、模块得分和动态召回比例的检索机制。

---

### 1.2 普通 RAG 的问题

普通 RAG 在本项目中主要存在以下问题：

```text
1. 只依赖内容相似度，无法理解当前标的的市场状态。
2. 不知道报告当前应该重点关注成交量、价格、趋势、风险、估值还是情绪。
3. 不同类型的 chunk 在报告中的召回比例固定，无法根据行情状态动态变化。
4. 容易检索到语义相似但场景不匹配的内容。
5. LLM 承担了过多判断，系统自身缺少可解释的分析过程。
```

例如用户选择某只股票生成报告时，系统不应该直接搜索：

```text
青农商行 怎么看
```

而应先分析该股票当前是否存在：

```text
放量上涨
高换手
接近近期高位
低 PB
短线情绪升温
追高风险
```

然后再决定应该重点检索哪些知识。

---

### 1.3 本系统的核心思路

本系统的核心思路是：

```text
知识入库时，给 chunk 打 7 大类场景标签；
报告生成时，给当前标的也打 7 大类场景标签和得分；
根据 7 大类得分动态计算每类 chunk 的召回比例；
按类别分别检索、分别重排、分别取 TopN；
最后把分组后的 knowledgeContext 交给 LLM 生成报告。
```

也就是说：

```text
先分类配额，
再类内检索，
再类内重排，
最后按类别组织知识上下文。
```

这比一次性全库检索 TopK 更稳定，也更符合投资分析报告的结构。

---

### 1.4 核心流程概览

整体流程如下：

```text
Chunk 入库阶段：
人工复核文本
  ↓
切分 chunk
  ↓
给 chunk 打 7 大类标签
  ↓
生成 embedding
  ↓
写入 knowledge_vector

报告生成阶段：
用户选择标的
  ↓
查询行情 / K 线 / 分时 / 估值数据
  ↓
根据数据生成当前标的 7 大类标签和得分
  ↓
用指数函数计算每类 chunk 召回比例
  ↓
按类别分别检索对应 chunk
  ↓
每类内部重排序
  ↓
构建 knowledgeContext
  ↓
LLM 生成结构化报告
```

---

### 1.5 当前落地状态

当前报告流程已经打通，落地实现和本文设计的对应关系如下：

```text
前端报告页面
  ↓
POST /api/ai/scene-analysis/tasks
  ↓
Java 创建 scene_analysis_task，组装行情上下文并发布 scene.analysis.current 消息
  ↓
Python 计算 currentScenes，回调 POST /api/ai/scene-analysis/tasks/{taskNo}/callback
  ↓
Java 计算 chunkAllocation，生成 retrievalTasks，发布 scene.analysis.retrieval.embedding 消息
  ↓
Python 为每个 retrievalTask 生成 queryEmbedding，再次回调 Java
  ↓
Java 使用 pgvector 做语义候选召回，结合 Jaccard 标签匹配和 cross_scene_score 类内重排
  ↓
Java 构建 knowledgeContext，异步调用 DeepSeek 生成结构化 JSON 报告
  ↓
写入 scene_analysis_report，前端轮询 /report 并支持历史列表、详情和重新生成
```

已落地能力：

```text
1. 支持 STOCK、INDEX、CONVERTIBLE_BOND 三类标的。
2. 支持 quick_analysis、risk_check、valuation_report 三类报告类型。
3. 支持配置档 scene_analysis_config_profile，前端可查询参数 schema、报告类型和自定义配置。
4. 支持报告目标分页、单标的历史版本、报告详情和基于已存上下文重新生成。
5. 报告生成时校验知识库 chunkId 引用，避免 LLM 编造不存在的引用。
6. Token 用量会从 DeepSeek 原始响应中提取并记录。
```

当前落地表：

```text
scene_analysis_task
  保存任务状态、标的、报告类型、配置快照、currentScenesPayload、reportPayload。

scene_analysis_report
  保存报告历史版本、生成类型、结构化 JSON、渲染文本、模型、错误信息和生成时间。

scene_analysis_config_profile
  保存系统默认或用户自定义的报告参数配置。
```

---

## 2. 整体流程设计

### 2.1 Chunk 入库流程

Chunk 入库流程负责把人工复核后的知识文本变成可检索的结构化知识。

推荐流程：

```text
OCR 识别
  ↓
文本清洗
  ↓
人工复核
  ↓
确认提交 reviewed.json
  ↓
按最终 paragraphs 生成 chunk
  ↓
为每个 chunk 生成 7 大类场景标签
  ↓
生成 keywords / summary
  ↓
生成 embedding
  ↓
写入 knowledge_vector
```

入库后的 chunk 不仅有文本和向量，还应有结构化 metadata。

---

### 2.2 报告生成流程

报告生成流程负责根据当前标的数据生成分析报告。

推荐流程：

```text
用户选择标的
  ↓
查询行情数据
  ↓
查询 K 线 / 分时 / 估值等上下文
  ↓
7 个场景模块分别打标签和打分
  ↓
生成 currentScenes
  ↓
计算每类 chunk 召回数量
  ↓
按类别检索知识库
  ↓
构建 knowledgeContext
  ↓
LLM 生成结构化报告
  ↓
保存报告和引用依据
```

---

### 2.3 检索召回流程

检索召回流程是本设计的核心。

推荐流程：

```text
currentScenes
  ↓
获取 7 大类总分
  ↓
指数函数计算 chunk 分配比例
  ↓
每一类生成独立检索任务
  ↓
每一类用 Jaccard 标签相似度过滤候选 chunk
  ↓
每一类生成 queryText
  ↓
每一类计算 semantic_score
  ↓
每一类内部 rerank
  ↓
每一类取 TopN
  ↓
按类别构建 knowledgeContext
```

---

### 2.4 三个流程之间的关系

三者关系如下：

```text
Chunk 入库流程
负责让知识库中的 chunk 带有 7 大类标签。

报告生成流程
负责让当前标的也带有 7 大类标签和得分。

检索召回流程
负责比较当前标的标签与 chunk 标签，
并根据得分动态决定各类 chunk 召回比例。
```

核心是：

```text
chunk 有 scenes；
当前标的也有 currentScenes；
检索就是比较这两者。
```

---

## 3. Chunk 入库设计

### 3.1 入库时机

Chunk 入库发生在 OCR 人工复核确认之后。

未经过人工复核的 OCR 内容不进入知识库。

因此第一版不需要单独引入质量权重，因为进入数据库的文本默认已经人工确认。

---

### 3.2 Chunk 生成规则

推荐规则：

```text
1. 一个最终 paragraph 对应一个 chunk。
2. chunk 文本使用人工复核后的最终文本。
3. chunk_index 从 1 开始递增。
4. 同一个 taskNo 重新入库时，可以先删除旧 chunk，再写入新 chunk。
5. chunk 需要保存 taskNo、chunkIndex、text、embedding、metadata。
```

---

### 3.3 Chunk 的 7 大类标签结构

每个 chunk 的 metadata 中应包含 7 大类 scenes：

```json
{
  "scenes": {
    "asset": [],
    "price": [],
    "volume": [],
    "trend": [],
    "valuation": [],
    "sentiment": [],
    "risk_strategy": []
  }
}
```

7 大类分别是：

```text
asset：资产类型
price：价格位置
volume：成交量 / 换手
trend：趋势结构
valuation：估值 / 基本面
sentiment：情绪 / 异动
risk_strategy：风险 / 策略
```

---

### 3.4 小标签生成规则

每个大类下面可以有多个小标签。

例如一段文本：

```text
放量上涨后不能马上判断趋势成立，要看第二天是否继续放量并站稳关键位置。
```

可以生成：

```json
{
  "scenes": {
    "price": ["price_rise"],
    "volume": ["volume_expand"],
    "trend": ["breakout_from_range"],
    "risk_strategy": ["wait_confirm", "observe_next_day"]
  }
}
```

这些小标签用于后续检索时的标签过滤和 tag_match_score 计算。

---

### 3.5 Chunk Embedding 生成

Embedding 只基于最终 chunk text 生成。

推荐流程：

```text
chunk.text
  ↓
embedding model
  ↓
embedding vector
  ↓
knowledge_vector.embedding
```

Embedding 用于内容相似度计算：

```text
semantic_score = cosine(query_embedding, chunk_embedding)
```

---

### 3.6 knowledge_vector.metadata 结构

推荐 metadata 结构：

```json
{
  "sourceType": "ocr_note",
  "reviewed": true,
  "taskNo": "ocr-xxx",
  "chunkIndex": 1,
  "scenes": {
    "asset": ["stock"],
    "price": ["price_rise"],
    "volume": ["volume_expand"],
    "trend": ["breakout_from_range"],
    "valuation": [],
    "sentiment": [],
    "risk_strategy": ["wait_confirm", "observe_next_day"]
  },
  "keywords": ["放量", "上涨", "站稳", "第二天观察"],
  "summary": "放量上涨后需要观察次日是否继续放量并站稳。"
}
```

---

### 3.7 Chunk 入库示例

原始复核文本：

```text
低价股突然放量上涨时，不要只看当天涨幅。需要观察换手率是否过高，以及第二天是否继续放量并站稳。
```

入库 metadata：

```json
{
  "sourceType": "ocr_note",
  "reviewed": true,
  "scenes": {
    "asset": ["stock", "low_price_stock"],
    "price": ["price_rise"],
    "volume": ["volume_expand", "high_turnover"],
    "trend": ["breakout_from_range"],
    "valuation": [],
    "sentiment": ["short_term_emotion"],
    "risk_strategy": ["chase_high_risk", "wait_confirm", "observe_next_day"]
  },
  "keywords": ["低价股", "放量上涨", "换手率", "追高", "观察次日"],
  "summary": "低价股放量上涨后，需要关注换手率和次日确认。"
}
```

### 3.8 Chunk 入库标签白名单与判断规则

Chunk 入库打标签时，需要先区分两种场景：

```text
chunk 入库打标签：
判断“这段知识以后适合在哪些场景下被检索出来”。

当前标的打标签：
判断“这只股票 / 转债 / 指数现在处于什么场景”。
```

本节主要定义 chunk 入库时使用的标签白名单和判断规则。

Chunk 入库时，推荐 metadata 结构如下：

```json
{
  "scenes": {
    "asset": [],
    "price": [],
    "volume": [],
    "trend": [],
    "valuation": [],
    "sentiment": [],
    "risk_strategy": []
  },
  "keywords": [],
  "summary": ""
}
```

注意：chunk 入库标签不需要 7 类都填满。如果某个 chunk 只适用于风险控制场景，则只填写 `risk_strategy` 即可，其他类别可以为空数组。

#### 3.8.1 asset：资产类型标签

| 标签 | 中文含义 | chunk 入库时如何判断 |
|---|---|---|
| `general` | 通用投资经验 | 文本没有限定股票、指数、可转债、基金等具体资产，适用于通用投资行为、风险控制、学习复盘等。 |
| `stock` | 股票 | 文本明确讨论股票、个股、股价、买股、卖股、个股走势等。 |
| `index` | 指数 | 文本明确讨论大盘、指数、上证、深成指、创业板、科创板、板块指数等。 |
| `convertible_bond` | 可转债 | 文本出现可转债、转债、溢价率、强赎、正股联动、转股价值等内容。 |
| `fund` | 基金 | 文本明确讨论基金、ETF、场内基金、基金定投、基金配置等。 |
| `bank_stock` | 银行股 | 文本明确讨论银行股、银行板块、低 PB 银行、息差、分红等银行相关内容。 |
| `low_price_stock` | 低价股 | 文本明确提到低价股，或讨论几元股、低价小票、低价补涨等。 |
| `large_cap_stock` | 大盘股 | 文本讨论大市值、权重股、蓝筹、大盘股、机构重仓等。 |
| `small_cap_stock` | 小盘股 | 文本讨论小市值、小盘股、题材小票、弹性较大等。 |

#### 3.8.2 price：价格位置标签

| 标签 | 中文含义 | 适用周期 | chunk 入库时如何判断 |
|---|---|---|---|
| `price_rise` | 价格上涨 | 日K / 周K / 月K | 文本讨论上涨、拉升、涨幅扩大、上涨过程中如何判断等。 |
| `price_drop` | 价格下跌 | 日K / 周K / 月K | 文本讨论下跌、杀跌、回落、跌幅扩大、下跌后的处理等。 |
| `sideways` | 横盘 | 日K / 周K / 月K | 文本讨论横盘、震荡、长期不涨、窄幅波动、区间整理等。 |
| `near_recent_high` | 接近近期高位 | 日K / 周K / 月K | 文本讨论高位、阶段高点、接近前高、涨到高位后的风险等。 |
| `near_recent_low` | 接近近期低位 | 日K / 周K / 月K | 文本讨论低位、阶段低点、跌到底部区域、低位观察等。 |
| `breakout` | 突破 | 日K / 周K / 月K | 文本讨论突破压力位、突破平台、突破前高、突破后的确认等。 |
| `pullback` | 回调 | 日K / 周K / 月K | 文本讨论上涨后的回调、回踩、调整、短线回落、回调买点等。 |
| `gap_up` | 跳空高开 | 仅日K | 文本讨论跳空高开、高开缺口、高开后的风险或机会。 |
| `gap_down` | 跳空低开 | 仅日K | 文本讨论跳空低开、低开缺口、低开后的观察或风险。 |

#### 3.8.3 volume：成交量 / 换手标签

| 标签 | 中文含义 | 适用周期 | chunk 入库时如何判断 |
|---|---|---|---|
| `volume_expand` | 放量 | 日K / 周K / 月K | 文本讨论放量、成交量放大、量能增加、资金明显活跃等。 |
| `volume_shrink` | 缩量 | 日K / 周K / 月K | 文本讨论缩量、成交量减少、量能不足、无人关注等。 |
| `high_turnover` | 高换手 | 日K / 周K / 月K | 文本讨论换手率高、交易活跃、筹码交换剧烈、分歧加大等。 |
| `low_turnover` | 低换手 | 日K / 周K / 月K | 文本讨论换手低、交易清淡、流动性不足、没人买卖等。 |
| `volume_price_confirm` | 量价配合 | 日K / 周K / 月K | 文本讨论上涨有量、下跌缩量、价格和成交量互相验证等。 |
| `volume_price_divergence` | 量价背离 | 日K / 周K / 月K | 文本讨论价格上涨但量没跟上、放量但价格不涨、量价不一致等。 |
| `volume_spike` | 成交量突然放大 | 日K优先 | 文本讨论突然爆量、异常放量、某天成交量明显突增等。周K / 月K 如启用，应解释为本周或本月相对历史周期异常放大。 |
| `volume_dry_up` | 成交枯竭 | 日K / 周K / 月K | 文本讨论成交极低、量能枯竭、没人交易、流动性很差等。 |

#### 3.8.4 trend：趋势结构标签

| 标签 | 中文含义 | 适用周期 | chunk 入库时如何判断 |
|---|---|---|---|
| `uptrend` | 上升趋势 | 日K / 周K / 月K | 文本讨论连续上涨、趋势向上、均线多头、逐步抬高等。 |
| `downtrend` | 下降趋势 | 日K / 周K / 月K | 文本讨论连续下跌、趋势向下、弱势下行、反弹无力等。 |
| `range_bound` | 区间震荡 | 日K / 周K / 月K | 文本讨论箱体震荡、区间波动、上下沿、震荡整理等。 |
| `rebound` | 反弹 | 日K / 周K / 月K | 文本讨论下跌后的反弹、超跌反弹、弱势后的阶段回升等。 |
| `pullback` | 回调 | 日K / 周K / 月K | 文本讨论上涨后的回调、回踩、调整，且原趋势尚未明显破坏。 |
| `repair` | 修复 | 周K / 月K优先，日K可用 | 文本讨论下跌或弱势之后企稳、跌幅收敛、缓慢回升、修复结构等。 |
| `breakout_from_range` | 横盘突破 | 日K / 周K / 月K | 文本讨论长期横盘后突破、箱体突破、平台突破等。 |
| `breakdown_from_range` | 区间破位 | 日K / 周K / 月K | 文本讨论横盘或支撑之后向下脱离区间、跌破平台、跌破关键支撑等。 |
| `trend_reversal` | 趋势反转 | 日K / 周K / 月K | 文本讨论趋势由弱转强、由强转弱、反转信号、拐点等。 |
| `continuation` | 趋势延续 | 日K / 周K / 月K | 文本讨论前序趋势和当前趋势方向一致、上涨延续、下跌延续、趋势继续等。 |
| `turn_weak` | 转弱 | 周K / 月K优先，日K可用 | 文本讨论上涨后动能衰减、上攻乏力、跌破支撑、趋势开始走弱等。 |
| `turn_strong` | 转强 | 周K / 月K优先，日K可用 | 文本讨论下跌或横盘后重心抬升、走势转强、弱势改善、开始抬升等。 |
| `failed_breakout` | 假突破 / 突破失败 | 日K / 周K / 月K | 文本讨论假突破、突破后回落、站不上去、冲高失败等。 |

#### 3.8.5 valuation：估值 / 基本面标签

| 标签 | 中文含义 | 适用周期 | chunk 入库时如何判断 |
|---|---|---|---|
| `low_pe` | 低 PE | 日K / 周K / 月K | 文本讨论低市盈率、PE 较低、盈利估值便宜等。 |
| `high_pe` | 高 PE | 日K / 周K / 月K | 文本讨论高市盈率、估值过高、盈利无法支撑估值等。 |
| `low_pb` | 低 PB | 日K / 周K / 月K | 文本讨论低市净率、破净、PB 小于 1、银行股低 PB 等。 |
| `high_pb` | 高 PB | 日K / 周K / 月K | 文本讨论高市净率、净资产估值偏高等。 |
| `high_dividend` | 高股息 | 日K / 周K / 月K | 文本讨论分红率、股息率、高股息策略、稳定分红等。 |
| `valuation_repair` | 估值修复 | 周K / 月K优先，日K可用 | 文本讨论低估值修复、估值回归、估值提升、补涨修复等。 |
| `valuation_trap` | 低估值陷阱 | 日K / 周K / 月K | 文本讨论低估值不一定安全、便宜有原因、低 PE / 低 PB 陷阱等。 |
| `fundamental_risk` | 基本面风险 | 日K / 周K / 月K | 文本讨论业绩变差、盈利压力、资产质量问题、基本面不确定等。 |

#### 3.8.6 sentiment：情绪 / 异动标签

| 标签 | 中文含义 | 适用周期 | chunk 入库时如何判断 |
|---|---|---|---|
| `market_attention_rise` | 关注度上升 | 日K / 周K / 月K | 文本讨论市场开始关注、热度提高、人气变强、资金关注度上升等。 |
| `short_term_emotion` | 短线情绪升温 | 仅日K | 文本讨论短线情绪、短线资金活跃、情绪推动上涨等。周K / 月K 不建议继续使用该标签名，避免把阶段性热度误写成短线情绪。 |
| `panic_selling` | 恐慌抛售 | 日K优先，周K可用 | 文本讨论恐慌杀跌、情绪性卖出、踩踏、急跌恐慌等。月K 默认不启用，除非文本明确讨论长期恐慌或极端抛售阶段。 |
| `news_driven` | 消息驱动 | 日K / 周K / 月K | 文本讨论新闻、公告、传闻、利好利空消息推动行情。 |
| `policy_driven` | 政策驱动 | 日K / 周K / 月K | 文本讨论政策影响、监管政策、宏观政策、行业政策刺激等。 |
| `sector_rotation` | 板块轮动 | 日K / 周K / 月K | 文本讨论板块切换、资金从一个板块转向另一个板块、轮动行情等。 |
| `weak_sentiment` | 情绪偏弱 | 日K / 周K / 月K | 文本讨论市场情绪弱、没人接力、上涨无力、关注度低等。 |
| `herding_effect` | 羊群效应 / 从众行为 | 日K / 周K优先 | 文本讨论羊群效应、羊群行为、从众行为、趋同性、交易行为趋同、只买不卖、只卖不买、既买又卖、净买基金、净卖基金等。 |
| `institutional_behavior` | 机构 / 基金行为 | 日K / 周K / 月K | 文本讨论机构投资者、证券投资基金、投资基金行为、基金交易行为、基金投资行为、基金持仓、基金中报、基金年报、基金家数、互助基金等。 |

#### 3.8.7 risk_strategy：风险 / 策略标签

| 标签 | 中文含义 | 适用周期 | chunk 入库时如何判断 |
|---|---|---|---|
| `chase_high_risk` | 追高风险 | 日K / 周K优先 | 文本提醒上涨后不要盲目追、接近高位要谨慎、涨多了风险变大等。月K 可用于长期高位追涨风险，但默认不作为高频信号。 |
| `false_breakout_risk` | 假突破风险 | 日K / 周K优先 | 文本讨论突破后可能失败、冲高回落、站不稳、骗线等。月K 如启用，应解释为长期平台突破失败风险。 |
| `liquidity_risk` | 流动性风险 | 日K / 周K / 月K | 文本讨论成交差、买卖困难、流动性不足、小票无法及时退出等。 |
| `drawdown_risk` | 回撤风险 | 日K / 周K / 月K | 文本讨论可能回撤、下跌空间、亏损扩大、短线回落风险等。 |
| `valuation_trap_risk` | 估值陷阱风险 | 日K / 周K / 月K | 文本讨论低估值可能是陷阱、低 PB / PE 背后有问题等。 |
| `overheated_risk` | 过热风险 | 日K / 周K优先 | 文本讨论涨得太快、情绪过热、短线拥挤、炒作过度等。月K 如启用，应解释为长期过热或估值交易拥挤。 |
| `risk_control` | 风险控制 | 日K / 周K / 月K | 文本讨论控制风险、先看风险、不要重仓冒险、避免大亏等。 |
| `position_control` | 仓位控制 | 日K / 周K / 月K | 文本讨论轻仓、分批、控制仓位、不要满仓、仓位管理等。 |
| `wait_confirm` | 等待确认 | 日K / 周K / 月K | 文本讨论不要马上判断、等确认、等站稳、等第二天走势验证等。报告文案必须按分析周期改写为次日、下周或下月确认。 |
| `observe_next_day` | 观察次日表现 | 仅日K | 文本明确提到第二天观察、次日是否继续放量、次日是否站稳等。周K / 月K 应使用“等待下一周期确认”的自然语言，不继续使用该标签名。 |
| `avoid_emotional_trade` | 避免情绪交易 | 日K / 周K优先 | 文本提醒不要冲动、不要被情绪影响、不要因为涨跌而乱操作等。 |
| `take_profit_plan` | 止盈计划 | 日K / 周K / 月K | 文本讨论涨到目标后减仓、止盈、分批卖出、落袋为安等。 |
| `stop_loss_plan` | 止损计划 | 日K / 周K / 月K | 文本讨论跌破条件止损、亏损控制、设置退出条件等。 |

#### 3.8.8 入库判断规则

| 规则 | 说明 |
|---|---|
| 只从白名单选 | LLM 不能自己创造新标签，否则后面检索会混乱。 |
| 不相关就空数组 | 不要为了填满 7 类而强行打标签。 |
| 每类不限制固定数量 | 只保留和 chunk 适用场景直接相关的标签，避免无关标签导致检索噪声变大。 |
| 优先打“适用场景” | 不是问这段话表面讲了什么，而是问它以后适合在哪些投资场景下被检索出来。 |
| 风险和策略优先保留 | 如果文本包含操作提醒、风险提醒、观察条件，优先给 `risk_strategy` 打标签。 |
| `general` 用于通用经验 | 如果文本不是特定股票 / 转债 / 指数场景，但适合投资通用经验，就打 `asset.general`。 |
| 适用周期只约束报告计算 | chunk 入库仍按文本适用场景打标签；报告生成时再根据模块输入周期过滤或启用对应标签。TrendSceneModule 会同时计算日K、周K、月K子趋势。 |
| 新增标签需要同步白名单 | 文档中新增的 trend 标签属于设计白名单，落地时需要同步 Python 打标白名单、规则标签器和 Java 知识库校验白名单。 |

#### 3.8.9 完整标注示例

chunk 文本：

```text
低价股突然放量上涨时，不要只看当天涨幅。需要观察换手率是否过高，以及第二天是否继续放量并站稳。
```

推荐标签：

```json
{
  "summary": "低价股放量上涨后，需要关注换手率和次日确认。",
  "keywords": ["低价股", "放量上涨", "换手率", "追高", "次日确认"],
  "scenes": {
    "asset": ["stock", "low_price_stock"],
    "price": ["price_rise", "breakout"],
    "volume": ["volume_expand", "high_turnover"],
    "trend": ["breakout_from_range"],
    "valuation": [],
    "sentiment": ["short_term_emotion"],
    "risk_strategy": ["chase_high_risk", "wait_confirm", "observe_next_day"]
  }
}
```

这段的核心不是“它提到了上涨”，而是它以后适合在以下场景下被检索出来：

```text
低价股
放量上涨
高换手
突破确认
追高风险
次日观察
```

---

## 4. 七个场景模块设计

### 4.1 模块统一输出结构

报告生成时，系统会根据当前标的数据运行 7 个场景模块。

普通场景模块默认使用任务指定的基础行情周期计算，不在同一个模块内部混用不同周期数据。`TrendSceneModule` 是例外：趋势天然需要多周期上下文，模块会同时接收日K、周K、月K数据，分别计算 `daily`、`weekly`、`monthly` 三个子趋势，再聚合成顶层趋势结果。

每个模块输出统一结构：

```json
{
  "module": "volume",
  "score": 0.82,
  "level": "high",
  "direction": "active",
  "tags": {
    "volume_expand": 0.90,
    "high_turnover": 0.80
  },
  "evidence": [
    "当前成交量相对 60 日稳健中位水平明显放大，volume_expand 标签触发",
    "当前换手率处于历史分布较高位置，high_turnover 标签触发"
  ]
}
```

注意：当前标的的小标签建议带 score，而 chunk 入库时的小标签可以先只存标签，不一定存分数。

普通模块的 `evidence` 固定为字符串数组，不输出 `metrics`、`reason` 等对象字段。文案应保持客观、细粒度，说明本次实际触发的标签和依据，便于 RAG 召回、后续文本拼接和 LLM 分析。组合类标签只描述本次实际达到阈值的子信号。TrendSceneModule 顶层不输出 `evidence`，证据下沉到 `periodTrends.daily`、`periodTrends.weekly`、`periodTrends.monthly`。

---

### 4.2 AssetSceneModule：资产类型模块

#### 4.2.1 模块作用

判断当前标的属于什么资产类型。

它主要用于控制检索方向，避免股票报告召回过多可转债知识，或可转债报告召回过多普通股票知识。

#### 4.2.2 推荐小标签

```text
general
stock
index
convertible_bond
fund
bank_stock
low_price_stock
large_cap_stock
small_cap_stock
```

#### 4.2.3 输出示例

```json
{
  "module": "asset",
  "score": 1.0,
  "level": "high",
  "direction": "neutral",
  "tags": {
    "stock": 1.0,
    "bank_stock": 1.0,
    "low_price_stock": 0.8
  },
  "evidence": [
    "标的类型识别为股票，stock 标签命中",
    "行业或用户配置识别为银行股，bank_stock 标签命中",
    "当前价格不高于低价股阈值，low_price_stock 标签命中"
  ]
}
```

---

### 4.3 PriceSceneModule：价格位置模块

#### 4.3.1 模块作用

描述当前价格行为和价格位置。

它回答：

```text
当前价格是在上涨、下跌、横盘、高位、低位、突破还是回调？
```

#### 4.3.2 推荐小标签

```text
price_rise
price_drop
sideways
near_recent_high
near_recent_low
breakout
pullback
gap_up
gap_down
```

#### 4.3.3 输出示例

```json
{
  "module": "price",
  "score": 0.74,
  "level": "high",
  "direction": "positive",
  "tags": {
    "price_rise": 0.80,
    "near_recent_high": 0.75
  },
  "evidence": [
    "当日涨幅超过上涨强度阈值，price_rise 标签触发",
    "当前价格处于近 20 日区间高位，near_recent_high 标签触发"
  ]
}
```

---

### 4.4 VolumeSceneModule：成交量 / 换手模块

#### 4.4.1 模块作用

描述成交量、换手率和资金活跃度。

它回答：

```text
成交量是否异常？
换手率是否过高？
当前量价是否配合？
```

#### 4.4.2 推荐小标签

```text
volume_expand
volume_shrink
high_turnover
low_turnover
volume_price_confirm
volume_price_divergence
volume_spike
volume_dry_up
```

#### 4.4.3 输出示例

```json
{
  "module": "volume",
  "score": 0.82,
  "level": "high",
  "direction": "active",
  "tags": {
    "volume_expand": 0.90,
    "high_turnover": 0.80
  },
  "evidence": [
    "当前成交量相对 60 日稳健中位水平明显放大，volume_expand 标签触发",
    "当前换手率处于历史分布较高位置，high_turnover 标签触发"
  ]
}
```

---

### 4.5 TrendSceneModule：趋势结构模块

#### 4.5.1 模块作用

描述日K、周K、月K三个周期下的趋势结构，并聚合成当前报告使用的趋势结论。

它和价格模块的区别是：

```text
PriceSceneModule 看当前价格状态；
TrendSceneModule 看不同周期下的走势结构和趋势关系。
```

TrendSceneModule 固定接收日K、周K、月K数据。模块内部先分别计算三个子趋势：

```text
daily：日线趋势，用于识别短期趋势和短期确认。
weekly：周线趋势，用于识别中期趋势关系，是趋势聚合的核心权重。
monthly：月线趋势，用于识别长期趋势背景。
```

顶层 `trend.tags` 取 `periodTrends.daily.tags`、`periodTrends.weekly.tags`、`periodTrends.monthly.tags` 的并集；如果同一个标签在多个周期出现，顶层分数取最大值。

顶层 `trend.score` 使用固定权重聚合：

```text
trend.score = daily.score * 0.2 + weekly.score * 0.5 + monthly.score * 0.3
```

顶层 `trend.evidence` 不输出，证据只保存在三个子趋势的 `evidence` 中，避免多周期依据重复或互相混淆。

顶层必须输出 `queryText`，用于 trend 类 RAG 召回。`queryText` 需要综合日线、周线、月线三个子趋势的标签和 evidence，形成一段可检索的趋势摘要。

#### 4.5.2 推荐小标签

```text
uptrend
downtrend
range_bound
rebound
pullback
repair
breakdown_from_range
trend_reversal
continuation
turn_weak
turn_strong
breakout_from_range
failed_breakout
```

#### 4.5.3 输出示例

```json
{
  "module": "trend",
  "score": 0.68,
  "level": "medium",
  "direction": "neutral",
  "tags": {
    "downtrend": 0.80,
    "repair": 0.60,
    "rebound": 0.70
  },
  "periodTrends": {
    "daily": {
      "score": 0.70,
      "level": "medium",
      "direction": "positive",
      "tags": {
        "rebound": 0.70
      },
      "evidence": [
        "日线前期下跌后近期价格回升，rebound 标签触发"
      ]
    },
    "weekly": {
      "score": 0.60,
      "level": "medium",
      "direction": "positive",
      "tags": {
        "repair": 0.60
      },
      "evidence": [
        "周线弱势后开始企稳并缓慢回升，repair 标签触发"
      ]
    },
    "monthly": {
      "score": 0.80,
      "level": "high",
      "direction": "negative",
      "tags": {
        "downtrend": 0.80
      },
      "evidence": [
        "月线仍处于下降趋势结构，downtrend 标签触发"
      ]
    }
  },
  "queryText": "趋势分析。月线仍处于下降趋势，周线弱势修复，日线出现阶段反弹。重点关注反弹能否延续、修复是否转强，以及长期下降趋势是否被改变。"
}
```

---

### 4.6 ValuationSceneModule：估值 / 基本面模块

#### 4.6.1 模块作用

描述估值水平和基础财务特征。

适合用于：

```text
银行股
低价股
蓝筹股
指数
中长期观察
```

#### 4.6.2 推荐小标签

```text
low_pe
high_pe
low_pb
high_pb
high_dividend
valuation_repair
valuation_trap
fundamental_risk
```

#### 4.6.3 输出示例

```json
{
  "module": "valuation",
  "score": 0.71,
  "level": "medium_high",
  "direction": "positive",
  "tags": {
    "low_pb": 0.80,
    "low_pe": 0.65
  },
  "evidence": [
    "PB 在历史估值分布中处于较低位置，low_pb 标签触发",
    "PE 在历史估值分布中处于较低位置，low_pe 标签触发"
  ]
}
```

---

### 4.7 SentimentSceneModule：情绪 / 异动模块

#### 4.7.1 模块作用

描述市场关注度、情绪热度和预留的消息驱动特征。

它可以来自：

```text
涨跌幅
成交量变化
换手率
涨停 / 大跌
新闻 / 公告（预留，当前 report 不接入）
板块联动（预留，当前 report 不计算）
观察池热度（预留，当前 report 不接入）
```

#### 4.7.2 推荐小标签

```text
market_attention_rise
short_term_emotion
panic_selling
news_driven
policy_driven
sector_rotation
weak_sentiment
herding_effect
institutional_behavior
```

当前 report 代码只产出 `market_attention_rise`、`short_term_emotion`、`panic_selling`、`weak_sentiment`、`herding_effect`。其余标签保留在白名单中，作为后续接入外部文本、政策、板块和机构行为数据时使用。

#### 4.7.3 输出示例

```json
{
  "module": "sentiment",
  "score": 0.68,
  "level": "medium_high",
  "direction": "active",
  "tags": {
    "short_term_emotion": 0.72,
    "market_attention_rise": 0.65
  },
  "evidence": [
    "基于成交额、换手率和振幅计算的交易关注度较近期均值上升，market_attention_rise 标签触发",
    "价格上涨、放量和交易关注度上升共同指向短线情绪升温，short_term_emotion 标签触发"
  ]
}
```

---

### 4.8 RiskStrategySceneModule：风险 / 策略模块

#### 4.8.1 模块作用

描述当前需要关注的风险和处理策略。

它回答：

```text
当前应该注意什么风险？
当前应该采取什么观察或处理策略？
```

#### 4.8.2 推荐小标签

```text
chase_high_risk
false_breakout_risk
liquidity_risk
drawdown_risk
valuation_trap_risk
overheated_risk
risk_control
position_control
wait_confirm
observe_next_day
avoid_emotional_trade
take_profit_plan
stop_loss_plan
```

当前 report 代码不产出 `valuation_trap_risk`，该标签保留为后续基本面风险增强使用。

#### 4.8.3 输出示例

```json
{
  "module": "risk_strategy",
  "score": 0.76,
  "level": "high",
  "direction": "risk",
  "tags": {
    "chase_high_risk": 0.85,
    "wait_confirm": 0.70
  },
  "evidence": [
    "上涨强度、接近近期高位和高换手提高追高风险，chase_high_risk 标签触发",
    "突破、量能或短线情绪信号尚未形成充分确认，wait_confirm 标签触发"
  ]
}
```

---

## 5. 当前标的场景分析设计

### 5.1 数据查询范围

用户选择标的后，系统先查询数据。

根据资产类型不同，查询内容不同。

股票可以查询：

```text
最新行情
涨跌幅
成交量
换手率
PE / PB
分时走势
日 K 线
近期高低点
观察池状态
预警状态
```

可转债可以查询：

```text
转债价格
涨跌幅
成交量
换手率
溢价率
评级
剩余规模
正股表现
强赎相关信息
```

指数可以查询：

```text
指数最新点位
涨跌幅
成交额
日 K 线
近期趋势
关联市场表现
```

---

### 5.2 7 大类标签生成

查询数据后，系统运行 7 个场景模块：

```text
AssetSceneModule
PriceSceneModule
VolumeSceneModule
TrendSceneModule
ValuationSceneModule
SentimentSceneModule
RiskStrategySceneModule
```

每个模块生成：

```text
大类 score
小标签 tags
证据 evidence
```

---

### 5.3 小标签得分计算

每个小标签可以有独立得分。

例如 volume 类：

```json
{
  "volume_expand": 0.90,
  "high_turnover": 0.80
}
```

这说明：

```text
放量信号较强；
高换手信号也较明显。
```

小标签得分可以来自规则计算，也可以后续引入模型判断。

第一版建议优先使用规则。

具体标签公式、默认阈值、权重、时间衰减和来源权重以 `docs/标的标签计算规则2.md` 为准。公式本身保持稳定，公式参数可以支持页面配置，并按：

```text
用户自定义参数 > 资产类型参数 > 系统默认参数
```

逐层覆盖。

---

### 5.4 大类总分计算

每个大类总分由小标签聚合得到。

简单版本：

```text
category_score = max(tag_scores)
```

或：

```text
category_score = weighted_avg(tag_scores)
```

推荐第一版：

```text
category_score = weighted_avg(tag_scores)
```

因为一个类别中多个小标签同时命中时，总分应更稳定。

例如：

```text
volume_expand = 0.90
high_turnover = 0.80

volume_score = 0.85
```

---

### 5.5 currentScenes 输出结构

推荐 currentScenes 结构：

```json
{
  "asset": {
    "score": 1.0,
    "level": "high",
    "direction": "neutral",
    "tags": {
      "stock": 1.0,
      "bank_stock": 1.0,
      "low_price_stock": 0.8
    },
    "evidence": [
      "标的类型识别为股票，stock 标签命中",
      "行业或用户配置识别为银行股，bank_stock 标签命中",
      "当前价格不高于低价股阈值，low_price_stock 标签命中"
    ]
  },
  "price": {
    "score": 0.74,
    "level": "high",
    "direction": "positive",
    "tags": {
      "price_rise": 0.80,
      "near_recent_high": 0.75
    },
    "evidence": [
      "当日涨幅超过上涨强度阈值，price_rise 标签触发",
      "当前价格处于近 20 日区间高位，near_recent_high 标签触发"
    ]
  },
  "volume": {
    "score": 0.82,
    "level": "high",
    "direction": "positive",
    "tags": {
      "volume_expand": 0.90,
      "high_turnover": 0.80
    },
    "evidence": [
      "当前成交量相对 60 日稳健中位水平明显放大，volume_expand 标签触发",
      "当前换手率处于历史分布较高位置，high_turnover 标签触发"
    ]
  },
  "risk_strategy": {
    "score": 0.76,
    "level": "high",
    "direction": "risk",
    "tags": {
      "chase_high_risk": 0.85,
      "wait_confirm": 0.70
    },
    "evidence": [
      "上涨强度、接近近期高位和高换手提高追高风险，chase_high_risk 标签触发",
      "突破、量能或短线情绪信号尚未形成充分确认，wait_confirm 标签触发"
    ]
  }
}
```

---

### 5.6 场景分析示例

假设当前标的是低价银行股，出现放量上涨。

场景分析结果可能是：

```json
{
  "asset": {
    "score": 1.0,
    "level": "high",
    "direction": "neutral",
    "tags": {
      "stock": 1.0,
      "bank_stock": 1.0,
      "low_price_stock": 0.8
    },
    "evidence": [
      "标的类型识别为股票，stock 标签命中",
      "行业或用户配置识别为银行股，bank_stock 标签命中",
      "当前价格不高于低价股阈值，low_price_stock 标签命中"
    ]
  },
  "price": {
    "score": 0.74,
    "level": "high",
    "direction": "positive",
    "tags": {
      "price_rise": 0.80,
      "near_recent_high": 0.75
    },
    "evidence": [
      "当日涨幅超过上涨强度阈值，price_rise 标签触发",
      "当前价格处于近 20 日区间高位，near_recent_high 标签触发"
    ]
  },
  "volume": {
    "score": 0.82,
    "level": "high",
    "direction": "positive",
    "tags": {
      "volume_expand": 0.90,
      "high_turnover": 0.80
    },
    "evidence": [
      "当前成交量相对 60 日稳健中位水平明显放大，volume_expand 标签触发",
      "当前换手率处于历史分布较高位置，high_turnover 标签触发"
    ]
  },
  "trend": {
    "score": 0.65,
    "level": "medium",
    "direction": "positive",
    "tags": {
      "breakout_from_range": 0.70
    },
    "evidence": [
      "区间震荡后价格向上突破并获得成交确认，breakout_from_range 标签触发"
    ]
  },
  "valuation": {
    "score": 0.71,
    "level": "high",
    "direction": "positive",
    "tags": {
      "low_pb": 0.80,
      "low_pe": 0.65
    },
    "evidence": [
      "PB 在历史估值分布中处于较低位置，low_pb 标签触发",
      "PE 在历史估值分布中处于较低位置，low_pe 标签触发"
    ]
  },
  "sentiment": {
    "score": 0.68,
    "level": "medium",
    "direction": "positive",
    "tags": {
      "short_term_emotion": 0.72
    },
    "evidence": [
      "价格上涨、放量和交易关注度上升共同指向短线情绪升温，short_term_emotion 标签触发"
    ]
  },
  "risk_strategy": {
    "score": 0.76,
    "level": "high",
    "direction": "risk",
    "tags": {
      "chase_high_risk": 0.85,
      "wait_confirm": 0.70
    },
    "evidence": [
      "上涨强度、接近近期高位和高换手提高追高风险，chase_high_risk 标签触发",
      "突破、量能或短线情绪信号尚未形成充分确认，wait_confirm 标签触发"
    ]
  }
}
```

---

## 6. 检索召回设计

### 6.1 检索输入

检索召回模块输入包括：

```text
1. target：当前分析标的
2. reportType：报告类型
3. marketContext：行情数据
4. currentScenes：当前标的 7 大类标签和得分
5. totalChunks：本次报告希望召回的总 chunk 数
```

示例：

```json
{
  "target": {
    "type": "STOCK",
    "code": "002958",
    "name": "青农商行"
  },
  "reportType": "quick_analysis",
  "totalChunks": 10,
  "currentScenes": {
    "volume": {
      "score": 0.82,
      "level": "high",
      "direction": "positive",
      "tags": {
        "volume_expand": 0.90,
        "high_turnover": 0.80
      },
      "evidence": [
        "当前成交量相对 60 日稳健中位水平明显放大，volume_expand 标签触发",
        "当前换手率处于历史分布较高位置，high_turnover 标签触发"
      ]
    },
    "risk_strategy": {
      "score": 0.76,
      "level": "high",
      "direction": "risk",
      "tags": {
        "chase_high_risk": 0.85,
        "wait_confirm": 0.70
      },
      "evidence": [
        "上涨强度、接近近期高位和高换手提高追高风险，chase_high_risk 标签触发",
        "突破、量能或短线情绪信号尚未形成充分确认，wait_confirm 标签触发"
      ]
    }
  }
}
```

---

### 6.2 根据 7 大类得分计算 Chunk 分配比例

系统先根据 7 大类得分计算每一类应该召回多少 chunk。

当前 Java 实现中，`asset` 主要用于区分股票、指数、可转债等资产类型，不直接参与报告知识 chunk 配额分配。动态召回实际参与类别为：

```text
price
volume
trend
valuation
sentiment
risk_strategy
```

例如：

```text
volume      0.82
risk        0.76
price       0.74
valuation   0.71
sentiment   0.68
trend       0.65
```

如果总共希望召回 10 个 chunk，系统可能分配为：

```json
{
  "volume": 3,
  "risk_strategy": 2,
  "price": 2,
  "valuation": 1,
  "sentiment": 1,
  "trend": 1
}
```

---

#### 6.2.1 指数函数分配公式

推荐使用指数函数放大高分模块占比。

公式：

```text
effective_score_i = category_score_i * report_type_weight_i

allocation_score_i = exp(effective_score_i * alpha)

allocation_ratio_i = allocation_score_i / Σ allocation_score_j

chunk_count_i = round(allocation_ratio_i * total_chunks)
```

---

#### 6.2.2 alpha 放大系数

`alpha` 控制高分模块的放大程度。

```text
alpha 越大，高分模块占比越高。
```

推荐第一版：

```json
{
  "alpha": 6.0
}
```

调参建议：

```text
alpha = 4：较平滑
alpha = 6：推荐起点
alpha = 8：高分模块更突出
alpha > 10：可能过于激进
```

---

#### 6.2.3 reportType 权重

不同报告类型应有不同模块权重。

quick_analysis：

```json
{
  "price": 1.0,
  "volume": 1.0,
  "trend": 0.9,
  "valuation": 0.8,
  "sentiment": 0.9,
  "risk_strategy": 1.0
}
```

risk_check：

```json
{
  "price": 0.9,
  "volume": 1.0,
  "trend": 0.8,
  "valuation": 0.7,
  "sentiment": 1.1,
  "risk_strategy": 1.3
}
```

valuation_report：

```json
{
  "price": 0.7,
  "volume": 0.7,
  "trend": 0.7,
  "valuation": 1.5,
  "sentiment": 0.6,
  "risk_strategy": 1.0
}
```

---

#### 6.2.4 min / max 分配限制

为了避免指数函数过度放大，需要设置保护规则。

推荐配置：

```json
{
  "totalChunks": 10,
  "alpha": 6.0,
  "categoryScoreThreshold": 0.35,
  "minPerActiveCategory": 1,
  "maxPerCategory": 4
}
```

当前实现常量：

```text
alpha = 6.0
categoryScoreThreshold = 0.35
minPerActiveCategory = 1
maxPerCategory = 4
semanticCandidateLimit = 200
jaccardThreshold = 0.2
loweredJaccardThreshold = 0.1
embeddingDimension = 512
finalScore = 0.45 * semantic_score + 0.45 * tag_match_score + 0.10 * cross_scene_score
```

规则：

```text
1. category_score < categoryScoreThreshold 的类别不参与召回。
2. active category 至少分配 1 个 chunk。
3. 单个类别最多不超过 maxPerCategory。
4. 如果最终总数不足或超出 totalChunks，再进行修正。
```

---

#### 6.2.5 Chunk 分配示例

输入：

```text
volume      0.82
risk        0.76
price       0.74
valuation   0.71
sentiment   0.68
trend       0.65
```

输出：

```json
{
  "volume": 3,
  "risk_strategy": 2,
  "price": 2,
  "valuation": 1,
  "sentiment": 1,
  "trend": 1
}
```

含义：

```text
本次报告主要召回成交量、风险策略、价格位置相关 chunk。
估值、情绪、趋势作为补充。
```

---

### 6.3 按类别生成检索任务

得到 chunk 分配后，每一类生成一个检索任务。

例如：

```json
[
  {
    "category": "volume",
    "chunkCount": 3,
    "currentTags": {
      "volume_expand": 0.90,
      "high_turnover": 0.80
    }
  },
  {
    "category": "risk_strategy",
    "chunkCount": 2,
    "currentTags": {
      "chase_high_risk": 0.85,
      "wait_confirm": 0.70
    }
  }
]
```

---

### 6.4 每类检索的标签查询与候选过滤

每类检索时，系统先根据当前类别需要的标签 `requiredTags`，与 chunk 入库时保存在 `metadata.scenes` 中的同类别标签 `chunkTags` 进行标签匹配。

这里的核心思想是：

```text
当前需要什么标签，就去知识库 chunk 的 metadata.scenes 对应类别下查什么标签。
```

例如 volume 类检索：

```text
requiredTags = [volume_expand, high_turnover]
chunkTags = chunk.metadata.scenes.volume
```

risk_strategy 类检索：

```text
requiredTags = [chase_high_risk, wait_confirm]
chunkTags = chunk.metadata.scenes.risk_strategy
```

系统使用 Jaccard similarity 计算当前需要标签和 chunk 已有标签之间的匹配程度：

```text
jaccard_score = |requiredTags ∩ chunkTags| / |requiredTags ∪ chunkTags|
```

只有当：

```text
jaccard_score >= jaccardThreshold
```

该 chunk 才会进入当前类别的候选集合。

推荐第一版配置：

```json
{
  "jaccardThreshold": 0.2
}
```

示例一：

```text
requiredTags = [volume_expand, high_turnover]
chunkTags = [volume_expand, volume_price_confirm]

intersection = [volume_expand]
union = [volume_expand, high_turnover, volume_price_confirm]

jaccard_score = 1 / 3 = 0.33
```

如果 `jaccardThreshold = 0.2`，则该 chunk 可以进入 volume 类候选集合。

示例二：

```text
requiredTags = [volume_expand, high_turnover]
chunkTags = [low_pb, valuation_trap]

intersection = []
union = [volume_expand, high_turnover, low_pb, valuation_trap]

jaccard_score = 0
```

该 chunk 不进入 volume 类候选集合。

这一层只负责候选过滤，不负责最终排序。进入候选集合后，还需要继续计算内容相似度和跨场景加分，并在当前类别内部进行重排序。

---

### 6.5 每类语义检索 Query 生成

完成标签过滤后，系统需要为当前类别生成 `queryText`，用于后续计算内容相似度 `semantic_score`。

`queryText` 不是给 LLM 看的，也不是最终报告内容。它的作用是：

```text
把当前类别的小标签转换成适合 embedding 检索的语义查询文本。
```

也就是说：

```text
requiredTags
  ↓
queryText
  ↓
queryEmbedding
  ↓
与候选 chunk.embedding 计算 semantic_score
```

`queryText` 不直接使用用户原始问题，而是根据当前类别的 `requiredTags`、标的类型和报告类型生成。

例如 volume 类：

```text
requiredTags = [volume_expand, high_turnover]

queryText = 放量上涨 高换手 成交量持续性 量价关系
```

risk_strategy 类：

```text
requiredTags = [chase_high_risk, wait_confirm]

queryText = 追高风险 等待确认 风险控制 高位放量
```

price 类：

```text
requiredTags = [price_rise, near_recent_high]

queryText = 价格上涨 接近近期高位 突破 回调
```

valuation 类：

```text
requiredTags = [low_pb, low_pe]

queryText = 低 PB 低 PE 银行股 估值修复 低估值陷阱
```

queryText 生成后，系统会调用 embedding 模型生成向量：

```text
queryEmbedding = embedding(queryText)
```

然后用于计算候选 chunk 的内容相似度：

```text
semantic_score = cosine(queryEmbedding, chunkEmbedding)
```

---

### 6.6 每类候选集合生成与语义相似度计算

每个类别会根据前面动态分配得到的 `chunkCount`，独立执行召回。

例如动态分配结果为：

```json
{
  "volume": 3,
  "risk_strategy": 2,
  "price": 2,
  "valuation": 1,
  "sentiment": 1,
  "trend": 1
}
```

则每类分别执行：

```text
volume 类最终取 3 个 chunk
risk_strategy 类最终取 2 个 chunk
price 类最终取 2 个 chunk
valuation 类最终取 1 个 chunk
sentiment 类最终取 1 个 chunk
trend 类最终取 1 个 chunk
```

每一类内部召回流程如下：

```text
当前类别 requiredTags
  ↓
读取 chunk.metadata.scenes 对应类别的 chunkTags
  ↓
计算 jaccard_score
  ↓
只保留 jaccard_score >= jaccardThreshold 的候选 chunk
  ↓
生成当前类别 queryText
  ↓
计算 queryEmbedding
  ↓
计算每个候选 chunk 的 semantic_score
  ↓
进入类内重排序
```

注意：每类内部召回不是直接取最终结果，而是先形成候选集合，再交给 6.7 进行综合评分和排序。

如果某一类候选数量过少，可以采用降级策略：

```text
1. 降低 jaccardThreshold
2. 放宽 requiredTags，只保留核心标签
3. 使用同类别下所有 chunk 做语义召回
4. 最后仍不足时，该类别返回空列表
```

第一版可以先只实现前两种降级策略。

---

### 6.7 候选 Chunk 综合评分与类内重排序

类内重排序用于决定当前类别中哪些候选 chunk 最终进入报告。

6.4 只负责判断 chunk 能不能进入候选集合；  
6.5 只负责生成 embedding 检索用的 queryText；  
6.6 只负责得到候选集合和计算 semantic_score；  
6.7 才负责综合评分和排序。

每个候选 chunk 需要计算三个分数：

```text
semantic_score
tag_match_score
cross_scene_score
```

最终类内排序公式推荐为(也可以基于alpha，beta和gama系数)：

```text
final_score =
0.45 * semantic_score
+ 0.45 * tag_match_score
+ 0.10 * cross_scene_score
```

#### 6.7.1 semantic_score 内容相似度

`semantic_score` 用于衡量 `queryText` 和 chunk 文本内容的语义相似度。

计算方式：

```text
semantic_score = cosine(queryEmbedding, chunkEmbedding)
```

其中：

```text
queryEmbedding = embedding(queryText)
chunkEmbedding = chunk.embedding
```

它回答的问题是：

```text
当前类别的语义查询文本和这个 chunk 的正文内容是否相似？
```

例如 volume 类：

```text
queryText = 放量上涨 高换手 成交量持续性 量价关系
```

如果某个 chunk 内容是：

```text
放量上涨后要观察成交量是否持续，不能只看当天涨幅。
```

则它通常会有较高的 `semantic_score`。

#### 6.7.2 tag_match_score 标签匹配度

`tag_match_score` 用于衡量当前类别下，候选 chunk 的标签与当前检索需求标签的匹配程度。

第一版中，`tag_match_score` 可以直接复用 6.4 中计算得到的 Jaccard similarity：

```text
tag_match_score = jaccard_score
```

也就是：

```text
tag_match_score = |requiredTags ∩ chunkTags| / |requiredTags ∪ chunkTags|
```

示例：

```text
requiredTags = [volume_expand, high_turnover]
chunkTags = [volume_expand, volume_price_confirm]

intersection = [volume_expand]
union = [volume_expand, high_turnover, volume_price_confirm]

tag_match_score = 1 / 3 = 0.33
```

如果完全匹配：

```text
requiredTags = [volume_expand, high_turnover]
chunkTags = [volume_expand, high_turnover]

intersection = [volume_expand, high_turnover]
union = [volume_expand, high_turnover]

tag_match_score = 2 / 2 = 1.0
```

`tag_match_score` 和 `semantic_score` 的作用不同：

```text
semantic_score 判断文本内容是否相似；
tag_match_score 判断场景标签是否匹配。
```

一个 chunk 可能文本很相似，但标签不够匹配；也可能标签很匹配，但文本表达不够接近。

因此两个分数需要同时参与最终排序。

#### 6.7.3 cross_scene_score 跨场景加分

`cross_scene_score` 用于奖励同时命中其他高分场景的 chunk。

例如当前正在检索 volume 类，但某个 chunk 不仅命中了 volume 标签，还命中了 risk_strategy 标签：

```json
{
  "scenes": {
    "volume": ["volume_expand"],
    "risk_strategy": ["chase_high_risk", "wait_confirm"]
  }
}
```

如果当前报告中 `risk_strategy` 的分数也很高，那么这个 chunk 更有价值。

原因是它不仅能解释放量，还能连接到风险判断。

`cross_scene_score` 可以根据当前其他高分类别的匹配情况计算。

第一版可以简单处理：

```text
如果 chunk 同时命中其他 primary categories 中的任意标签，则给予 0.1 ~ 0.3 的加分。
```

例如：

```text
当前 primary categories = [volume, risk_strategy, price]

当前检索类别 = volume

chunk 同时命中：
volume.volume_expand
risk_strategy.chase_high_risk

则 cross_scene_score 可以设为 0.3。
```

如果 chunk 只命中当前类别，没有命中其他高分类别：

```text
cross_scene_score = 0
```

#### 6.7.4 final_score 最终分数

最终类内排序使用：

```text
final_score =
0.45 * semantic_score
+ 0.45 * tag_match_score
+ 0.10 * cross_scene_score
```

示例：

候选 A：

```text
semantic_score = 0.82
tag_match_score = 1.00
cross_scene_score = 0.30
```

计算：

```text
final_score =
0.45 * 0.82
+ 0.45 * 1.00
+ 0.10 * 0.30
= 0.849
```

候选 B：

```text
semantic_score = 0.88
tag_match_score = 0.50
cross_scene_score = 0.10
```

计算：

```text
final_score =
0.45 * 0.88
+ 0.45 * 0.50
+ 0.10 * 0.10
= 0.631
```

虽然候选 B 的文本相似度更高，但候选 A 的标签匹配度和跨场景价值更高，因此候选 A 排名更靠前。

每一类候选 chunk 按照 `final_score` 从高到低排序。排序结果不会直接混合成一个全局 TopK，而是先保留在各自类别中，后续由 6.8 按照该类别分配到的 `chunkCount` 取 TopN。

---

### 6.8 每类取 TopN

完成类内重排序后，每一类根据前面动态分配得到的 `chunkCount` 取 TopN。

例如动态分配结果为：

```json
{
  "volume": 3,
  "risk_strategy": 2,
  "price": 2,
  "valuation": 1,
  "sentiment": 1,
  "trend": 1
}
```

则最终截断规则为：

```text
volume 类按 final_score 排序后取 Top 3
risk_strategy 类按 final_score 排序后取 Top 2
price 类按 final_score 排序后取 Top 2
valuation 类按 final_score 排序后取 Top 1
sentiment 类按 final_score 排序后取 Top 1
trend 类按 final_score 排序后取 Top 1
```

这一层的作用是把前面计算出来的召回比例真正落实到最终结果中。

也就是说：

```text
动态 Chunk 分配决定每类要几个；
类内重排序决定每类谁排前面；
每类取 TopN 决定最终进入报告的 chunk。
```

如果某一类候选 chunk 数量少于分配数量，则该类返回已有候选即可，不需要强行用其他类别补齐。

---

### 6.9 构建 knowledgeContext

每类取 TopN 后，系统需要把最终 chunk 按类别组织成 `knowledgeContext`。

不要把所有 chunk 混成一个全局列表，而应保持类别结构：

```json
{
  "knowledgeContext": {
    "volume": [],
    "risk_strategy": [],
    "price": [],
    "valuation": [],
    "sentiment": [],
    "trend": []
  }
}
```

报告生成阶段交给 LLM 的 `knowledgeContext` 只保留报告分析和引用所需字段：

```json
{
  "chunkId": 123,
  "scene": "risk_strategy",
  "text": "接近高位时不要只看涨幅，需要等待确认。",
  "matchedTags": ["chase_high_risk", "wait_confirm"]
}
```

`taskNo`、`chunkIndex`、`semanticScore`、`tagMatchScore`、`crossSceneScore`、`finalScore` 不进入 LLM 输入。这些字段是检索流水线内部定位和调参字段，应保存在内部 payload 或日志中，用于排查、审计、复盘和后续参数优化。

这样设计的好处是：

```text
1. LLM 可以清楚知道每段知识属于哪个分析维度。
2. LLM 只看到可用于报告生成的文本、场景和标签，不会把检索分数误当作结论置信度。
3. 报告不会变成一堆无结构的知识片段拼接。
4. 系统内部仍可保留完整检索调试字段，支持后续复盘和参数优化。
```

`knowledgeContext` 是报告生成阶段交给 LLM 的主要知识输入。

---

### 6.10 检索召回完整示例

假设当前标的是一只低价银行股，系统根据行情数据分析得到：

```json
{
  "volume": {
    "score": 0.82,
    "tags": {
      "volume_expand": 0.90,
      "high_turnover": 0.80
    }
  },
  "risk_strategy": {
    "score": 0.76,
    "tags": {
      "chase_high_risk": 0.85,
      "wait_confirm": 0.70
    }
  },
  "price": {
    "score": 0.74,
    "tags": {
      "price_rise": 0.80,
      "near_recent_high": 0.75
    }
  }
}
```

系统根据指数函数计算 chunk 分配结果：

```json
{
  "volume": 3,
  "risk_strategy": 2,
  "price": 2,
  "valuation": 1,
  "sentiment": 1,
  "trend": 1
}
```

以 volume 类为例，检索流程如下：

```text
category = volume
chunkCount = 3
requiredTags = [volume_expand, high_turnover]
chunkTags = chunk.metadata.scenes.volume

1. 计算 requiredTags 与 chunkTags 的 jaccard_score
2. 只保留 jaccard_score >= jaccardThreshold 的候选 chunk
3. 生成 queryText = 放量上涨 高换手 成交量持续性 量价关系
4. 计算 queryEmbedding
5. 计算候选 chunk 的 semantic_score
6. 计算 tag_match_score 和 cross_scene_score
7. 计算 final_score
8. 按 final_score 排序
9. 取 Top 3
```

以 risk_strategy 类为例，检索流程如下：

```text
category = risk_strategy
chunkCount = 2
requiredTags = [chase_high_risk, wait_confirm]
chunkTags = chunk.metadata.scenes.risk_strategy

1. 计算 requiredTags 与 chunkTags 的 jaccard_score
2. 只保留 jaccard_score >= jaccardThreshold 的候选 chunk
3. 生成 queryText = 追高风险 等待确认 风险控制 高位放量
4. 计算 queryEmbedding
5. 计算候选 chunk 的 semantic_score
6. 计算 tag_match_score 和 cross_scene_score
7. 计算 final_score
8. 按 final_score 排序
9. 取 Top 2
```

最终构建出的 `knowledgeContext` 可能是：

```json
{
  "knowledgeContext": {
    "volume": [
      {
        "chunkId": 101,
        "scene": "volume",
        "matchedTags": ["volume_expand"],
        "text": "放量上涨后需要观察成交量是否持续，不能只看当天涨幅。"
      }
    ],
    "risk_strategy": [
      {
        "chunkId": 205,
        "scene": "risk_strategy",
        "matchedTags": ["chase_high_risk", "wait_confirm"],
        "text": "接近高位时不要只看涨幅，需要等待确认。"
      }
    ]
  }
}
```

这个结果会作为结构化知识上下文传给 LLM，用于生成最终报告。完整检索结果可以在内部保留 `taskNo`、`chunkIndex` 和各类 score，但不作为 LLM 输入。

---


## 7. 报告生成设计

### 7.1 报告生成输入

LLM 输入不应只是用户问题，而应包含完整结构化上下文：

```text
marketContext
currentScenes
knowledgeContext
```

其中 `marketContext` 表示客观市场事实和压缩后的行情特征，`currentScenes` 表示 Python 计算后的场景标签、解释和计算结果，`knowledgeContext` 表示 Java RAG 召回并重排后的知识库片段。

`chunkAllocation`、`retrievalTasks`、`retrievalEmbeddings`、检索分数和原始任务定位字段属于内部流水线数据，不进入最终 LLM 上下文。

---

### 7.2 marketContext 结构

`marketContext` 由 Python 统一计算并回调 Java，避免 Java 和 Python 对行情、分时、K 线特征各算一套导致口径漂移。

当前代码由 `MarketContextBuilder` 构建，顶层包含：

```text
snapshot
valuation
intraday
klineTrends.daily
klineTrends.weekly
klineTrends.monthly
```

每个数据块统一是：

```json
{
  "meta": {
    "数据范围": "",
    "价格口径": "",
    "用途": "",
    "限制": []
  },
  "data": {}
}
```

其中 `meta` 是给 LLM 的中文数据口径、用途和限制说明，`data` 是可引用的客观事实数值。报告生成 prompt 明确要求优先遵守 `marketContext` 各数据块 `meta.限制`，不得跨数据范围滥用字段，也不得把不同价格口径的数据差异解释成数据错误或时滞。

`marketContext` 只放客观事实、原始数值和压缩后的客观特征，不放场景标签、投资建议、风险判断或“看多/看空”等解释性结论。

示例结构：

```json
{
  "snapshot": {
    "meta": {
      "数据范围": "实时行情快照",
      "价格口径": "原始行情价",
      "用途": "用于描述当前行情快照、当前价、当日涨跌幅、成交额和换手率等实时事实",
      "限制": [
        "不能用于判断日K、周K、月K趋势",
        "不能与复权K线收盘价直接比较"
      ]
    },
    "data": {
      "targetType": "STOCK",
      "targetCode": "000001",
      "targetName": "平安银行",
      "secid": "0.000001",
      "latestPrice": 12.34,
      "changeAmount": 0.56,
      "changePercent": 4.76,
      "openPrice": 11.8,
      "highPrice": 12.5,
      "lowPrice": 11.72,
      "previousClosePrice": 11.78,
      "volume": 123456789,
      "turnoverAmount": 2345678901,
      "turnoverRate": 3.21,
      "amplitude": 6.62,
      "syncedAt": "2026-06-04 14:55:00"
    }
  },
  "valuation": {
    "meta": {
      "数据范围": "股票估值与分红数据",
      "用途": "用于分析PE、PB、股息率和历史估值位置",
      "限制": [
        "不能脱离PE、PB、股息率具体数值直接写估值有吸引力",
        "分红金额和股息率不能混为一谈"
      ]
    },
    "data": {
      "current": {
        "peTtm": 6.8,
        "pbRatio": 0.72,
        "totalMarketValue": 123456789000
      },
      "historySummary": {
        "peTtm": {"latest": 6.8, "count": 120, "percentileRank": 0.32},
        "pbMrq": {"latest": 0.72, "count": 120, "percentileRank": 0.18}
      },
      "dividend": {
        "estimatedDividendYieldPct": 5.1,
        "historySummary": {"yieldPctLatest": 5.1, "count": 5}
      }
    }
  },
  "intraday": {
    "meta": {
      "数据范围": "最近一个交易日分时数据",
      "价格口径": "原始分时行情价",
      "用途": "用于描述盘中价格路径、盘中高低点、开盘至最新分时变化和成交集中度",
      "限制": [
        "不能表述为当前涨跌幅",
        "不能代表日K、周K或月K趋势",
        "不能与复权K线收盘价直接比较"
      ]
    },
    "data": {
      "available": true,
      "window": "latest_trading_day",
      "points": 241,
      "openToLatestPct": 3.8,
      "highTime": "10:34",
      "lowTime": "09:42",
      "latestPositionInDayRange": 0.82,
      "morningReturnPct": 2.1,
      "afternoonReturnPct": 1.6,
      "volumeConcentration": {
        "first30MinPct": 28.4,
        "last30MinPct": 18.2
      },
      "pathFeatures": [
        {
          "startTime": "09:30",
          "endTime": "10:35",
          "direction": "up",
          "returnPct": 3.2,
          "durationMinutes": 65
        }
      ]
    }
  },
  "klineTrends": {
    "daily": {
      "meta": {
        "数据范围": "日K窗口，最近120根日K",
        "周期": "日K",
        "价格口径": "后复权K线价格",
        "用途": "只能用于分析日K趋势结构、区间位置、均线关系和波动特征",
        "限制": [
          "latestClose只能表述为K线后复权收盘价，不能表述为当前价",
          "不能与实时行情快照中的当前价直接比较",
          "不能代表其他K线周期的趋势"
        ]
      },
      "data": {
        "period": "daily",
        "availableBars": 120,
        "startDate": "2025-12-01",
        "endDate": "2026-06-04",
        "latestClose": 12.34,
        "windowHigh": 13.2,
        "windowLow": 9.8,
        "rangePct": 0.34,
        "position": 0.72,
        "returnPct": 18.4,
        "return5Bars": 3.1,
        "return20Bars": 8.6,
        "maxDrawdownPct": -12.6,
        "volatilityPct": 2.1,
        "movingAverages": {
          "ma5": 12.1,
          "ma10": 11.9,
          "ma20": 11.5,
          "ma60": 10.8
        },
        "pathFeatures": {
          "method": "zigzag",
          "reversalThresholdPct": 8,
          "segments": []
        }
      }
    }
  }
}
```

分时线和 K 线不把完整点位数组直接传给 LLM，只传压缩后的摘要、位置、路径和统计特征。趋势标签和解释进入 `currentScenes`，`marketContext.klineTrends.<period>.data` 只保留对应周期的客观 K 线上下文。

---

### 7.3 currentScenes 结构

LLM 输入版 `currentScenes` 只保留 Python 计算后的场景结论和解释依据：`score`、`level`、`direction`、`tags`、`evidence`。如果模块中存在 `periodTrends`，也会保留；当前主要用于 `trend` 模块的日 K、周 K、月 K 分周期分析。

`queryText` 是检索 embedding 使用的内部字段，不进入 LLM 输入。`evidence` 才是给 LLM 和报告解释使用的标签触发依据。

示例：

```json
{
  "price": {
    "score": 0.74,
    "level": "high",
    "direction": "positive",
    "tags": {
      "price_rise": 0.80,
      "near_recent_high": 0.75
    },
    "evidence": [
      "当日涨幅超过上涨强度阈值，price_rise 标签触发",
      "当前价格处于近 20 日区间高位，near_recent_high 标签触发"
    ]
  },
  "volume": {
    "score": 0.82,
    "level": "high",
    "direction": "positive",
    "tags": {
      "volume_expand": 0.90,
      "high_turnover": 0.80
    },
    "evidence": [
      "当前成交量相对 60 日稳健中位水平明显放大，volume_expand 标签触发",
      "当前换手率处于历史分布较高位置，high_turnover 标签触发"
    ]
  },
  "riskStrategy": {
    "score": 0.76,
    "level": "high",
    "direction": "risk",
    "tags": {
      "chase_high_risk": 0.85,
      "wait_confirm": 0.70
    },
    "evidence": [
      "上涨强度、接近近期高位和高换手提高追高风险，chase_high_risk 标签触发",
      "突破、量能或短线情绪信号尚未形成充分确认，wait_confirm 标签触发"
    ]
  }
}
```

如果内部 `currentScenesPayload` 中包含 `queryText`，报告生成前需要裁剪掉该字段。

---

### 7.4 knowledgeContext 结构

示例：

```json
{
  "volume": [
    {
      "chunkId": 101,
      "scene": "volume",
      "text": "放量上涨后需要观察成交量是否持续，不能只看当天涨幅。",
      "matchedTags": ["volume_expand"]
    }
  ],
  "risk_strategy": [
    {
      "chunkId": 205,
      "scene": "risk_strategy",
      "text": "接近高位时不要只看涨幅，需要等待确认。",
      "matchedTags": ["chase_high_risk", "wait_confirm"]
    }
  ],
  "price": [],
  "valuation": [],
  "sentiment": [],
  "trend": []
}
```

LLM 输入版 `knowledgeContext` 不包含 `taskNo`、`chunkIndex`、`semanticScore`、`tagMatchScore`、`crossSceneScore`、`finalScore`。

---

### 7.5 LLM Prompt 设计

当前报告生成使用 `SYSTEM_PROMPT` 和 `userPrompt` 两部分。

`SYSTEM_PROMPT` 核心要求：

```text
你是一个面向个人投资研究的理财分析报告生成助手。
必须基于输入的 marketContext、currentScenes 和 knowledgeContext 生成结构化 JSON 报告。
marketContext 中各数据块统一包含 meta 和 data：meta 是中文数据口径、用途和限制说明，data 是可引用的客观事实数值。
生成报告时必须优先遵守每个 marketContext 数据块 meta.限制，不得跨数据范围滥用字段。
三者边界：marketContext 负责事实，currentScenes 负责系统解释，knowledgeContext 负责可引用知识依据。
可以给出买入、卖出、持有、观望或回避建议，但必须解释依据、适用条件和主要风险。
不要承诺收益，不要编造缺失数据，不要把建议表述为确定性结论。
使用知识库内容时必须引用 chunkId，chunkId 只能来自输入的 knowledgeContext。
报告面向普通用户，不得在输出内容中直接暴露内部标签名、字段名、score 或系统术语。
只输出 JSON object，不要输出 Markdown，不要输出 JSON 之外的解释文本。
```

`userPrompt` 会附加：

```text
1. 输出必须是 JSON object。
2. 引用知识库内容时必须在对应对象里填写 chunkIds。
3. chunkIds 只能来自 allowedChunkIds。
4. 没有知识库依据的判断可以使用空数组 chunkIds: []，但不能编造 chunkId。
5. 可以给出买入、卖出、持有、观望或回避建议，但必须同时说明 reason、conditions 和 risks。
6. 操作建议只能作为研究判断，不得承诺收益，不得使用“必涨”“必跌”“一定买入”等确定性表述。
7. 必须输出 periodTrendAnalysis，且包含 daily、weekly、monthly 三项，分别解释日 K、周 K、月 K 的趋势状态、主要依据和对当前判断的影响。
8. periodTrendAnalysis 必须优先使用 currentScenes.trend.periodTrends 与 marketContext.klineTrends.<period>.data 中对应周期的数据；只能用自然语言表达，不得暴露内部标签名、字段名或 score。
9. 必须遵守 marketContext 各数据块 meta 中的“数据范围”“价格口径”“用途”“限制”；不得跨数据范围滥用字段，不得把不同价格口径的数据差异解释成数据错误、数据时滞或多周期不一致。
10. 不得输出输入中没有数据支持的内容，例如资金出逃、主力撤退、筹码松动；不得把短周期信号升级成长周期结论。
```

---

### 7.6 LLM 输出 JSON 结构

当前 `outputRequirement.recommendedSchema`：

```json
{
  "summary": {"title": "", "conclusion": "", "confidence": "low|medium|high"},
  "marketFacts": [{"fact": "", "source": "marketContext|currentScenes|knowledgeContext", "chunkIds": []}],
  "sceneInterpretation": [{"scene": "", "view": "", "basis": [], "chunkIds": []}],
  "periodTrendAnalysis": [
    {"period": "daily", "title": "日K趋势", "trend": "", "basis": [], "interpretation": "", "chunkIds": []},
    {"period": "weekly", "title": "周K趋势", "trend": "", "basis": [], "interpretation": "", "chunkIds": []},
    {"period": "monthly", "title": "月K趋势", "trend": "", "basis": [], "interpretation": "", "chunkIds": []}
  ],
  "knowledgeBasedAnalysis": [{"scene": "", "point": "", "chunkIds": []}],
  "tradingSuggestions": [{
    "action": "buy|sell|hold|watch|avoid",
    "suggestion": "",
    "reason": "",
    "conditions": [],
    "risks": [],
    "chunkIds": []
  }],
  "riskWarnings": [{"risk": "", "reason": "", "chunkIds": []}],
  "watchPoints": [{"item": "", "reason": "", "chunkIds": []}],
  "missingData": [],
  "conclusion": ""
}
```

当前输出结构中的新增重点：

| 字段 | 当前用途 |
|---|---|
| `periodTrendAnalysis` | 周期趋势分析，必须包含 `daily`、`weekly`、`monthly` 三项。每项说明对应周期的趋势状态、依据和对当前判断的影响。 |
| `tradingSuggestions` | 操作建议，允许输出买入、卖出、持有、观望或回避。每条建议必须包含 `action`、`suggestion`、`reason`、`conditions`、`risks`、`chunkIds`。 |

`tradingSuggestions.action` 当前支持：

| action | 渲染中文 |
|---|---|
| `buy` | 买入 |
| `sell` | 卖出 |
| `hold` | 持有 |
| `watch` | 观望 |
| `avoid` | 回避 |

报告渲染当前读取这些字段生成 Markdown：

| 输出字段 | Markdown 小节 |
|---|---|
| `summary.title` | 报告标题 |
| `conclusion` | 结论 |
| `marketFacts[].fact` | 市场事实 |
| `sceneInterpretation[].view` | 场景解读 |
| `periodTrendAnalysis` | 周期趋势分析 |
| `knowledgeBasedAnalysis[].point` | 知识库分析 |
| `tradingSuggestions` | 操作建议 |
| `riskWarnings[].risk` | 风险提示 |
| `watchPoints[].item` | 观察点 |

---

### 7.7 报告引用依据设计

当前最终报告不单独输出引用关系对象，而是在各报告段落对象中保留 `chunkIds` 数组。报告生成后 Java 会递归校验所有 `chunkIds` 必须来自本次 `allowedChunkIds`，避免模型编造不存在的知识库引用。

```json
{
  "riskWarnings": [
    {
      "risk": "接近高位时需要关注追高风险。",
      "reason": "相关知识库内容提示高位放量后应等待确认。",
      "chunkIds": [205]
    }
  ]
}
```

---

### 7.8 报告保存内容

报告生成前会把本次实际传给 LLM 的业务上下文保存到 `scene_analysis_task.report_payload.llmInput`：

```text
reportPayload.llmInput.marketContext
reportPayload.llmInput.currentScenes
reportPayload.llmInput.knowledgeContext
```

其中：

```text
llmInput.marketContext
  Python 回调的客观市场上下文；缺失时 Java 会兜底最小 snapshot。

llmInput.currentScenes
  从 current_scenes_payload.currentScenes 裁剪而来，不包含 queryText。

llmInput.knowledgeContext
  从 report_payload.knowledgeContext 裁剪而来，不包含 taskNo、chunkIndex 和检索分数。
```

最终报告内容保存到 `scene_analysis_report`：

```text
reportContent
model
createdAt
```

内部检索审计快照可以额外保存：

```text
chunkAllocation
retrievalTasks
retrievalEmbeddings
knowledgeContextInternal
```

保存内容可以比 LLM 输入更完整，但最终传给 LLM 的报告上下文只保留 `marketContext`、`currentScenes`、`knowledgeContext`。

重新生成报告时优先复用 `report_payload.llmInput`，保证 regenerate 使用上一次保存的 LLM 输入快照。只有老任务没有 `llmInput` 时，才从 `current_scenes_payload` 和 `report_payload.knowledgeContext` 重新构建并写回 `llmInput`。

这样后续可以复盘：

```text
当时基于什么行情数据生成？
当时哪些模块分数最高？
当时引用了哪些知识？
```

---

## 8. 数据库设计

### 8.1 knowledge_vector 表设计

推荐字段：

```sql
CREATE TABLE knowledge_vector (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(64),
    chunk_index INTEGER,
    text TEXT NOT NULL,
    embedding VECTOR(512),
    metadata JSONB,
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

### 8.2 knowledge_vector.metadata 设计

示例：

```json
{
  "sourceType": "ocr_note",
  "reviewed": true,
  "taskNo": "ocr-xxx",
  "chunkIndex": 1,
  "scenes": {
    "asset": ["stock"],
    "price": ["price_rise"],
    "volume": ["volume_expand"],
    "trend": ["breakout_from_range"],
    "valuation": [],
    "sentiment": [],
    "risk_strategy": ["wait_confirm", "observe_next_day"]
  },
  "keywords": ["放量", "上涨", "站稳", "第二天观察"],
  "summary": "放量上涨后需要观察次日是否继续放量并站稳。"
}
```

---

### 8.3 scene_analysis_task 表设计

```sql
CREATE TABLE scene_analysis_task (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_code VARCHAR(32) NOT NULL,
    target_name VARCHAR(100),
    report_type VARCHAR(64) NOT NULL,
    config_profile VARCHAR(64) NOT NULL,
    config_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    current_scenes_payload JSONB,
    report_payload JSONB,
    report_text TEXT,
    error_message TEXT,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

### 8.4 scene_analysis_report 表设计

报告历史版本独立保存到 `scene_analysis_report`：

```sql
CREATE TABLE scene_analysis_report (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    task_no VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_code VARCHAR(32) NOT NULL,
    target_name VARCHAR(100),
    report_type VARCHAR(64) NOT NULL,
    generation_type VARCHAR(32) NOT NULL,
    version_no INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'generating_report',
    report_content JSONB,
    report_text TEXT,
    model VARCHAR(100),
    error_message TEXT,
    generated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

`generation_type` 当前支持 `initial` 和 `regenerate`。同一任务下 `version_no` 从 1 递增。

---

### 8.5 scene_analysis_config_profile 表设计

报告配置档保存到 `scene_analysis_config_profile`，系统默认配置为 `system_recommended`：

```sql
CREATE TABLE scene_analysis_config_profile (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    name VARCHAR(100) NOT NULL,
    config_group VARCHAR(100) NOT NULL DEFAULT '默认',
    config_profile VARCHAR(64) NOT NULL,
    target_type VARCHAR(32),
    report_type VARCHAR(64) NOT NULL DEFAULT 'quick_analysis',
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    system_default BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

### 8.6 报告引用关系存储

当前实现把内部引用依据存入 `report_payload.knowledgeContext`，把 LLM 输入版引用依据存入 `report_payload.llmInput.knowledgeContext`，最终报告引用存入 `reportContent` 的 `chunkIds` 字段。报告生成阶段会校验 `chunkIds` 必须来自本次实际 LLM 输入的 `knowledgeContext`，防止模型编造不存在的知识库引用。

如果后续需要单独统计引用频率，可以新增引用关系表。

---

### 8.7 索引设计建议

metadata 可以加 GIN 索引：

```sql
CREATE INDEX idx_knowledge_vector_metadata
ON knowledge_vector USING GIN (metadata);
```

如果按 scenes 高频检索，可以考虑额外拆字段或建立表达式索引。

---

## 9. 服务类设计

### 9.1 Chunk 入库相关服务

```text
ChunkBuildService
负责根据人工复核后的 paragraphs 构建 chunk。

ChunkSceneTaggingService
负责为 chunk 生成 7 大类 scenes、keywords、summary。

ChunkEmbeddingService
负责生成 embedding。

KnowledgeVectorManage
负责 knowledge_vector 的保存、删除、查询。
```

---

### 9.2 七个场景模块服务

```text
AssetSceneModule
PriceSceneModule
VolumeSceneModule
TrendSceneModule
ValuationSceneModule
SentimentSceneModule
RiskStrategySceneModule
```

---

### 9.3 场景分析服务

```text
Python current_scene_handler
消费 scene.analysis.current 消息，调用各场景处理器，生成 currentScenes。

SceneAnalysisTaskController.callback
接收 Python 回调，保存 currentScenesPayload，并继续 Java 侧检索流程。
```

---

### 9.4 检索召回服务

```text
SceneReportPipelineServiceImpl.allocateChunks
根据七大类得分和指数函数计算每类 chunkCount。

SceneReportPipelineServiceImpl.buildRetrievalTasks
根据 chunkAllocation 生成每类检索任务。

Python retrieval_embedding_handler
消费 retrievalTasks，为每个 queryText 生成 queryEmbedding，并回调 Java。

KnowledgeVectorManage / KnowledgeVectorMapper
按 scene 和 queryEmbedding 使用 pgvector 生成语义候选。

SceneReportPipelineServiceImpl.retrieveKnowledge
按类别执行标签过滤、embedding 相似度计算和类内重排。

SceneReportPipelineServiceImpl.continueWithRetrievalEmbeddings
按类别组织最终 knowledgeContext。
```

---

### 9.5 报告生成服务

```text
SceneAnalysisReportGenerationServiceImpl
初次生成时组装 marketContext、currentScenes、knowledgeContext，写入 report_payload.llmInput，并调用 LLM 生成报告。
重新生成时优先读取 report_payload.llmInput 直接调用 LLM；老任务缺失 llmInput 时才兼容性重建。

SceneAnalysisReportManage
负责报告存储和查询。

SceneAnalysisTaskController
提供任务提交、回调、报告轮询、重新生成、历史列表和详情接口。

SceneAnalysisReportQueryServiceImpl
提供报告目标分页、单标的历史版本和报告详情查询。
```

---

### 9.6 Controller 接口设计

当前接口：

```http
POST /api/ai/scene-analysis/tasks
POST /api/ai/scene-analysis/tasks/{taskNo}/callback
GET /api/ai/scene-analysis/tasks/{taskNo}/report
POST /api/ai/scene-analysis/tasks/{taskNo}/report/regenerate
GET /api/ai/scene-analysis/tasks/reports/targets
GET /api/ai/scene-analysis/tasks/reports?targetType=STOCK&targetCode=000001
GET /api/ai/scene-analysis/tasks/reports/{reportId}
GET /api/ai/scene-analysis/targets/search
GET /api/ai/scene-analysis/config-profiles
GET /api/ai/scene-analysis/config-profiles/parameter-schema
GET /api/ai/scene-analysis/config-profiles/report-types
POST /api/ai/scene-analysis/config-profiles
PUT /api/ai/scene-analysis/config-profiles/{id}
DELETE /api/ai/scene-analysis/config-profiles/{id}
```

任务状态流转：

```text
pending
  ↓
processing_current_scenes
  ↓
current_scenes_ready
  ↓
retrieving_knowledge
  ↓
generating_report
  ↓
success / failed
```

---

## 10. 分阶段落地计划

### 10.1 第一阶段：Chunk 入库标签化（已完成）

```text
1. 为 knowledge_vector.metadata 增加 scenes。
2. OCR 复核入库时生成 chunk scenes。
3. 保存 keywords 和 summary。
4. 保证每个 chunk 有完整 metadata。
```

---

### 10.2 第二阶段：七个场景模块打分（已完成）

```text
1. 实现七个场景模块。
2. 每个模块输出 score、tags、evidence。
3. 实现 currentScenes 结构。
4. 将 currentScenes 保存到 scene_analysis_task.current_scenes_payload。
```

---

### 10.3 第三阶段：动态 Chunk 分配（已完成）

```text
1. 实现指数函数分配公式。
2. 使用当前代码常量 alpha = 6.0。
3. 支持 reportType 权重。
4. 使用当前代码常量 categoryScoreThreshold、min、max 限制。
5. 输出每类 chunkCount。
```

---

### 10.4 第四阶段：分类检索与类内重排（已完成）

```text
1. 按类别生成检索任务。
2. 根据 requiredTags 和 chunk.metadata.scenes 中对应类别的 chunkTags 计算 Jaccard similarity。
3. 只保留 jaccard_score >= jaccardThreshold 的候选 chunk。
4. 按类别生成 queryText。
5. 计算 semantic_score。
6. 计算 tag_match_score 和 cross_scene_score。
7. 每类按 final_score 排序后取 TopN。
```

---

### 10.5 第五阶段：结构化报告生成（已完成）

```text
1. 构建 currentScenesPayload 和 reportPayload。
2. 构建 currentScenes。
3. 构建 knowledgeContext。
4. 调用 DeepSeek 输出 JSON 报告。
5. 保存 scene_analysis_report、渲染文本、版本号、模型和引用依据。
```

---

### 10.6 第六阶段：前端展示与引用溯源（部分完成）

```text
1. 已支持报告目标分页、生成、轮询、历史版本、详情和重新生成。
2. 已支持报告内容展示和报告状态展示。
3. 待增强：展开展示各模块 score、chunkAllocation 和 retrievalTasks。
4. 待增强：展示知识库引用详情。
5. 待增强：支持点击 chunk 查看原始 OCR 复核内容。
```

---

### 10.7 第七阶段：参数优化与覆盖范围扩展

```text
1. 优化 alpha、categoryScoreThreshold、jaccardThreshold、semantic/tag/cross-scene 权重等计算参数。
2. 扩展参数考虑范围，纳入更多行情、估值、成交、波动、财务、行业和标的类型特征。
3. 针对不同 reportType 和 targetType 维护差异化参数配置。
4. 增加参数调整后的对比样例，观察召回结果、报告重点和风险提示变化。
```

---

### 10.8 第八阶段：报告准确度测试与可用度评估

```text
1. 建立报告准确度测试集，覆盖股票、指数、可转债和不同市场状态。
2. 评估报告结论是否和输入数据、currentScenes、knowledgeContext 一致。
3. 检查 chunkIds 引用是否准确、是否存在无依据推断或过度结论。
4. 通过历史复盘验证报告风险提示、观察点和结论表达是否有实际参考价值。
```

---

### 10.9 第九阶段：外部数据源增强

```text
1. 尝试接入新闻、公告、政策、行业动态和机构观点等外部数据。
2. 为外部文本数据建立来源、发布时间、可信度和适用标的等元数据。
3. 将外部数据纳入 currentScenes 和 knowledgeContext，但需要区分事实、观点和推断。
4. 在报告中明确标注外部信息来源和时效性，增强报告可用度。
```

---

### 10.10 第十阶段：可转债报告扩展

```text
1. 扩展报告生成对 CONVERTIBLE_BOND 的完整覆盖。
2. 增加可转债专属分析维度：转股溢价率、纯债价值、到期收益率、评级、赎回/回售条款和正股联动。
3. 为可转债补充差异化 reportType 权重和 scene 参数。
4. 优化可转债报告展示，避免沿用股票口径导致结论失真。
```

---

## 11. 最终总结

本设计的核心不是：

```text
把知识库内容塞给 LLM。
```

而是：

```text
知识入库时先打标签；
报告生成时当前标的也打标签和得分；
根据 7 大类得分动态分配每类 chunk 数量；
按类别检索；
按类别重排；
按类别组织知识上下文；
最后让 LLM 生成结构化报告。
```

最终链路是：

```text
Chunk 入库标签化
  ↓
当前标的场景分析
  ↓
7 大类得分
  ↓
指数函数动态分配 chunk 数量
  ↓
分类检索
  ↓
类内重排
  ↓
Grouped Knowledge Context
  ↓
结构化报告生成
```

相比普通 RAG，这种方式的优势是：

```text
1. 检索更贴合当前行情状态。
2. 高分场景能获得更多知识引用比例。
3. 报告重点会随市场场景动态变化。
4. 可以清晰区分成交量、风险、价格、估值、情绪等不同知识来源。
5. 后续可以自然扩展到复盘、预警和个人投资记忆系统。
```
