from __future__ import annotations

from math import sqrt
from statistics import mean
from typing import Any


class MarketContextBuilder:
    DAILY_KLINE_WINDOW = 120
    ZIGZAG_REVERSAL_THRESHOLD_PCT = 8.0

    def build(self, message: dict[str, Any]) -> dict[str, Any]:
        return {
            "snapshot": self._snapshot(message),
            "intraday": self._intraday(message),
            "dailyKline": self._daily_kline(message),
        }

    def _snapshot(self, message: dict[str, Any]) -> dict[str, Any]:
        target = self._dict(message.get("target"))
        market_data = self._dict(message.get("marketData"))
        snapshot = {
            "targetType": target.get("type"),
            "targetCode": target.get("code"),
            "targetName": target.get("name"),
            "secid": target.get("secid") or market_data.get("secid"),
            "latestPrice": self._number(market_data.get("latestPrice")),
            "changeAmount": self._number(market_data.get("changeAmount")),
            "changePercent": self._number(market_data.get("changePercent")),
            "openPrice": self._number(market_data.get("openPrice")),
            "highPrice": self._number(market_data.get("highPrice")),
            "lowPrice": self._number(market_data.get("lowPrice")),
            "previousClosePrice": self._number(market_data.get("previousClosePrice")),
            "volume": self._number(market_data.get("volume")),
            "turnoverAmount": self._number(market_data.get("turnoverAmount") or market_data.get("amount")),
            "turnoverRate": self._number(market_data.get("turnoverRate")),
            "amplitude": self._number(market_data.get("amplitude")),
            "syncedAt": market_data.get("syncedAt"),
        }
        return self._compact(snapshot)

    def _intraday(self, message: dict[str, Any]) -> dict[str, Any]:
        rows = self._sorted_rows(
            self._list(message.get("intradayData")),
            ("trendTime", "time", "tradeTime"),
        )
        points = [
            {
                "time": row.get("trendTime") or row.get("time") or row.get("tradeTime"),
                "price": self._number(row.get("price") or row.get("latestPrice") or row.get("closePrice")),
                "volume": self._number(row.get("volume")),
            }
            for row in rows
        ]
        points = [point for point in points if point["price"] is not None]
        if not points:
            return {"available": False, "window": "latest_trading_day", "points": 0}

        prices = [point["price"] for point in points]
        first_price = prices[0]
        latest_price = prices[-1]
        high_price = max(prices)
        low_price = min(prices)
        high_index = prices.index(high_price)
        low_index = prices.index(low_price)
        result = {
            "available": True,
            "window": "latest_trading_day",
            "points": len(points),
            "openToLatestPct": self._return_pct(first_price, latest_price),
            "highTime": points[high_index]["time"],
            "lowTime": points[low_index]["time"],
            "latestPositionInDayRange": self._position(latest_price, high_price, low_price),
            "morningReturnPct": self._period_return(points, 0, max(0, min(len(points) - 1, len(points) // 2 - 1))),
            "afternoonReturnPct": self._period_return(points, len(points) // 2, len(points) - 1),
            "volumeConcentration": self._volume_concentration(points),
            "pathFeatures": self._intraday_path_features(points),
        }
        return self._compact(result)

    def _daily_kline(self, message: dict[str, Any]) -> dict[str, Any]:
        rows = self._sorted_rows(self._list(message.get("dailyKlines")), ("tradeDate",))
        rows = rows[-self.DAILY_KLINE_WINDOW:]
        points = [
            {
                "date": row.get("tradeDate"),
                "close": self._number(row.get("closePrice")),
                "high": self._number(row.get("highPrice")),
                "low": self._number(row.get("lowPrice")),
            }
            for row in rows
        ]
        points = [point for point in points if point["date"] and point["close"] is not None]
        if not points:
            return {
                "windowDays": self.DAILY_KLINE_WINDOW,
                "availableDays": 0,
                "pathFeatures": self._empty_zigzag(),
            }

        closes = [point["close"] for point in points]
        highs = [point["high"] for point in points if point["high"] is not None]
        lows = [point["low"] for point in points if point["low"] is not None]
        latest_close = closes[-1]
        window_high = max(highs or closes)
        window_low = min(lows or closes)
        result = {
            "windowDays": self.DAILY_KLINE_WINDOW,
            "availableDays": len(points),
            "startDate": points[0]["date"],
            "endDate": points[-1]["date"],
            "latestClose": latest_close,
            "windowHigh": window_high,
            "windowLow": window_low,
            "returnPct": self._return_pct(closes[0], latest_close),
            "maxDrawdownPct": self._max_drawdown_pct(closes),
            "volatilityPct": self._volatility_pct(closes),
            "distanceToHighPct": self._distance_pct(latest_close, window_high),
            "distanceToLowPct": self._distance_pct(latest_close, window_low),
            "movingAverages": self._moving_averages(closes),
            "pathFeatures": self._zigzag_path_features(points),
        }
        return self._compact(result)

    def _intraday_path_features(self, points: list[dict[str, Any]]) -> list[dict[str, Any]]:
        if len(points) < 2:
            return []
        anchors = sorted(set([0, len(points) // 2, len(points) - 1]))
        segments = []
        for start, end in zip(anchors, anchors[1:]):
            if end <= start:
                continue
            start_price = points[start]["price"]
            end_price = points[end]["price"]
            segments.append(self._compact({
                "startTime": points[start]["time"],
                "endTime": points[end]["time"],
                "direction": self._direction(start_price, end_price),
                "returnPct": self._return_pct(start_price, end_price),
                "durationMinutes": end - start,
            }))
        return segments

    def _zigzag_path_features(self, points: list[dict[str, Any]]) -> dict[str, Any]:
        turning_points = self._zigzag_turning_points(points)
        index_by_date = {point["date"]: index for index, point in enumerate(points)}
        segments = []
        for start, end in zip(turning_points, turning_points[1:]):
            duration = index_by_date.get(end["date"], 0) - index_by_date.get(start["date"], 0)
            return_pct = self._return_pct(start["price"], end["price"])
            segments.append(self._compact({
                "startDate": start["date"],
                "endDate": end["date"],
                "direction": self._direction(start["price"], end["price"]),
                "durationDays": duration,
                "startPrice": start["price"],
                "endPrice": end["price"],
                "returnPct": return_pct,
                "slopePctPerDay": return_pct / duration if return_pct is not None and duration > 0 else None,
            }))
        return {
            "method": "zigzag",
            "reversalThresholdPct": self.ZIGZAG_REVERSAL_THRESHOLD_PCT,
            "turningPoints": turning_points,
            "segments": segments,
        }

    def _zigzag_turning_points(self, points: list[dict[str, Any]]) -> list[dict[str, Any]]:
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

    def _empty_zigzag(self) -> dict[str, Any]:
        return {
            "method": "zigzag",
            "reversalThresholdPct": self.ZIGZAG_REVERSAL_THRESHOLD_PCT,
            "turningPoints": [],
            "segments": [],
        }

    def _moving_averages(self, closes: list[float]) -> dict[str, float]:
        return self._compact({
            "ma5": self._mean_last(closes, 5),
            "ma20": self._mean_last(closes, 20),
            "ma60": self._mean_last(closes, 60),
        })

    def _volume_concentration(self, points: list[dict[str, Any]]) -> dict[str, float]:
        volumes = [point["volume"] for point in points if point["volume"] is not None]
        total_volume = sum(volumes)
        if total_volume <= 0:
            return {}
        first_30 = sum(point["volume"] or 0 for point in points[:30])
        last_30 = sum(point["volume"] or 0 for point in points[-30:])
        return {
            "first30MinPct": first_30 / total_volume * 100,
            "last30MinPct": last_30 / total_volume * 100,
        }

    def _period_return(self, points: list[dict[str, Any]], start: int, end: int) -> float | None:
        if start < 0 or end < 0 or start >= len(points) or end >= len(points) or end <= start:
            return None
        return self._return_pct(points[start]["price"], points[end]["price"])

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
        avg = mean(returns)
        variance = sum((item - avg) ** 2 for item in returns) / (len(returns) - 1)
        return sqrt(variance)

    def _mean_last(self, values: list[float], window: int) -> float | None:
        if len(values) < window:
            return None
        return mean(values[-window:])

    def _position(self, value: float, high: float, low: float) -> float | None:
        denominator = high - low
        if denominator <= 0:
            return None
        return (value - low) / denominator

    def _distance_pct(self, value: float, anchor: float) -> float | None:
        if not anchor:
            return None
        return (value - anchor) / anchor * 100

    def _return_pct(self, start: float, end: float) -> float | None:
        if not start:
            return None
        return (end - start) / start * 100

    def _direction(self, start: float, end: float) -> str:
        if end > start:
            return "up"
        if end < start:
            return "down"
        return "flat"

    def _sorted_rows(self, rows: list[dict[str, Any]], keys: tuple[str, ...]) -> list[dict[str, Any]]:
        return sorted(rows, key=lambda row: next((str(row[key]) for key in keys if row.get(key)), ""))

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

    def _compact(self, data: dict[str, Any]) -> dict[str, Any]:
        return {key: value for key, value in data.items() if value is not None and value != {}}
