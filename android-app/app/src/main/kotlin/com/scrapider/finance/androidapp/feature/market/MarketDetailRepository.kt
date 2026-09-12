package com.scrapider.finance.androidapp.feature.market

import com.scrapider.finance.androidapp.core.network.ApiConfig
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.network.NetworkFailure
import com.scrapider.finance.androidapp.core.network.NetworkResult
import com.scrapider.finance.androidapp.core.network.toJsonArrayPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/** 将已有报价、分时和 K 线接口映射为 Android 详情数据，不触发后台行情采集。 */
class MarketDetailRepository(private val apiClient: FinanceApiClient) {
    suspend fun quote(type: String, code: String): NetworkResult<MarketDetailQuote?> = withContext(Dispatchers.Default) {
        val source = source(type) ?: return@withContext NetworkResult.Failure(NetworkFailure.InvalidResponse)
        // 现有报价接口仅支持列表筛选，沿用行情已有查询上限，再按代码精确匹配。
        when (val result = apiClient.get("${source.path}/quotes?limit=$SYSTEM_TARGET_QUOTE_REQUEST_LIMIT").toJsonArrayPayload()) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> {
                val row = (0 until result.data.length()).asSequence().mapNotNull(result.data::optJSONObject)
                    .firstOrNull { it.optString(source.codeField) == code }
                NetworkResult.Success(row?.let { parseQuote(it, type) })
            }
        }
    }

    suspend fun chart(type: String, code: String, period: MarketChartPeriod, adjust: MarketChartAdjust): NetworkResult<List<MarketChartPoint>> = withContext(Dispatchers.Default) {
        val source = source(type) ?: return@withContext NetworkResult.Failure(NetworkFailure.InvalidResponse)
        val query = "${source.codeField}=${URLEncoder.encode(code, "UTF-8")}"
        val path = if (period == MarketChartPeriod.Intraday) {
            "${source.path}/intraday-trends?$query"
        } else {
            // 不覆盖接口已有的 250 条默认上限。
            "${source.path}/klines?$query&periodType=${period.apiValue}" +
                if (type == "STOCK") "&adjustType=${adjust.apiValue}" else ""
        }
        when (val result = apiClient.get(path).toJsonArrayPayload()) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> try {
                NetworkResult.Success(parsePoints(result.data, period))
            } catch (_: IllegalArgumentException) {
                NetworkResult.Failure(NetworkFailure.InvalidResponse)
            }
        }
    }

    private fun parsePoints(rows: JSONArray, period: MarketChartPeriod): List<MarketChartPoint> {
        val points = (0 until rows.length()).map { index ->
            val row = requireNotNull(rows.optJSONObject(index))
            val time = row.optString(if (period == MarketChartPeriod.Intraday) "trendTime" else "tradeDate", "")
            require(time.isNotBlank() && time != "null")
            val close = requireNotNull(row.number("closePrice"))
            val open = row.number("openPrice")
            val high = row.number("highPrice")
            val low = row.number("lowPrice")
            if (period != MarketChartPeriod.Intraday) {
                require(open != null && high != null && low != null && high >= maxOf(open, close) && low <= minOf(open, close))
            }
            MarketChartPoint(
                time = time,
                close = close,
                open = open,
                high = high,
                low = low,
                volume = row.number("volume"),
                average = row.number("averagePrice"),
                ma5 = row.number("ma5"),
                ma10 = row.number("ma10"),
                ma20 = row.number("ma20"),
            )
        }.distinctBy { it.time }.sortedBy { it.time }
        if (period != MarketChartPeriod.Intraday || points.isEmpty()) return points
        val latestDay = points.last().time.take(DATE_LENGTH)
        return points.filter { it.time.take(DATE_LENGTH) == latestDay }
    }

    private fun parseQuote(row: JSONObject, type: String): MarketDetailQuote {
        fun metric(label: String, field: String, suffix: String = "") =
            MarketQuoteMetric(label, row.number(field)?.let { decimal(it) + suffix } ?: "暂无")
        val primary = listOf(
            metric("今开", "openPrice"), metric("昨收", "previousClosePrice"),
            metric("最高", "highPrice"), metric("最低", "lowPrice"),
            metric("振幅", "amplitude", "%"), metric("成交量", "volume"),
        ) + if (type != "INDEX") listOf(metric("换手率", "turnoverRate", "%")) else emptyList()
        val extra = buildList {
            if (type != "INDEX") add(metric("均价", "averagePrice"))
            if (type == "STOCK") {
                add(metric("量比", "volumeRatio"))
                add(metric("涨停价", "limitUpPrice"))
                add(metric("跌停价", "limitDownPrice"))
                add(metric("市盈率 TTM", "peTtm"))
                add(metric("动态市盈率", "peDynamic"))
                add(metric("静态市盈率", "peStatic"))
                add(metric("市净率", "pbRatio"))
            }
            if (type == "BOND") {
                add(metric("转股价值", "conversionValue"))
                add(metric("转股溢价率", "conversionPremiumRate", "%"))
                add(MarketQuoteMetric("评级", row.optString("bondRating", "").takeIf { it.isNotBlank() && it != "null" } ?: "暂无"))
            }
        }
        return MarketDetailQuote(
            latestPrice = row.number("latestPrice"), changePercent = row.number("changePercent"),
            previousClose = row.number("previousClosePrice"),
            timeLabel = row.optString("syncedAt", "").takeIf { it != "null" }.orEmpty().replace('T', ' ').take(DATE_TIME_LENGTH),
            primaryMetrics = primary, extraMetrics = extra,
        )
    }

    private fun source(type: String): Source? = when (type) {
        "STOCK" -> Source(ApiConfig.STOCK_QUOTES_PATH.removeSuffix("/quotes"), "stockCode")
        "INDEX" -> Source(ApiConfig.INDEX_QUOTES_PATH.removeSuffix("/quotes"), "indexCode")
        "BOND" -> Source(ApiConfig.BOND_QUOTES_PATH.removeSuffix("/quotes"), "bondCode")
        else -> null
    }

    private data class Source(val path: String, val codeField: String)
    private companion object {
        const val DATE_LENGTH = 10
        const val DATE_TIME_LENGTH = 19
    }
}

private fun JSONObject.number(field: String): Double? =
    if (isNull(field)) null else optString(field).toDoubleOrNull()?.takeIf(Double::isFinite)

private fun decimal(value: Double): String = String.format(Locale.CHINA, "%,.3f", value).trimEnd('0').trimEnd('.')
