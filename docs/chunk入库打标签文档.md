

# Chunk 入库打标签流程设计

## 1. 当前入库主流程

当前知识库入库流程修改为：

```text
格式校验
  ↓
OCR 识别
  ↓
文本清洗
  ↓
质量校验(同时切分chunk)
  ↓
规则标签（规则标签质量判断）
  ↓
LLM 标签
  ↓
标签回正
  ↓
向量入库
```

其中，系统在质量校验通过后会自动切分 chunk。每个 chunk 在生成 embedding 和写入向量库之前，需要先完成标签生成和标签回正。

最终入库时，正式用于检索的是 `metadata.scenes` 中的最终标签结果。

---

## 2. 标签生成整体思路

chunk 标签生成不直接完全依赖 LLM，而是拆成三个阶段：

```text
规则标签
  ↓
LLM 标签（可选）
  ↓
标签回正
```

### 2.1 规则标签

规则标签用于识别明确、稳定、可通过关键词或简单规则判断的场景标签。

例如：

```text
出现“可转债 / 转债 / 溢价率 / 强赎”
→ asset: convertible_bond
```

```text
出现“放量 / 爆量 / 成交量放大”
→ volume: volume_expand / volume_spike
```

```text
出现“不要追高 / 追高风险 / 涨多了别追”
→ risk_strategy: chase_high_risk
```

规则标签的作用是稳定命中明显信号，并减少不必要的 LLM 调用。

### 2.2 LLM 标签

LLM 标签用于理解规则难以判断的语义场景。

例如：

```text
价格突然拉起来以后，不要马上判断趋势已经成立，最好看第二天能不能继续站住。
```

这类文本虽然不一定直接出现“突破”“等待确认”等标准词，但语义上可以对应：

```json
{
  "price": ["price_rise"],
  "trend": ["breakout"],
  "risk_strategy": ["wait_confirm", "observe_next_day"]
}
```

LLM 标签生成时，需要把 3.8 中定义的标签白名单和判断规则放入 prompt 中。LLM 只能从白名单中选择标签，不能创造新标签。

### 2.3 标签回正

标签回正是所有标签进入向量库前的统一出口。

无论是否调用 LLM，最终都必须经过标签回正。

标签回正主要负责：

```text
1. 合并规则标签和 LLM 标签
2. 删除不在白名单中的标签
3. 将中文标签或近义标签映射回标准标签
4. 去重
5. 每个大类最多保留 3 个标签
6. 处理冲突标签
7. 补齐 7 大类空数组
8. 生成最终 metadata.scenes
```

最终检索只使用标签回正后的 `metadata.scenes`，不直接使用 `ruleScenes` 或 `llmScenes`。

---

## 3. 是否调用 LLM 的判断系统

规则标签生成后，系统需要判断当前规则标签结果是否已经足够可靠。

如果规则标签结果质量足够，则可以不调用 LLM，直接进入标签回正。

如果规则标签结果质量不足，则需要调用 LLM 标签，再进入标签回正。

该判断系统可以称为：

```text
RuleTagQualityGate
```

整体流程为：

```text
chunk
  ↓
规则标签 ruleScenesWithConfidence
  ↓
RuleTagQualityGate 判断规则标签质量
  ↓
是否需要 LLM？
    ├─ 否：ruleScenes → 标签回正 → finalScenes → 入库
    └─ 是：ruleScenes + llmScenes → 标签回正 → finalScenes → 入库
```

---

## 4. 规则标签质量判断指标

规则标签质量判断主要看两个方面：

```text
1. 覆盖率
2. 置信度
```

### 4.1 覆盖率

系统一共有 7 个大类标签：

```text
asset
price
volume
trend
valuation
sentiment
risk_strategy
```

覆盖率用于判断规则标签是否覆盖了足够多的场景类别。

初步规则：

```text
7 个大类中，至少需要有 3 个大类被打上标签，才认为规则标签覆盖率较好。
```

例如：

```json
{
  "asset": ["stock", "low_price_stock"],
  "price": ["price_rise", "breakout"],
  "volume": ["volume_expand", "high_turnover"],
  "trend": [],
  "valuation": [],
  "sentiment": [],
  "risk_strategy": []
}
```

该结果命中了 `asset`、`price`、`volume` 三个大类，覆盖率满足要求。

如果规则标签只命中 1～2 个大类，则认为覆盖率不足，需要进入 LLM 标签阶段。

### 4.2 置信度

规则标签需要为每个命中的标签或每个命中的大类提供置信度。

例如：

```json
{
  "asset": {
    "stock": 0.85,
    "low_price_stock": 0.95
  },
  "volume": {
    "volume_expand": 0.92,
    "high_turnover": 0.90
  },
  "risk_strategy": {
    "observe_next_day": 0.88,
    "wait_confirm": 0.82
  }
}
```

每个被打上标签的大类都需要计算一个大类置信度。

第一版可以使用简单规则：

```text
大类置信度 = 该大类下所有标签置信度的最大值
```

例如：

```text
asset 置信度 = max(0.85, 0.95) = 0.95
volume 置信度 = max(0.92, 0.90) = 0.92
risk_strategy 置信度 = max(0.88, 0.82) = 0.88
```

判断规则：

```text
凡是被打上标签的大类，其大类置信度不能低于设定阈值。
```

如果某个大类置信度低于阈值，则说明规则判断不够稳定，需要调用 LLM 进行语义补充。

---

## 5. 第一版是否调用 LLM 的判断规则

第一版可以采用以下判断逻辑：

```text
如果满足以下两个条件，则不调用 LLM，直接进入标签回正：

1. 覆盖率满足要求：7 个大类中至少 2 个大类被打上标签。
2. 置信度满足要求：所有被打上标签的大类置信度都不低于设定阈值。

否则，调用 LLM 标签。
```

可以写成伪代码：

```java
boolean needLlm(RuleTagResult ruleTagResult) {
    int coveredCategoryCount = countNonEmptyCategories(ruleTagResult);

    if (coveredCategoryCount < 2) {
        return true;
    }

    for (CategoryTagResult category : ruleTagResult.getCategories()) {
        if (category.isEmpty()) {
            continue;
        }

        if (category.getCategoryConfidence() < confidenceThreshold) {
            return true;
        }
    }

    return false;
}
```

其中：

```text
coveredCategoryCount：被打上标签的大类数量
confidenceThreshold：大类置信度阈值，例如 0.70 或 0.75
```

---

## 6. 判断结果结构

RuleTagQualityGate 不建议只返回 true / false，而是返回一个完整的判断结果，方便后续排查。

示例：

```json
{
  "needLlm": true,
  "reason": "LOW_COVERAGE",
  "coveredCategoryCount": 2,
  "confidenceThreshold": 0.75,
  "lowConfidenceCategories": []
}
```

或者：

```json
{
  "needLlm": true,
  "reason": "LOW_CONFIDENCE",
  "coveredCategoryCount": 4,
  "confidenceThreshold": 0.75,
  "lowConfidenceCategories": ["sentiment"]
}
```

如果规则标签质量足够：

```json
{
  "needLlm": false,
  "reason": "RULE_TAGS_CONFIDENT_ENOUGH",
  "coveredCategoryCount": 3,
  "confidenceThreshold": 0.75,
  "lowConfidenceCategories": []
}
```

---

## 7. 标签回正输入来源

标签回正阶段需要兼容两种输入情况。

### 7.1 未调用 LLM

如果规则标签质量足够，则标签回正只接收规则标签：

```text
ruleScenes
  ↓
标签回正
  ↓
finalScenes
```

### 7.2 调用了 LLM

如果规则标签质量不足，则标签回正同时接收规则标签和 LLM 标签：

```text
ruleScenes + llmScenes
  ↓
标签回正
  ↓
finalScenes
```

不管是哪种情况，最终只生成一份标准标签结果：

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

---

## 8. metadata 保存建议

最终正式用于检索的是 `metadata.scenes`。

为了方便调试，可以在 metadata 中临时保留规则标签、LLM 标签和判断结果。

示例：

```json
{
  "sourceType": "ocr_note",
  "reviewed": true,
  "taskNo": "ocr-xxx",
  "chunkIndex": 1,
  "scenes": {
    "asset": ["stock", "low_price_stock"],
    "price": ["price_rise", "breakout"],
    "volume": ["volume_expand", "high_turnover"],
    "trend": [],
    "valuation": [],
    "sentiment": [],
    "risk_strategy": ["chase_high_risk", "wait_confirm", "observe_next_day"]
  },
  "keywords": ["低价股", "放量上涨", "换手率", "追高", "次日确认"],
  "summary": "低价股放量上涨后，需要关注换手率和次日确认。",
  "tagging": {
    "ruleScenes": {
      "asset": ["low_price_stock"],
      "volume": ["volume_expand", "high_turnover"],
      "risk_strategy": ["observe_next_day"]
    },
    "llmScenes": {
      "asset": ["stock", "low_price_stock"],
      "price": ["price_rise", "breakout"],
      "risk_strategy": ["chase_high_risk", "wait_confirm", "observe_next_day"]
    },
    "qualityGate": {
      "needLlm": true,
      "reason": "LOW_COVERAGE",
      "coveredCategoryCount": 2,
      "confidenceThreshold": 0.75,
      "lowConfidenceCategories": []
    },
    "tagVersion": "v1.0"
  }
}
```

稳定后，也可以只保留最终的 `scenes`，将 `ruleScenes`、`llmScenes` 和 `qualityGate` 作为日志记录，而不长期保存在 metadata 中。

---

## 9. 最终总结

新版 chunk 入库打标签流程为：

```text
质量校验
  ↓
规则标签
  ↓
规则标签质量判断
      ├─ 覆盖率不足 → 调用 LLM
      ├─ 置信度不足 → 调用 LLM
      └─ 覆盖率和置信度都满足 → 不调用 LLM
  ↓
标签回正
  ↓
生成 finalScenes
  ↓
embedding(chunk.text)
  ↓
knowledge_vector 入库
```

其中：

```text
规则标签负责稳定命中明显信号；
LLM 标签负责补充复杂语义；
规则标签质量判断负责决定是否需要调用 LLM；
标签回正负责生成最终可入库、可检索的标准 scenes。
```