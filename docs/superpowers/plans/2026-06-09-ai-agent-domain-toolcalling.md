# AI Agent Domain Tool Calling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增股票基本面、可转债上下文和已有报告文本工具，让 AI Agent 能按业务域查询快照以外的数据。

**Architecture:** Java 继续作为 Agent 数据网关，新增只读 `AgentDataActionHandler` 聚合数据库数据并做字段白名单。Python 新增工具类并注册给 LangChain，股票估值摘要沿用现有场景分析口径，只输出 `peTtm`、`pbMrq` 和分红收益率摘要，不改标签处理器。

**Tech Stack:** Java Spring Boot、MyBatis Plus、Jackson、Python LangChain、现有 Agent data gateway HTTP 链路。

---

## File Structure

Python 修改范围：

- `ai-python/app/agent/tools/stock_fundamental_context_tool.py`：新增股票基本面工具，调用 `stock.fundamental_context`，压缩 Java 返回结果。
- `ai-python/app/agent/tools/convertible_bond_context_tool.py`：新增可转债上下文工具，调用 `convertible_bond.context`。
- `ai-python/app/agent/tools/scene_report_context_tool.py`：新增已有报告文本工具，调用 `scene_report.context`。
- `ai-python/app/agent/tools/tool_registry.py`：注册三个新工具。
- `ai-python/app/agent/prompts/prompt_builder.py`：补充工具选择规则，强调快照字段仍走 `market_quote`。

Java 修改范围：

- `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/StockFundamentalContextActionHandler.java`：新增股票基本面 action。
- `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/ConvertibleBondContextActionHandler.java`：新增可转债 action。
- `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/SceneReportContextActionHandler.java`：新增报告文本 action。
- `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/converter/AiAgentDomainToolDataConverter.java`：新增字段白名单和摘要转换器。
- `backend-java/finance-data/src/main/java/com/scrapider/finance/mapper/SceneAnalysisReportMapper.java`：新增当前用户某标的最近成功报告查询。
- `backend-java/finance-data/src/main/java/com/scrapider/finance/manage/SceneAnalysisReportManage.java`：封装最近成功报告查询方法。

本计划不新增单元测试文件；按项目当前约束，先使用 Java/Python 编译、字段泄漏 grep 和手工 JSON 结构检查收口。

---

### Task 1: Java 股票基本面 Action

**Files:**
- Create: `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/converter/AiAgentDomainToolDataConverter.java`
- Create: `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/StockFundamentalContextActionHandler.java`

- [ ] **Step 1: Create converter skeleton**

Create `AiAgentDomainToolDataConverter` with static methods:

```java
public final class AiAgentDomainToolDataConverter {

    private AiAgentDomainToolDataConverter() {
    }

    public static Map<String, Object> stockTarget(String targetCode, String targetName) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("targetType", "stock");
        target.put("targetCode", targetCode);
        target.put("targetName", targetName);
        return compact(target);
    }
}
```

- [ ] **Step 2: Add valuation summary conversion**

Add conversion methods that output only:

```text
valuation.current.peTtm
valuation.current.pbRatio
valuation.historySummary.peTtm.*
valuation.historySummary.pbMrq.*
valuation.dividend.*
```

Use the same distribution fields as `MarketContextBuilder._distribution_summary`: `latest/count/min/max/average/percentileRank`.

- [ ] **Step 3: Add financial indicator white-list conversion**

Convert recent N `StockFinancialIndicatorPO` rows with only these fields:

```text
reportDate, reportType, reportDateName, noticeDate, epsBasic, bps,
totalOperateRevenue, parentNetProfit, totalOperateRevenueYoy,
parentNetProfitYoy, roeWeighted, debtAssetRatio, totalDeposits,
grossLoans, loanToDepositRatio, capitalAdequacyRatio,
coreTier1CapitalAdequacyRatio, firstAdequacyRatio,
nonPerformingLoanRatio, provisionCoverageRatio, netInterestSpread,
netInterestMargin, loanProvisionRatio
```

- [ ] **Step 4: Implement action handler**

Create `StockFundamentalContextActionHandler`:

```java
public static final String ACTION = "stock.fundamental_context";
```

Read `targetCode`, `targetName`, `sections`, and `limit` from `AgentDataQueryParam.params()`. Query by stock code first; if only name is present, resolve via existing stock config or quote snapshot manage. Return a single row with:

```json
{
  "target": {},
  "valuation": {},
  "financialIndicators": [],
  "dataCompleteness": {}
}
```

- [ ] **Step 5: Verify no forbidden fields are returned**

Run:

```bash
rg -n "latestPrice|changePercent|turnoverRate|peLar|psTtm|pegCar" backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/StockFundamentalContextActionHandler.java backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/converter/AiAgentDomainToolDataConverter.java
```

Expected: no matches except comments explaining excluded fields, if comments are added.

---

### Task 2: Java 可转债和报告 Action

**Files:**
- Modify: `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/converter/AiAgentDomainToolDataConverter.java`
- Create: `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/ConvertibleBondContextActionHandler.java`
- Create: `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/SceneReportContextActionHandler.java`
- Modify: `backend-java/finance-data/src/main/java/com/scrapider/finance/mapper/SceneAnalysisReportMapper.java`
- Modify: `backend-java/finance-data/src/main/java/com/scrapider/finance/manage/SceneAnalysisReportManage.java`

- [ ] **Step 1: Add convertible bond conversion**

Add white-list converters for:

```text
basic
valuationHistory
shareChanges
```

Exclude real-time quote fields: `latestPrice/changePercent/turnoverAmount/turnoverRate/volume/amplitude`.

- [ ] **Step 2: Implement convertible bond action**

Create `ConvertibleBondContextActionHandler`:

```java
public static final String ACTION = "convertible_bond.context";
```

Read `targetCode`, `targetName`, `sections`, `limit`. Query `ConvertibleBondBasicManage`, `ConvertibleBondDailyValuationManage`, and `ConvertibleBondShareManage`. Return one row with `target/basic/valuationHistory/shareChanges/dataCompleteness`.

- [ ] **Step 3: Add report query methods**

Add this mapper method to `SceneAnalysisReportMapper`:

```java
@Select("""
        <script>
        SELECT r.*
        FROM scene_analysis_report r
        JOIN scene_analysis_task t ON t.id = r.task_id
        WHERE r.target_type = #{targetType}
          AND r.target_code = #{targetCode}
          AND r.status = 'success'
        <if test="ownerUserId != null">
          AND t.user_id = #{ownerUserId}
        </if>
        ORDER BY COALESCE(r.generated_at, r.created_at) DESC, r.id DESC
        LIMIT #{limit}
        </script>
        """)
@ResultMap("sceneAnalysisReportMap")
List<SceneAnalysisReportPO> listLatestSuccessByTarget(
        @Param("targetType") String targetType,
        @Param("targetCode") String targetCode,
        @Param("ownerUserId") Long ownerUserId,
        @Param("limit") int limit);
```

Add this manage method to `SceneAnalysisReportManage`:

```java
public List<SceneAnalysisReportPO> listLatestSuccessByTarget(
        String targetType,
        String targetCode,
        Long ownerUserId,
        int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 3));
    return this.baseMapper.listLatestSuccessByTarget(targetType, targetCode, ownerUserId, safeLimit);
}
```

- [ ] **Step 4: Implement report action**

Create `SceneReportContextActionHandler`:

```java
public static final String ACTION = "scene_report.context";
```

If `reportId` is present, call `findByIdForOwner(reportId, session.userId())`. Otherwise require `targetType` and `targetCode`, and return latest successful reports for the current user. Output only:

```text
reportId, targetType, targetCode, targetName, reportType,
generationType, versionNo, status, generatedAt, reportText
```

Apply a report text length cap and include metadata showing truncation.

- [ ] **Step 5: Verify Java compile**

Run:

```bash
mvn -pl backend-java/finance-ai -am -DskipTests compile
```

Expected: `BUILD SUCCESS`.

---

### Task 3: Python Tools And Registry

**Files:**
- Create: `ai-python/app/agent/tools/stock_fundamental_context_tool.py`
- Create: `ai-python/app/agent/tools/convertible_bond_context_tool.py`
- Create: `ai-python/app/agent/tools/scene_report_context_tool.py`
- Modify: `ai-python/app/agent/tools/tool_registry.py`
- Modify: `ai-python/app/agent/prompts/prompt_builder.py`

- [ ] **Step 1: Create stock tool**

Implement `StockFundamentalContextTool.invoke(...)` using `AgentDataGatewayClient.query(...)` with:

```python
action="stock.fundamental_context"
params={
    "targetCode": target_code,
    "targetName": target_name,
    "sections": sections or ["valuation", "financial_indicator", "dividend"],
}
limit=max(1, min(int(limit or 4), 8))
```

Return `json.dumps(result, ensure_ascii=False, default=str)`.

- [ ] **Step 2: Create convertible bond tool**

Implement `ConvertibleBondContextTool.invoke(...)` with:

```python
action="convertible_bond.context"
params={
    "targetCode": target_code,
    "targetName": target_name,
    "sections": sections or ["basic", "valuation_history", "share_change"],
}
limit=max(1, min(int(limit or 8), 20))
```

- [ ] **Step 3: Create report tool**

Implement `SceneReportContextTool.invoke(...)` with:

```python
action="scene_report.context"
params={
    "targetType": target_type,
    "targetCode": target_code,
    "reportId": report_id,
}
limit=max(1, min(int(limit or 1), 3))
```

- [ ] **Step 4: Register tools**

Update `AgentToolRegistry` constructor and `build_langchain_tools(...)` to include:

```text
stock_fundamental_context
convertible_bond_context
scene_report_context
```

Descriptions must state that `market_quote` remains the tool for current price,涨跌幅、成交、换手 and latest quote facts.

- [ ] **Step 5: Update prompt**

In `AgentPromptBuilder`, add tool selection rules:

```text
财务、分红、PE/PB 历史估值位置：stock_fundamental_context
可转债条款、溢价率历史、纯债价值、YTM、余额变化：convertible_bond_context
已有报告文本、历史报告结论：scene_report_context
当前价格、涨跌、成交、换手、量比、最新快照：market_quote
```

- [ ] **Step 6: Verify Python syntax**

Run:

```bash
python3 -m compileall ai-python/app/agent
```

Expected: no syntax errors.

---

### Task 4: End-To-End Verification And Commit

**Files:**
- Verify changed Python and Java files.

- [ ] **Step 1: Run Java compile**

Run:

```bash
mvn -pl backend-java/finance-ai -am -DskipTests compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run Python compile**

Run:

```bash
python3 -m compileall ai-python/app/agent
```

Expected: compile completes without errors.

- [ ] **Step 3: Run leakage checks**

Run:

```bash
rg -n '"peLar"|"psTtm"|"pegCar"|"latestPrice"|"changePercent"|"turnoverRate"' ai-python/app/agent/tools backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/StockFundamentalContextActionHandler.java backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/converter/AiAgentDomainToolDataConverter.java
```

Expected: no stock fundamental output mapping for those fields.

- [ ] **Step 4: Commit implementation**

Use project commit format:

```bash
git add ai-python/app/agent backend-java/finance-ai/src/main/java backend-java/finance-data/src/main/java
git commit -m "[add] : 1. 增加 AI Agent 业务域工具调用"
```
