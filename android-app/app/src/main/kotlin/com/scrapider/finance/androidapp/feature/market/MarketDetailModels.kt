package com.scrapider.finance.androidapp.feature.market

import androidx.compose.runtime.Immutable

/** 周期值与现有行情接口保持一致。 */
enum class MarketChartPeriod(val label: String, val apiValue: String) {
    Intraday("分时", "intraday"),
    Daily("日 K", "daily"),
    Weekly("周 K", "weekly"),
    Monthly("月 K", "monthly"),
}

enum class MarketChartAdjust(val label: String, val apiValue: String) {
    Forward("前复权", "qfq"),
    Backward("后复权", "hfq"),
    None("不复权", "none"),
}

@Immutable
data class MarketChartPoint(
    val time: String,
    val close: Double,
    val open: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val volume: Double? = null,
    val average: Double? = null,
    val ma5: Double? = null,
    val ma10: Double? = null,
    val ma20: Double? = null,
)

@Immutable
data class MarketQuoteMetric(val label: String, val value: String)

@Immutable
data class MarketDetailQuote(
    val latestPrice: Double?,
    val changePercent: Double?,
    val previousClose: Double?,
    val timeLabel: String,
    val primaryMetrics: List<MarketQuoteMetric>,
    val extraMetrics: List<MarketQuoteMetric>,
)

@Immutable
data class MarketDetailState(
    val targetKey: String = "",
    val period: MarketChartPeriod = MarketChartPeriod.Intraday,
    val adjust: MarketChartAdjust = MarketChartAdjust.Backward,
    val quote: MarketDetailQuote? = null,
    val points: List<MarketChartPoint> = emptyList(),
    val quoteLoading: Boolean = false,
    val chartLoading: Boolean = false,
    val quoteUnavailable: Boolean = false,
    val quoteError: String = "",
    val chartError: String = "",
)

/** 仅行情可见页面使用，不属于全局后台任务。 */
internal const val MARKET_REFRESH_INTERVAL_MS = 10_000L
