# AI Agent Tool Calling RAG 设计规格

## 背景

当前 AI Agent 已经具备有限多轮 Tool Calling 能力，Java 侧通过内部数据网关控制业务数据边界，Python 侧负责 LangChain Agent、工具执行和答案生成。现有场景分析报告链路已经具备一套成熟的投资场景标签能力：

- Java 侧 `SceneTargetDataProvider` 负责按标的拼装发送给 Python 场景处理器的数据包。
- Python 侧 `BaseMetricsCalculator`、`SceneAnalysisContext` 和七类场景处理器负责计算场景分数、标签和 evidence。
- 知识库 chunk 入库后，`knowledge_vector.metadata.scenes` 已保存同一套七大场景和子标签。
- 报告流程已有基于语义相似度、标签匹配和重排的 RAG 思路，但它服务于异步报告任务，不适合直接嵌入同步 Agent Tool Calling。

本次设计目标是把现有标签体系接入 Agent RAG，让 Agent 能先识别当前问题需要哪些场景，再复用现有数据和 Python 处理器得到当前标的的事实标签，最后用 `queryText + scenes + tags` 做知识库召回。

## 目标

1. 新增 Agent 可调用的场景信号工具，让 LLM 能请求指定场景的处理器结果。
2. Java 复用现有 `SceneTargetDataProvider.buildMessage()` 拼装处理器输入数据，不创建报告任务，不发布 RabbitMQ 消息。
3. Python Agent 复用现有场景处理器直接计算标签和 evidence，不新增第二套标签计算口径。
4. 场景处理器依赖关系在单次工具调用内复用，工具调用结束后不保留计算结果。
5. 新增知识库召回工具，按 `queryText + scenes + tags` 检索知识库。
6. 返回给 LLM 的知识库片段必须极简，只包含文件名和 chunk 内容。
7. Java 继续作为数据访问边界，Python 和 LLM 不直接访问数据库。

## 不做范围

- 不重跑完整场景分析报告流程。
- 不创建 `scene_analysis_task`。
- 不通过 RabbitMQ 异步计算 Agent 场景信号。
- 不把七大场景白名单做成单独工具；白名单通过 prompt、tool schema 或 enum 约束。
- 不让 LLM 自由创造场景或标签。
- 不把 RAG 召回分数、chunkId、taskNo、matchedTags、finalScore 暴露给 LLM。
- 不把场景计算元参数暴露给 LLM，包括 `config.parameters`、`userOverrides`、base metrics、原始 marketContext 和原始行情序列。
- 不把场景处理器结果做进程级、会话级或长期内存缓存。
- 不改知识库 chunk 入库打标签流程。

## 总体流程

```text
用户问题
  -> LLM 基于问题选择 scenes
  -> Python scene_signal_context tool
      -> Java data gateway action = scene.signal_data
          -> 复用 SceneTargetDataProvider.buildMessage()
          -> 返回 SceneAnalysisMessageDTO 数据包
      -> Python 复用现有 scene_analysis processors
      -> 单次工具调用内处理依赖和结果复用
      -> 只返回请求 scenes 的处理器结果
  -> LLM 基于用户问题和 scene signals 生成 queryText + scenes + tags
  -> Python knowledge_search tool
      -> Python 使用现有 embedding model 生成 queryEmbedding
      -> Java data gateway action = knowledge.search
          -> Java 校验 session、scope、limit、scenes、tags
          -> Java 查询 knowledge_vector
          -> Java 结合语义分、同场景标签匹配和交叉场景标签命中重排
      -> Python 返回极简 chunks 给 LLM
  -> LLM 基于 chunks 生成最终回答
```

## Java 侧设计

### scene.signal_data

新增 Agent Data Gateway action：

```text
action = scene.signal_data
```

职责：

- 校验 Agent session 和 scope。
- 从参数中读取 `targetType`、`targetCode`、`targetName`、`totalChunks`、K 线 limit 等可选项。
- 校验 `totalChunks` 由 Agent 控制，默认 6，最大不超过 10，最小为 1。
- 复用现有 `SceneTargetDataProvider` 列表按 `targetType` 找 provider。
- 调用 `buildMessage(taskNo, targetCode, SceneAnalysisSubmitParam)` 拼装 `SceneAnalysisMessageDTO`。
- 不保存 `SceneAnalysisTaskPO`。
- 不发布 `publishCurrentSceneAnalysisMessage`。
- 不触发报告流程 callback。

参数建议：

```json
{
  "targetType": "STOCK",
  "targetCode": "002958",
  "targetName": "青农商行",
  "totalChunks": 6,
  "dailyKlineLimit": 90,
  "weeklyKlineLimit": 52,
  "monthlyKlineLimit": 60
}
```

`totalChunks` 由 Agent 根据本轮需要控制，用于影响后续场景信号和 RAG 召回的知识片段预算。Java 侧必须做上限保护，最大值为 `10`；未传时使用默认值 `6`。

返回：

```json
{
  "rows": [
    {
      "taskNo": "agent-scene-signal-...",
      "reportType": "quick_analysis",
      "totalChunks": 6,
      "target": {},
      "config": {},
      "marketData": {},
      "valuationData": {},
      "industryData": {},
      "valuationHistory": [],
      "financialIndicators": [],
      "dividendHistory": [],
      "dailyKlines": [],
      "weeklyKlines": [],
      "monthlyKlines": [],
      "intradayData": [],
      "assetSpecificData": {},
      "dataCompleteness": {}
    }
  ]
}
```

### knowledge.search

新增 Agent Data Gateway action：

```text
action = knowledge.search
```

职责：

- 校验 Agent session 和 scope。
- 校验 `queryText` 非空。
- 校验 `queryEmbedding` 维度符合 `knowledge_vector.embedding`。
- 校验 `scenes` 和 `tags` 只能来自现有白名单。
- 限制 `limit`，默认 5，最大不超过 8。
- 查询 `knowledge_vector`，排除 `metadata.deleted = true` 的 chunk。
- 结合语义相似度、同场景标签匹配和交叉场景标签命中重排。
- 综合分沿用报告流程权重：`finalScore = 0.45 * semanticScore + 0.45 * tagMatchScore + 0.10 * crossSceneScore`。
- `crossSceneScore` 表示 chunk 除主命中场景外，还命中其他请求场景标签的程度；每多命中一个其他场景加 `0.1`，最高 `0.3`。
- 返回给 Python 的结果可以包含内部字段，但 Python tool 返回给 LLM 时必须裁剪。

参数建议：

```json
{
  "queryText": "低PB高股息银行股是否可能是价值陷阱，以及弱趋势下如何控制仓位",
  "queryEmbedding": [0.01, 0.02],
  "scenes": ["valuation", "risk_strategy"],
  "tags": {
    "valuation": ["low_pb", "high_dividend", "valuation_trap"],
    "risk_strategy": ["position_control", "risk_control"]
  },
  "limit": 5
}
```

Java 内部返回可包含：

```json
{
  "filename": "低估值投资方法.pdf",
  "content": "低估值不等于低风险，需要结合盈利质量、趋势确认和分红可持续性判断...",
  "chunkId": 123,
  "taskNo": "ocr-...",
  "semanticScore": 0.82,
  "tagMatchScore": 0.5,
  "crossSceneScore": 0.1,
  "finalScore": 0.74
}
```

其中 `chunkId`、`taskNo`、score、matchedTags 只允许进入 trace、日志或调试视图，不进入 LLM tool result。

## Python Agent 侧设计

### scene_signal_context tool

工具签名建议：

```python
scene_signal_context(
    target_type: str,
    target_code: str | None = None,
    target_name: str | None = None,
    scenes: list[str] | None = None,
    total_chunks: int = 6,
) -> str
```

工具职责：

- 读取 LLM 传入的 `scenes`。
- 读取 LLM 传入的 `total_chunks`，并交给 Java `scene.signal_data` 做最终上限校验。
- 调用 Java `scene.signal_data` 获取处理器输入包。
- 使用现有 `BaseMetricsCalculator` 和 `SceneAnalysisContext` 构建上下文。
- 直接调用现有处理器，不新增标签算法。
- 根据依赖关系预跑必要处理器，并在单次工具调用内复用结果。
- 只返回 LLM 请求的场景，不返回依赖场景，除非依赖场景也在请求列表里。
- 返回结构直接复用现有处理器 payload 形态，不二次改写标签、证据、分数或 queryText。
- 不返回计算元参数、原始输入包或完整行情序列。

### 场景依赖复用

依赖表：

```python
SCENE_DEPENDENCIES = {
    "asset": [],
    "price": [],
    "volume": [],
    "trend": [],
    "sentiment": [],
    "valuation": ["trend"],
    "risk_strategy": ["trend", "valuation", "sentiment"],
}
```

执行规则：

- `requested_scenes` 来自 LLM。
- 先校验场景白名单。
- 根据依赖表拓扑展开执行顺序。
- 每个处理器结果保存在本次 tool invocation 的局部 `results` 字典。
- 同一场景在一次工具调用内最多计算一次。
- 工具函数返回后，`results` 不再被引用，不保留进程级或会话级缓存。

示例：

```text
requested = ["risk_strategy"]
run order = ["trend", "valuation", "sentiment", "risk_strategy"]
return scenes = ["risk_strategy"]
```

```text
requested = ["valuation", "risk_strategy"]
run order = ["trend", "valuation", "sentiment", "risk_strategy"]
return scenes = ["valuation", "risk_strategy"]
```

处理器调用关系：

```python
results["trend"] = TrendProcessor().process(context)

results["valuation"] = ValuationProcessor().process(
    context,
    results["trend"].tags,
)

results["sentiment"] = SentimentProcessor().process(context)

results["risk_strategy"] = RiskStrategyProcessor().process(
    context,
    results["trend"].tags,
    results["valuation"].tags,
    results["sentiment"].tags,
)
```

返回给 LLM 的结构应直接复用 `current_scene_result._module_payload()` 的字段形态。工具只做 requested scene 过滤，不改写单个场景模块的 payload。

```json
{
  "sceneSignals": {
    "valuation": {
      "score": 0.82,
      "level": "high",
      "direction": "positive",
      "tags": {
        "low_pb": 0.92,
        "high_dividend": 0.76
      },
      "evidence": ["PB处于历史低位", "股息率较高"],
      "queryText": "估值分析，低市净率、高股息。PB处于历史低位，股息率较高。"
    },
    "risk_strategy": {
      "score": 0.74,
      "level": "high",
      "direction": "risk",
      "tags": {
        "position_control": 0.81,
        "risk_control": 0.66
      },
      "evidence": ["低估值仍需结合趋势和盈利质量确认"],
      "queryText": "风险策略分析，仓位控制、风险控制。低估值仍需结合趋势和盈利质量确认。"
    }
  }
}
```

不得把 `tags` 从 `{tag: score}` 改成列表，不得重写 `evidence`，不得重新合成 `score`、`level` 或 `direction`。如果处理器通过 `query_text()` 生成 `queryText`，直接使用该值；如果处理器提供 `extra`，保持和报告流程一致的字段形态。是否返回 `extra` 由具体处理器现有 payload 决定，Agent tool 不再自行发明新的字段。

`scene.signal_data` 返回的 `SceneAnalysisMessageDTO` 只允许在 Python tool 内部使用。`config.parameters`、`base_metrics`、`market_context` 和完整 K 线、分时、估值历史等原始序列不得透传给 LLM。LLM 只能看到被请求场景对应的处理器输出 payload。

### knowledge_search tool

工具签名建议：

```python
knowledge_search(
    query_text: str,
    scenes: list[str] | None = None,
    tags: dict[str, list[str]] | None = None,
    limit: int = 5,
) -> str
```

工具职责：

- 校验 `query_text` 非空。
- 使用现有 embedding model 生成 `queryEmbedding`。
- 调用 Java `knowledge.search`。
- 对 Java 返回结果做 LLM 上下文裁剪。

返回给 LLM 的结构必须极简：

```json
{
  "chunks": [
    {
      "filename": "低估值投资方法.pdf",
      "content": "低估值不等于低风险，需要结合盈利质量、趋势确认和分红可持续性判断..."
    }
  ]
}
```

## LLM 工具选择规则

系统提示中加入规则：

- 问题涉及具体标的的估值、趋势、风险、仓位、追涨、止损、低估陷阱时，优先调用 `scene_signal_context`。
- `scenes` 只能从七大场景中选择：`asset`、`price`、`volume`、`trend`、`valuation`、`sentiment`、`risk_strategy`。
- 不需要先调用工具获取七大场景白名单。
- 拿到 `scene_signal_context` 后，再生成适合知识库召回的 `query_text`，不要只原样使用用户问题。
- `knowledge_search` 的 `tags` 必须来自 `scene_signal_context` 返回的标签，或来自白名单内的问题意图标签。
- 最终回答不要暴露内部标签名、score、工具名或系统字段。

## 错误处理

- `scene.signal_data` 找不到标的数据时，返回结构化错误，Python tool 将其转成简短中文错误。
- `scene_signal_context` 如果某个依赖处理器缺数据，应返回可用场景结果和 `dataCompleteness` 摘要，不让整次工具调用失败。
- `knowledge.search` 如果标签为空，允许只按语义相似度召回，但 limit 仍受控。
- `knowledge.search` 如果无召回结果，返回空 `chunks`，最终答案说明知识库未找到足够依据。
- 非法 scenes/tags 在 Java 侧必须校验。策略优先选择丢弃非法项并记录 warning；如果全部非法，则返回参数错误。

## 验证标准

1. Java `scene.signal_data` 能复用 `SceneTargetDataProvider.buildMessage()` 返回和报告流程同结构的数据包。
2. `scene.signal_data` 不写入 `scene_analysis_task`，不发布 RabbitMQ。
3. `scene.signal_data` 接受 Agent 传入的 `totalChunks`，并把最大值限制为 10。
4. Python `scene_signal_context` 对 `risk_strategy` 能自动预跑 `trend`、`valuation`、`sentiment`，且同一处理器单次调用内只跑一次。
5. `scene_signal_context` 返回中只包含 LLM 请求的场景，且单个场景 payload 复用处理器输出形态，不做二次变形。
6. 工具调用结束后不保留处理器结果缓存。
7. `knowledge_search` 返回给 LLM 的 chunk 只包含 `filename` 和 `content`。
8. Java `knowledge.search` 能校验七大场景和子标签白名单。
9. Agent 对“低PB高股息银行股是否是价值陷阱”类问题能形成 `valuation + risk_strategy` 的 RAG 查询。
10. 问普通闲聊、技术解释、无关问题时不调用 RAG 工具。

## 实施顺序

1. Java 新增 `scene.signal_data` action handler，复用 `SceneTargetDataProvider`。
2. Python 新增 `scene_signal_context` 工具和单次调用内依赖调度。
3. Java 新增 `knowledge.search` action handler 和知识库检索服务。
4. Python 新增 `knowledge_search` 工具，并裁剪 LLM tool result。
5. 更新 Agent 工具注册和 prompt 选择规则。
6. 做编译、静态检查和手动工具链验证。
