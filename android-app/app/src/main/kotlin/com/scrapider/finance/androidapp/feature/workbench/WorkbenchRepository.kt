package com.scrapider.finance.androidapp.feature.workbench

import com.scrapider.finance.androidapp.core.network.ApiConfig
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.network.NetworkFailure
import com.scrapider.finance.androidapp.core.network.NetworkResult
import com.scrapider.finance.androidapp.core.network.toEnvelope
import com.scrapider.finance.androidapp.core.network.toJsonPayload
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

class WorkbenchRepository(
    private val apiClient: FinanceApiClient,
) {
    suspend fun load(): NetworkResult<WorkbenchContent> = coroutineScope {
        val watchRequest = async { loadWatchTargets() }
        val alertRequest = async { loadAlerts() }
        val reportRequest = async { loadReports() }

        val watchResult = watchRequest.await()
        val alertResult = alertRequest.await()
        val reportResult = reportRequest.await()
        val failures = listOf(watchResult, alertResult, reportResult)
            .mapNotNull { result -> (result as? NetworkResult.Failure)?.reason }

        if (failures.size == WORKBENCH_DATA_SOURCE_COUNT) {
            return@coroutineScope NetworkResult.Failure(failures.first())
        }

        val watchTargets = (watchResult as? NetworkResult.Success)?.data.orEmpty()
        val alerts = (alertResult as? NetworkResult.Success)?.data.orEmpty()
        val reports = (reportResult as? NetworkResult.Success)?.data.orEmpty()
        NetworkResult.Success(
            WorkbenchContent(
                focusItems = buildFocusItems(watchTargets, alerts),
                reportItems = reports,
                partialFailure = failures.firstOrNull(),
            ),
        )
    }

    private suspend fun loadWatchTargets(): NetworkResult<List<WatchTarget>> =
        apiClient.get(ApiConfig.WATCH_GROUPS_PATH).toEnvelope().mapPayload(::parseWatchTargets)

    private suspend fun loadAlerts(): NetworkResult<List<StockAlert>> =
        apiClient.get(ApiConfig.STOCK_ALERTS_PATH).toEnvelope().mapPayload(::parseAlerts)

    private suspend fun loadReports(): NetworkResult<List<ReportItem>> =
        // 报告列表接口直接返回分页对象，不使用通用 code/data 信封。
        apiClient.get(reportTargetsPath()).toJsonPayload().mapPayload(::parseReports)

    private fun parseWatchTargets(root: JSONObject): List<WatchTarget> {
        val groups = root.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (groupIndex in 0 until groups.length()) {
                val group = groups.optJSONObject(groupIndex) ?: continue
                val groupId = group.stringValue("id")
                val items = group.optJSONArray("items") ?: continue
                for (itemIndex in 0 until items.length()) {
                    val item = items.optJSONObject(itemIndex) ?: continue
                    val targetType = item.stringValue("targetType")
                    val targetCode = item.stringValue("targetCode")
                    val targetName = item.stringValue("targetName")
                        .ifBlank { targetCode }
                        .ifBlank { "未命名标的" }
                    add(
                        WatchTarget(
                            id = item.stringValue("id")
                                .ifBlank { groupId + ":" + targetType + ":" + targetCode },
                            targetType = targetType,
                            targetCode = targetCode,
                            targetName = targetName,
                            changePercent = item.nullableDouble("changePercent"),
                        ),
                    )
                }
            }
        }
    }

    private fun parseAlerts(root: JSONObject): List<StockAlert> {
        val alerts = root.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (index in 0 until alerts.length()) {
                val alert = alerts.optJSONObject(index) ?: continue
                val targetType = alert.stringValue("targetType")
                val targetCode = alert.stringValue("targetCode")
                    .ifBlank { alert.stringValue("stockCode") }
                val targetName = alert.stringValue("targetName")
                    .ifBlank { alert.stringValue("stockName") }
                    .ifBlank { targetCode }
                    .ifBlank { "未命名标的" }
                add(
                    StockAlert(
                        id = alert.stringValue("id")
                            .ifBlank { "alert:" + targetType + ":" + targetCode },
                        targetType = targetType,
                        targetCode = targetCode,
                        targetName = targetName,
                        enabled = alert.optBoolean("enabled", false),
                        outOfThreshold = alert.optBoolean("outOfThreshold", false),
                        changePercent = alert.nullableDouble("changePercent"),
                    ),
                )
            }
        }
    }

    private fun parseReports(root: JSONObject): List<ReportItem> {
        val data = root.optJSONObject("data")
        val records = data?.optJSONArray("records")
            ?: root.optJSONArray("records")
            ?: root.optJSONArray("data")
            ?: JSONArray()
        return buildList {
            for (index in 0 until records.length()) {
                val item = records.optJSONObject(index) ?: continue
                val targetCode = item.stringValue("targetCode")
                val targetName = item.stringValue("targetName")
                    .ifBlank { targetCode }
                    .ifBlank { "未命名标的" }
                val rawStatus = item.stringValue("latestStatus")
                add(
                    ReportItem(
                        id = item.stringValue("latestReportId")
                            .ifBlank { item.stringValue("latestTaskNo") }
                            .ifBlank { "report:" + targetCode + ":" + index },
                        targetName = targetName,
                        reportTypeLabel = reportTypeLabel(item.stringValue("latestReportType")),
                        timeLabel = reportTimeLabel(item),
                        status = reportStatus(rawStatus),
                    ),
                )
            }
        }
    }

    private fun buildFocusItems(
        watchTargets: List<WatchTarget>,
        alerts: List<StockAlert>,
    ): List<FocusItem> {
        val alertItems = alerts
            .filter { it.enabled && it.outOfThreshold }
            .sortedByDescending { it.changePercent.magnitude() }
            .map { alert ->
                FocusItem(
                    id = alert.id,
                    targetName = alert.targetName,
                    targetCode = alert.targetCode,
                    targetTypeLabel = targetTypeLabel(alert.targetType),
                    changePercent = alert.changePercent,
                    status = FocusStatus.ThresholdExceeded,
                )
            }
        val alertedTargets = alerts
            .filter { it.enabled && it.outOfThreshold }
            .map { it.targetKey }
            .toSet()
        val movementItems = watchTargets
            .filterNot { it.targetKey in alertedTargets }
            .sortedByDescending { it.changePercent.magnitude() }
            .map { target ->
                FocusItem(
                    id = target.id,
                    targetName = target.targetName,
                    targetCode = target.targetCode,
                    targetTypeLabel = targetTypeLabel(target.targetType),
                    changePercent = target.changePercent,
                    status = FocusStatus.Movement,
                )
            }
        return (alertItems + movementItems)
            .distinctBy { it.targetTypeLabel + ":" + it.targetCode }
            .take(WORKBENCH_PREVIEW_ITEM_LIMIT)
    }

    private fun reportTargetsPath(): String =
        ApiConfig.REPORT_TARGETS_PATH +
            "?pageNum=" + REPORT_TARGETS_INITIAL_PAGE +
            "&pageSize=" + REPORT_TARGETS_INITIAL_PAGE_SIZE

    private companion object {
        const val WORKBENCH_DATA_SOURCE_COUNT = 3
        const val REPORT_TARGETS_INITIAL_PAGE = 1
        const val REPORT_TARGETS_INITIAL_PAGE_SIZE = 4
    }
}

private data class WatchTarget(
    val id: String,
    val targetType: String,
    val targetCode: String,
    val targetName: String,
    val changePercent: Double?,
) {
    val targetKey: String
        get() = targetType + ":" + targetCode
}

private data class StockAlert(
    val id: String,
    val targetType: String,
    val targetCode: String,
    val targetName: String,
    val enabled: Boolean,
    val outOfThreshold: Boolean,
    val changePercent: Double?,
) {
    val targetKey: String
        get() = targetType + ":" + targetCode
}

private fun <T> NetworkResult<JSONObject>.mapPayload(
    parser: (JSONObject) -> T,
): NetworkResult<T> = when (this) {
    is NetworkResult.Failure -> this
    is NetworkResult.Success -> runCatching { NetworkResult.Success(parser(data)) }
        .getOrElse { NetworkResult.Failure(NetworkFailure.InvalidResponse) }
}

private fun JSONObject.stringValue(key: String): String =
    optString(key, "").trim().takeUnless { it == "null" }.orEmpty()

private fun JSONObject.nullableDouble(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}

private fun Double?.magnitude(): Double = this?.let(::abs) ?: -1.0

private fun targetTypeLabel(rawType: String): String = when (rawType.uppercase(Locale.ROOT)) {
    "STOCK" -> "股票"
    "INDEX" -> "指数"
    "BOND" -> "可转债"
    "FUND" -> "基金"
    "SECTOR" -> "板块"
    else -> rawType.ifBlank { "标的" }
}

private fun reportTypeLabel(rawType: String): String = when (rawType.lowercase(Locale.ROOT)) {
    "quick_analysis" -> "快速分析"
    "risk_check" -> "风险检查"
    "valuation_report" -> "估值报告"
    else -> rawType.ifBlank { "未标注类型" }
}

private fun reportTimeLabel(item: JSONObject): String {
    val value = item.stringValue("latestGeneratedAt")
        .ifBlank { item.stringValue("latestCreatedAt") }
    return value.replace('T', ' ').take(REPORT_TIME_TEXT_LENGTH).ifBlank { "暂无时间" }
}

private fun reportStatus(rawStatus: String): ReportStatus = when (rawStatus.lowercase(Locale.ROOT)) {
    "success" -> ReportStatus.Generated
    "failed" -> ReportStatus.Failed
    "pending", "queued" -> ReportStatus.Pending
    "processing_current_scenes",
    "current_scenes_ready",
    "retrieving_knowledge",
    "generating_report",
    "running" -> ReportStatus.Generating
    else -> ReportStatus.Unknown
}

private const val REPORT_TIME_TEXT_LENGTH = 16
