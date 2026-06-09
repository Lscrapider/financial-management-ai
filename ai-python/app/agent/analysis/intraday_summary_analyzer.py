from __future__ import annotations

from typing import Any


class IntradaySummaryAnalyzer:
    def summarize(self, rows: list[dict[str, Any]]) -> dict[str, Any]:
        points = self._points(rows)
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
            "pathFeatures": self._path_features(points),
        }
        return self._compact(result)

    def _points(self, rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
        sorted_rows = sorted(
            [row for row in rows if isinstance(row, dict)],
            key=lambda row: str(row.get("trendTime") or row.get("time") or row.get("tradeTime") or ""),
        )
        points = [
            {
                "time": row.get("trendTime") or row.get("time") or row.get("tradeTime"),
                "price": self._number(row.get("price") or row.get("latestPrice") or row.get("closePrice")),
                "volume": self._number(row.get("volume")),
            }
            for row in sorted_rows
        ]
        return [point for point in points if point["price"] is not None]

    def _path_features(self, points: list[dict[str, Any]]) -> list[dict[str, Any]]:
        if len(points) < 2:
            return []
        anchors = sorted(set([0, len(points) // 2, len(points) - 1]))
        segments = []
        for start, end in zip(anchors, anchors[1:]):
            if end <= start:
                continue
            start_price = points[start]["price"]
            end_price = points[end]["price"]
            segments.append(
                self._compact(
                    {
                        "startTime": points[start]["time"],
                        "endTime": points[end]["time"],
                        "direction": self._direction(start_price, end_price),
                        "returnPct": self._return_pct(start_price, end_price),
                        "durationMinutes": end - start,
                    }
                )
            )
        return segments

    def _volume_concentration(self, points: list[dict[str, Any]]) -> dict[str, float]:
        volumes = [point["volume"] for point in points if point["volume"] is not None]
        total_volume = sum(volumes)
        if total_volume <= 0:
            return {}
        first_30 = sum(point["volume"] or 0 for point in points[:30])
        last_30 = sum(point["volume"] or 0 for point in points[-30:])
        first_half = sum(point["volume"] or 0 for point in points[: len(points) // 2])
        second_half = sum(point["volume"] or 0 for point in points[len(points) // 2 :])
        top_segment = "morning" if first_half >= second_half else "afternoon"
        return {
            "first30MinPct": first_30 / total_volume * 100,
            "last30MinPct": last_30 / total_volume * 100,
            "topSegment": top_segment,
            "ratio": max(first_half, second_half) / total_volume,
        }

    def _period_return(self, points: list[dict[str, Any]], start: int, end: int) -> float | None:
        if start < 0 or end < 0 or start >= len(points) or end >= len(points) or end <= start:
            return None
        return self._return_pct(points[start]["price"], points[end]["price"])

    def _position(self, value: float, high: float, low: float) -> float | None:
        denominator = high - low
        if denominator <= 0:
            return None
        return (value - low) / denominator

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

    def _number(self, value: Any) -> float | None:
        if value is None or value == "":
            return None
        try:
            return float(value)
        except (TypeError, ValueError):
            return None

    def _compact(self, data: dict[str, Any]) -> dict[str, Any]:
        return {key: value for key, value in data.items() if value is not None and value != {}}
