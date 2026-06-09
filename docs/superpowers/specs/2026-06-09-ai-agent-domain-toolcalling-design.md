# AI Agent 业务域 Tool Calling 扩展设计规格

## 背景

当前 AI Agent 已经具备有限多轮 Tool Calling 能力，已注册的核心工具包括 `market_quote`、`watch_pool_context`、`market_kline_trend` 和 `market_intraday_summary`。其中 `market_quote` 负责查询行情快照，K 线和分时工具负责把大量行情序列压缩为趋势摘要。

下一步需要把股票基本面、可转债资料和已有分析报告纳入 Agent 能力范围。相关数据底层来自 `stock_financial_indicator`、`stock_dividend_history`、`stock_valuation_history`、`convertible_bond_daily_valuation`、`convertible_bond_share`、`convertible_bond_basic` 和 `scene_analysis_report` 等表。

这些表存在较多和快照表重复的字段。如果按表暴露工具，模型会看到过多底层概念，并且容易重复调用或把实时快照、历史估值、基础条款混在一起。本次设计采用业务域聚合工具：快照继续由 `market_quote` 负责，新工具只补充快照以外的慢变量、历史变量和报告变量。

## 目标

1. 保持 `market_quote` 作为唯一行情快照工具，不在新增工具里重复实时价格、涨跌幅、成交、换手等快照字段。
2. 新增股票基本面聚合工具，覆盖财务指标、分红历史和估值历史。
3. 新增可转债聚合工具，覆盖基础条款、每日估值历史和转股/余额变化。
4. 新增报告上下文工具，允许 Agent 查询当前用户已有报告和报告详情。
5. 隐藏底层表结构，模型只面对业务语义清晰的工具。
6. 所有工具保持只读，不引入交易、修改观察池或触发报告生成等写操作。

## 不做范围

- 不删除数据库字段。
- 不改造快照表结构。
- 不把每张表单独暴露成一个模型工具。
- 不触发生成新报告。
- 不接入知识库召回或 RAG。
- 不返回完整原始 PO、完整历史序列或原始接口响应。
- 不把工具结果直接设计成前端展示协议；前端仍渲染模型最终回答。

## 工具边界

### market_quote

保留现有工具，继续负责当前行情快照。

适用问题：

- “现在价格多少”
- “今天涨跌怎么样”
- “成交、换手、量比怎么样”
- “当前 PE/PB 是多少”

边界：

- 当前价格、涨跌幅、成交额、成交量、换手率、振幅、量比、最新 PE/PB 等快照字段只从 `market_quote` 返回。
- 新增业务域工具不重复这些字段。

### stock_fundamental_context

新增股票基本面工具，聚合股票财务、分红和估值历史。

建议工具签名：

```python
stock_fundamental_context(
    target_code: str | None = None,
    target_name: str | None = None,
    sections: list[str] | None = None,
    limit: int = 8,
) -> str
```

`sections` 可选值：

- `valuation_history`
- `financial_indicator`
- `dividend_history`

默认返回全部分区。`limit` 控制每个历史分区最多返回条数，Java 侧应设置上限，避免模型上下文膨胀。

内部数据来源：

- `stock_valuation_history`
- `stock_financial_indicator`
- `stock_dividend_history`

返回结构建议：

```json
{
  "target": {
    "targetType": "stock",
    "targetCode": "002958",
    "targetName": "青农商行"
  },
  "valuationHistory": [
    {
      "tradeDate": "2026-06-09",
      "peTtm": 5.04,
      "pbMrq": 0.43,
      "totalMarketValue": 16100000000
    }
  ],
  "financialIndicators": [
    {
      "reportDate": "2026-03-31",
      "reportType": "quarter",
      "revenue": 1230000000,
      "netProfit": 320000000,
      "roe": 2.1,
      "netInterestMargin": 1.82,
      "nonPerformingLoanRatio": 1.28
    }
  ],
  "dividendHistory": [
    {
      "reportDate": "2025-12-31",
      "exDividendDate": "2026-05-20",
      "cashDividendPerShare": 0.12,
      "dividendYield": 4.1
    }
  ],
  "dataCompleteness": {
    "complete": true,
    "missing": []
  }
}
```

字段策略：

- 估值历史保留日期和估值变化需要的字段。
- 财务指标保留报告期、营收、利润、ROE、利润率、资产负债类和银行专项关键字段。
- 分红历史保留报告期、除权日、分红金额、股息率等字段。
- 不返回实时价格、涨跌幅、成交额、换手率等快照字段。

### convertible_bond_context

新增可转债工具，聚合可转债基础条款、估值历史和余额变化。

建议工具签名：

```python
convertible_bond_context(
    target_code: str | None = None,
    target_name: str | None = None,
    sections: list[str] | None = None,
    limit: int = 8,
) -> str
```

`sections` 可选值：

- `basic`
- `valuation_history`
- `share_change`

默认返回全部分区。

内部数据来源：

- `convertible_bond_basic`
- `convertible_bond_daily_valuation`
- `convertible_bond_share`

返回结构建议：

```json
{
  "target": {
    "targetType": "bond",
    "targetCode": "113xxx",
    "targetName": "某某转债"
  },
  "basic": {
    "underlyingStockCode": "600000",
    "underlyingStockName": "浦发银行",
    "rating": "AA+",
    "remainingSize": 18.2,
    "conversionPrice": 12.35,
    "maturityDate": "2030-06-01",
    "maturityCallPrice": 110.0,
    "couponRate": "第一年0.3%，第二年0.5%",
    "redeemClause": "...",
    "putbackClause": "...",
    "resetClause": "..."
  },
  "valuationHistory": [
    {
      "tradeDate": "2026-06-09",
      "conversionValue": 92.3,
      "premiumRate": 28.6,
      "pureBondValue": 88.4,
      "ytm": 1.9
    }
  ],
  "shareChanges": [
    {
      "endDate": "2026-03-31",
      "remainingSize": 18.2,
      "convertedAmount": 0.6,
      "convertedRatio": 3.2
    }
  ],
  "dataCompleteness": {
    "complete": true,
    "missing": []
  }
}
```

字段策略：

- 基础条款保留转股价、到期日、评级、剩余规模、赎回/回售/下修条款。
- 估值历史保留转股价值、溢价率、纯债价值、YTM。
- 余额变化保留期末日期、剩余规模和转股变化。
- 不返回可转债快照中的实时价格、涨跌幅、成交额、换手率等字段。

### scene_report_context

新增已有报告查询工具，只读取 `scene_analysis_report`。

建议工具签名：

```python
scene_report_context(
    target_type: str | None = None,
    target_code: str | None = None,
    report_id: int | None = None,
    limit: int = 3,
) -> str
```

行为：

- 有 `report_id` 时，查询当前用户可见的报告详情。
- 无 `report_id` 且有 `target_type`、`target_code` 时，查询该标的最近报告列表。
- 无足够定位参数时，返回参数不足错误，不自动枚举用户全部报告。

权限边界：

- Java 侧从 Agent Session 获取 `userId`。
- 查询必须限定当前用户可见范围。
- Python 不能传入或覆盖 `userId`。

返回结构建议：

```json
{
  "target": {
    "targetType": "stock",
    "targetCode": "002958",
    "targetName": "青农商行"
  },
  "reports": [
    {
      "reportId": 123,
      "reportType": "quick_analysis",
      "versionNo": 1,
      "status": "success",
      "generatedAt": "2026-06-09T18:20:00",
      "title": "青农商行快速分析",
      "conclusion": "估值偏低但成长弹性有限",
      "reportTextPreview": "..."
    }
  ],
  "dataCompleteness": {
    "complete": true,
    "missing": []
  }
}
```

边界：

- 第一版不触发新报告生成。
- 报告正文需要截断，默认只返回摘要和预览。
- 仅当用户明确要求“报告详情”或传入 `report_id` 时返回较长正文。

## 字段去重规则

新增工具统一应用输出层字段去重，不修改数据库 schema。

通用删除字段：

- `id`
- `rawResponse`
- `source`
- `createdAt`
- `updatedAt`
- `syncedAt`
- `secid`
- `secucode`
- `tsCode`
- `marketCode`
- `exchangeCode`

身份字段处理：

- 顶层 `target` 统一放一次 `targetType`、`targetCode`、`targetName`。
- 分区列表内不重复 `stockCode`、`stockName`、`bondCode`、`bondName`。

快照字段处理：

- `latestPrice`
- `changePercent`
- `changeAmount`
- `turnover`
- `volume`
- `turnoverRate`
- `amplitude`
- `volumeRatio`

这些字段只由 `market_quote` 返回。新工具如果需要解释历史估值变化，应返回历史日期和估值字段，而不是重复当前快照。

## 数据流

Python 侧：

```text
AgentToolRegistry
  -> stock_fundamental_context / convertible_bond_context / scene_report_context
  -> 对应 Python Tool
  -> Agent data gateway HTTP
```

Java 侧：

```text
AgentDataGatewayService
  -> AgentDataActionHandler
  -> Manage / Mapper
  -> 业务聚合和字段压缩
  -> AgentDataGatewayResponseVO
```

建议新增 Java action：

- `stock.fundamental_context`
- `convertible_bond.context`
- `scene_report.context`

建议新增 Python tool 文件：

- `stock_fundamental_context_tool.py`
- `convertible_bond_context_tool.py`
- `scene_report_context_tool.py`

## 模型选择规则

Prompt 中补充以下选择规则：

- 当前价格、涨跌、成交、换手、量比、最新估值：调用 `market_quote`。
- 财务、分红、估值历史、基本面质量：调用 `stock_fundamental_context`。
- 可转债条款、转股价、剩余规模、溢价率历史、纯债价值、YTM、转股变化：调用 `convertible_bond_context`。
- 历史报告、最近结论、已有分析：调用 `scene_report_context`。
- 全面分析股票：可组合 `market_quote`、`stock_fundamental_context`、`market_kline_trend`、`market_intraday_summary`。
- 全面分析可转债：可组合 `market_quote`、`convertible_bond_context`、`market_kline_trend`、`market_intraday_summary`。

工具预算仍由有限多轮 loop 控制，第一版不为这些工具单独增加更高预算。

## 错误处理

- 标的参数缺失时，Java action 返回 `success=false` 和明确错误码。
- 找不到标的时，返回空数据和 `dataCompleteness.missing`，不抛出不可恢复异常。
- 某个分区无数据时，只标记该分区 missing，不影响其他分区返回。
- `sections` 包含未知值时，忽略未知值并在 metadata 中返回 warning。
- 报告权限不匹配时，返回不可见或不存在，不暴露其他用户报告信息。

## 验证标准

实现时至少验证：

1. Python agent 包可以通过 `compileall`。
2. Java `finance-ai` 及依赖模块可以通过 `mvn -pl backend-java/finance-ai -am -DskipTests compile`。
3. 新增 action 在重复注册时仍能被 `AgentDataGatewayServiceImpl` 拦截。
4. `stock_fundamental_context` 不返回快照实时字段。
5. `convertible_bond_context` 不返回可转债快照实时字段。
6. `scene_report_context` 只能返回当前 Agent Session 用户可见报告。
7. Prompt 中工具选择规则不会要求模型按底层表名调用工具。

