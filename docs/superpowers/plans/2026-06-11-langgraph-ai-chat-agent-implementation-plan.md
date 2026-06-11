# AI Chat Agent LangGraph Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有 Python AI Chat 手写 loop 迁移到 LangGraph，前置按需读取用户画像和短期记忆，并把 final answer 拆成可回退的 `final_decision` 和最终流式 `final_stream`。

**Architecture:** 保留现有工具、Java data gateway、prompt 业务约束、预算和对话保存语义。新增 `app/agent/graph` 包承载 LangGraph 状态和节点，`BasicAgentExecutor` 直接依赖 `AgentGraphRunner`，删除旧 `AgentLoopRunner`/`AgentPlanner` 入口。

**Tech Stack:** Python, LangChain, LangGraph, pytest, Java Spring Boot token phase enum, Vue token usage page.

---

### Task 1: Graph State And Planning Node

**Files:**
- Create: `ai-python/app/agent/graph/__init__.py`
- Create: `ai-python/app/agent/graph/state.py`
- Create: `ai-python/app/agent/graph/nodes/__init__.py`
- Create: `ai-python/app/agent/graph/nodes/planner_node.py`
- Modify: `ai-python/requirements.txt`

- [x] Add `langgraph` to Python requirements.
- [x] Define `AgentGraphState`, `AgentGraphDeps`, `FinalDecision`, and `AgentGraphRunResult`.
- [x] Move existing tool planning behavior into `planner_node.py` using `model.bind_tools(tools).invoke(messages)`.
- [x] Preserve existing token phases: first planning is `initial_planning`, later planning is `tool_followup_planning`.

### Task 2: Tool Node And Same-Run Reuse

**Files:**
- Create: `ai-python/app/agent/graph/nodes/tool_node.py`
- Modify: `ai-python/app/agent/runtime/tool_call_budget.py`

- [x] Reuse `ToolCallRunner` for real tool execution.
- [x] Keep same-run duplicate protection using existing budget signatures.
- [x] Add graph-local cache hit behavior for repeated same-signature calls.
- [x] Keep Java tool request and response contracts unchanged.

### Task 3: Context Gate, Load Profile, Load Memory, Final Decision, Final Stream

**Files:**
- Create: `ai-python/app/agent/graph/nodes/context_gate_node.py`
- Create: `ai-python/app/agent/graph/nodes/load_profile_node.py`
- Create: `ai-python/app/agent/graph/nodes/load_memory_node.py`
- Create: `ai-python/app/agent/graph/profile_context.py`
- Create: `ai-python/app/agent/graph/nodes/final_decision_node.py`
- Create: `ai-python/app/agent/graph/nodes/final_stream_node.py`
- Modify: `ai-python/app/agent/runtime/answer_generator.py`

- [x] Use `context_gate` to decide whether the current user question needs a psych profile or short-term memory.
- [x] Use an unbound LLM JSON fallback for context needs that rules cannot determine; profile and memory are judged independently.
- [x] Load the current psych profile before planner only when `context_gate` says it is needed.
- [x] Load short-term memory before planner only when `context_gate` says the current question needs reference or continuation context.
- [x] Implement `final_decision` as a non-streaming readiness check that can route to planner or final stream.
- [x] Record `answer_readiness_check` token usage for final decision.
- [x] Ensure only `final_stream` calls `answer_delta_callback`.
- [x] Use an unbound model instance for final stream by calling existing answer generation with the base model.
- [x] Add final answer guard so DSML/tool_calls/invoke markup is not exposed to the frontend; retry once as plain natural language when needed.

### Task 4: Graph Runner And Executor Switch

**Files:**
- Create: `ai-python/app/agent/graph/routing.py`
- Create: `ai-python/app/agent/graph/graph_runner.py`
- Modify: `ai-python/app/agent/services/agent_executor.py`
- Delete: `ai-python/app/agent/runtime/agent_loop_runner.py`
- Delete: `ai-python/app/agent/planning/agent_planner.py`

- [x] Assemble `StateGraph` with `context_gate`, `load_profile`, `load_memory`, `planner`, `tools`, `final_decision`, and `final_stream`.
- [x] Route `final_decision=need_tool` back to planner within existing budget.
- [x] Expand the default tool budget to support longer quote/Kline/fundamental/scene-signal/knowledge-search chains.
- [x] Return the same `answer` and `token_usage_events` shape to `AgentRunHandler`.
- [x] Remove old loop/planner imports and constructor wiring.

### Task 5: Token Phase Integration

**Files:**
- Modify: `backend-java/finance-data/src/main/java/com/scrapider/finance/domain/enums/AiTokenUsagePhaseEnum.java`
- Modify: `frontend-vue/apps/web-ele/src/views/system-config/token-usage/index.vue`

- [x] Add Java enum values `CONTEXT_GATE("context_gate", "上下文门控")` and `ANSWER_READINESS_CHECK("answer_readiness_check", "回答证据检查")`.
- [x] Add frontend `PHASE_OPTIONS` entries for `context_gate` and `answer_readiness_check`.
- [x] Do not change Java controllers, params, gateway actions, request signatures, response shapes, or auth.

### Task 6: Tests And Verification

**Files:**
- Rename/modify: `ai-python/test/agent_loop_token_phase_test.py` -> `ai-python/test/agent_graph_token_phase_test.py`

- [x] Rewrite loop tests to use `AgentGraphRunner`.
- [x] Cover direct answer, tool result final stream, final decision backtrack, context-gated memory loading, LLM fallback memory loading, and token phases.
- [x] Cover profile-rule plus memory-fallback context gate behavior, doubled budget defaults, tool-cache signature normalization, and final answer DSML guard.
- [x] Run focused Python tests.
- [x] Run Java `finance-ai` tests.
- [x] Run frontend typecheck if dependency state allows.
- [x] Run `git diff --check`.

### Follow-up Fixes Applied During Review

- [x] Tighten `scene_signal_context` LangChain tool schema: `target_code` and `target_name` are required because Java `scene.signal_data` requires `targetCode`; Java interfaces remain unchanged.
- [x] Update planner prompt so name-only or code-only requests resolve standard code/name through `market_quote` before scene signal.
- [x] Log the converted final decision state after budget/backtrack checks, so logs no longer show `need_tool` when the graph actually routes as `insufficient`.
- [x] Expand context gate short-follow-up rules for “解释呢、为什么、依据呢、那怎么办、接下来呢、现在说的不是”等 expressions.
- [x] Keep direct answer token accounting under `initial_planning`; final stream fallback/retry token accounting stays under `final_answer`.
