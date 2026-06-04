from __future__ import annotations

from math import sqrt
from statistics import mean
from typing import Any

from app.scene_analysis.services.module_scoring import active_tags, clamp, module_level, module_score, number

PERIOD_LABELS = {
    "daily": "日线",
    "weekly": "周线",
    "monthly": "月线",
}

POSITIVE_TAGS = {
    "uptrend",
    "rebound",
    "repair",
    "breakout_from_range",
    "turn_strong",
}
NEGATIVE_TAGS = {
    "downtrend",
    "pullback",
    "breakdown_from_range",
    "turn_weak",
    "failed_breakout",
}


class TrendKlineAnalyzer:
    ZIGZAG_REVERSAL_THRESHOLD_PCT = 8.0
    RANGE_WINDOW = 20

    def analyze(self, period: str, rows: list[dict[str, Any]]) -> dict[str, Any]:
        points = self._points(rows)
        label = PERIOD_LABELS.get(period, period)
        if not points:
            return {
                "score": 0.0,
                "level": "low",
                "direction": "neutral",
                "tags": {},
                "evidence": [],
                "context": {
                    "period": period,
                    "availableBars": 0,
                },
            }

        context = self._context(period, points)
        tags = active_tags(self._tags(context))
        score = module_score(tags)
        direction = self._direction(tags, context)
        return {
            "score": score,
            "level": module_level(score),
            "direction": direction,
            "tags": tags,
            "evidence": self._evidence(label, tags, context),
            "context": context,
        }

    def _points(self, rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
        sorted_rows = sorted(
            [row for row in rows if row.get("tradeDate")],
            key=lambda row: str(row.get("tradeDate")),
        )
        points = [
            {
                "date": row.get("tradeDate"),
                "open": number(row.get("openPrice")),
                "close": number(row.get("closePrice")),
                "high": number(row.get("highPrice")),
                "low": number(row.get("lowPrice")),
                "volume": number(row.get("volume")),
                "ma5": number(row.get("ma5")),
                "ma10": number(row.get("ma10")),
                "ma20": number(row.get("ma20")),
            }
            for row in sorted_rows
        ]
        return [point for point in points if point["date"] and point["close"] is not None]

    def _context(self, period: str, points: list[dict[str, Any]]) -> dict[str, Any]:
        closes = [point["close"] for point in points]
        highs = [self._value_or_default(point.get("high"), point["close"]) for point in points]
        lows = [self._value_or_default(point.get("low"), point["close"]) for point in points]
        volumes = [point["volume"] for point in points if point.get("volume") is not None]
        latest = points[-1]
        latest_close = latest["close"]
        window = min(self.RANGE_WINDOW, len(points))
        window_high = max(highs[-window:])
        window_low = min(lows[-window:])
        previous_high = max(highs[-window - 1:-1] if len(points) > window else highs[:-1] or highs)
        previous_low = min(lows[-window - 1:-1] if len(points) > window else lows[:-1] or lows)
        path_features = self._path_features(points)
        segments = path_features["segments"]
        latest_segment = segments[-1] if segments else None
        previous_segment = segments[-2] if len(segments) >= 2 else None
        return {
            "period": period,
            "availableBars": len(points),
            "startDate": points[0]["date"],
            "endDate": latest["date"],
            "latestClose": latest_close,
            "windowBars": window,
            "windowHigh": window_high,
            "windowLow": window_low,
            "previousHigh": previous_high,
            "previousLow": previous_low,
            "rangePct": self._range_pct(window_high, window_low),
            "position": self._position(latest_close, window_high, window_low),
            "returnPct": self._return_pct(closes[0], latest_close),
            "return5Bars": self._return_last(closes, 5),
            "return20Bars": self._return_last(closes, 20),
            "maxDrawdownPct": self._max_drawdown_pct(closes),
            "volatilityPct": self._volatility_pct(closes),
            "volumeRatio5Bars": self._volume_ratio(volumes, 5),
            "movingAverages": {
                "ma5": self._ma(latest, closes, "ma5", 5),
                "ma10": self._ma(latest, closes, "ma10", 10),
                "ma20": self._ma(latest, closes, "ma20", 20),
                "ma60": self._mean_last(closes, 60),
            },
            "latestCandle": self._latest_candle(latest),
            "pathFeatures": path_features,
            "latestSegment": latest_segment,
            "previousSegment": previous_segment,
        }

    def _tags(self, context: dict[str, Any]) -> dict[str, float | None]:
        uptrend = self._uptrend(context)
        downtrend = self._downtrend(context)
        range_bound = self._range_bound(context)
        breakout = self._breakout_from_range(context)
        breakdown = self._breakdown_from_range(context)
        rebound = self._rebound(context)
        pullback = self._pullback(context)
        repair = self._repair(context, uptrend)
        trend_reversal = self._trend_reversal(context, uptrend, downtrend)
        continuation = self._continuation(context, uptrend, downtrend)
        turn_weak = self._turn_weak(context, uptrend)
        turn_strong = self._turn_strong(context, downtrend, range_bound)
        failed_breakout = self._failed_breakout(context)
        return {
            "uptrend": uptrend,
            "downtrend": downtrend,
            "range_bound": range_bound,
            "rebound": rebound,
            "pullback": pullback,
            "repair": repair,
            "breakout_from_range": breakout,
            "breakdown_from_range": breakdown,
            "trend_reversal": trend_reversal,
            "continuation": continuation,
            "turn_weak": turn_weak,
            "turn_strong": turn_strong,
            "failed_breakout": failed_breakout,
        }

    def _uptrend(self, context: dict[str, Any]) -> float:
        ma = context["movingAverages"]
        latest_close = context["latestClose"]
        ma5 = ma.get("ma5")
        ma10 = ma.get("ma10")
        ma20 = ma.get("ma20")
        if None in (ma5, ma10, ma20):
            return 0.0
        score = 0.75 if ma5 > ma10 > ma20 else 0.0
        if latest_close > ma5:
            score += 0.15
        if latest_close > ma20:
            score += 0.10
        return clamp(score)

    def _downtrend(self, context: dict[str, Any]) -> float:
        ma = context["movingAverages"]
        latest_close = context["latestClose"]
        ma5 = ma.get("ma5")
        ma10 = ma.get("ma10")
        ma20 = ma.get("ma20")
        if None in (ma5, ma10, ma20):
            return 0.0
        score = 0.75 if ma5 < ma10 < ma20 else 0.0
        if latest_close < ma5:
            score += 0.15
        if latest_close < ma20:
            score += 0.10
        return clamp(score)

    def _range_bound(self, context: dict[str, Any]) -> float | None:
        range_pct = context.get("rangePct")
        if range_pct is None:
            return None
        return clamp((0.14 - range_pct) / 0.14)

    def _breakout_from_range(self, context: dict[str, Any]) -> float | None:
        previous_high = context.get("previousHigh")
        latest_close = context.get("latestClose")
        if not previous_high or latest_close is None or latest_close <= previous_high:
            return None
        strength = (latest_close - previous_high) / previous_high
        return clamp(0.35 + strength / 0.05 * 0.65)

    def _breakdown_from_range(self, context: dict[str, Any]) -> float | None:
        previous_low = context.get("previousLow")
        latest_close = context.get("latestClose")
        if not previous_low or latest_close is None or latest_close >= previous_low:
            return None
        strength = (previous_low - latest_close) / previous_low
        return clamp(0.35 + strength / 0.05 * 0.65)

    def _rebound(self, context: dict[str, Any]) -> float | None:
        previous = context.get("previousSegment")
        latest = context.get("latestSegment")
        if not previous or not latest or previous.get("direction") != "down" or latest.get("direction") != "up":
            return None
        previous_drop = abs(previous.get("returnPct") or 0)
        current_rise = latest.get("returnPct") or 0
        return clamp(previous_drop / 20 * 0.45 + current_rise / 10 * 0.55)

    def _pullback(self, context: dict[str, Any]) -> float | None:
        previous = context.get("previousSegment")
        latest = context.get("latestSegment")
        ma20 = context["movingAverages"].get("ma20")
        latest_close = context.get("latestClose")
        if not previous or not latest or previous.get("direction") != "up" or latest.get("direction") != "down":
            return None
        if ma20 is not None and latest_close is not None and latest_close < ma20:
            return None
        previous_rise = previous.get("returnPct") or 0
        current_drop = abs(latest.get("returnPct") or 0)
        return clamp(previous_rise / 20 * 0.45 + current_drop / 10 * 0.55)

    def _repair(self, context: dict[str, Any], uptrend: float) -> float | None:
        ma = context["movingAverages"]
        latest_close = context.get("latestClose")
        return5 = context.get("return5Bars")
        return20 = context.get("return20Bars")
        if latest_close is None or return5 is None:
            return None
        ma5 = ma.get("ma5")
        ma10 = ma.get("ma10")
        ma20 = ma.get("ma20")
        if ma5 is None or ma10 is None or latest_close < ma5 or latest_close < ma10 or return5 <= 0:
            return None
        if uptrend >= 0.7:
            return None
        base = 0.35 + clamp(return5 / 8) * 0.35
        if return20 is not None and return20 > 0:
            base += 0.15
        if ma20 is not None and latest_close >= ma20:
            base += 0.15
        return clamp(base)

    def _trend_reversal(self, context: dict[str, Any], uptrend: float, downtrend: float) -> float | None:
        previous = context.get("previousSegment")
        latest = context.get("latestSegment")
        ma20 = context["movingAverages"].get("ma20")
        latest_close = context.get("latestClose")
        if not previous or not latest or previous.get("direction") == latest.get("direction"):
            return None
        latest_return = abs(latest.get("returnPct") or 0)
        ma_cross = ma20 is not None and latest_close is not None and (
            latest.get("direction") == "up" and latest_close > ma20
            or latest.get("direction") == "down" and latest_close < ma20
        )
        if latest_return < 8 and not ma_cross:
            return None
        return clamp(0.4 + latest_return / 20 * 0.4 + max(uptrend, downtrend) * 0.2)

    def _continuation(self, context: dict[str, Any], uptrend: float, downtrend: float) -> float | None:
        previous = context.get("previousSegment")
        latest = context.get("latestSegment")
        if previous and latest and previous.get("direction") == latest.get("direction"):
            return clamp(0.45 + abs(latest.get("returnPct") or 0) / 20 * 0.35 + max(uptrend, downtrend) * 0.2)
        return20 = context.get("return20Bars")
        if return20 is not None and uptrend >= 0.7 and return20 > 0:
            return clamp(0.45 + return20 / 20 * 0.35 + uptrend * 0.2)
        if return20 is not None and downtrend >= 0.7 and return20 < 0:
            return clamp(0.45 + abs(return20) / 20 * 0.35 + downtrend * 0.2)
        return None

    def _turn_weak(self, context: dict[str, Any], uptrend: float) -> float | None:
        latest_close = context.get("latestClose")
        return5 = context.get("return5Bars")
        ma = context["movingAverages"]
        ma5 = ma.get("ma5")
        ma10 = ma.get("ma10")
        if latest_close is None or return5 is None or ma5 is None:
            return None
        broke_short_ma = latest_close < ma5 or (ma10 is not None and latest_close < ma10)
        if not broke_short_ma or return5 >= 0:
            return None
        return clamp(0.35 + abs(return5) / 8 * 0.4 + uptrend * 0.25)

    def _turn_strong(self, context: dict[str, Any], downtrend: float, range_bound: float | None) -> float | None:
        latest_close = context.get("latestClose")
        return5 = context.get("return5Bars")
        ma = context["movingAverages"]
        ma5 = ma.get("ma5")
        ma10 = ma.get("ma10")
        if latest_close is None or return5 is None or ma5 is None or ma10 is None:
            return None
        if latest_close < ma5 or latest_close < ma10 or return5 <= 0:
            return None
        return clamp(0.35 + return5 / 8 * 0.35 + max(downtrend, range_bound or 0) * 0.30)

    def _failed_breakout(self, context: dict[str, Any]) -> float | None:
        candle = context.get("latestCandle") or {}
        previous_high = context.get("previousHigh")
        latest_close = context.get("latestClose")
        latest_high = candle.get("high")
        if not previous_high or latest_close is None or latest_high is None or latest_high <= previous_high:
            return None
        upper_shadow_ratio = candle.get("upperShadowRatio") or 0
        if latest_close > previous_high and upper_shadow_ratio < 0.45:
            return None
        return clamp(0.35 + upper_shadow_ratio * 0.45 + clamp((latest_high - previous_high) / previous_high / 0.05) * 0.20)

    def _direction(self, tags: dict[str, float], context: dict[str, Any]) -> str:
        positive = max((tags.get(tag, 0.0) for tag in POSITIVE_TAGS), default=0.0)
        negative = max((tags.get(tag, 0.0) for tag in NEGATIVE_TAGS), default=0.0)
        continuation = tags.get("continuation", 0.0)
        if continuation >= 0.3:
            if tags.get("downtrend", 0.0) >= tags.get("uptrend", 0.0):
                negative = max(negative, continuation)
            else:
                positive = max(positive, continuation)
        if positive > negative and positive >= 0.3:
            return "positive"
        if negative >= 0.3:
            return "negative"
        return "neutral"

    def _evidence(self, label: str, tags: dict[str, float], context: dict[str, Any]) -> list[str]:
        evidence: list[str] = []
        if tags.get("uptrend", 0.0) >= 0.3:
            evidence.append(f"{label} ma5、ma10、ma20 呈多头排列，且最新收盘价位于均线之上，uptrend 标签触发")
        if tags.get("downtrend", 0.0) >= 0.3:
            evidence.append(f"{label} ma5、ma10、ma20 呈空头排列，且最新收盘价位于均线之下，downtrend 标签触发")
        if tags.get("range_bound", 0.0) >= 0.3:
            evidence.append(f"{label}最近 {context['windowBars']} 根 K 线振幅约 {self._fmt_pct(context.get('rangePct'))}，range_bound 标签触发")
        if tags.get("rebound", 0.0) >= 0.3:
            evidence.append(f"{label}前一趋势段下跌后，最近趋势段转为上涨，rebound 标签触发")
        if tags.get("pullback", 0.0) >= 0.3:
            evidence.append(f"{label}前一趋势段上涨后，最近趋势段回落但未破坏中期均线结构，pullback 标签触发")
        if tags.get("repair", 0.0) >= 0.3:
            evidence.append(f"{label}价格重新站上 ma5/ma10 且短期收益转正，repair 标签触发")
        if tags.get("breakout_from_range", 0.0) >= 0.3:
            evidence.append(f"{label}最新收盘价突破前期区间高点，breakout_from_range 标签触发")
        if tags.get("breakdown_from_range", 0.0) >= 0.3:
            evidence.append(f"{label}最新收盘价跌破前期区间低点，breakdown_from_range 标签触发")
        if tags.get("trend_reversal", 0.0) >= 0.3:
            evidence.append(f"{label}最近趋势段与前一趋势段方向相反，并伴随均线位置切换，trend_reversal 标签触发")
        if tags.get("continuation", 0.0) >= 0.3:
            evidence.append(f"{label}前后趋势段方向保持一致，continuation 标签触发")
        if tags.get("turn_weak", 0.0) >= 0.3:
            evidence.append(f"{label}价格跌破短期均线且短期收益转负，turn_weak 标签触发")
        if tags.get("turn_strong", 0.0) >= 0.3:
            evidence.append(f"{label}价格站回 ma5/ma10 且短期收益转正，turn_strong 标签触发")
        if tags.get("failed_breakout", 0.0) >= 0.3:
            evidence.append(f"{label}盘中突破前高但收盘未有效站稳或上影线明显，failed_breakout 标签触发")
        return evidence

    def _path_features(self, points: list[dict[str, Any]]) -> dict[str, Any]:
        turning_points = self._turning_points(points)
        index_by_date = {point["date"]: index for index, point in enumerate(points)}
        segments = []
        for start, end in zip(turning_points, turning_points[1:]):
            duration = index_by_date.get(end["date"], 0) - index_by_date.get(start["date"], 0)
            return_pct = self._return_pct(start["price"], end["price"])
            segments.append(self._compact({
                "startDate": start["date"],
                "endDate": end["date"],
                "direction": self._segment_direction(return_pct),
                "durationBars": duration,
                "startPrice": start["price"],
                "endPrice": end["price"],
                "returnPct": return_pct,
                "slopePctPerBar": return_pct / duration if return_pct is not None and duration > 0 else None,
            }))
        if not segments and len(points) >= 2:
            return_pct = self._return_pct(points[0]["close"], points[-1]["close"])
            segments.append(self._compact({
                "startDate": points[0]["date"],
                "endDate": points[-1]["date"],
                "direction": self._segment_direction(return_pct),
                "durationBars": len(points) - 1,
                "startPrice": points[0]["close"],
                "endPrice": points[-1]["close"],
                "returnPct": return_pct,
            }))
        return {
            "method": "zigzag",
            "reversalThresholdPct": self.ZIGZAG_REVERSAL_THRESHOLD_PCT,
            "segments": segments,
        }

    def _turning_points(self, points: list[dict[str, Any]]) -> list[dict[str, Any]]:
        closes = [{"date": point["date"], "price": point["close"]} for point in points]
        if len(closes) <= 2:
            return closes

        threshold = self.ZIGZAG_REVERSAL_THRESHOLD_PCT / 100
        turning_points = [closes[0]]
        anchor = closes[0]
        candidate = closes[0]
        direction: str | None = None
        for point in closes[1:]:
            if direction is None:
                if point["price"] >= candidate["price"]:
                    candidate = point
                    if anchor["price"] and (candidate["price"] - anchor["price"]) / anchor["price"] >= threshold:
                        direction = "up"
                elif point["price"] <= candidate["price"]:
                    candidate = point
                    if anchor["price"] and (anchor["price"] - candidate["price"]) / anchor["price"] >= threshold:
                        direction = "down"
                continue
            if direction == "up":
                if point["price"] >= candidate["price"]:
                    candidate = point
                elif candidate["price"] and (candidate["price"] - point["price"]) / candidate["price"] >= threshold:
                    turning_points.append({"date": candidate["date"], "price": candidate["price"], "type": "high"})
                    direction = "down"
                    candidate = point
            else:
                if point["price"] <= candidate["price"]:
                    candidate = point
                elif candidate["price"] and (point["price"] - candidate["price"]) / candidate["price"] >= threshold:
                    turning_points.append({"date": candidate["date"], "price": candidate["price"], "type": "low"})
                    direction = "up"
                    candidate = point
        last_type = "high" if direction == "up" else "low" if direction == "down" else None
        turning_points.append(self._compact({
            "date": closes[-1]["date"],
            "price": closes[-1]["price"],
            "type": last_type,
        }))
        return turning_points

    def _latest_candle(self, latest: dict[str, Any]) -> dict[str, float | None]:
        open_price = latest.get("open")
        close = latest.get("close")
        high = latest.get("high")
        low = latest.get("low")
        if None in (open_price, close, high, low) or high == low:
            return {"high": high}
        upper_shadow = high - max(open_price, close)
        body = abs(close - open_price)
        return {
            "high": high,
            "low": low,
            "upperShadowRatio": clamp(upper_shadow / (high - low)),
            "bodyRatio": clamp(body / (high - low)),
        }

    def _ma(self, latest: dict[str, Any], closes: list[float], key: str, window: int) -> float | None:
        value = latest.get(key)
        if value is not None:
            return value
        return self._mean_last(closes, window)

    def _mean_last(self, values: list[float], window: int) -> float | None:
        if len(values) < window:
            return None
        return mean(values[-window:])

    def _volume_ratio(self, values: list[float], window: int) -> float | None:
        if len(values) < window + 1:
            return None
        baseline = values[-window - 1:-1]
        average = mean(baseline)
        if average == 0:
            return None
        return values[-1] / average

    def _return_last(self, closes: list[float], lookback: int) -> float | None:
        if len(closes) <= lookback:
            return None
        return self._return_pct(closes[-lookback - 1], closes[-1])

    def _return_pct(self, start: float | None, end: float | None) -> float | None:
        if start is None or end is None or start == 0:
            return None
        return (end - start) / start * 100

    def _range_pct(self, high: float | None, low: float | None) -> float | None:
        if high is None or low is None or low == 0:
            return None
        return (high - low) / low

    def _position(self, value: float | None, high: float | None, low: float | None) -> float | None:
        if value is None or high is None or low is None or high == low:
            return None
        return (value - low) / (high - low)

    def _max_drawdown_pct(self, closes: list[float]) -> float | None:
        if not closes:
            return None
        peak = closes[0]
        max_drawdown = 0.0
        for close in closes:
            peak = max(peak, close)
            if peak:
                max_drawdown = min(max_drawdown, (close - peak) / peak * 100)
        return max_drawdown

    def _volatility_pct(self, closes: list[float]) -> float | None:
        if len(closes) < 2:
            return None
        returns = [
            (current - previous) / previous * 100
            for previous, current in zip(closes, closes[1:])
            if previous
        ]
        if len(returns) < 2:
            return None
        average = mean(returns)
        variance = sum((item - average) ** 2 for item in returns) / (len(returns) - 1)
        return sqrt(variance)

    def _segment_direction(self, return_pct: float | None) -> str:
        if return_pct is None:
            return "flat"
        if return_pct >= 3:
            return "up"
        if return_pct <= -3:
            return "down"
        return "flat"

    def _value_or_default(self, value: float | None, default: float | None) -> float | None:
        return value if value is not None else default

    def _fmt_pct(self, value: float | None) -> str:
        if value is None:
            return "-"
        return f"{value * 100:.2f}%"

    def _compact(self, value: dict[str, Any]) -> dict[str, Any]:
        return {key: item for key, item in value.items() if item is not None}
