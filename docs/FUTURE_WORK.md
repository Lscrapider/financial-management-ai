# Future Work

本文档记录后续能力建设方向。这里先保留讨论结论和待确认问题，具体实现方案确认后再同步到对应设计文档。

## 1. 增强趋势上下文能力（Trend Context）

### 目标

提升投资报告对价格趋势、阶段位置和趋势变化的理解能力，减少直接把完整 K 线交给 LLM 导致的信息冗余、解释漂移和上下文浪费。

目标链路：

```text
Market Facts
↓
Trend Path Features
↓
Trend Context
↓
Knowledge
↓
LLM
```

### 边界原则

`market` 只保留元数据、客观事实和原始数值。

`currentScene` 负责保存标签、解释和计算后的结果。

`dailyKlines` 保持统一窗口，当前按 120 天处理。

不把完整 120 天走势直接给 LLM，而是先压缩成趋势路径特征，再进入报告上下文。

### 需要扩展的 trend scene

候选趋势上下文标签：

- `bull_continuation`：多头延续。
- `pullback_repair`：回调修复。
- `bear_rebound`：空头反弹。
- `bottom_stabilization`：底部企稳。
- `trend_exhaustion`：趋势衰竭。

### 趋势路径特征

趋势路径特征需要保留：

- 关键拐点。
- 每段斜率。
- 每段涨跌幅。
- 每段持续时间。

建议进一步补充：

- 当前价格相对 120 日高点和低点的位置。
- 最近一段趋势方向和强度。
- 最大回撤和回撤修复比例。
- 最近上涨段和下跌段的持续时间对比。
- 短期、中期、120 天趋势是否一致。

### 拐点检测方案

拐点检测使用 ZigZag，不使用简单局部极值。

ZigZag 以反向波动确认关键拐点，避免把普通噪声识别为趋势变化。

初始可讨论参数：

- `reversalThresholdPct`：反向确认阈值，例如 5%、8%、10%。
- `minSegmentDays`：最小趋势段天数，例如 3 天或 5 天。

### 建议的落地顺序

1. 先定义 `Trend Path Features` 输出结构。
2. 再实现 ZigZag 拐点检测和趋势段计算。
3. 然后基于特征计算一级趋势状态。
4. 最后扩展 `bull_continuation`、`pullback_repair` 等细分标签。

### 待讨论问题

- 120 天窗口是否对股票、指数、可转债都统一使用。
- ZigZag 默认阈值是否需要按标的类型区分。
- trend scene 是单独作为新模块，还是合并进当前 `currentScene` 的趋势类标签。
- 细分标签是否需要输出置信度和 evidence。
- 可转债趋势是否需要同时参考正股走势。

## 2. 报告质量增强

- 优化报告计算参数和不同 `reportType` / `targetType` 的权重配置。
- 扩展参数考虑范围，纳入更多财务、行业、成交、波动和标的类型特征。
- 建立报告准确度测试集和历史复盘评估机制。

## 3. marketBrief 上下文压缩

### 目标

后续可考虑增加 `marketBrief` 阶段，由第一步 LLM 将 `marketContext + currentScenes` 忠实压缩成自然语言市场简报，再把该简报作为最终报告生成的市场上下文。

该阶段的目标不是增加最终报告 LLM 的输入，而是替换原始结构化大 JSON 输入，降低字段口径理解成本、减少 token 占用，并降低报告中出现价格口径混用、分时和 K 线混写、估值事实缺失等问题的概率。

### 边界原则

- 第一阶段 LLM 只做忠实转述和上下文压缩，不做投资建议、不引入知识库、不新增判断。
- `marketBrief` 必须保留关键数值和口径限制，例如当前价、涨跌幅、换手率、分时变化、日/周/月 K 趋势、PE、PB、估算股息率和主要场景方向。
- `marketBrief` 不应覆盖原始 `marketContext` 或 `currentScenes`；原始结构化结果仍保留用于审计、排查和重新生成。
- 最终报告 LLM 可改为消费 `marketBrief + knowledgeContext`，不再直接消费完整 `marketContext + currentScenes`。
- `evidence` 不立即废弃，继续作为规则计算审计依据和生成 `marketBrief` 的原材料。

### RAG 召回方向

如果 `marketBrief` 质量稳定，后续可评估用 `marketBrief` 的分段自然语言替代当前 `currentScenes.queryText` 参与 RAG 召回。

建议分阶段推进：

1. 保留现有 `queryText` 和 RAG 流程，仅新增 `marketBrief` 实验链路。
2. 对比 `queryText` 召回和 `marketBrief` 召回的 chunk 命中质量。
3. 稳定后再考虑让 `marketBrief.sections[].retrievalQuery` 替代部分或全部 `queryText`。
4. `evidence` 继续保留为规则标签计算的可解释输出，不作为优先删除对象。

### 待讨论问题

- `marketBrief` 是否使用结构化 section，例如 `snapshot`、`intraday`、`dailyTrend`、`weeklyTrend`、`monthlyTrend`、`valuation`、`risk`。
- 第一阶段 LLM 的输出是否需要质量校验，防止遗漏关键数值或引入无数据支持表达。
- `marketBrief` 存储位置是 `reportPayload`、独立表，还是仅作为报告生成过程中的临时上下文。
- 是否需要支持 A/B 对比：原始 `queryText` 召回、`marketBrief` 召回、混合召回。

## 4. 外部数据源增强

- 尝试接入新闻、公告、政策、行业动态和机构观点数据。
- 为外部数据建立来源、发布时间、可信度和适用标的 metadata。
- 在报告中区分事实、观点、模型推断和风险提示。

## 5. 可转债报告深化

- 完善可转债估值、转股溢价、纯债价值、评级、赎回/回售条款和正股联动分析。
- 为可转债维护差异化场景参数和展示结构。

## 6. 引用溯源与复盘系统

- 报告引用支持点击 chunk 回到 OCR 原文和页面图片。
- 把分析报告转成复盘记录，后续填写结果并统计常见判断问题。

## 7. 智能预警和个性化投资记忆

- 预警触发后自动生成分析报告。
- 支持多条件组合预警、相似历史场景检索、交易前 checklist 和常见错误提醒。
