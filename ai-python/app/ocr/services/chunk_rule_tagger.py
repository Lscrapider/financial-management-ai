import re
from collections.abc import Callable
from dataclasses import dataclass
from typing import Any


SCENE_CATEGORIES = (
    "asset",
    "price",
    "volume",
    "trend",
    "valuation",
    "sentiment",
    "risk_strategy",
)

NEGATION_WORDS = ("不是", "不能", "不要", "别", "不宜", "避免", "无法", "没有", "未能", "并非")


@dataclass(frozen=True)
class NumericPattern:
    pattern: re.Pattern[str]
    score: Callable[[float], float]
    group: int = 1
    negation_sensitive: bool = True


@dataclass(frozen=True)
class PhrasePattern:
    phrase: str
    score: float
    negation_sensitive: bool = True


@dataclass(frozen=True)
class KeywordPattern:
    keywords: tuple[str, ...]
    base_score: float = 0.2
    score_per_hit: float = 0.05
    max_score: float = 0.4
    negation_sensitive: bool = True


@dataclass(frozen=True)
class TagRule:
    category: str
    tag: str
    numeric_patterns: tuple[NumericPattern, ...] = ()
    phrase_patterns: tuple[PhrasePattern, ...] = ()
    keyword_patterns: tuple[KeywordPattern, ...] = ()


class ChunkRuleTagger:
    def __init__(self, confidence_threshold: float = 0.75, min_covered_categories: int = 3) -> None:
        self._confidence_threshold = confidence_threshold
        self._min_covered_categories = min_covered_categories
        self._rules = self._build_rules()

    def tag(self, reviewed_json: dict[str, Any]) -> dict[str, Any]:
        content = reviewed_json.get("content") or {}
        paragraphs = content.get("paragraphs") or []
        task_no = str(reviewed_json.get("taskNo") or "")
        chunks = []

        for index, paragraph in enumerate(paragraphs):
            text = str(paragraph.get("text") or "")
            if not text.strip():
                continue
            chunk_index = index + 1
            scored = self._score_text(text)
            confidence_scenes = {category: tags for category, tags in scored.items()}
            quality_gate = self._quality_gate(confidence_scenes)
            chunks.append(
                {
                    "chunkId": f"{task_no}:chunk:{chunk_index:04d}",
                    "chunkIndex": chunk_index,
                    "text": text,
                    "pageNos": paragraph.get("sourcePages") or [],
                    "paragraphNos": [paragraph.get("paragraphNo") or chunk_index],
                    "ruleScenes": self._to_scene_lists(confidence_scenes),
                    "ruleScenesWithConfidence": confidence_scenes,
                    "qualityGate": quality_gate,
                }
            )

        return {
            "taskNo": task_no,
            "tagVersion": "rule-v1.1",
            "confidenceThreshold": self._confidence_threshold,
            "minCoveredCategories": self._min_covered_categories,
            "chunkCount": len(chunks),
            "needLlm": any(chunk["qualityGate"]["needLlm"] for chunk in chunks),
            "chunks": chunks,
        }

    def _score_text(self, text: str) -> dict[str, dict[str, float]]:
        result: dict[str, dict[str, float]] = {category: {} for category in SCENE_CATEGORIES}
        for rule in self._rules:
            score = self._score_rule(text, rule)
            if score <= 0:
                continue
            result[rule.category][rule.tag] = score
        return {
            category: dict(sorted(tags.items(), key=lambda item: item[1], reverse=True)[:3])
            for category, tags in result.items()
        }

    def _score_rule(self, text: str, rule: TagRule) -> float:
        numeric_score = self._score_numeric(text, rule.numeric_patterns)
        phrase_score = self._score_phrase(text, rule.phrase_patterns)
        keyword_score = self._score_keywords(text, rule.keyword_patterns)
        return max(numeric_score, phrase_score, keyword_score)

    def _score_numeric(self, text: str, patterns: tuple[NumericPattern, ...]) -> float:
        scores = []
        for item in patterns:
            for match in item.pattern.finditer(text):
                raw_value = match.group(item.group).replace(",", "")
                try:
                    value = float(raw_value)
                except ValueError:
                    continue
                score = self._clamp(item.score(value))
                if item.negation_sensitive and self._has_near_negation(text, match.start(), match.end()):
                    score *= 0.5
                scores.append(score)
        return max(scores, default=0.0)

    def _score_phrase(self, text: str, patterns: tuple[PhrasePattern, ...]) -> float:
        scores = []
        for item in patterns:
            start = text.find(item.phrase)
            if start < 0:
                continue
            score = item.score
            if item.negation_sensitive and self._has_near_negation(text, start, start + len(item.phrase)):
                score *= 0.5
            scores.append(score)
        return max(scores, default=0.0)

    def _score_keywords(self, text: str, patterns: tuple[KeywordPattern, ...]) -> float:
        scores = []
        for item in patterns:
            hits = [
                keyword
                for keyword in item.keywords
                if keyword in text and not self._keyword_negated(text, keyword, item.negation_sensitive)
            ]
            if not hits:
                continue
            scores.append(min(item.max_score, item.base_score + len(set(hits)) * item.score_per_hit))
        return max(scores, default=0.0)

    def _keyword_negated(self, text: str, keyword: str, negation_sensitive: bool) -> bool:
        if not negation_sensitive:
            return False
        start = text.find(keyword)
        return start >= 0 and self._has_near_negation(text, start, start + len(keyword))

    def _has_near_negation(self, text: str, start: int, end: int) -> bool:
        window = text[max(0, start - 6) : end]
        return any(word in window for word in NEGATION_WORDS)

    def _quality_gate(self, scenes: dict[str, dict[str, float]]) -> dict[str, Any]:
        covered_categories = {category: tags for category, tags in scenes.items() if tags}
        low_confidence_categories = [
            category
            for category, tags in covered_categories.items()
            if max(tags.values()) < self._confidence_threshold
        ]
        if len(covered_categories) < self._min_covered_categories:
            reason = "LOW_COVERAGE"
            need_llm = True
        elif low_confidence_categories:
            reason = "LOW_CONFIDENCE"
            need_llm = True
        else:
            reason = "RULE_TAGS_CONFIDENT_ENOUGH"
            need_llm = False
        return {
            "needLlm": need_llm,
            "reason": reason,
            "coveredCategoryCount": len(covered_categories),
            "confidenceThreshold": self._confidence_threshold,
            "lowConfidenceCategories": low_confidence_categories,
        }

    def _to_scene_lists(self, scenes: dict[str, dict[str, float]]) -> dict[str, list[str]]:
        return {category: list(tags.keys()) for category, tags in scenes.items()}

    def _build_rules(self) -> tuple[TagRule, ...]:
        return (
            *self._build_asset_rules(),
            *self._build_price_rules(),
            *self._build_volume_rules(),
            *self._build_trend_rules(),
            *self._build_valuation_rules(),
            *self._build_sentiment_rules(),
            *self._build_risk_strategy_rules(),
        )

    def _build_asset_rules(self) -> tuple[TagRule, ...]:
        return (
            self._tag("asset", "general", phrases=(("通用投资经验", 0.80), ("投资纪律", 0.78), ("交易纪律", 0.78))),
            self._tag("asset", "stock", phrases=(("个股走势", 0.86), ("低价股", 0.86), ("买股", 0.82), ("卖股", 0.82), ("这只票", 0.78), ("这只股", 0.78), ("持有股票", 0.78)), keywords=("股票", "个股", "股价", "正股", "票")),
            self._tag("asset", "index", phrases=(("板块指数", 0.88),), keywords=("指数", "大盘", "上证", "深成指", "创业板", "科创板")),
            self._tag(
                "asset",
                "convertible_bond",
                phrases=(("转股价值", 0.90), ("正股联动", 0.88)),
                keywords=("可转债", "转债", "溢价率", "强赎"),
            ),
            self._tag("asset", "fund", phrases=(("基金定投", 0.90), ("基金配置", 0.88), ("场内基金", 0.86)), keywords=("基金", "ETF")),
            self._tag("asset", "bank_stock", phrases=(("银行股", 0.90), ("银行板块", 0.88), ("低 PB 银行", 0.88)), keywords=("息差", "分红")),
            self._tag("asset", "low_price_stock", phrases=(("低价股", 0.88), ("几元股", 0.86), ("低价小票", 0.86), ("低价补涨", 0.84), ("便宜小票", 0.80), ("低位小票", 0.80), ("几块钱的股", 0.80))),
            self._tag("asset", "large_cap_stock", phrases=(("大盘股", 0.88), ("机构重仓", 0.84)), keywords=("大市值", "权重股", "蓝筹")),
            self._tag("asset", "small_cap_stock", phrases=(("小盘股", 0.88), ("题材小票", 0.86)), keywords=("小市值", "弹性较大")),
        )

    def _build_price_rules(self) -> tuple[TagRule, ...]:
        return (
            self._tag("price", "price_rise", phrases=(("涨幅扩大", 0.82), ("放量上涨", 0.78), ("价格上涨", 0.75), ("突然拉起来", 0.78), ("往上冲", 0.76), ("拉得很快", 0.76), ("涨起来", 0.75), ("涨得比较快", 0.75)), keywords=("上涨", "拉升", "冲高", "涨幅", "大涨", "急拉")),
            self._tag("price", "price_drop", phrases=(("跌幅扩大", 0.82), ("下跌后的处理", 0.80), ("价格下跌", 0.75), ("突然砸下来", 0.78), ("往下杀", 0.76), ("跌得比较快", 0.75), ("跌下去", 0.75)), keywords=("下跌", "杀跌", "回落", "跌幅", "大跌", "急跌")),
            self._tag("price", "sideways", phrases=(("区间整理", 0.82), ("窄幅波动", 0.80), ("长期不涨", 0.78)), keywords=("横盘", "震荡")),
            self._tag("price", "near_recent_high", phrases=(("接近前高", 0.86), ("阶段高点", 0.84), ("涨到高位", 0.82)), keywords=("高位", "近期高位")),
            self._tag("price", "near_recent_low", phrases=(("底部区域", 0.84), ("低位观察", 0.82), ("阶段低点", 0.82)), keywords=("低位", "近期低位")),
            self._tag("price", "breakout", phrases=(("突破压力位", 0.88), ("突破平台", 0.86), ("突破前高", 0.86), ("突破后的确认", 0.84), ("冲过压力位", 0.84), ("站上关键位置", 0.82), ("站上前高", 0.82), ("向上打开空间", 0.80)), keywords=("突破", "站上", "破位上去")),
            self._tag("price", "pullback", phrases=(("回调买点", 0.84), ("上涨后的回调", 0.82), ("短线回落", 0.78), ("涨多了回踩", 0.80), ("回踩确认", 0.80), ("回落以后再看", 0.76)), keywords=("回调", "回踩", "调整", "洗盘")),
            self._tag("price", "gap_up", phrases=(("跳空高开", 0.88), ("高开缺口", 0.84))),
            self._tag("price", "gap_down", phrases=(("跳空低开", 0.88), ("低开缺口", 0.84))),
        )

    def _build_volume_rules(self) -> tuple[TagRule, ...]:
        return (
            self._tag("volume", "volume_expand", numeric=((r"量比\s*([0-9]+(?:\.[0-9]+)?)", lambda value: 0.5 + value / 10),), phrases=(("成交量放大", 0.78), ("量能增加", 0.76), ("资金明显活跃", 0.76), ("放量上涨", 0.75), ("量起来了", 0.76), ("成交明显变多", 0.76), ("有人开始买", 0.74), ("资金进来了", 0.74)), keywords=("放量", "量能放大", "带量", "有量")),
            self._tag("volume", "volume_shrink", phrases=(("成交量减少", 0.78), ("量能不足", 0.76), ("无人关注", 0.72)), keywords=("缩量",)),
            self._tag("volume", "high_turnover", numeric=((r"换手率?\s*([0-9]+(?:\.[0-9]+)?)\s*%", lambda value: 0.3 + value / 30),), phrases=(("换手率高", 0.78), ("筹码交换剧烈", 0.78), ("交易活跃", 0.74), ("分歧加大", 0.72), ("换手很大", 0.76), ("筹码换得很快", 0.76), ("买卖很活跃", 0.74)), keywords=("高换手", "换手大")),
            self._tag("volume", "low_turnover", numeric=((r"换手率?\s*([0-9]+(?:\.[0-9]+)?)\s*%", lambda value: 0.75 if value <= 2 else 0),), phrases=(("换手低", 0.76), ("交易清淡", 0.76), ("流动性不足", 0.74), ("没人买卖", 0.72)), keywords=("低换手",)),
            self._tag("volume", "volume_price_confirm", phrases=(("上涨有量", 0.84), ("下跌缩量", 0.82), ("量价配合", 0.82), ("价格和成交量互相验证", 0.82), ("涨的时候有量", 0.82), ("价格上涨同时放量", 0.82), ("量能跟得上", 0.80))),
            self._tag("volume", "volume_price_divergence", phrases=(("价格上涨但量没跟上", 0.86), ("放量但价格不涨", 0.84), ("量价不一致", 0.82), ("量价背离", 0.82))),
            self._tag("volume", "volume_spike", phrases=(("异常放量", 0.84), ("成交量明显突增", 0.84), ("突然爆量", 0.82), ("天量", 0.80), ("爆量", 0.80))),
            self._tag("volume", "volume_dry_up", phrases=(("成交极低", 0.82), ("量能枯竭", 0.82), ("没人交易", 0.78), ("流动性很差", 0.76))),
        )

    def _build_trend_rules(self) -> tuple[TagRule, ...]:
        return (
            self._tag("trend", "uptrend", phrases=(("连续上涨", 0.82), ("趋势向上", 0.82), ("均线多头", 0.80), ("逐步抬高", 0.78), ("多头排列", 0.78), ("一路往上", 0.78), ("高点和低点都在抬高", 0.80), ("走势越来越强", 0.78))),
            self._tag("trend", "downtrend", phrases=(("连续下跌", 0.82), ("趋势向下", 0.82), ("弱势下行", 0.80), ("反弹无力", 0.78))),
            self._tag("trend", "range_bound", phrases=(("箱体震荡", 0.82), ("区间波动", 0.80), ("震荡整理", 0.78)), keywords=("上下沿",)),
            self._tag("trend", "rebound", phrases=(("超跌反弹", 0.82), ("短线修复", 0.80), ("下跌后的反弹", 0.80)), keywords=("反弹",)),
            self._tag("trend", "trend_reversal", phrases=(("由弱转强", 0.84), ("由强转弱", 0.84), ("反转信号", 0.82), ("趋势反转", 0.82), ("拐点", 0.76))),
            self._tag("trend", "breakout_from_range", phrases=(("长期横盘后突破", 0.88), ("箱体突破", 0.86), ("平台突破", 0.86), ("横盘突破", 0.84), ("继续放量并站稳", 0.78), ("横了很久后突破", 0.84), ("整理后突破", 0.82), ("平台站稳", 0.80))),
            self._tag("trend", "failed_breakout", phrases=(("突破后回落", 0.86), ("冲高失败", 0.84), ("突破失败", 0.84), ("站不上去", 0.82), ("假突破", 0.82), ("冲上去又下来", 0.82), ("没能站稳", 0.82), ("突破不成功", 0.82))),
        )

    def _build_valuation_rules(self) -> tuple[TagRule, ...]:
        return (
            self._tag("valuation", "low_pe", numeric=((r"PE\s*([0-9]+(?:\.[0-9]+)?)", lambda value: 0.85 if value <= 12 else 0), (r"市盈率\s*([0-9]+(?:\.[0-9]+)?)", lambda value: 0.85 if value <= 12 else 0)), phrases=(("低市盈率", 0.84), ("PE 较低", 0.82), ("盈利估值便宜", 0.80))),
            self._tag("valuation", "high_pe", numeric=((r"PE\s*([0-9]+(?:\.[0-9]+)?)", lambda value: 0.82 if value >= 50 else 0), (r"市盈率\s*([0-9]+(?:\.[0-9]+)?)", lambda value: 0.82 if value >= 50 else 0)), phrases=(("高市盈率", 0.84), ("估值过高", 0.82), ("盈利无法支撑估值", 0.82))),
            self._tag("valuation", "low_pb", numeric=((r"PB\s*([0-9]+(?:\.[0-9]+)?)", lambda value: 0.9 if value < 1 else 0), (r"市净率\s*([0-9]+(?:\.[0-9]+)?)", lambda value: 0.9 if value < 1 else 0)), phrases=(("低市净率", 0.84), ("PB 小于 1", 0.86), ("破净", 0.84), ("银行股低 PB", 0.86))),
            self._tag("valuation", "high_pb", numeric=((r"PB\s*([0-9]+(?:\.[0-9]+)?)", lambda value: 0.82 if value >= 5 else 0), (r"市净率\s*([0-9]+(?:\.[0-9]+)?)", lambda value: 0.82 if value >= 5 else 0)), phrases=(("高市净率", 0.84), ("净资产估值偏高", 0.82))),
            self._tag("valuation", "high_dividend", numeric=((r"股息率\s*([0-9]+(?:\.[0-9]+)?)\s*%", lambda value: 0.5 + value / 20),), phrases=(("高股息策略", 0.84), ("稳定分红", 0.80)), keywords=("分红率", "股息率", "高股息")),
            self._tag("valuation", "valuation_repair", phrases=(("低估值修复", 0.84), ("估值回归", 0.82), ("估值提升", 0.80), ("补涨修复", 0.78))),
            self._tag("valuation", "valuation_trap", phrases=(("低估值不一定安全", 0.88), ("便宜有原因", 0.84), ("低估值陷阱", 0.84), ("低 PE 陷阱", 0.84), ("低 PB 陷阱", 0.84), ("看着便宜但有问题", 0.82), ("便宜不代表安全", 0.82), ("估值低也要小心", 0.80))),
            self._tag("valuation", "fundamental_risk", phrases=(("业绩变差", 0.84), ("盈利压力", 0.82), ("资产质量问题", 0.82), ("基本面不确定", 0.80))),
        )

    def _build_sentiment_rules(self) -> tuple[TagRule, ...]:
        return (
            self._tag("sentiment", "market_attention_rise", phrases=(("市场开始关注", 0.82), ("热度提高", 0.80), ("人气变强", 0.80), ("资金关注度上升", 0.80))),
            self._tag("sentiment", "short_term_emotion", phrases=(("短线情绪", 0.82), ("短线资金活跃", 0.82), ("情绪推动上涨", 0.82), ("突然放量上涨", 0.80), ("短线资金在做", 0.80), ("情绪起来了", 0.78), ("市场情绪带动", 0.78)), keywords=("短线", "情绪", "接力")),
            self._tag("sentiment", "panic_selling", phrases=(("恐慌杀跌", 0.86), ("情绪性卖出", 0.84), ("急跌恐慌", 0.82)), keywords=("踩踏",)),
            self._tag("sentiment", "news_driven", phrases=(("消息推动", 0.82), ("利好消息", 0.80), ("利空消息", 0.80)), keywords=("新闻", "公告", "传闻")),
            self._tag("sentiment", "policy_driven", phrases=(("政策刺激", 0.84), ("政策影响", 0.82), ("监管政策", 0.80), ("宏观政策", 0.80), ("行业政策", 0.80))),
            self._tag("sentiment", "sector_rotation", phrases=(("板块轮动", 0.84), ("板块切换", 0.82), ("轮动行情", 0.80), ("资金从一个板块转向另一个板块", 0.82))),
            self._tag("sentiment", "weak_sentiment", phrases=(("市场情绪弱", 0.82), ("没人接力", 0.80), ("上涨无力", 0.78), ("关注度低", 0.76), ("没人愿意买", 0.78), ("资金不愿意接", 0.78), ("人气不够", 0.76))),
        )

    def _build_risk_strategy_rules(self) -> tuple[TagRule, ...]:
        return (
            self._tag("risk_strategy", "chase_high_risk", phrases=(("不要盲目追", 0.88), ("不要追高", 0.88), ("接近高位要谨慎", 0.86), ("涨多了风险变大", 0.84), ("追高风险", 0.84), ("别追太急", 0.84), ("不要看到涨就买", 0.84), ("高位别乱追", 0.84), ("涨起来后别急着买", 0.82)), keywords=("追高", "乱追"), negation_sensitive=False),
            self._tag("risk_strategy", "false_breakout_risk", phrases=(("突破后可能失败", 0.86), ("冲高回落", 0.84), ("站不稳", 0.82), ("骗线", 0.82), ("假突破", 0.82))),
            self._tag("risk_strategy", "liquidity_risk", phrases=(("成交差", 0.82), ("买卖困难", 0.82), ("流动性不足", 0.80), ("无法及时退出", 0.80))),
            self._tag("risk_strategy", "drawdown_risk", phrases=(("可能回撤", 0.82), ("下跌空间", 0.80), ("亏损扩大", 0.80), ("短线回落风险", 0.80))),
            self._tag("risk_strategy", "valuation_trap_risk", phrases=(("低估值可能是陷阱", 0.88), ("低 PB 背后有问题", 0.84), ("低 PE 背后有问题", 0.84))),
            self._tag("risk_strategy", "overheated_risk", phrases=(("涨得太快", 0.82), ("情绪过热", 0.82), ("短线拥挤", 0.80), ("炒作过度", 0.80))),
            self._tag("risk_strategy", "risk_control", phrases=(("控制风险", 0.82), ("先看风险", 0.80), ("不要重仓冒险", 0.82), ("避免大亏", 0.80)), keywords=("风控",), negation_sensitive=False),
            self._tag("risk_strategy", "position_control", phrases=(("控制仓位", 0.82), ("不要满仓", 0.82), ("仓位管理", 0.80), ("分批买入", 0.78), ("轻仓", 0.76), ("少买一点", 0.78), ("先小仓位", 0.78), ("不要一下子买太多", 0.80), ("分几次买", 0.78)), keywords=("分批", "轻仓", "小仓位"), negation_sensitive=False),
            self._tag("risk_strategy", "wait_confirm", phrases=(("不要马上判断", 0.84), ("等待确认", 0.84), ("等确认", 0.82), ("等站稳", 0.82), ("走势验证", 0.80), ("继续放量并站稳", 0.80), ("先别急着判断", 0.82), ("先看能不能站住", 0.82), ("确认后再说", 0.80), ("等走势走出来", 0.80), ("再观察一下", 0.78)), keywords=("站稳", "确认"), negation_sensitive=False),
            self._tag("risk_strategy", "observe_next_day", phrases=(("第二天观察", 0.86), ("第二天是否继续放量", 0.86), ("第二天是否站稳", 0.86), ("次日是否继续放量", 0.86), ("次日是否站稳", 0.86), ("明天观察", 0.82), ("隔天再看", 0.82), ("后面一个交易日再看", 0.82), ("明天能不能站住", 0.82), ("明天是否继续", 0.80)), keywords=("第二天", "次日", "明天", "隔天"), negation_sensitive=False),
            self._tag("risk_strategy", "avoid_emotional_trade", phrases=(("不要冲动", 0.84), ("不要被情绪影响", 0.84), ("不要因为涨跌而乱操作", 0.84), ("避免情绪交易", 0.82), ("别被盘面带着走", 0.82), ("不要上头", 0.82), ("不要一激动就买", 0.82), ("冷静一点", 0.78)), negation_sensitive=False),
            self._tag("risk_strategy", "take_profit_plan", phrases=(("涨到目标后减仓", 0.84), ("分批卖出", 0.82), ("落袋为安", 0.80), ("涨多了先卖一部分", 0.82), ("先止盈一部分", 0.82), ("有利润先保住", 0.80)), keywords=("止盈", "减仓")),
            self._tag("risk_strategy", "stop_loss_plan", numeric=((r"止损\s*([0-9]+(?:\.[0-9]+)?)\s*%", lambda value: 0.5 + value / 30),), phrases=(("跌破条件止损", 0.84), ("亏损控制", 0.82), ("设置退出条件", 0.80), ("跌破就走", 0.82), ("不对就退出", 0.80), ("亏了要及时处理", 0.80), ("到止损位就卖", 0.82)), keywords=("止损", "割肉")),
        )

    def _tag(
        self,
        category: str,
        tag: str,
        numeric: tuple[tuple[str, Callable[[float], float]], ...] = (),
        phrases: tuple[tuple[str, float], ...] = (),
        keywords: tuple[str, ...] = (),
        negation_sensitive: bool = True,
    ) -> TagRule:
        return TagRule(
            category=category,
            tag=tag,
            numeric_patterns=tuple(
                NumericPattern(re.compile(pattern, re.IGNORECASE), score, negation_sensitive=negation_sensitive)
                for pattern, score in numeric
            ),
            phrase_patterns=tuple(PhrasePattern(phrase, score, negation_sensitive) for phrase, score in phrases),
            keyword_patterns=(
                (KeywordPattern(keywords, negation_sensitive=negation_sensitive),) if keywords else ()
            ),
        )

    @staticmethod
    def _clamp(score: float) -> float:
        return max(0.0, min(1.0, round(score, 4)))
