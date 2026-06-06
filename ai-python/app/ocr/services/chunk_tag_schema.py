from dataclasses import dataclass


SCENE_CATEGORIES = (
    "asset",
    "price",
    "volume",
    "trend",
    "valuation",
    "sentiment",
    "risk_strategy",
)


@dataclass(frozen=True)
class TagDefinition:
    category: str
    tag: str
    name: str
    assets: tuple[str, ...]
    description: str


def _d(category: str, tag: str, name: str, assets: tuple[str, ...], description: str) -> TagDefinition:
    return TagDefinition(category, tag, name, assets, description)


TAG_DEFINITIONS: tuple[TagDefinition, ...] = (
    _d("asset", "general", "通用投资经验", ("通用",), "文本没有限定股票、指数、可转债、基金等具体资产，适用于通用投资行为、风险控制、学习复盘。"),
    _d("asset", "stock", "股票", ("股票",), "文本明确讨论股票、个股、股价、买股、卖股、个股走势。"),
    _d("asset", "index", "指数", ("指数",), "文本明确讨论大盘、指数、上证、深成指、创业板、科创板、板块指数。"),
    _d("asset", "convertible_bond", "可转债", ("可转债",), "文本出现可转债、转债、溢价率、强赎、正股联动、转股价值。"),
    _d("asset", "fund", "基金", ("基金",), "文本明确讨论基金、场内基金、场外基金、基金定投、基金配置。"),
    _d("asset", "etf", "ETF", ("基金",), "文本明确讨论 ETF、交易型开放式指数基金、场内 ETF 买卖。"),
    _d("asset", "lof", "LOF", ("基金",), "文本明确讨论 LOF、上市开放式基金、场内外套利。"),
    _d("asset", "index_fund", "指数基金", ("基金",), "文本讨论指数基金、被动跟踪、跟踪指数、指数增强。"),
    _d("asset", "active_fund", "主动基金", ("基金",), "文本讨论主动管理基金、基金经理选股、主动权益基金。"),
    _d("asset", "bond_fund", "债券基金", ("基金",), "文本讨论债基、纯债基金、一级债基、二级债基、久期和信用风险。"),
    _d("asset", "money_fund", "货币基金", ("基金",), "文本讨论货币基金、现金管理、七日年化、万份收益。"),
    _d("asset", "qdii_fund", "QDII 基金", ("基金",), "文本讨论 QDII、海外基金、汇率、海外市场、额度限制。"),
    _d("asset", "bank_stock", "银行股", ("股票",), "文本明确讨论银行股、银行板块、低 PB 银行、息差、分红。"),
    _d("asset", "low_price_stock", "低价股", ("股票",), "文本明确提到低价股，或讨论几元股、低价小票、低价补涨。"),
    _d("asset", "large_cap_stock", "大盘股", ("股票",), "文本讨论大市值、权重股、蓝筹、大盘股、机构重仓。"),
    _d("asset", "small_cap_stock", "小盘股", ("股票",), "文本讨论小市值、小盘股、题材小票、弹性较大。"),

    _d("price", "price_rise", "价格上涨", ("通用",), "文本讨论上涨、拉升、涨幅扩大、上涨过程中如何判断。"),
    _d("price", "price_drop", "价格下跌", ("通用",), "文本讨论下跌、杀跌、回落、跌幅扩大、下跌后的处理。"),
    _d("price", "sideways", "横盘", ("通用",), "文本讨论横盘、震荡、长期不涨、窄幅波动、区间整理。"),
    _d("price", "near_recent_high", "接近近期高位", ("通用",), "文本讨论高位、阶段高点、接近前高、涨到高位后的风险。"),
    _d("price", "near_recent_low", "接近近期低位", ("通用",), "文本讨论低位、阶段低点、跌到底部区域、低位观察。"),
    _d("price", "breakout", "突破", ("通用",), "文本讨论突破压力位、突破平台、突破前高、突破后的确认。"),
    _d("price", "pullback", "回调", ("通用",), "文本讨论上涨后的回调、回踩、调整、短线回落、回调买点。"),
    _d("price", "gap_up", "跳空高开", ("股票", "指数", "可转债"), "文本讨论跳空高开、高开缺口、高开后的风险或机会。"),
    _d("price", "gap_down", "跳空低开", ("股票", "指数", "可转债"), "文本讨论跳空低开、低开缺口、低开后的观察或风险。"),
    _d("price", "convertible_high_price_risk", "转债高价风险", ("可转债",), "文本讨论转债价格较高、高价债波动和回撤风险。"),
    _d("price", "convertible_low_price_defensive", "转债低价防御", ("可转债",), "文本讨论低价转债、防御性、接近债底、下行空间相对有限。"),
    _d("price", "fund_nav_rise", "基金净值上涨", ("基金",), "文本讨论基金净值上涨、净值修复、组合收益回升。"),
    _d("price", "fund_nav_drop", "基金净值下跌", ("基金",), "文本讨论基金净值下跌、净值回撤、组合收益走弱。"),

    _d("volume", "volume_expand", "放量", ("股票", "指数", "可转债", "ETF", "LOF"), "文本讨论放量、成交量放大、量能增加、资金明显活跃。"),
    _d("volume", "volume_shrink", "缩量", ("股票", "指数", "可转债", "ETF", "LOF"), "文本讨论缩量、成交量减少、量能不足、无人关注。"),
    _d("volume", "high_turnover", "高换手", ("股票", "可转债", "ETF", "LOF"), "文本讨论换手率高、交易活跃、筹码交换剧烈、分歧加大。"),
    _d("volume", "low_turnover", "低换手", ("股票", "可转债", "ETF", "LOF"), "文本讨论换手低、交易清淡、流动性不足、没人买卖。"),
    _d("volume", "volume_price_confirm", "量价配合", ("股票", "指数", "可转债", "ETF", "LOF"), "文本讨论上涨有量、下跌缩量、价格和成交量互相验证。"),
    _d("volume", "volume_price_divergence", "量价背离", ("股票", "指数", "可转债", "ETF", "LOF"), "文本讨论价格上涨但量没跟上、放量但价格不涨、量价不一致。"),
    _d("volume", "volume_spike", "成交量突然放大", ("股票", "指数", "可转债", "ETF", "LOF"), "文本讨论突然爆量、异常放量、某天成交量明显突增。"),
    _d("volume", "volume_dry_up", "成交枯竭", ("股票", "指数", "可转债", "ETF", "LOF"), "文本讨论成交极低、量能枯竭、没人交易、流动性很差。"),
    _d("volume", "fund_share_growth", "基金份额增长", ("基金",), "文本讨论基金份额增长、申购增加、规模或份额持续流入。"),
    _d("volume", "fund_share_shrink", "基金份额缩水", ("基金",), "文本讨论基金份额下降、赎回压力、规模或份额持续流出。"),

    _d("trend", "uptrend", "上升趋势", ("通用",), "文本讨论连续上涨、趋势向上、均线多头、逐步抬高。"),
    _d("trend", "downtrend", "下降趋势", ("通用",), "文本讨论连续下跌、趋势向下、弱势下行、反弹无力。"),
    _d("trend", "range_bound", "区间震荡", ("通用",), "文本讨论箱体震荡、区间波动、上下沿、震荡整理。"),
    _d("trend", "rebound", "反弹", ("通用",), "文本讨论下跌后的反弹、超跌反弹、短线修复。"),
    _d("trend", "pullback", "回调", ("通用",), "文本讨论上涨趋势中的回调、回踩、调整，且原趋势尚未明显破坏。"),
    _d("trend", "repair", "修复", ("通用",), "文本讨论下跌或弱势之后企稳、跌幅收敛、缓慢回升、修复结构。"),
    _d("trend", "trend_reversal", "趋势反转", ("通用",), "文本讨论趋势由弱转强、由强转弱、反转信号、拐点。"),
    _d("trend", "breakout_from_range", "横盘突破", ("通用",), "文本讨论长期横盘后突破、箱体突破、平台突破。"),
    _d("trend", "breakdown_from_range", "区间破位", ("通用",), "文本讨论横盘或支撑之后向下脱离区间、跌破平台、跌破关键支撑。"),
    _d("trend", "continuation", "趋势延续", ("通用",), "文本讨论前序趋势和当前趋势方向一致、上涨延续、下跌延续、趋势继续。"),
    _d("trend", "turn_weak", "转弱", ("通用",), "文本讨论上涨后动能衰减、上攻乏力、跌破支撑、趋势开始走弱。"),
    _d("trend", "turn_strong", "转强", ("通用",), "文本讨论下跌或横盘后重心抬升、走势转强、弱势改善、开始抬升。"),
    _d("trend", "failed_breakout", "突破失败", ("通用",), "文本讨论假突破、突破后回落、站不上去、冲高失败。"),
    _d("trend", "fund_nav_uptrend", "基金净值上升趋势", ("基金",), "文本讨论基金净值趋势向上、基金曲线持续修复、净值创新高。"),
    _d("trend", "fund_nav_downtrend", "基金净值下降趋势", ("基金",), "文本讨论基金净值趋势向下、净值持续回撤、基金曲线走弱。"),

    _d("valuation", "low_pe", "低 PE", ("股票",), "文本讨论低市盈率、PE 较低、盈利估值便宜。"),
    _d("valuation", "high_pe", "高 PE", ("股票",), "文本讨论高市盈率、估值过高、盈利无法支撑估值。"),
    _d("valuation", "low_pb", "低 PB", ("股票",), "文本讨论低市净率、破净、PB 小于 1、银行股低 PB。"),
    _d("valuation", "high_pb", "高 PB", ("股票",), "文本讨论高市净率、净资产估值偏高。"),
    _d("valuation", "high_dividend", "高股息", ("股票",), "文本讨论分红率、股息率、高股息策略、稳定分红。"),
    _d("valuation", "valuation_repair", "估值修复", ("股票",), "文本讨论低估值修复、估值回归、估值提升、补涨修复。"),
    _d("valuation", "valuation_trap", "低估值陷阱", ("股票",), "文本讨论低估值不一定安全、便宜有原因、低 PE / 低 PB 陷阱。"),
    _d("valuation", "fundamental_risk", "基本面风险", ("股票", "可转债", "基金"), "文本讨论业绩变差、盈利压力、资产质量、信用资质、持仓基本面等不确定。"),
    _d("valuation", "convertible_low_premium", "转股低溢价", ("可转债",), "文本讨论转股溢价率较低、股性较强、正股上涨时弹性更直接。"),
    _d("valuation", "convertible_high_premium", "转股高溢价", ("可转债",), "文本讨论转股溢价率较高、估值偏贵、正股上涨难以覆盖溢价。"),
    _d("valuation", "convertible_premium_compression", "溢价压缩", ("可转债",), "文本讨论转债溢价率下降、估值压缩、债价跟不上正股或高溢价回落。"),
    _d("valuation", "convertible_premium_expansion", "溢价扩张", ("可转债",), "文本讨论转债溢价率上升、资金推高转债估值、脱离正股上涨。"),
    _d("valuation", "convertible_debt_floor_support", "债底支撑", ("可转债",), "文本讨论纯债价值、债底、防御性、下修或到期价值提供支撑。"),
    _d("valuation", "convertible_high_ytm", "到期收益率较高", ("可转债",), "文本讨论到期收益率较高、持有到期收益、债性价值更突出。"),
    _d("valuation", "convertible_low_ytm", "到期收益率较低或为负", ("可转债",), "文本讨论到期收益率低、负收益率、价格已经透支债性收益。"),
    _d("valuation", "convertible_high_conversion_value", "转股价值较高", ("可转债",), "文本讨论转股价值高、接近平价或超过面值、转股价值支撑价格。"),
    _d("valuation", "fund_premium", "基金场内溢价", ("基金",), "文本讨论 ETF / LOF 场内价格高于净值、溢价交易、套利风险。"),
    _d("valuation", "fund_discount", "基金场内折价", ("基金",), "文本讨论 ETF / LOF 场内价格低于净值、折价、折价套利或折价原因。"),
    _d("valuation", "fund_high_fee", "费率偏高", ("基金",), "文本讨论管理费、托管费、销售费、申赎费较高侵蚀收益。"),
    _d("valuation", "fund_large_scale", "基金规模较大", ("基金",), "文本讨论基金规模大、流动性好、跟踪稳定、调仓不灵活。"),
    _d("valuation", "fund_small_scale", "基金规模较小", ("基金",), "文本讨论规模小、清盘风险、成交不足、跟踪误差扩大。"),
    _d("valuation", "fund_tracking_error", "跟踪误差", ("基金",), "文本讨论指数基金或 ETF 跟踪误差、跑输基准、复制偏差。"),
    _d("valuation", "fund_high_drawdown", "基金高回撤", ("基金",), "文本讨论基金最大回撤大、净值波动大、持有体验差。"),
    _d("valuation", "fund_stable_nav", "净值稳定", ("基金",), "文本讨论货基、债基或低波基金净值稳定、波动较小。"),

    _d("sentiment", "market_attention_rise", "关注度上升", ("通用",), "文本讨论市场开始关注、热度提高、人气变强、资金关注度上升。"),
    _d("sentiment", "short_term_emotion", "短线情绪升温", ("股票", "指数", "可转债", "ETF", "LOF"), "文本讨论短线情绪、短线资金活跃、情绪推动上涨。"),
    _d("sentiment", "panic_selling", "恐慌抛售", ("通用",), "文本讨论恐慌杀跌、情绪性卖出、踩踏、急跌恐慌。"),
    _d("sentiment", "news_driven", "消息驱动", ("通用",), "文本讨论新闻、公告、传闻、利好利空消息推动行情。"),
    _d("sentiment", "policy_driven", "政策驱动", ("通用",), "文本讨论政策影响、监管政策、宏观政策、行业政策刺激。"),
    _d("sentiment", "sector_rotation", "板块轮动", ("股票", "指数", "基金"), "文本讨论板块切换、资金从一个板块转向另一个板块、轮动行情。"),
    _d("sentiment", "weak_sentiment", "情绪偏弱", ("通用",), "文本讨论市场情绪弱、没人接力、上涨无力、关注度低。"),
    _d("sentiment", "herding_effect", "羊群效应 / 从众行为", ("股票", "指数", "可转债", "基金"), "文本讨论羊群效应、从众行为、交易趋同、基金净申购或净赎回拥挤。"),
    _d("sentiment", "institutional_behavior", "机构 / 基金行为", ("股票", "指数", "基金"), "文本讨论机构投资者、证券投资基金、基金持仓、调研、基金中报、基金年报。"),
    _d("sentiment", "convertible_stock_linkage", "正股联动", ("可转债",), "文本讨论转债与正股同涨同跌、正股驱动转债、股性增强。"),
    _d("sentiment", "convertible_independent_strength", "转债独立走强", ("可转债",), "文本讨论转债脱离正股独立上涨、资金炒作转债、债性或条款驱动走强。"),
    _d("sentiment", "fund_flow_in", "基金资金流入", ("基金",), "文本讨论 ETF 资金流入、基金申购增加、份额上升、净流入。"),
    _d("sentiment", "fund_flow_out", "基金资金流出", ("基金",), "文本讨论 ETF 资金流出、基金赎回、份额下降、净流出。"),

    _d("risk_strategy", "chase_high_risk", "追高风险", ("通用",), "文本提醒上涨后不要盲目追、接近高位要谨慎、涨多了风险变大。"),
    _d("risk_strategy", "false_breakout_risk", "假突破风险", ("通用",), "文本讨论突破后可能失败、冲高回落、站不稳、骗线。"),
    _d("risk_strategy", "liquidity_risk", "流动性风险", ("通用",), "文本讨论成交差、买卖困难、流动性不足、小票、转债、ETF 或基金无法及时退出。"),
    _d("risk_strategy", "drawdown_risk", "回撤风险", ("通用",), "文本讨论可能回撤、下跌空间、亏损扩大、短线回落风险。"),
    _d("risk_strategy", "valuation_trap_risk", "估值陷阱风险", ("股票",), "文本讨论低估值可能是陷阱、低 PB / PE 背后有问题。"),
    _d("risk_strategy", "overheated_risk", "过热风险", ("通用",), "文本讨论涨得太快、情绪过热、短线拥挤、炒作过度。"),
    _d("risk_strategy", "risk_control", "风险控制", ("通用",), "文本讨论控制风险、先看风险、不要重仓冒险、避免大亏。"),
    _d("risk_strategy", "position_control", "仓位控制", ("通用",), "文本讨论轻仓、分批、控制仓位、不要满仓、仓位管理。"),
    _d("risk_strategy", "wait_confirm", "等待确认", ("通用",), "文本讨论不要马上判断、等确认、等站稳、等第二天走势验证。"),
    _d("risk_strategy", "observe_next_day", "观察次日表现", ("股票", "指数", "可转债", "ETF", "LOF"), "文本明确提到第二天观察、次日是否继续放量、次日是否站稳。"),
    _d("risk_strategy", "avoid_emotional_trade", "避免情绪交易", ("通用",), "文本提醒不要冲动、不要被情绪影响、不要因涨跌乱操作。"),
    _d("risk_strategy", "take_profit_plan", "止盈计划", ("通用",), "文本讨论涨到目标后减仓、止盈、分批卖出、落袋为安。"),
    _d("risk_strategy", "stop_loss_plan", "止损计划", ("通用",), "文本讨论跌破条件止损、亏损控制、设置退出条件。"),
    _d("risk_strategy", "convertible_forced_redeem_risk", "强赎风险", ("可转债",), "文本讨论强赎、满足强赎条件、赎回触发进度、强赎公告导致价格风险。"),
    _d("risk_strategy", "convertible_putback_risk", "回售相关风险", ("可转债",), "文本讨论回售条款、回售触发、回售收益或回售失败风险。"),
    _d("risk_strategy", "convertible_low_rating_risk", "低评级风险", ("可转债",), "文本讨论转债评级偏低、信用风险、主体资质弱、违约或信用下修风险。"),
    _d("risk_strategy", "convertible_small_balance_risk", "剩余规模过小风险", ("可转债",), "文本讨论剩余规模小、容易被炒作、流动性和波动放大、退市或强赎接近。"),
    _d("risk_strategy", "convertible_liquidity_risk", "转债流动性风险", ("可转债",), "文本讨论转债成交清淡、买卖价差、规模小导致退出困难。"),
    _d("risk_strategy", "fund_tracking_deviation_risk", "基金跟踪偏离风险", ("基金",), "文本讨论指数基金、ETF、LOF 跟踪偏离、跟踪误差、跑输基准。"),
    _d("risk_strategy", "fund_concentration_risk", "基金持仓集中风险", ("基金",), "文本讨论基金重仓单一行业、单一风格、前十大持仓集中度高。"),
    _d("risk_strategy", "fund_credit_risk", "债基信用风险", ("基金",), "文本讨论债基持仓信用等级、信用债违约、低评级债暴露。"),
    _d("risk_strategy", "fund_duration_risk", "债基久期风险", ("基金",), "文本讨论债基久期、利率上行导致净值波动、长久期债基风险。"),
    _d("risk_strategy", "fund_qdii_fx_risk", "QDII 汇率风险", ("基金",), "文本讨论 QDII 汇率波动、海外市场和汇率共同影响收益。"),
    _d("risk_strategy", "fund_liquidity_risk", "基金流动性风险", ("基金",), "文本讨论 ETF 成交不足、基金限购限赎、巨额赎回、清盘或无法及时交易。"),
)


TAG_DEFINITIONS_BY_CATEGORY: dict[str, tuple[TagDefinition, ...]] = {
    category: tuple(item for item in TAG_DEFINITIONS if item.category == category)
    for category in SCENE_CATEGORIES
}

VALID_TAGS: dict[str, frozenset[str]] = {
    category: frozenset(item.tag for item in definitions)
    for category, definitions in TAG_DEFINITIONS_BY_CATEGORY.items()
}

TAG_DEFINITION_LOOKUP: dict[tuple[str, str], TagDefinition] = {
    (item.category, item.tag): item
    for item in TAG_DEFINITIONS
}

BROAD_ASSET_ALIASES = {
    "通用": "通用",
    "股票": "股票",
    "指数": "指数",
    "可转债": "可转债",
    "基金": "基金",
    "ETF": "基金",
    "LOF": "基金",
}

ASSET_TAG_SCOPES = {
    "stock": "股票",
    "bank_stock": "股票",
    "low_price_stock": "股票",
    "large_cap_stock": "股票",
    "small_cap_stock": "股票",
    "index": "指数",
    "convertible_bond": "可转债",
    "fund": "基金",
    "etf": "基金",
    "lof": "基金",
    "index_fund": "基金",
    "active_fund": "基金",
    "bond_fund": "基金",
    "money_fund": "基金",
    "qdii_fund": "基金",
}


def broad_assets(assets: tuple[str, ...]) -> frozenset[str]:
    return frozenset(BROAD_ASSET_ALIASES.get(asset, asset) for asset in assets)


def asset_scopes_from_tags(asset_tags: set[str]) -> frozenset[str]:
    return frozenset(
        scope
        for tag in asset_tags
        if (scope := ASSET_TAG_SCOPES.get(tag)) is not None
    )


def tag_matches_asset_scope(category: str, tag: str, asset_scopes: set[str] | frozenset[str]) -> bool:
    if not asset_scopes:
        return True
    definition = TAG_DEFINITION_LOOKUP.get((category, tag))
    if definition is None:
        return False
    allowed_assets = broad_assets(definition.assets)
    return "通用" in allowed_assets or bool(allowed_assets & asset_scopes)


def llm_tag_prompt_section() -> str:
    lines = ["## 7 大类标签", "", "你需要从以下 7 个大类中选择合适的标签。不限制每类固定数量，不相关的大类返回空数组。"]
    for category in SCENE_CATEGORIES:
        lines.extend(["", f"### {category}"])
        for item in TAG_DEFINITIONS_BY_CATEGORY[category]:
            assets = " / ".join(item.assets)
            lines.append(f"- {item.tag}: {item.name}；适用标的：{assets}；{item.description}")
    return "\n".join(lines)


LLM_TAG_SYSTEM_PROMPT = f"""你是投资知识场景标签专家。你的任务是为投资知识 chunk 打上场景标签，用于后续检索召回。

{llm_tag_prompt_section()}

## 核心规则

1. **只从以上白名单选标签**，不能创造新标签，否则检索会混乱。
2. **不相关就空数组**，不要为了填满 7 类而强行打标签。
3. **不限制每类固定数量**，但只保留和 chunk 适用场景直接相关的标签，避免无关标签导致检索噪声变大。
4. **优先打“适用场景”**：不是问这段话表面讲了什么，而是问它以后适合在哪些投资场景下被检索出来。
5. **风险和策略优先保留**：如果文本包含操作提醒、风险提醒、观察条件，优先给 risk_strategy 打标签。
6. **general 用于通用经验**：如果文本不是特定股票 / 转债 / 指数 / 基金场景，但适合投资通用经验，就打 asset.general。
7. **适用标的必须匹配**：可转债溢价、强赎、基金跟踪误差、债基久期等专属概念只能打对应资产专属标签；通用风险、趋势、量价经验才可跨标的复用。

## 输出格式

只返回一个 JSON 对象，不要 Markdown，不要解释文本：

{{
  "scenes": {{
    "asset": [],
    "price": [],
    "volume": [],
    "trend": [],
    "valuation": [],
    "sentiment": [],
    "risk_strategy": []
  }}
}}"""


def empty_scenes() -> dict[str, list[str]]:
    return {category: [] for category in SCENE_CATEGORIES}
