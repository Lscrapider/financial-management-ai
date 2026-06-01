"""DeepSeek 打标测试 — 输入文本，输出 DeepSeek 原始返回数据。

用法：
    python test/deepseek_tag_test.py                        # 交互式输入
    python test/deepseek_tag_test.py "你的文本..."            # 命令行传参
    echo "你的文本..." | python test/deepseek_tag_test.py -   # 管道输入
"""

import json
import sys

from app.core.config import DeepSeekSettings
from app.ocr.engines.deepseek_chat_engine import DeepSeekChatEngine

SYSTEM_PROMPT = """你是投资知识场景标签专家。你的任务是为投资知识 chunk 打上场景标签，用于后续检索召回。

## 7 大类标签

你需要从以下 7 个大类中选择合适的标签。不限制每类固定数量，不相关的大类返回空数组。

### asset（资产类型）
- general: 通用投资经验，不限定具体资产类型
- stock: 股票，文本明确讨论股票、个股、股价、买股、卖股、个股走势
- index: 指数，文本明确讨论大盘、指数、上证、深成指、创业板、科创板、板块指数
- convertible_bond: 可转债，文本出现可转债、转债、溢价率、强赎、正股联动、转股价值
- fund: 基金，文本明确讨论基金、ETF、场内基金、基金定投、基金配置
- bank_stock: 银行股，文本明确讨论银行股、银行板块、低PB银行、息差、分红
- low_price_stock: 低价股，文本明确提到低价股，或讨论几元股、低价小票、低价补涨
- large_cap_stock: 大盘股，文本讨论大市值、权重股、蓝筹、大盘股、机构重仓
- small_cap_stock: 小盘股，文本讨论小市值、小盘股、题材小票、弹性较大

### price（价格位置）
- price_rise: 价格上涨，文本讨论上涨、拉升、涨幅扩大、上涨过程中如何判断
- price_drop: 价格下跌，文本讨论下跌、杀跌、回落、跌幅扩大、下跌后的处理
- sideways: 横盘，文本讨论横盘、震荡、长期不涨、窄幅波动、区间整理
- near_recent_high: 接近近期高位，文本讨论高位、阶段高点、接近前高、涨到高位后的风险
- near_recent_low: 接近近期低位，文本讨论低位、阶段低点、跌到底部区域、低位观察
- breakout: 突破，文本讨论突破压力位、突破平台、突破前高、突破后的确认
- pullback: 回调，文本讨论上涨后的回调、回踩、调整、短线回落、回调买点
- gap_up: 跳空高开，文本讨论跳空高开、高开缺口、高开后的风险或机会
- gap_down: 跳空低开，文本讨论跳空低开、低开缺口、低开后的观察或风险

### volume（成交量/换手）
- volume_expand: 放量，文本讨论放量、成交量放大、量能增加、资金明显活跃
- volume_shrink: 缩量，文本讨论缩量、成交量减少、量能不足、无人关注
- high_turnover: 高换手，文本讨论换手率高、交易活跃、筹码交换剧烈、分歧加大
- low_turnover: 低换手，文本讨论换手低、交易清淡、流动性不足、没人买卖
- volume_price_confirm: 量价配合，文本讨论上涨有量、下跌缩量、价格和成交量互相验证
- volume_price_divergence: 量价背离，文本讨论价格上涨但量没跟上、放量但价格不涨、量价不一致
- volume_spike: 成交量突然放大，文本讨论突然爆量、异常放量、某天成交量明显突增
- volume_dry_up: 成交枯竭，文本讨论成交极低、量能枯竭、没人交易、流动性很差

### trend（趋势结构）
- uptrend: 上升趋势，文本讨论连续上涨、趋势向上、均线多头、逐步抬高
- downtrend: 下降趋势，文本讨论连续下跌、趋势向下、弱势下行、反弹无力
- range_bound: 区间震荡，文本讨论箱体震荡、区间波动、上下沿、震荡整理
- rebound: 反弹，文本讨论下跌后的反弹、超跌反弹、短线修复
- trend_reversal: 趋势反转，文本讨论趋势由弱转强、由强转弱、反转信号、拐点
- breakout_from_range: 横盘突破，文本讨论长期横盘后突破、箱体突破、平台突破
- failed_breakout: 突破失败，文本讨论假突破、突破后回落、站不上去、冲高失败

### valuation（估值/基本面）
- low_pe: 低PE，文本讨论低市盈率、PE较低、盈利估值便宜
- high_pe: 高PE，文本讨论高市盈率、估值过高、盈利无法支撑估值
- low_pb: 低PB，文本讨论低市净率、破净、PB小于1、银行股低PB
- high_pb: 高PB，文本讨论高市净率、净资产估值偏高
- high_dividend: 高股息，文本讨论分红率、股息率、高股息策略、稳定分红
- valuation_repair: 估值修复，文本讨论低估值修复、估值回归、估值提升、补涨修复
- valuation_trap: 低估值陷阱，文本讨论低估值不一定安全、便宜有原因、低PE/低PB陷阱
- fundamental_risk: 基本面风险，文本讨论业绩变差、盈利压力、资产质量问题、基本面不确定

### sentiment（情绪/异动）
- market_attention_rise: 关注度上升，文本讨论市场开始关注、热度提高、人气变强、资金关注度上升
- short_term_emotion: 短线情绪升温，文本讨论短线情绪、短线资金活跃、情绪推动上涨
- panic_selling: 恐慌抛售，文本讨论恐慌杀跌、情绪性卖出、踩踏、急跌恐慌
- news_driven: 消息驱动，文本讨论新闻、公告、传闻、利好利空消息推动行情
- policy_driven: 政策驱动，文本讨论政策影响、监管政策、宏观政策、行业政策刺激
- sector_rotation: 板块轮动，文本讨论板块切换、资金从一个板块转向另一个板块、轮动行情
- weak_sentiment: 情绪偏弱，文本讨论市场情绪弱、没人接力、上涨无力、关注度低
- herding_effect: 羊群效应/从众行为，文本讨论羊群效应、从众行为、交易行为趋同
- institutional_behavior: 机构/基金行为，文本讨论机构投资者、基金交易行为、基金持仓、基金年报

### risk_strategy（风险/策略）
- chase_high_risk: 追高风险，文本提醒上涨后不要盲目追、接近高位要谨慎、涨多了风险变大
- false_breakout_risk: 假突破风险，文本讨论突破后可能失败、冲高回落、站不稳、骗线
- liquidity_risk: 流动性风险，文本讨论成交差、买卖困难、流动性不足、小票无法及时退出
- drawdown_risk: 回撤风险，文本讨论可能回撤、下跌空间、亏损扩大、短线回落风险
- valuation_trap_risk: 估值陷阱风险，文本讨论低估值可能是陷阱、低PB/PE背后有问题
- overheated_risk: 过热风险，文本讨论涨得太快、情绪过热、短线拥挤、炒作过度
- risk_control: 风险控制、风险规避，文本讨论控制风险、先看风险、不要重仓冒险、避免大亏，或涉及风险厌恶偏好、保守型投资选择、规避波动、低风险资产倾向等。
- position_control: 仓位控制，文本讨论轻仓、分批、控制仓位、不要满仓、仓位管理
- wait_confirm: 等待确认，文本讨论不要马上判断、等确认、等站稳、等第二天走势验证
- observe_next_day: 观察次日表现，文本明确提到第二天观察、次日是否继续放量、次日是否站稳
- avoid_emotional_trade: 避免情绪交易，文本提醒不要冲动、不要被情绪影响、不要因涨跌乱操作
- take_profit_plan: 止盈计划，文本讨论涨到目标后减仓、止盈、分批卖出、落袋为安
- stop_loss_plan: 止损计划，文本讨论跌破条件止损、亏损控制、设置退出条件

## 核心规则

1. **只从以上白名单选标签**，不能创造新标签，否则检索会混乱。
2. **不相关就空数组**，不要为了填满 7 类而强行打标签。
3. **不限制每类固定数量**，但只保留和 chunk 适用场景直接相关的标签，避免无关标签导致检索噪声变大。
4. **优先打"适用场景"**：不是问这段话表面讲了什么，而是问它以后适合在哪些投资场景下被检索出来。
5. **风险和策略优先保留**：如果文本包含操作提醒、风险提醒、观察条件，优先给 risk_strategy 打标签。
6. **general 用于通用经验**：如果文本不是特定股票/转债/指数场景，但适合投资通用经验，就打 asset.general。

## 输出格式

只返回一个 JSON 对象，不要 Markdown，不要解释文本：

{
  "scenes": {
    "asset": [],
    "price": [],
    "volume": [],
    "trend": [],
    "valuation": [],
    "sentiment": [],
    "risk_strategy": []
  }
}
"""


def get_input_text() -> str:
    if len(sys.argv) > 1:
        arg = sys.argv[1]
        if arg == "-":
            return sys.stdin.read().strip()
        return arg
    return input("请输入文本: ").strip()


def main() -> None:
    text = "在现有新股发行条件下，新股可能成为风险厌恶型投资者的选择。"

    if not text:
        print("未输入文本，退出")
        sys.exit(1)

    print(f"\n输入文本 ({len(text)} 字):\n{text}\n")
    print("=" * 60)

    settings = DeepSeekSettings()
    engine = DeepSeekChatEngine(settings)

    user_message = f"## Chunk 文本\n\n{text}\n\n请输出 JSON。"
    result = engine.chat(SYSTEM_PROMPT, user_message)

    print(f"\n模型: {settings.model}")
    print(f"温度: {settings.temperature}")
    print(f"\n--- 原始返回 ---\n")
    print(result["content"])
    print(f"\n--- Token 用量 ---")
    print(json.dumps(result["usage"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
