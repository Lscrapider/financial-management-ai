# AI Agent 有限多轮 Tool Calling 设计规格

## 背景

当前 AI Agent 已经具备基础 Tool Calling 链路：Java 负责 WebSocket、Agent Session、HMAC 验签、数据网关和业务数据边界；Python 负责 LangChain 模型调用、工具规划、工具执行和最终答案生成。

现有第一版只注册了 `market_quote`，工具能力偏少。后续如果一次性加入大量工具，单轮规划容易出现误选、漏选或过度调用。更合适的方向是先扩展少量高价值只读工具，并把执行器升级为有限多轮，让模型能基于前一次工具结果决定是否继续查询。

本次设计不接入知识库召回、RAG、研报检索和财务指标。知识库能力后续需要和现有 RAG 体系单独合并设计。

## 目标

1. 扩展 Agent 可用工具，使模型能查询行情快照、用户观察池、K 线趋势摘要和分时摘要。
2. K 线和分时工具不向模型返回完整原始序列，只返回 Python 压缩后的结构化摘要。
3. 引入有限多轮执行框架，允许模型在受控预算内多次规划和调用工具。
4. 保持 Java 数据网关作为业务数据访问边界，Python 和 LLM 不直接访问数据库。
5. 为后续迁移 LangGraph 保留清晰运行时边界，但第一版不直接引入 LangGraph。

## 不做范围

- 不做 `knowledge_search`。
- 不做 RAG 或向量召回。
- 不做 `report_latest`。
- 不做财务指标工具。
- 不做交易、下单、修改观察池等写操作工具。
- 不向模型返回全量 K 线或全量分时明细。
- 不直接上 LangGraph。

## 工具设计

### market_quote

用途：查询股票、指数或可转债的最新行情快照。

适用问题：

- “今天涨了吗”
- “现在价格多少”
- “帮我看一下当前表现”

数据流：

```text
Python MarketQuoteTool
  -> Java action = market.quote
  -> Java 查询行情快照
  -> Python 返回紧凑 JSON 给模型
```

第一版沿用现有 `MarketQuoteTool` 和 `MarketQuoteActionHandler`，只做必要的工具描述优化和多轮执行适配。

### watch_pool_context

用途：查询当前用户观察池、自选分组、关注标的和备注上下文。

适用问题：

- “我关注的票里哪个更强”
- “自选池里的新能源怎么看”
- “观察池里有没有需要注意的”

边界：

- 只能按 Agent Session 中的 `userId` 查询当前用户数据。
- Python 不能指定其他用户。
- 返回内容需要限制数量和字段，避免把完整观察池明细塞给模型。

Java 新增 action：

```text
watch_pool.context
```

Python 新增 tool：

```text
watch_pool_context
```

### market_kline_trend

用途：查询并分析 K 线趋势，用于趋势、阶段涨跌、均线、回撤、波动、区间位置等问题。

适用问题：

- “最近趋势怎么样”
- “有没有破位”
- “近 20 日走势弱不弱”
- “均线结构怎么样”

关键设计：模型调用的是趋势工具，但工具结果必须是摘要，不是原始 K 线。

数据流：

```text
Python MarketKlineTrendTool
  -> Java action = market.kline
  -> Java 返回受限窗口 K 线
  -> Python TrendKlineAnalyzer.analyze()
  -> Python 压缩结果
  -> LLM 读取趋势摘要
```

窗口限制：

- 日 K：默认最近 120 根以内。
- 周 K：默认最近 80 根以内。
- 月 K：默认最近 60 根以内。
- Java 可以进一步配置最大窗口，避免 Python 侧收到过量数据。

Python 复用：

- `app.scene_analysis.services.trend_kline_analysis.TrendKlineAnalyzer`

返回摘要字段建议：

```json
{
  "targetType": "stock",
  "targetCode": "601012",
  "targetName": "隆基绿能",
  "periods": {
    "daily": {
      "score": 0.72,
      "level": "medium",
      "direction": "negative",
      "tags": {
        "downtrend": 0.86,
        "turn_weak": 0.63
      },
      "evidence": ["日线趋势偏弱，均线结构承压"],
      "context": {
        "availableBars": 120,
        "startDate": "2026-01-01",
        "endDate": "2026-06-09",
        "return5Bars": -3.2,
        "return20Bars": -8.4,
        "maxDrawdownPct": 15.6,
        "volatilityPct": 2.1,
        "position": 0.18,
        "movingAverages": {
          "ma5": 16.8,
          "ma10": 17.1,
          "ma20": 17.6,
          "ma60": 18.9
        }
      }
    }
  }
}
```

压缩规则：

- 保留 `score`、`level`、`direction`、`tags`、`evidence`。
- 保留 `context` 中对回答有用的数值字段。
- 删除或截断过长的 `pathFeatures.segments`。
- 不返回完整 K 线数组。

### market_intraday_summary

用途：查询并分析最近一个交易日分时走势，用于盘中路径、拉升、跳水、日内高低点和成交集中度。

适用问题：

- “今天盘中走势怎么样”
- “是不是冲高回落”
- “分时有没有异动”
- “今天是不是放量拉升”

数据流：

```text
Python MarketIntradaySummaryTool
  -> Java action = market.intraday
  -> Java 返回最近一个交易日受限分时数据
  -> Python 生成分时摘要
  -> LLM 读取分时摘要
```

Python 复用：

- 第一版可从 `MarketContextBuilder._intraday` 抽出公共 `IntradaySummaryAnalyzer`。
- 不建议直接让 Agent 工具调用私有方法。

返回摘要字段建议：

```json
{
  "targetType": "stock",
  "targetCode": "601012",
  "targetName": "隆基绿能",
  "available": true,
  "window": "latest_trading_day",
  "points": 240,
  "openToLatestPct": -1.8,
  "highTime": "10:12",
  "lowTime": "14:37",
  "latestPositionInDayRange": 0.21,
  "morningReturnPct": 0.6,
  "afternoonReturnPct": -2.3,
  "volumeConcentration": {
    "topSegment": "afternoon",
    "ratio": 0.58
  },
  "pathFeatures": [
    {
      "startTime": "09:30",
      "endTime": "11:30",
      "direction": "up",
      "returnPct": 0.6
    },
    {
      "startTime": "13:00",
      "endTime": "15:00",
      "direction": "down",
      "returnPct": -2.3
    }
  ]
}
```

压缩规则：

- 只保留最近一个交易日。
- 返回摘要和少量路径片段。
- 不返回完整分时数组。

## 有限多轮执行

第一版多轮不是无限自主 Agent，而是受控循环。

建议限制：

```text
max_steps = 3
max_tool_calls_total = 5
max_tool_calls_per_step = 2
timeout_seconds = 25
```

停止条件：

- 模型不再返回 tool_calls。
- 达到最大 step。
- 达到最大工具调用数。
- 当前轮所有工具调用失败。
- 出现相同 tool 和相同参数的重复调用。
- 总耗时超过限制。

执行流程：

```text
1. Python 构建 system prompt、短期记忆和当前问题。
2. 模型在可用 tools 中选择是否调用工具。
3. Python 执行 tool_calls。
4. 工具通过 Java data gateway 查询业务数据。
5. Python 将工具结果压缩为 ToolMessage。
6. 如果仍在预算内，模型基于已有结果继续规划。
7. 停止后，模型基于完整工具轨迹生成最终答案。
8. Python callback Java，Java 通过 WebSocket 推送前端并保存最终 assistant message。
```

## Python 模块结构

新增或调整：

```text
ai-python/app/agent/runtime/
  agent_loop_runner.py       # 有限多轮循环控制
  tool_call_budget.py        # 工具预算、重复调用和超时控制
  agent_trace.py             # 每轮工具调用轨迹
  tool_call_runner.py        # 复用，必要时补充错误结构化
  answer_generator.py        # 复用，支持基于完整 trace 生成答案

ai-python/app/agent/tools/
  market_quote_tool.py
  watch_pool_context_tool.py
  market_kline_trend_tool.py
  market_intraday_summary_tool.py
  tool_registry.py

ai-python/app/agent/analysis/
  intraday_summary_analyzer.py
```

`TrendKlineAnalyzer` 继续放在 `scene_analysis` 模块中复用，不复制算法。

`IntradaySummaryAnalyzer` 可以从 `MarketContextBuilder` 的分时逻辑抽出，供 `scene_analysis` 和 `agent` 共用。

## Java 模块结构

Java 继续作为数据边界，不执行模型规划。

新增 action handler：

```text
MarketKlineActionHandler
  action = market.kline

MarketIntradayActionHandler
  action = market.intraday

WatchPoolContextActionHandler
  action = watch_pool.context
```

保留：

```text
MarketQuoteActionHandler
  action = market.quote
```

`AgentDataGatewayServiceImpl` 不增加 if/switch，继续通过 `AgentDataActionHandler` 自动注册。

`InMemoryAgentSessionServiceImpl` 默认 scopes 增加：

```text
market.kline
market.intraday
watch_pool.context
```

## 事件与流式输出

第一版至少保留现有 `final_answer`。

建议同步设计这些事件，便于前端展示多轮过程：

```text
tool_call_start
tool_call_done
tool_call_error
final_answer
```

后续做文本流式时增加：

```text
answer_delta
```

最终答案仍以 `final_answer` 为权威结果，用于 Java 落库。`answer_delta` 只负责前端实时展示，不单独落库。

## Prompt 约束

系统提示需要明确：

- 只有当前问题需要投资行情、趋势、盘中走势或观察池上下文时才调用工具。
- 问技术实现、Tool Calling 原理、闲聊时不要调用行情工具。
- 不要为简单快照问题过度调用趋势和分时工具。
- 趋势问题优先调用 `market_kline_trend`。
- 盘中问题优先调用 `market_intraday_summary`。
- 涉及“我的自选、我关注的、观察池”时调用 `watch_pool_context`。
- 最终回答只能基于工具返回的数据和短期记忆，数据不足时要明确说明。
- 不给确定性买卖指令。

## 验证标准

实现完成后至少验证：

1. 非投资问题不调用任何行情工具。
2. 简单价格问题只调用 `market_quote`。
3. 趋势问题调用 `market_kline_trend`，且工具结果不包含完整 K 线数组。
4. 盘中问题调用 `market_intraday_summary`，且工具结果不包含完整分时数组。
5. 观察池问题调用 `watch_pool_context`，且只能返回当前用户数据。
6. 多轮执行不超过 `max_steps`、`max_tool_calls_total` 和超时限制。
7. 相同 tool 和相同参数不会重复调用。
8. Java data gateway 对未授权 action 返回 403。
9. Python callback 的最终答案仍能被 Java 保存并通过 WebSocket 推送。

## 预计修改文件

Python：

```text
ai-python/app/agent/services/agent_executor.py
ai-python/app/agent/runtime/agent_loop_runner.py
ai-python/app/agent/runtime/tool_call_budget.py
ai-python/app/agent/runtime/agent_trace.py
ai-python/app/agent/runtime/tool_call_runner.py
ai-python/app/agent/runtime/answer_generator.py
ai-python/app/agent/prompts/prompt_builder.py
ai-python/app/agent/tools/tool_registry.py
ai-python/app/agent/tools/watch_pool_context_tool.py
ai-python/app/agent/tools/market_kline_trend_tool.py
ai-python/app/agent/tools/market_intraday_summary_tool.py
ai-python/app/agent/analysis/intraday_summary_analyzer.py
```

Java：

```text
backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/InMemoryAgentSessionServiceImpl.java
backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/MarketKlineActionHandler.java
backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/MarketIntradayActionHandler.java
backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/WatchPoolContextActionHandler.java
```

实现过程事件或流式输出时需要修改：

```text
backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/controller/AgentInternalController.java
backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/domain/vo/AiChatWebSocketMessageVO.java
ai-python/app/agent/services/callback_client.py
```

## 设计决策确认

已确认方向：

- 第一版使用有限多轮 Tool Calling。
- 暂不上 LangGraph。
- 工具全部由模型按问题选择，不做强制工具调用。
- 第一版工具范围为 `market_quote`、`watch_pool_context`、`market_kline_trend`、`market_intraday_summary`。
- K 线趋势使用 Python 现有 `TrendKlineAnalyzer` 压缩数据。
- 分时摘要从现有 `MarketContextBuilder` 分时逻辑中抽出可复用分析器。
- 知识库召回、RAG、研报和财务指标暂不考虑。
