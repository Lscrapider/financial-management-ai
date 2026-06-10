# AI Agent Tool Calling RAG Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 Agent Tool Calling RAG 能力，复用 Java 现有场景数据拼装和 Python 现有场景处理器，并提供标签引导的知识库召回。

**Architecture:** Java 继续作为 Agent 数据网关，新增 `scene.signal_data` 和 `knowledge.search` 两个只读 action。Python Agent 新增 `scene_signal_context` 和 `knowledge_search` 工具，场景标签由现有 processor 计算，依赖结果只在单次 tool 调用内复用。知识库召回返回给 LLM 时只保留 `filename` 和 `content`。

**Tech Stack:** Java Spring Boot、MyBatis Plus、pgvector、Python LangChain tools、pytest、Maven。

---

### Task 1: Java Scene Signal Data Action

**Files:**
- Create: `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/SceneSignalDataActionHandler.java`
- Test: `backend-java/finance-ai/src/test/java/com/scrapider/finance/ai/service/impl/SceneSignalDataActionHandlerTest.java`

- [ ] **Step 1: Write failing tests**

Add tests that instantiate a fake `SceneTargetDataProvider`, call the handler with `totalChunks = 15`, and assert the returned `SceneAnalysisMessageDTO.totalChunks()` is capped at `10`. Add a second test for missing `targetCode` returning `success=false`.

- [ ] **Step 2: Run focused Java test and verify RED**

Run:

```bash
cd backend-java && mvn -pl finance-ai -Dtest=SceneSignalDataActionHandlerTest test
```

Expected: compilation fails because `SceneSignalDataActionHandler` does not exist.

- [ ] **Step 3: Implement handler**

Create an `AgentDataActionHandler` with `action() = "scene.signal_data"`. Normalize target type using the same values as `SceneAnalysisTaskServiceImpl`, build a `SceneAnalysisSubmitParam` from JsonNode params, clamp `totalChunks` to `1..10`, create a synthetic `taskNo`, call the matching `SceneTargetDataProvider.buildMessage`, and return the message as one row.

- [ ] **Step 4: Run focused Java test and verify GREEN**

Run the same Maven command. Expected: test passes.

### Task 2: Python Scene Signal Context Tool

**Files:**
- Create: `ai-python/app/agent/scene_signal/scene_signal_runner.py`
- Create: `ai-python/app/agent/tools/scene_signal_context_tool.py`
- Modify: `ai-python/app/agent/tools/tool_registry.py`
- Test: `ai-python/test/agent_scene_signal_context_test.py`

- [ ] **Step 1: Write failing tests**

Add pytest tests with stub processors and a stub gateway response. Verify requesting `risk_strategy` runs `trend`, `valuation`, `sentiment`, and `risk_strategy` once each, returns only `riskStrategy`, and preserves payload fields including `score`, `tags` as `{tag: score}`, `evidence`, and `queryText`.

- [ ] **Step 2: Run focused Python test and verify RED**

Run:

```bash
cd ai-python && python -m pytest test/agent_scene_signal_context_test.py -q
```

Expected: import fails because the new modules do not exist.

- [ ] **Step 3: Implement scene signal runner and tool**

Implement a per-invocation dependency runner that builds `BaseMetricsCalculator` and `SceneAnalysisContext`, calls existing processors, caches results in a local dict only, converts module names using existing `current_scene_result` output conventions, and returns only requested scene payloads. Implement the tool to call Java `scene.signal_data`, then run the runner.

- [ ] **Step 4: Register tool**

Add `scene_signal_context` to `AgentToolRegistry.build_langchain_tools`.

- [ ] **Step 5: Run focused Python test and verify GREEN**

Run the same pytest command. Expected: test passes.

### Task 3: Java Knowledge Search Action

**Files:**
- Modify: `backend-java/finance-data/src/main/java/com/scrapider/finance/mapper/KnowledgeVectorMapper.java`
- Modify: `backend-java/finance-data/src/main/java/com/scrapider/finance/manage/KnowledgeVectorManage.java`
- Create: `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/KnowledgeSearchActionHandler.java`
- Test: `backend-java/finance-ai/src/test/java/com/scrapider/finance/ai/service/impl/KnowledgeSearchActionHandlerTest.java`

- [ ] **Step 1: Write failing tests**

Add handler tests with a fake `KnowledgeVectorManage` that returns candidate rows. Verify invalid tags are rejected or ignored according to current design, limit is capped at 8, and successful response rows include internal fields for Python to crop.

- [ ] **Step 2: Run focused Java test and verify RED**

Run:

```bash
cd backend-java && mvn -pl finance-ai -Dtest=KnowledgeSearchActionHandlerTest test
```

Expected: compilation fails because `KnowledgeSearchActionHandler` does not exist.

- [ ] **Step 3: Implement mapper/manage support**

Add a semantic search method that can search by multiple scenes when provided, falling back to semantic-only if scenes are empty. Keep deleted chunk filtering and query embedding ordering.

- [ ] **Step 4: Implement handler**

Parse `queryText`, `queryEmbedding`, `scenes`, `tags`, and `limit`. Validate scene/tag white lists using the existing tag constants already present in `KnowledgeServiceImpl` semantics. Score rows with semantic score plus tag-match score, dedupe, truncate, and return rows containing `filename`, `content`, and internal metadata.

- [ ] **Step 5: Run focused Java test and verify GREEN**

Run both Java focused tests. Expected: pass.

### Task 4: Python Knowledge Search Tool

**Files:**
- Create: `ai-python/app/agent/tools/knowledge_search_tool.py`
- Modify: `ai-python/app/agent/tools/tool_registry.py`
- Test: `ai-python/test/agent_knowledge_search_tool_test.py`

- [ ] **Step 1: Write failing tests**

Add pytest test with a fake embedding provider and fake data gateway client. Verify returned JSON only contains `filename` and `content`, even if Java rows include `chunkId`, `semanticScore`, `tagMatchScore`, and tags.

- [ ] **Step 2: Run focused Python test and verify RED**

Run:

```bash
cd ai-python && python -m pytest test/agent_knowledge_search_tool_test.py -q
```

Expected: import fails because the tool does not exist.

- [ ] **Step 3: Implement knowledge search tool**

Generate query embeddings with the same embedding engine path used by OCR when available through dependency injection; in tests use a fake provider. Call Java `knowledge.search`, crop rows to `filename` and `content`, and return JSON.

- [ ] **Step 4: Register tool**

Add `knowledge_search` to `AgentToolRegistry.build_langchain_tools`.

- [ ] **Step 5: Run focused Python test and verify GREEN**

Run both Python focused tests. Expected: pass.

### Task 5: Prompt Rules And Verification

**Files:**
- Modify: `ai-python/app/agent/prompts/prompt_builder.py`
- Modify: `docs/superpowers/specs/2026-06-10-ai-agent-tool-calling-rag-design.md` if implementation names differ from the spec.

- [ ] **Step 1: Update prompt rules**

Add concise tool-selection rules: use `scene_signal_context` for concrete target valuation/trend/risk questions; use `knowledge_search` after scene signals when knowledge evidence is needed; do not expose internal tags or score names in final user-facing prose.

- [ ] **Step 2: Run compile checks**

Run:

```bash
cd ai-python && python -m compileall app/agent app/scene_analysis
cd backend-java && mvn -pl finance-ai -DskipTests compile
```

Expected: both pass.

- [ ] **Step 3: Run focused tests**

Run:

```bash
cd ai-python && python -m pytest test/agent_scene_signal_context_test.py test/agent_knowledge_search_tool_test.py -q
cd backend-java && mvn -pl finance-ai -Dtest=SceneSignalDataActionHandlerTest,KnowledgeSearchActionHandlerTest test
```

Expected: all pass.

