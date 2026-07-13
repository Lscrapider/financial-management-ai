# 新增标的初始化与 Agent 数据兜底 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增股票和可转债在完成必要校验后立即返回，后台补齐慢数据，并让 Agent 查询通过共享服务兜底补齐缺失或过期数据。

**Architecture:** 在 `finance-service` 新增无 AI DTO 依赖的 `AssetDataEnsureService` 与 `AssetDataInitializationService`。前者统一基本面、可转债基础资料、日度估值和份额变动的刷新规则；后者沿用现有 `CompletableFuture.runAsync` 调度后台工作。`finance-ai` 的 Report 和 Agent Handler 只调用共享服务并从本地库组装响应。

**Tech Stack:** Spring Boot、MyBatis-Plus、Java `CompletableFuture`、Vue 3/TypeScript、Maven、pnpm。

## Global Constraints

- 模块依赖必须保持 `finance-ai -> finance-service`，禁止新增反向依赖。
- 复用既有 Provider、Manage、`bond.sync.convertible-daily-limit` 与 Report 现有刷新时效/数量上限，不新增同义配置或业务阈值。
- 后台执行沿用已有 `CompletableFuture.runAsync` 模式；当前单实例按标的串行进行中的刷新，等待方在前一轮结束后复查自己的缺失项，避免 Agent 与后台并发重复拉源且不混用不同补齐范围的结果。
- Agent action、Python 工具签名、既有 `data` 字段和数据库 schema 保持不变；新状态只追加到 `metadata.dataRefresh`。
- 用户已明确授权新增聚焦单元测试；每个新增行为先写失败测试并观察其失败，再写最小生产代码。
- 未经用户明确同意不得创建分支、提交或推送；本计划不包含 git 写操作。
- 代码、注释和文档使用中文；保持 Spring Boot 分层，Controller 不编排数据源逻辑。

---

## 文件结构

| 文件 | 责任 |
| --- | --- |
| `backend-java/finance-service/src/main/java/com/scrapider/finance/service/AssetDataEnsureService.java` | 定义股票、可转债数据确保的共享接口。 |
| `backend-java/finance-service/src/main/java/com/scrapider/finance/service/impl/AssetDataEnsureServiceImpl.java` | 执行 Provider 拉取、时效判断、持久化、日度估值落库确认和同标的并发合并。 |
| `backend-java/finance-service/src/main/java/com/scrapider/finance/service/AssetDataEnsureResult.java` | 将刷新是否发起、未获得的数据分区返回给后台与 Agent。 |
| `backend-java/finance-service/src/main/java/com/scrapider/finance/service/AssetDataEnsurePolicy.java` | 集中保存既有股票基本面数量上限和刷新时效，供业务层和 AI 查询层共用。 |
| `backend-java/finance-service/src/main/java/com/scrapider/finance/service/AssetDataInitializationService.java` | 定义新增后后台初始化入口。 |
| `backend-java/finance-service/src/main/java/com/scrapider/finance/service/impl/AssetDataInitializationServiceImpl.java` | 调度行情任务和共享确保服务，不阻塞新增接口。 |
| `backend-java/finance-service/src/main/java/com/scrapider/finance/service/impl/SystemConfigStockServiceImpl.java` | 保留同步校验/快照落库，改为投递股票后台初始化。 |
| `backend-java/finance-service/src/main/java/com/scrapider/finance/service/impl/SystemConfigBondServiceImpl.java` | 保留同步基础资料校验，改为投递可转债后台初始化。 |
| `backend-java/finance-service/src/main/java/com/scrapider/finance/task/BondMarketSyncTask.java` | 让单债日度估值同步只在确认落库后返回成功。 |
| `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/SceneAssetDataEnsureServiceImpl.java` | 委托共享确保服务，保留 AI DTO 转换和快照覆盖。 |
| `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/handler/StockFundamentalContextActionHandler.java` | 首次股票基本面查询调用共享确保服务并追加刷新 metadata。 |
| `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/handler/ConvertibleBondContextActionHandler.java` | 首次可转债上下文查询调用共享确保服务并追加刷新 metadata。 |
| `backend-java/finance-data/src/main/java/com/scrapider/finance/domain/vo/StockConfigAddResultVO.java` | 增加后台初始化已投递状态。 |
| `backend-java/finance-data/src/main/java/com/scrapider/finance/domain/vo/BondConfigAddResultVO.java` | 增加后台初始化已投递状态。 |
| `backend-java/finance-service/src/main/java/com/scrapider/finance/converter/SystemConfigConverter.java` | 按真实完成状态组装新增可转债结果。 |
| `frontend-vue/apps/web-ele/src/api/stock/index.ts` | 扩展股票新增结果类型。 |
| `frontend-vue/apps/web-ele/src/api/bond/index.ts` | 扩展可转债新增结果类型。 |
| `frontend-vue/apps/web-ele/src/views/system-config/target-config/index.vue` | 将新增成功提示改为“后台初始化已启动”。 |

## 共享接口

```java
package com.scrapider.finance.service;

import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.StockConfigPO;

public interface AssetDataEnsureService {

    AssetDataEnsureResult ensureStockData(StockConfigPO stock);

    AssetDataEnsureResult ensureConvertibleBondData(BondConfigPO bond);

    boolean ensureConvertibleBondDailyValuations(BondConfigPO bond);
}
```

```java
package com.scrapider.finance.service;

import java.util.List;

public record AssetDataEnsureResult(boolean refreshAttempted, List<String> unavailableSections) {

    public boolean completed() {
        return this.unavailableSections().isEmpty();
    }
}
```

```java
package com.scrapider.finance.service;

import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.StockConfigPO;

public interface AssetDataInitializationService {

    boolean scheduleStockInitialization(StockConfigPO stock);

    boolean scheduleConvertibleBondInitialization(BondConfigPO bond);
}
```

### Task 1: 建立共享标的数据确保服务

**Files:**
- Create: `backend-java/finance-service/src/main/java/com/scrapider/finance/service/AssetDataEnsureService.java`
- Create: `backend-java/finance-service/src/main/java/com/scrapider/finance/service/AssetDataEnsureResult.java`
- Create: `backend-java/finance-service/src/main/java/com/scrapider/finance/service/AssetDataEnsurePolicy.java`
- Create: `backend-java/finance-service/src/main/java/com/scrapider/finance/service/impl/AssetDataEnsureServiceImpl.java`
- Create: `backend-java/finance-service/src/test/java/com/scrapider/finance/service/impl/AssetDataEnsureServiceImplTest.java`
- Modify: `backend-java/finance-service/src/main/java/com/scrapider/finance/task/BondMarketSyncTask.java:215-248`

**Interfaces:**
- Consumes: `StockFundamentalProvider`、`ConvertibleBondDataProvider`、既有 Stock/ConvertibleBond Manage。
- Produces: `ensureStockData`、`ensureConvertibleBondData`、`ensureConvertibleBondDailyValuations`，供初始化服务、Agent 和 Report 使用。

- [x] **Step 1: 先写共享服务失败测试**

创建 `AssetDataEnsureServiceImplTest`，先覆盖以下行为：

```java
@Test
void returnsFalseWhenDailyProviderReturnsNoValuation() {
    FakeValuationManage valuationManage = new FakeValuationManage();
    FakeConvertibleBondDataProvider provider = new FakeConvertibleBondDataProvider();
    provider.dailyValuations = List.of();

    boolean result = service(valuationManage, provider).ensureConvertibleBondDailyValuations(bond());

    assertThat(result).isFalse();
}

@Test
void returnsTrueOnlyAfterDailyValuationIsPersisted() {
    FakeValuationManage valuationManage = new FakeValuationManage();
    FakeConvertibleBondDataProvider provider = new FakeConvertibleBondDataProvider();
    provider.dailyValuations = List.of(valuation());

    boolean result = service(valuationManage, provider).ensureConvertibleBondDailyValuations(bond());

    assertThat(result).isTrue();
    assertThat(valuationManage.latest).isNotNull();
}
```

Run: `mvn -pl finance-service -Dtest=AssetDataEnsureServiceImplTest test`

Expected: 测试编译或执行失败，原因是共享服务尚不存在；不得先创建生产代码。

另增加两线程回归：同一债券的两个 `ensureConvertibleBondDailyValuations` 调用必须共享首轮刷新，待首轮落库后第二个调用重查本地数据，Provider 只调用一次。

- [x] **Step 2: 定义共享接口与结果记录**

创建上述三个公开类型。`unavailableSections` 只使用明确业务键：股票为 `industry`、`valuation`、`financialIndicators`、`dividends`；可转债为 `basic`、`dailyValuation`、`shareChanges`。结果列表必须以不可变副本返回。

同时创建唯一的策略常量类：

```java
public final class AssetDataEnsurePolicy {

    public static final int STOCK_VALUATION_LIMIT = 250;
    public static final int STOCK_FINANCIAL_LIMIT = 10;
    public static final int STOCK_DIVIDEND_LIMIT = 10;
    public static final int STOCK_FUNDAMENTAL_FRESH_DAYS = 7;
    public static final int STOCK_DIVIDEND_FRESH_DAYS = 30;
    public static final int CONVERTIBLE_BOND_FRESH_DAYS = 7;

    private AssetDataEnsurePolicy() {
    }
}
```

这些值从当前 AI 确保服务迁移而来；业务层和 AI 层只能引用此类，不能各自定义副本。

- [x] **Step 3: 将股票刷新逻辑下沉到 `AssetDataEnsureServiceImpl`**

把 `SceneAssetDataEnsureServiceImpl` 当前的以下行为完整迁移到 `finance-service`：

```java
private AssetDataEnsureResult doEnsureStockData(StockConfigPO stock) {
    boolean refreshAttempted = false;
    List<String> unavailableSections = new ArrayList<>();
    refreshAttempted |= this.ensureIndustryInfoFresh(stock, unavailableSections);
    refreshAttempted |= this.ensureValuationHistoryFresh(stock, unavailableSections);
    this.ensureIndustryInfoFromValuationHistory(stock);
    refreshAttempted |= this.ensureFinancialIndicatorsFresh(stock, unavailableSections);
    refreshAttempted |= this.ensureDividendHistoryFresh(stock, unavailableSections);
    return new AssetDataEnsureResult(refreshAttempted, List.copyOf(unavailableSections));
}
```

保持原有估值 250、财务/分红 10、财务七天、分红三十天的值及含义；实现只能引用 `AssetDataEnsurePolicy`。行业 Provider 返回空时必须继续执行估值板块名称回填。

- [x] **Step 4: 实现可转债基础资料、份额和日度估值确保逻辑**

使用 `ObjectProvider<ConvertibleBondDataProvider>`；不再依赖仅供 Report 的 `ConvertibleBondSceneDataProvider`。基础资料和份额按既有七天时效刷新；日度估值只在当前债券没有任何记录时拉取，拉取条数复用已配置的 `${bond.sync.convertible-daily-limit}`。

```java
public boolean ensureConvertibleBondDailyValuations(BondConfigPO bond) {
    if (bond == null || StrUtil.isBlank(bond.getBondCode())) {
        return false;
    }
    if (this.convertibleBondDailyValuationManage.latestByBondCode(bond.getBondCode()) != null) {
        return true;
    }
    ConvertibleBondDataProvider provider = this.convertibleBondDataProvider.getIfAvailable();
    if (provider == null) {
        return false;
    }
    List<ConvertibleBondDailyValuationPO> rows = provider.getDailyValuations(bond, this.convertibleDailyLimit);
    this.convertibleBondDailyValuationManage.saveValuations(rows);
    return this.convertibleBondDailyValuationManage.latestByBondCode(bond.getBondCode()) != null;
}
```

`ensureConvertibleBondData` 必须将基础资料、日度估值、份额的未获得状态填入结果；空列表不能被解释为成功。

聚合方法内部调用私有 `doEnsureConvertibleBondDailyValuations`，不能在已持有 `bond:<债券代码>` 串行 key 时再次调用公开包装方法，避免当前线程等待自身 gate。

- [x] **Step 5: 合并同一标的进行中的刷新**

在实现类中使用以 `stock:<股票代码>`、`bond:<债券代码>` 为键的 `ConcurrentHashMap<String, CompletableFuture<Void>>` 作为单标的 gate。第一个调用方同步执行实际刷新并完成 gate；后续调用方等待该轮结束后重新取得 gate 并复查自己的数据缺失项；无论成功或异常都在 finally 中移除对应 key。这样后台任务和 Agent 首查不会并发访问同一个数据源，也不会把“仅日度估值”的结果误作“完整可转债数据”的结果。两线程日度估值测试验证了等待方不会重复调用 Provider。

```java
private <T> T executeExclusively(String key, Supplier<T> action) {
    while (true) {
        CompletableFuture<Void> created = new CompletableFuture<>();
        CompletableFuture<Void> existing = this.inFlight.putIfAbsent(key, created);
        if (existing != null) {
            existing.join();
            continue;
        }
        try {
            return action.get();
        } finally {
            created.complete(null);
            this.inFlight.remove(key, created);
        }
    }
}
```

- [x] **Step 6: 修正 `BondMarketSyncTask` 的单债日度估值成功语义**

注入 `AssetDataEnsureService`，将原来“Provider 调用未抛异常即返回 true”的实现替换为：

```java
public boolean syncConvertibleDailyDataForBond(String bondCode) {
    BondConfigPO bond = this.bondConfigManage.getEnabledByBondCode(bondCode);
    if (!this.convertibleDataEnabled || bond == null) {
        log.warn("无法同步可转债日度估值，bondCode: {}", bondCode);
        return false;
    }
    try {
        return this.assetDataEnsureService.ensureConvertibleBondDailyValuations(bond);
    } catch (Exception ex) {
        log.warn("同步可转债日度估值失败，bondCode: {}", bondCode, ex);
        return false;
    }
}
```

删除因此不再使用的 `ConvertibleBondDataProvider`、`ConvertibleBondDailyValuationManage`、`convertibleDailyLimit` 字段、对应 import 与构造器参数；`convertibleDataEnabled` 保留，继续作为手动单债补齐是否启用的既有开关。

- [x] **Step 7: 运行共享服务测试和编译**

Run: `mvn -pl finance-service -am -Dtest=AssetDataEnsureServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: `BUILD SUCCESS`；新增的日度估值空结果与持久化确认测试通过。

### Task 2: 将新增后的慢数据改为后台初始化

**Files:**
- Create: `backend-java/finance-service/src/main/java/com/scrapider/finance/service/AssetDataInitializationService.java`
- Create: `backend-java/finance-service/src/main/java/com/scrapider/finance/service/impl/AssetDataInitializationServiceImpl.java`
- Modify: `backend-java/finance-service/src/main/java/com/scrapider/finance/service/impl/SystemConfigStockServiceImpl.java:19-48`
- Modify: `backend-java/finance-service/src/main/java/com/scrapider/finance/service/impl/SystemConfigBondServiceImpl.java:20-99`
- Modify: `backend-java/finance-data/src/main/java/com/scrapider/finance/domain/vo/StockConfigAddResultVO.java:6-28`
- Modify: `backend-java/finance-data/src/main/java/com/scrapider/finance/domain/vo/BondConfigAddResultVO.java:6-20`
- Modify: `backend-java/finance-service/src/main/java/com/scrapider/finance/converter/SystemConfigConverter.java:55-76`

**Interfaces:**
- Consumes: Task 1 的 `AssetDataEnsureService`、既有 `StockMarketSyncTask`、`BondMarketSyncTask`。
- Produces: 新增接口的 `initializationScheduled` 状态和非阻塞初始化行为。

- [x] **Step 1: 实现后台初始化调度器**

`AssetDataInitializationServiceImpl` 使用 `CompletableFuture.runAsync` 执行；行情同步和基础数据确保分别捕获异常、输出中文日志，任一失败不阻断另一项，也不向新增调用方传播异常。

```java
public boolean scheduleStockInitialization(StockConfigPO stock) {
    if (stock == null || StrUtil.isBlank(stock.getStockCode())) {
        return false;
    }
    CompletableFuture.runAsync(() -> this.initializeStock(stock));
    return true;
}

public boolean scheduleConvertibleBondInitialization(BondConfigPO bond) {
    if (bond == null || StrUtil.isBlank(bond.getBondCode())) {
        return false;
    }
    CompletableFuture.runAsync(() -> this.initializeConvertibleBond(bond));
    return true;
}
```

- [x] **Step 2: 改造股票新增链路**

`SystemConfigStockServiceImpl.addStock` 保留快照查询、代码/名称校验、配置和快照保存；移除同步 `syncStockTrend` 调用，改为：

```java
boolean initializationScheduled = this.assetDataInitializationService.scheduleStockInitialization(stock);
return StockConfigAddResultVO.of(StockQuoteVO.fromPO(snapshot), false, initializationScheduled);
```

`quoteSynced` 保持 true；`trendSynced` 为 false 表示响应返回时尚未完成；新增 `initializationScheduled` 表示已成功投递后台工作。

- [x] **Step 3: 改造可转债新增链路**

保留 `fetchAndValidateBasic`、债券配置/基础资料保存和正股 `addStock` 调用；移除同步 `syncMarketDataForBond`、`syncConvertibleDailyDataForBond` 调用，改为：

```java
boolean initializationScheduled = this.assetDataInitializationService.scheduleConvertibleBondInitialization(bond);
return this.toResult(bond, basic, underlyingStock, initializationScheduled);
```

转换器必须设置：`basicSynced=true`、`underlyingStockSynced=underlyingStock != null`、`marketDataSynced=false`、`dailyValuationSynced=false`、`shareSynced=false`、`initializationScheduled=initializationScheduled`。这些 false 表示响应时尚未完成，不是后台任务失败。

- [x] **Step 4: 扩展新增结果 VO**

在两个 VO 中添加 `private Boolean initializationScheduled;`；修改 `StockConfigAddResultVO.of` 的签名为：

```java
public static StockConfigAddResultVO of(
        StockQuoteVO quote,
        boolean trendSynced,
        boolean initializationScheduled)
```

并写入对应字段。所有调用点必须使用新签名，不能遗留旧重载以掩盖状态含义。

- [x] **Step 5: 编译 `finance-service` 与依赖模块**

Run: `mvn -pl finance-service -am -DskipTests compile`

Expected: `BUILD SUCCESS`。

### Task 3: 让 Report 委托共享确保服务

**Files:**
- Modify: `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/service/impl/SceneAssetDataEnsureServiceImpl.java:1-281`
- Modify if constructor changes require fixture compatibility: `backend-java/finance-ai/src/test/java/com/scrapider/finance/ai/service/impl/SceneAssetDataEnsureServiceImplTest.java:1-260`

**Interfaces:**
- Consumes: Task 1 的 `AssetDataEnsureService`。
- Produces: 保持现有 `SceneAssetDataEnsureService` 对 Report 调用方的接口和 DTO 输出不变。

- [x] **Step 1: 收缩 AI 层确保服务职责**

将外部 Provider、基本面保存和 freshness 逻辑从 `SceneAssetDataEnsureServiceImpl` 删除，改为注入 `AssetDataEnsureService` 及读取 DTO 所需的 Manage。股票路径固定执行：

```java
this.assetDataEnsureService.ensureStockData(stockConfig);
return StockSceneDataConverter.toDTO(
        this.stockIndustryInfoManage.getBySecid(stockConfig.getSecid()),
        this.stockValuationHistoryManage.listByStockCode(
                stockConfig.getStockCode(), AssetDataEnsurePolicy.STOCK_VALUATION_LIMIT),
        this.stockFinancialIndicatorManage.listByStockCode(
                stockConfig.getStockCode(), AssetDataEnsurePolicy.STOCK_FINANCIAL_LIMIT),
        this.stockDividendHistoryManage.listByStockCode(
                stockConfig.getStockCode(), AssetDataEnsurePolicy.STOCK_DIVIDEND_LIMIT));
```

保留 `SNAPSHOT_OVERLAY_SOURCE`；股票 DTO 查询上限改为引用 `AssetDataEnsurePolicy`，不再在 AI 层定义数值。

- [x] **Step 2: 让可转债 Report 补齐空日度估值**

在 `ensureBondSceneData` 开头调用：

```java
this.assetDataEnsureService.ensureConvertibleBondData(bond);
```

随后从现有 Manage 查询 basic、share、valuation，并复用现有 `overlayLatestValuation`、`overlay`、`copy` 实现。这样 Report 不改变 DTO 结构，却能补齐原来空的 `cb_daily`。

- [x] **Step 3: 维护现有 Report 测试兼容性**

将既有 fake Provider 替换为 fake `AssetDataEnsureService`。保留同日覆盖、跨日合成覆盖、基础资料/份额刷新后的 DTO 读取，并新增“共享服务已落库日度估值后 Report 重查”的断言。

- [x] **Step 4: 运行既有 AI 确保服务测试**

Run: `mvn -pl finance-ai -am -Dtest=SceneAssetDataEnsureServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: `BUILD SUCCESS`，四个 Report 场景保持通过。

### Task 4: 为 Agent 上下文 Handler 加入首查兜底

**Files:**
- Modify: `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/handler/StockFundamentalContextActionHandler.java:29-346`
- Modify: `backend-java/finance-ai/src/main/java/com/scrapider/finance/ai/handler/ConvertibleBondContextActionHandler.java:29-279`

**Interfaces:**
- Consumes: Task 1 的 `AssetDataEnsureService` 和 `AssetDataEnsureResult`。
- Produces: 不变的 `data`、`dataCompleteness`，新增兼容的 `metadata.dataRefresh`。

- [x] **Step 1: 在股票 Handler 中确保数据并安全回退**

先查询本地数据；仅当请求的估值、分红或财务指标缺失/过期时，使用 `stockConfigManage.getEnabledByStockCode(target.targetCode())` 取得配置并调用共享服务。捕获异常并记录 warning，不能让刷新异常覆盖已有查询结果。

```java
StockFundamentalData data = this.loadFundamentalData(target.targetCode(), sections, limit);
if (this.requiresRefresh(target.targetCode(), sections, data) && stock != null) {
    AssetDataEnsureResult result = this.assetDataEnsureService.ensureStockData(stock);
    dataRefresh = AgentDataRefreshMetadata.completed(List.copyOf(sections), result);
    data = this.loadFundamentalData(target.targetCode(), sections, limit);
}
```

使用包内共享的 `AgentDataRefreshMetadata` 组装兼容 Map。成功刷新状态为 `completed` 或 `partial`；异常状态及 `failureReason` 固定为 `refresh_failed`，详细堆栈仅写服务端日志。仅在实际触发兜底时追加该 metadata，保持已有只读查询响应不变。

- [x] **Step 2: 在可转债 Handler 中确保数据并安全回退**

先查询本地数据；当基础资料、日度估值或份额缺失时，或基础资料/份额超过七天时，使用 `bondConfigManage.getEnabledByBondCode(target.targetCode())` 获取配置后调用 `ensureConvertibleBondData`。无配置但可通过历史 basic 解析到目标时，保持旧的只读行为，不对未配置标的发起外部拉取；日度估值维持既有“仅缺失补齐”语义。

```java
ConvertibleBondContextData data = this.loadContextData(target.targetCode(), sections, limit);
if (this.requiresRefresh(target.targetCode(), sections, data) && bond != null) {
    AssetDataEnsureResult result = this.assetDataEnsureService.ensureConvertibleBondData(bond);
    dataRefresh = AgentDataRefreshMetadata.completed(List.copyOf(sections), result);
    data = this.loadContextData(target.targetCode(), sections, limit);
}
```

- [x] **Step 3: 保持 Agent 协议兼容性**

不要修改 `AgentDataGatewayResponseVO`、Python `stock_fundamental_context_tool.py`、`convertible_bond_context_tool.py` 或工具注册表。`metadata` 是可扩展 Map，旧调用方会忽略新增 `dataRefresh`。

- [x] **Step 4: 编译并运行既有 Agent 网关测试**

Run: `mvn -pl finance-ai -am -Dtest=AgentDataGatewayServiceImplTest,SceneAssetDataEnsureServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: `BUILD SUCCESS`；网关的 running 进度和异常传播既有行为保持通过，并覆盖股票分红缺失/过期、可转债基础资料/份额过期的首查兜底。

### Task 5: 更新新增结果类型与系统配置提示

**Files:**
- Modify: `frontend-vue/apps/web-ele/src/api/stock/index.ts:110-121`
- Modify: `frontend-vue/apps/web-ele/src/api/bond/index.ts:95-106`
- Modify: `frontend-vue/apps/web-ele/src/views/system-config/target-config/index.vue:90-104`

**Interfaces:**
- Consumes: Task 2 返回的 `initializationScheduled`。
- Produces: 前端对“已排队”与“已完成”的准确提示。

- [x] **Step 1: 扩展 TypeScript API 类型**

在 `StockConfigAddResult` 和 `BondConfigAddResult` 中添加：

```ts
initializationScheduled: boolean;
```

不要删除或重命名已有同步状态字段。

- [x] **Step 2: 修正股票新增成功提示**

将原有只按 `trendSynced` 显示“分时同步完成/未完成”的提示替换为：

```ts
if (result.initializationScheduled) {
  ElMessage.success(`${result.stockName} 快照同步完成，已加入后台初始化`);
} else {
  ElMessage.warning(`${result.stockName} 快照同步完成，后台初始化未成功投递`);
}
```

这样响应时 `trendSynced=false` 不会被错误呈现为同步失败。

- [x] **Step 3: 执行前端类型检查**

Run: `corepack pnpm@11.2.2 --dir frontend-vue run check:type`

Expected: typecheck 成功；若仓库已有无关错误，记录其文件和错误文本，不修改无关代码。

### Task 6: 全量验证与交付检查

**Files:**
- Modify: `docs/superpowers/specs/2026-07-13-asset-initialization-agent-fallback-design.md`，仅在实施发现与规格不一致时更新事实描述。
- Modify: `docs/superpowers/plans/2026-07-13-asset-initialization-agent-fallback.md`，在执行时勾选已完成步骤。

**Interfaces:**
- Consumes: Task 1 至 Task 5 的最终实现。
- Produces: 经编译、既有测试、静态检查和人工路径检查验证的交付结果。

- [x] **Step 1: 运行后端编译与既有测试**

Run: `mvn -pl finance-ai -am -Dtest=AssetDataEnsureServiceImplTest,AssetDataInitializationServiceImplTest,SystemConfigStockServiceImplTest,SystemConfigBondServiceImplTest,BondMarketSyncTaskTest,SceneAssetDataEnsureServiceImplTest,StockFundamentalContextActionHandlerTest,ConvertibleBondContextActionHandlerTest,AgentDataGatewayServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: `BUILD SUCCESS`。

- [x] **Step 2: 检查差异格式与未预期改动**

Run: `git diff --check`

Expected: 无输出且退出码为 0。使用 `git status --short` 对比任务前状态，确认没有覆盖既有的交易日/网络可靠性改动或用户的 `database/rabbitmq/definitions.json` 改动。

- [ ] **Step 3: 受控手工验证路径**

在具备有效行情与 Tushare 凭据的环境中执行以下顺序：

```text
1. 新增一只股票，响应立即返回 quoteSynced=true、trendSynced=false、initializationScheduled=true。
2. 新增一只可转债，响应立即返回 basicSynced=true、marketDataSynced=false、dailyValuationSynced=false、shareSynced=false、initializationScheduled=true。
3. 后台日志确认对应初始化任务开始和结束。
4. 初始化未结束时调用 stock.fundamental_context 或 convertible_bond.context，确认 metadata.dataRefresh 存在且数据补齐后 dataCompleteness 不再为空。
5. 提交同一标的 Report，确认 Report 仍能读取已保存数据，且空日度估值会触发补齐。
```

- [x] **Step 4: 记录未验证项**

当前环境未提供可用于受控新增的有效行情与 Tushare 凭据，因此外部 Provider 真实调用、后台日志和前端人工路径均未执行。已完成聚焦 Maven 测试、`finance-app` 编译和前端类型检查；不要将上述未执行项表述为已验证。
