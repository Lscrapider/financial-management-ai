from __future__ import annotations

from collections.abc import Iterable
from math import exp, log1p, sqrt
from statistics import median
from typing import Any

from app.scene_analysis.models import BaseMetrics


class BaseMetricsCalculator:
    def calculate(self, message: dict[str, Any]) -> BaseMetrics:
        values: dict[str, Any] = {}
        missing: list[str] = []

        market_data = self._dict(message.get("marketData"))
        daily_klines = self._list(message.get("dailyKlines"))
        intraday_data = self._list(message.get("intradayData"))
        config = self._dict(self._dict(message.get("config")).get("parameters"))
        target_type = self._dict(message.get("target")).get("type")

        self._add_market_metrics(values, market_data, missing)
        self._add_daily_kline_metrics(values, daily_klines, missing)
        self._add_intraday_metrics(values, intraday_data, missing)
        self._add_formula_metrics(values, config, missing)
        self._add_distribution_requirements(target_type, missing)
        return BaseMetrics(values=values, missing=self._unique(missing))

    def _add_market_metrics(self, values: dict[str, Any], market_data: dict[str, Any], missing: list[str]) -> None:
        latest_price = self._number(market_data.get("latestPrice"))
        open_price = self._number(market_data.get("openPrice"))
        high_price = self._number(market_data.get("highPrice"))
        low_price = self._number(market_data.get("lowPrice"))
        previous_close_price = self._number(market_data.get("previousClosePrice"))
        change_percent = self._number(market_data.get("changePercent"))
        turnover_amount = self._number(market_data.get("turnoverAmount") or market_data.get("amount"))

        self._put(values, "latest_price", latest_price)
        self._put(values, "open_price", open_price)
        self._put(values, "high_price", high_price)
        self._put(values, "low_price", low_price)
        self._put(values, "previous_close_price", previous_close_price)
        self._put(values, "change_pct", change_percent)
        self._put(values, "turnover_rate", self._number(market_data.get("turnoverRate")))
        self._put(values, "volume_ratio", self._number(market_data.get("volumeRatio")))
        self._put(values, "amplitude", self._number(market_data.get("amplitude")))
        self._put(values, "turnover_amount", turnover_amount)
        self._put(values, "pe_ttm", self._number(market_data.get("peTtm")))
        self._put(values, "pe_dynamic", self._number(market_data.get("peDynamic")))
        self._put(values, "pe_static", self._number(market_data.get("peStatic")))
        self._put(values, "pb_ratio", self._number(market_data.get("pbRatio")))
        self._put(values, "total_market_value", self._number(market_data.get("totalMarketValue")))
        self._put(values, "float_market_value", self._number(market_data.get("floatMarketValue")))

        if change_percent is None and latest_price is not None and previous_close_price:
            values["change_pct"] = (latest_price - previous_close_price) / previous_close_price * 100
        if latest_price is None:
            missing.append("market.latest_price")
        if previous_close_price is None:
            missing.append("market.previous_close_price")

    def _add_daily_kline_metrics(
        self,
        values: dict[str, Any],
        daily_klines: list[dict[str, Any]],
        missing: list[str],
    ) -> None:
        if not daily_klines:
            missing.append("daily_klines")
            return

        klines = sorted(
            [row for row in daily_klines if row.get("tradeDate")],
            key=lambda row: str(row.get("tradeDate")),
        )
        closes = self._numbers(row.get("closePrice") for row in klines)
        highs = self._numbers(row.get("highPrice") for row in klines)
        lows = self._numbers(row.get("lowPrice") for row in klines)
        volumes = self._numbers(row.get("volume") for row in klines)
        turnover_rates = self._numbers(row.get("turnoverRate") for row in klines)
        amounts = [
            self._amount(row.get("turnoverAmount") or row.get("amount"), row.get("volume"), row.get("closePrice"))
            for row in klines
        ]
        amplitudes = self._amplitude_history(highs, lows, closes)
        if not closes:
            missing.append("daily_klines.close_price")
            return

        current_price = self._number(values.get("latest_price")) or closes[-1]
        current_volume = volumes[-1] if volumes else None
        values["daily_count"] = len(klines)
        self._put(values, "current_price", current_price)
        self._put(values, "current_volume", current_volume)
        values["ma5"] = self._mean_last(closes, 5)
        values["ma10"] = self._mean_last(closes, 10)
        values["ma20"] = self._mean_last(closes, 20)
        values["recent_high_20d"] = self._max_last(highs, 20)
        values["recent_low_20d"] = self._min_last(lows, 20)
        values["prev_high_20d"] = self._max_last(highs[:-1], 20)
        values["prev_low_20d"] = self._min_last(lows[:-1], 20)
        values["range_pct_20d"] = self._range_pct(values["recent_high_20d"], values["recent_low_20d"])
        values["position_20d"] = self._position(current_price, values["recent_high_20d"], values["recent_low_20d"])
        values["volume_ratio_5d"] = self._ratio_to_mean_last(volumes, 5)
        values["volume_ratio_20d"] = self._ratio_to_mean_last(volumes, 20)
        values["volume_ratio_20d_history"] = self._ratio_to_mean_history(volumes, 20)
        values["volatility_20d"] = self._volatility(closes, 20)
        values["price_return_5d"] = self._return_pct(closes, 5)
        values["price_return_20d"] = self._return_pct(closes, 20)
        values["volume_robust_zscore"] = self._robust_zscore_last(volumes, 20)
        values["volume_history_60d"] = volumes[-60:]
        values["turnover_rate_history"] = turnover_rates[-250:]
        values["trading_attention_history_20d"] = self._trading_attention_history(
            amounts,
            turnover_rates,
            amplitudes,
            20,
        )
        values["atr_ratio_14"] = self._atr_ratio_last(highs, lows, closes, 14)
        values["atr_ratio_history_250d"] = self._atr_ratio_history(highs, lows, closes, 14)[-250:]
        self._put(values, "support_price", values["recent_low_20d"])

        if len(closes) < 20:
            missing.append("daily_klines.20d_window")
        if len(volumes) < 20:
            missing.append("daily_klines.volume_20d_window")
        if len(volumes) < 60:
            missing.append("daily_klines.volume_60d_window")
        if len(turnover_rates) < 20:
            missing.append("daily_klines.turnover_rate_window")
        if len(values["trading_attention_history_20d"]) < 20:
            missing.append("daily_klines.trading_attention_20d_window")
        if len(closes) < 15 or len(highs) < 15 or len(lows) < 15:
            missing.append("daily_klines.atr_14_window")

    def _add_intraday_metrics(
        self,
        values: dict[str, Any],
        intraday_data: list[dict[str, Any]],
        missing: list[str],
    ) -> None:
        if not intraday_data:
            missing.append("intraday_data")
            return

        prices = self._numbers(
            row.get("price") or row.get("latestPrice") or row.get("closePrice")
            for row in intraday_data
        )
        if not prices:
            missing.append("intraday_data.price")
            return

        previous_close = self._number(values.get("previous_close_price"))
        values["intraday_latest_price"] = prices[-1]
        values["intraday_high"] = max(prices)
        values["intraday_low"] = min(prices)
        values["intraday_range_pct"] = self._range_pct(max(prices), min(prices))
        if previous_close:
            values["intraday_return_pct"] = (prices[-1] - previous_close) / previous_close * 100

    def _add_formula_metrics(
        self,
        values: dict[str, Any],
        config: dict[str, Any],
        missing: list[str],
    ) -> None:
        price_config = self._dict(config.get("price_config"))
        volume_config = self._dict(config.get("volume_config"))
        risk_config = self._dict(config.get("risk_strategy_config"))
        sentiment_config = self._dict(config.get("sentiment_config"))

        change_pct = self._number(values.get("change_pct"))
        if change_pct is not None:
            values["price_move"] = self._sigmoid_score(
                abs(change_pct),
                self._config_number(price_config, "price_move_center", missing),
                self._config_number(price_config, "price_move_scale", missing),
            )
            if change_pct > 0:
                values["price_rise"] = self._sigmoid_score(
                    change_pct,
                    self._config_number(price_config, "price_rise_center", missing),
                    self._config_number(price_config, "price_rise_scale", missing),
                )
                values["price_drop"] = 0.0
            elif change_pct < 0:
                values["price_drop"] = self._sigmoid_score(
                    abs(change_pct),
                    self._config_number(price_config, "price_drop_center", missing),
                    self._config_number(price_config, "price_drop_scale", missing),
                )
                values["price_rise"] = 0.0
            else:
                values["price_rise"] = 0.0
                values["price_drop"] = 0.0

        volume_zscore = self._number(values.get("volume_robust_zscore"))
        if volume_zscore is not None:
            values["volume_expand"] = self._sigmoid_score(
                volume_zscore,
                self._config_number(volume_config, "volume_expand_center", missing),
                self._config_number(volume_config, "volume_expand_scale", missing),
            )
            values["volume_spike"] = self._sigmoid_score(
                volume_zscore,
                self._config_number(volume_config, "volume_spike_center", missing),
                self._config_number(volume_config, "volume_spike_scale", missing),
            )

        current_volume = self._number(values.get("current_volume"))
        volume_history_60d = self._number_list(values.get("volume_history_60d"))
        volume_rank = self._percentile_rank(current_volume, volume_history_60d)
        if volume_rank is not None:
            values["volume_shrink"] = self._clamp(1 - volume_rank)

        turnover_rate = self._number(values.get("turnover_rate"))
        turnover_rank = self._percentile_rank(turnover_rate, self._number_list(values.get("turnover_rate_history")))
        if turnover_rank is not None:
            values["high_turnover"] = turnover_rank
            values["low_turnover"] = self._clamp(1 - turnover_rank)

        position_20d = self._number(values.get("position_20d"))
        if position_20d is not None:
            values["near_recent_high"] = self._clamp((position_20d - 0.8) / 0.2)
            values["near_recent_low"] = self._clamp((0.2 - position_20d) / 0.2)

        current_price = self._number(values.get("current_price") or values.get("latest_price"))
        prev_high_20d = self._number(values.get("prev_high_20d"))
        prev_low_20d = self._number(values.get("prev_low_20d"))
        if current_price is not None and prev_high_20d is not None:
            values["breakout"] = 1.0 if current_price > prev_high_20d else 0.0
        if current_price is not None and prev_low_20d is not None:
            values["break_recent_low"] = 1.0 if current_price < prev_low_20d else 0.0

        intraday_high = self._number(values.get("intraday_high"))
        intraday_low = self._number(values.get("intraday_low"))
        if current_price is not None and intraday_high is not None and intraday_low is not None:
            denominator = intraday_high - intraday_low
            if denominator > 0:
                values["close_weak"] = self._clamp((intraday_high - current_price) / denominator)

        open_price = self._number(values.get("open_price"))
        high_price = self._number(values.get("high_price"))
        low_price = self._number(values.get("low_price"))
        if None not in (open_price, high_price, low_price, current_price):
            denominator = high_price - low_price
            if denominator > 0:
                values["upper_shadow"] = self._clamp((high_price - max(open_price, current_price)) / denominator)

        previous_close = self._number(values.get("previous_close_price"))
        if high_price is not None and low_price is not None and previous_close:
            values["large_intraday_move"] = self._clamp((high_price - low_price) / previous_close / 0.08)

        atr_ratio = self._number(values.get("atr_ratio_14"))
        atr_history = self._number_list(values.get("atr_ratio_history_250d"))
        volatility = self._percentile_rank(atr_ratio, atr_history)
        if volatility is not None:
            values["volatility"] = volatility

        support_price = self._number(values.get("support_price"))
        support_distance_threshold = self._config_number(
            risk_config,
            "support_distance_threshold",
            missing,
        )
        if current_price is not None and support_price is not None and support_distance_threshold:
            values["support_distance"] = self._clamp(
                ((current_price - support_price) / current_price) / support_distance_threshold
            )

        turnover_amount = self._number(values.get("turnover_amount"))
        current_volume = self._number(values.get("current_volume"))
        turnover_rate = self._number(values.get("turnover_rate"))
        amplitude = self._number(values.get("amplitude"))
        amount = turnover_amount if turnover_amount is not None else self._amount(None, current_volume, current_price)
        trading_attention = self._trading_attention(amount, turnover_rate, amplitude)
        if trading_attention is not None:
            values["trading_attention"] = trading_attention
            history = self._number_list(values.get("trading_attention_history_20d"))
            if history:
                average_attention = sum(history) / len(history)
                if average_attention > 0:
                    attention_rise = trading_attention / average_attention
                    values["trading_attention_rise"] = attention_rise
                    values["market_attention_rise"] = self._sigmoid_score(
                        attention_rise,
                        self._config_number_any(
                            sentiment_config,
                            ("attention_rise_center", "attention_center"),
                            1.5,
                        ),
                        self._config_number_any(
                            sentiment_config,
                            ("attention_rise_scale", "attention_scale"),
                            0.4,
                        ),
                    )
                    values["low_attention"] = self._clamp(
                        (1 - attention_rise)
                        / self._config_number_any(sentiment_config, ("low_attention_scale",), 0.5)
                    )

    def _add_distribution_requirements(self, target_type: Any, missing: list[str]) -> None:
        missing.append("distribution.pe_history_or_industry")
        missing.append("distribution.pb_history_or_industry")
        if str(target_type or "").upper() == "STOCK":
            missing.append("fundamental.financial_summary")
            missing.append("fundamental.industry")

    def _put(self, values: dict[str, Any], key: str, value: Any) -> None:
        if value is not None:
            values[key] = value

    def _dict(self, value: Any) -> dict[str, Any]:
        return value if isinstance(value, dict) else {}

    def _list(self, value: Any) -> list[dict[str, Any]]:
        if not isinstance(value, list):
            return []
        return [row for row in value if isinstance(row, dict)]

    def _number(self, value: Any) -> float | None:
        if value is None or value == "":
            return None
        try:
            return float(value)
        except (TypeError, ValueError):
            return None

    def _numbers(self, values: Iterable[Any]) -> list[float]:
        return [number for number in (self._number(value) for value in values) if number is not None]

    def _number_list(self, value: Any) -> list[float]:
        if not isinstance(value, list):
            return []
        return self._numbers(value)

    def _mean_last(self, values: list[float], window: int) -> float | None:
        if len(values) < window:
            return None
        sample = values[-window:]
        return sum(sample) / len(sample)

    def _max_last(self, values: list[float], window: int) -> float | None:
        if not values:
            return None
        return max(values[-window:])

    def _min_last(self, values: list[float], window: int) -> float | None:
        if not values:
            return None
        return min(values[-window:])

    def _range_pct(self, high: float | None, low: float | None) -> float | None:
        if high is None or low is None or low == 0:
            return None
        return (high - low) / low

    def _position(self, price: float | None, high: float | None, low: float | None) -> float | None:
        if price is None or high is None or low is None or high == low:
            return None
        return (price - low) / (high - low)

    def _ratio_to_mean_last(self, values: list[float], window: int) -> float | None:
        if len(values) < window + 1:
            return None
        baseline = values[-window - 1 : -1]
        average = sum(baseline) / len(baseline)
        if average == 0:
            return None
        return values[-1] / average

    def _ratio_to_mean_history(self, values: list[float], window: int) -> list[float]:
        if len(values) < window + 1:
            return []
        ratios: list[float] = []
        for index in range(window, len(values)):
            baseline = values[index - window : index]
            average = sum(baseline) / len(baseline)
            if average == 0:
                continue
            ratios.append(values[index] / average)
        return ratios

    def _return_pct(self, closes: list[float], lookback: int) -> float | None:
        if len(closes) <= lookback or closes[-lookback - 1] == 0:
            return None
        return (closes[-1] - closes[-lookback - 1]) / closes[-lookback - 1] * 100

    def _amount(self, amount: Any, volume: Any, price: Any) -> float | None:
        value = self._number(amount)
        if value is not None:
            return value
        volume_value = self._number(volume)
        price_value = self._number(price)
        if volume_value is None or price_value is None:
            return None
        return volume_value * price_value

    def _amplitude_history(self, highs: list[float], lows: list[float], closes: list[float]) -> list[float | None]:
        values: list[float | None] = []
        length = min(len(highs), len(lows), len(closes))
        for index in range(length):
            if index == 0:
                values.append(None)
                continue
            previous_close = closes[index - 1]
            if previous_close == 0:
                values.append(None)
                continue
            values.append((highs[index] - lows[index]) / previous_close * 100)
        return values

    def _trading_attention(
        self,
        amount: float | None,
        turnover_rate: float | None,
        amplitude: float | None,
    ) -> float | None:
        if amount is None or amount <= 0 or turnover_rate is None or amplitude is None:
            return None
        return log1p(amount) * (1 + turnover_rate / 100) * (1 + amplitude / 100)

    def _trading_attention_history(
        self,
        amounts: list[float | None],
        turnover_rates: list[float],
        amplitudes: list[float | None],
        window: int,
    ) -> list[float]:
        length = min(len(amounts), len(turnover_rates), len(amplitudes))
        scores = [
            score
            for score in (
                self._trading_attention(amounts[index], turnover_rates[index], amplitudes[index])
                for index in range(length)
            )
            if score is not None
        ]
        if len(scores) < window + 1:
            return []
        return scores[-window - 1 : -1]

    def _volatility(self, closes: list[float], window: int) -> float | None:
        if len(closes) < window + 1:
            return None
        returns = [
            (closes[index] - closes[index - 1]) / closes[index - 1]
            for index in range(len(closes) - window, len(closes))
            if closes[index - 1] != 0
        ]
        if len(returns) < 2:
            return None
        average = sum(returns) / len(returns)
        variance = sum((item - average) ** 2 for item in returns) / (len(returns) - 1)
        return sqrt(variance)

    def _robust_zscore_last(self, values: list[float], window: int) -> float | None:
        if len(values) < window + 1:
            return None
        baseline = values[-window - 1 : -1]
        baseline_median = median(baseline)
        deviations = [abs(item - baseline_median) for item in baseline]
        mad = median(deviations)
        if mad == 0:
            return None
        return (values[-1] - baseline_median) / (1.4826 * mad)

    def _atr_ratio_last(
        self,
        highs: list[float],
        lows: list[float],
        closes: list[float],
        window: int,
    ) -> float | None:
        history = self._atr_ratio_history(highs, lows, closes, window)
        return history[-1] if history else None

    def _atr_ratio_history(
        self,
        highs: list[float],
        lows: list[float],
        closes: list[float],
        window: int,
    ) -> list[float]:
        length = min(len(highs), len(lows), len(closes))
        if length < window + 1:
            return []
        true_ranges: list[float] = []
        for index in range(1, length):
            high = highs[index]
            low = lows[index]
            previous_close = closes[index - 1]
            true_ranges.append(max(high - low, abs(high - previous_close), abs(low - previous_close)))
        ratios: list[float] = []
        for end in range(window, len(true_ranges) + 1):
            close = closes[end]
            if close == 0:
                continue
            atr = sum(true_ranges[end - window : end]) / window
            ratios.append(atr / close)
        return ratios

    def _percentile_rank(self, value: float | None, history_values: list[float]) -> float | None:
        if value is None or not history_values:
            return None
        return self._clamp(sum(1 for item in history_values if item <= value) / len(history_values))

    def _sigmoid_score(self, value: float, center: float | None, scale: float | None) -> float | None:
        if center is None or scale is None or scale == 0:
            return None
        return self._clamp(1 / (1 + exp(-(value - center) / scale)))

    def _config_number(self, config: dict[str, Any], key: str, missing: list[str]) -> float | None:
        value = self._number(config.get(key))
        if value is None:
            missing.append(f"config.{key}")
        return value

    def _config_number_any(self, config: dict[str, Any], keys: tuple[str, ...], default: float) -> float:
        for key in keys:
            value = self._number(config.get(key))
            if value is not None:
                return value
        return default

    def _clamp(self, value: float) -> float:
        return min(max(value, 0.0), 1.0)

    def _unique(self, values: list[str]) -> list[str]:
        result: list[str] = []
        for value in values:
            if value not in result:
                result.append(value)
        return result
