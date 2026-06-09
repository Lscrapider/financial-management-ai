# AI Agent Limited Loop Tool Calling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 扩展 AI Agent 为有限多轮 Tool Calling，并新增观察池、K 线趋势摘要、分时摘要工具。

**Architecture:** Java 继续作为受控数据网关，新增 `AgentDataActionHandler` 查询受限业务数据。Python 新增有限多轮运行器和工具，K 线使用现有 `TrendKlineAnalyzer` 压缩，分时抽出摘要分析器压缩。模型只看到工具摘要，不看到完整 K 线或完整分时序列。

**Tech Stack:** Java Spring Boot、MyBatis Plus/Hutool、Python LangChain、现有 RabbitMQ/HTTP callback/WebSocket 链路。

---

## File Structure

Python 修改范围：

- `ai-python/app/agent/services/agent_executor.py`：从单轮执行切到有限多轮执行。
- `ai-python/app/agent/runtime/agent_loop_runner.py`：新增有限多轮循环。
- `ai-python/app/agent/runtime/tool_call_budget.py`：新增工具调用预算和重复调用检查。
- `ai-python/app/agent/runtime/agent_trace.py`：新增工具调用轨迹对象。
- `ai-python/app/agent/runtime/tool_call_runner.py`：返回结构化执行结果，同时保留 ToolMessage 生成。
- `ai-python/app/agent/runtime/answer_generator.py`：基于多轮 scratchpad 生成最终答案。
- `ai-python/app/agent/prompts/prompt_builder.py`：更新工具选择约束。
- `ai-python/app/agent/tools/tool_registry.py`：注册新增工具。
- `ai-python/app/agent/tools/watch_pool_context_tool.py`：新增观察池工具。
- `ai-python/app/agent/tools/market_kline_trend_tool.py`：新增 K 线趋势摘要工具。
- `ai-python/app/agent/tools/market_intraday_summary_tool.py`：新增分时摘要工具。
- `ai-python/app/agent/analysis/intraday_summary_analyzer.py`：新增分时摘要分析器。

Java 修改范围：

- `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/InMemoryAgentSessionServiceImpl.java`：补默认 scopes。
- `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/MarketKlineActionHandler.java`：新增 K 线数据 action。
- `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/MarketIntradayActionHandler.java`：新增分时数据 action。
- `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/WatchPoolContextActionHandler.java`：新增观察池上下文 action。
- `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/converter/AiMarketDataConverter.java`：必要时补 index/bond 分时 map 或通用 map 转换。

本计划不强制新增单元测试；按项目指令先使用静态编译和现有验证命令收口。

---

### Task 1: Python Limited Loop Runtime And Tools

**Files:**
- Modify: `ai-python/app/agent/services/agent_executor.py`
- Modify: `ai-python/app/agent/runtime/tool_call_runner.py`
- Modify: `ai-python/app/agent/runtime/answer_generator.py`
- Modify: `ai-python/app/agent/prompts/prompt_builder.py`
- Modify: `ai-python/app/agent/tools/tool_registry.py`
- Create: `ai-python/app/agent/runtime/agent_loop_runner.py`
- Create: `ai-python/app/agent/runtime/tool_call_budget.py`
- Create: `ai-python/app/agent/runtime/agent_trace.py`
- Create: `ai-python/app/agent/tools/watch_pool_context_tool.py`
- Create: `ai-python/app/agent/tools/market_kline_trend_tool.py`
- Create: `ai-python/app/agent/tools/market_intraday_summary_tool.py`
- Create: `ai-python/app/agent/analysis/intraday_summary_analyzer.py`

- [ ] **Step 1: Add call budget**

Create `ToolCallBudget` with these defaults:

```python
from __future__ import annotations

import json
import time
from dataclasses import dataclass, field
from typing import Any


@dataclass
class ToolCallBudget:
    max_steps: int = 3
    max_tool_calls_total: int = 5
    max_tool_calls_per_step: int = 2
    timeout_seconds: float = 25.0
    started_at: float = field(default_factory=time.monotonic)
    total_tool_calls: int = 0
    seen_signatures: set[str] = field(default_factory=set)

    def step_allowed(self, step_index: int) -> bool:
        return step_index < self.max_steps and not self.timed_out()

    def trim_tool_calls(self, tool_calls: list[dict[str, Any]]) -> list[dict[str, Any]]:
        allowed: list[dict[str, Any]] = []
        for tool_call in tool_calls[:self.max_tool_calls_per_step]:
            if self.total_tool_calls >= self.max_tool_calls_total or self.timed_out():
                break
            signature = self.signature(tool_call)
            if signature in self.seen_signatures:
                continue
            self.seen_signatures.add(signature)
            self.total_tool_calls += 1
            allowed.append(tool_call)
        return allowed

    def timed_out(self) -> bool:
        return time.monotonic() - self.started_at >= self.timeout_seconds

    def signature(self, tool_call: dict[str, Any]) -> str:
        return json.dumps({
            "name": tool_call.get("name"),
            "args": tool_call.get("args") or {},
        }, ensure_ascii=False, sort_keys=True, default=str)
```

- [ ] **Step 2: Add agent trace**

Create an `AgentTrace` dataclass that records per-step tool names, args, success, and result preview. Keep previews short and do not store `sessionSecret`.

- [ ] **Step 3: Update ToolCallRunner**

Keep `run_standard_tools(...)` behavior, but return an object that contains both `tool_messages` and trace entries. Unsupported tools should be skipped and recorded as failed trace entries.

- [ ] **Step 4: Add IntradaySummaryAnalyzer**

Extract the public equivalent of `MarketContextBuilder._intraday` into `IntradaySummaryAnalyzer.summarize(rows)`. The analyzer must return only summary fields and short `pathFeatures`, not raw rows.

- [ ] **Step 5: Add watch_pool_context tool**

Create a Python tool class that calls:

```text
action = watch_pool.context
```

It should return compact JSON from Java data gateway.

- [ ] **Step 6: Add market_kline_trend tool**

Create a Python tool class that calls:

```text
action = market.kline
```

Expected Java response data shape:

```json
[
  {
    "targetType": "stock",
    "targetCode": "601012",
    "targetName": "隆基绿能",
    "dailyKlines": [],
    "weeklyKlines": [],
    "monthlyKlines": []
  }
]
```

Use `TrendKlineAnalyzer.analyze("daily", rows)`, `weekly`, and `monthly`. Remove raw K line arrays from the final tool output.

- [ ] **Step 7: Add market_intraday_summary tool**

Create a Python tool class that calls:

```text
action = market.intraday
```

Use `IntradaySummaryAnalyzer.summarize(rows)` and return the compact summary.

- [ ] **Step 8: Register tools**

Update `AgentToolRegistry` to register:

```text
market_quote
watch_pool_context
market_kline_trend
market_intraday_summary
```

Tool descriptions must state when to call and when not to call each tool.

- [ ] **Step 9: Add AgentLoopRunner**

Implement:

```text
for step in max_steps:
  plan = planner.plan(model, messages + scratchpad, tools, session_id)
  trimmed_calls = budget.trim_tool_calls(plan.standard_tool_calls)
  if no calls: return plan content or final answer from scratchpad
  execute calls
  append planning_message and tool_messages to scratchpad
final answer from messages + scratchpad
```

- [ ] **Step 10: Wire executor**

Update `BasicAgentExecutor._tool_calling_answer` to use `AgentLoopRunner`. Keep fallback behavior unchanged.

- [ ] **Step 11: Verify Python syntax**

Run:

```bash
python3 -m compileall ai-python/app/agent
```

Expected: compile completes without syntax errors.

---

### Task 2: Java Data Gateway Action Handlers

**Files:**
- Modify: `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/InMemoryAgentSessionServiceImpl.java`
- Modify: `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/converter/AiMarketDataConverter.java`
- Create: `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/MarketKlineActionHandler.java`
- Create: `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/MarketIntradayActionHandler.java`
- Create: `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/WatchPoolContextActionHandler.java`

- [ ] **Step 1: Add scopes**

Add these default scopes:

```text
market.kline
market.intraday
watch_pool.context
```

- [ ] **Step 2: Implement MarketKlineActionHandler**

Action:

```text
market.kline
```

Inputs from `param.params()`:

```text
targetType: stock | index | bond
targetCode
targetName
dailyLimit
weeklyLimit
monthlyLimit
```

Limits:

```text
daily: default 120, max 120
weekly: default 80, max 80
monthly: default 60, max 60
```

Output data list should contain one map:

```text
targetType
targetCode
targetName
dailyKlines
weeklyKlines
monthlyKlines
```

Use existing Manage classes and `KlinePeriodTypeEnum`. For stock K line use HFQ if the existing manage method requires adjust type.

- [ ] **Step 3: Implement MarketIntradayActionHandler**

Action:

```text
market.intraday
```

Inputs:

```text
targetType: stock | index | bond
targetCode
targetName
```

Output data list should contain one map:

```text
targetType
targetCode
targetName
intradayData
```

Use existing Influx Manage classes that expose `listLatestTradingTrends(...)`.

- [ ] **Step 4: Implement WatchPoolContextActionHandler**

Action:

```text
watch_pool.context
```

Use `session.userId()` only. Return current user's groups and items with compact fields:

```text
groupName
targetType
targetCode
targetName
remark
buyPrice
position
latestPrice
changePercent
```

Do not create default groups from this internal readonly action. Query `WatchGroupManage` and `WatchGroupItemManage` directly so readonly context has no side effects.

- [ ] **Step 5: Verify Java compile**

Run:

```bash
mvn -pl backend-java/finance-ai -am -DskipTests compile
```

Expected: compile succeeds.

---

### Task 3: Integration Review And Final Verification

**Files:**
- Review all files from Task 1 and Task 2.

- [ ] **Step 1: Confirm action/tool name mapping**

Check:

```text
market_quote -> market.quote
watch_pool_context -> watch_pool.context
market_kline_trend -> market.kline
market_intraday_summary -> market.intraday
```

- [ ] **Step 2: Confirm raw data is not exposed to LLM**

Inspect Python tool outputs and confirm:

```text
market_kline_trend output has no dailyKlines/weeklyKlines/monthlyKlines raw arrays
market_intraday_summary output has no intradayData raw array
```

- [ ] **Step 3: Run verification**

Run:

```bash
python3 -m compileall ai-python/app/agent
mvn -pl backend-java/finance-ai -am -DskipTests compile
```

- [ ] **Step 4: Report residual risks**

Report any unavailable verification, missing external services, or behavior requiring live RabbitMQ/LLM testing.
