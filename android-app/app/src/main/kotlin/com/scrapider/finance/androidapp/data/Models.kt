package com.scrapider.finance.androidapp.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

data class SessionState(
    val authenticated: Boolean = false,
    val accessToken: String = "",
    val username: String = "",
    val realName: String = "",
    val roles: List<String> = emptyList(),
) {
    val displayName: String
        get() = realName.ifBlank { username.ifBlank { "研究员" } }

    val isAdmin: Boolean
        get() = roles.any { it.contains("admin", ignoreCase = true) || it.contains("管理员") }
}

data class WorkbenchMovement(
    val type: String,
    val code: String,
    val name: String,
    val latestPrice: Double,
    val changePercent: Double,
)

data class WorkbenchReportLine(
    val title: String,
    val engine: String,
    val status: String,
    val detail: String,
    val eta: String,
)

data class WorkbenchSummary(
    val focusCount: Int = 0,
    val watchItemCount: Int = 0,
    val stockItemCount: Int = 0,
    val indexItemCount: Int = 0,
    val bondItemCount: Int = 0,
    val watchUpCount: Int = 0,
    val watchDownCount: Int = 0,
    val alertCount: Int = 0,
    val enabledAlertCount: Int = 0,
    val outAlertCount: Int = 0,
    val nearAlertCount: Int = 0,
    val reportGeneratingCount: Int = 0,
    val reportFailedCount: Int = 0,
    val updatedAt: String = "--:--",
    val primaryAction: String = "后端暂无高优先级动作，继续查看观察池和提醒。",
    val reportAction: String = "报告目标暂无更新，进入报告研究查看历史版本。",
    val marketAction: String = "行情异动待刷新，优先处理布控和报告。",
    val movements: List<WorkbenchMovement> = emptyList(),
    val reports: List<WorkbenchReportLine> = emptyList(),
) {
    val hasAnyData: Boolean
        get() = watchItemCount > 0 || outAlertCount > 0 || nearAlertCount > 0 ||
            reportGeneratingCount > 0 || reportFailedCount > 0 || movements.isNotEmpty() || reports.isNotEmpty()
}

enum class MarketAssetType(
    val label: String,
    val apiPath: String,
    val codeKey: String,
    val nameKey: String,
    val defaultSortField: String,
) {
    Stock("股票", ApiConfig.STOCK_QUOTES_PATH, "stockCode", "stockName", "changePercent"),
    Index("指数", ApiConfig.INDEX_QUOTES_PATH, "indexCode", "indexName", "changePercent"),
    Bond("可转债", ApiConfig.BOND_QUOTES_PATH, "bondCode", "bondName", "changePercent"),
}

data class MarketFilter(
    val label: String,
    val marketCode: String,
)

data class MarketQuote(
    val assetType: MarketAssetType,
    val code: String,
    val name: String,
    val marketCode: String,
    val latestPrice: Double,
    val changeAmount: Double,
    val changePercent: Double,
    val turnoverAmount: Double,
    val amplitude: Double,
    val syncedAt: String,
    val conversionPremiumRate: Double? = null,
) {
    fun matches(keyword: String): Boolean {
        val normalized = keyword.trim()
        if (normalized.isBlank()) return true
        return name.contains(normalized, ignoreCase = true) ||
            code.contains(normalized, ignoreCase = true) ||
            marketCode.contains(normalized, ignoreCase = true)
    }
}

data class MarketUiState(
    val assetType: MarketAssetType = MarketAssetType.Stock,
    val marketFilter: MarketFilter = MarketFilter("全部市场", ""),
    val keyword: String = "",
    val sortOrder: String = "desc",
    val updatedAt: String = "--:--",
    val quotes: List<MarketQuote> = emptyList(),
) {
    val visibleQuotes: List<MarketQuote>
        get() = quotes.filter { it.matches(keyword) }

    val upCount: Int
        get() = quotes.count { it.changePercent > 0.0 }

    val downCount: Int
        get() = quotes.count { it.changePercent < 0.0 }

    val flatCount: Int
        get() = (quotes.size - upCount - downCount).coerceAtLeast(0)

    val hasAnyData: Boolean
        get() = quotes.isNotEmpty()
}

class FinanceRepository(
    private val apiClient: ApiClient,
) {
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

    fun login(username: String, password: String, roleCode: String, callback: (ApiResult, SessionState) -> Unit) {
        if (username.isBlank() || password.isBlank()) {
            callback(ApiResult(false, 0, "", "请输入用户名和密码。"), SessionState())
            return
        }
        val payload = JSONObject()
            .put("username", username.trim())
            .put("password", password)
            .put("roleCode", roleCode)

        apiClient.postJson(ApiConfig.LOGIN_PATH, payload) { result ->
            if (!result.success || !isApiSuccess(result.body)) {
                callback(result.copy(success = false, message = apiMessage(result.body, "登录失败，请检查账号、密码和角色。")), SessionState())
                return@postJson
            }
            val token = JSONObject(result.body).optJSONObject("data")?.optString("accessToken", "").orEmpty()
            if (token.isBlank()) {
                callback(ApiResult(false, result.statusCode, result.body, "登录成功但未返回访问令牌。"), SessionState())
                return@postJson
            }
            apiClient.setAccessToken(token)
            refreshUserInfo(token, username.trim(), callback)
        }
    }

    fun refreshUserInfo(token: String, fallbackUsername: String, callback: (ApiResult, SessionState) -> Unit) {
        apiClient.get(ApiConfig.USER_INFO_PATH) { result ->
            if (!result.success || !isApiSuccess(result.body)) {
                callback(result.copy(success = false, message = apiMessage(result.body, result.message)), SessionState(true, token, fallbackUsername))
                return@get
            }
            val data = JSONObject(result.body).optJSONObject("data")
            val state = SessionState(
                authenticated = true,
                accessToken = data?.optString("token", token).orEmpty().ifBlank { token },
                username = data?.optString("username", fallbackUsername).orEmpty().ifBlank { fallbackUsername },
                realName = data?.optString("realName", "").orEmpty(),
                roles = data?.optJSONArray("roles").toStringList(),
            )
            apiClient.setAccessToken(state.accessToken)
            callback(ApiResult(true, result.statusCode, result.body, "后端用户信息已同步。"), state)
        }
    }

    fun loadWorkbench(callback: (ApiResult, WorkbenchSummary) -> Unit) {
        val pending = WorkbenchPending(callback)
        apiClient.get(ApiConfig.WATCH_GROUPS_PATH) { result ->
            pending.consume(result, "观察池同步失败。") { body -> readWatchGroups(body, pending) }
        }
        apiClient.get(ApiConfig.STOCK_ALERTS_PATH) { result ->
            pending.consume(result, "布控提醒同步失败。") { body -> readAlerts(body, pending) }
        }
        apiClient.get(ApiConfig.REPORT_TARGETS_PATH) { result ->
            pending.consume(result, "报告动态同步失败。") { body -> readReports(body, pending) }
        }
    }

    fun loadMarketQuotes(
        assetType: MarketAssetType,
        marketCode: String,
        sortOrder: String,
        callback: (ApiResult, MarketUiState) -> Unit,
    ) {
        apiClient.get(marketQuotePath(assetType, marketCode, sortOrder)) { result ->
            if (!result.success) {
                callback(result, MarketUiState(assetType = assetType, marketFilter = marketFilterOf(marketCode), sortOrder = sortOrder))
                return@get
            }
            runCatching {
                val quotes = quoteArray(result.body).toMarketQuotes(assetType)
                callback(
                    result.copy(message = "行情已同步：${assetType.label} ${quotes.size} 条。"),
                    MarketUiState(
                        assetType = assetType,
                        marketFilter = marketFilterOf(marketCode),
                        sortOrder = normalizeSortOrder(sortOrder),
                        updatedAt = LocalDateTime.now().format(timeFormat),
                        quotes = quotes,
                    ),
                )
            }.onFailure {
                callback(
                    ApiResult(false, result.statusCode, result.body, "行情解析失败：${it.message ?: it.javaClass.simpleName}"),
                    MarketUiState(assetType = assetType, marketFilter = marketFilterOf(marketCode), sortOrder = sortOrder),
                )
            }
        }
    }

    private fun readWatchGroups(body: String, pending: WorkbenchPending) {
        val groups = dataArray(body)
        for (i in 0 until groups.length()) {
            val group = groups.optJSONObject(i) ?: continue
            val items = group.optJSONArray("items") ?: continue
            pending.watchItemCount += items.length()
            for (j in 0 until items.length()) {
                val item = items.optJSONObject(j) ?: continue
                when (item.optString("targetType", "")) {
                    "STOCK" -> pending.stockItemCount++
                    "INDEX" -> pending.indexItemCount++
                    "BOND" -> pending.bondItemCount++
                }
                val change = item.optDouble("changePercent", 0.0)
                if (change > 0) pending.watchUpCount++ else if (change < 0) pending.watchDownCount++
                pending.movements += WorkbenchMovement(
                    type = item.optString("targetType", ""),
                    code = item.optString("targetCode", ""),
                    name = item.optString("targetName", "未命名标的"),
                    latestPrice = item.optDouble("latestPrice", 0.0),
                    changePercent = change,
                )
            }
        }
    }

    private fun readAlerts(body: String, pending: WorkbenchPending) {
        val alerts = dataArray(body)
        pending.alertCount = alerts.length()
        for (i in 0 until alerts.length()) {
            val alert = alerts.optJSONObject(i) ?: continue
            if (alert.optBoolean("enabled", false)) {
                pending.enabledAlertCount++
            }
            if (alert.optBoolean("outOfThreshold", false)) {
                pending.outAlertCount++
            } else if (isNearThreshold(alert)) {
                pending.nearAlertCount++
            }
        }
    }

    private fun readReports(body: String, pending: WorkbenchPending) {
        val root = JSONObject(body.ifBlank { "{}" })
        val data = root.optJSONObject("data")
        val records = data?.optJSONArray("records")
            ?: root.optJSONArray("records")
            ?: root.optJSONArray("data")
            ?: JSONArray()
        for (i in 0 until records.length()) {
            val item = records.optJSONObject(i) ?: continue
            val status = item.optString("latestStatus", "")
            if (status == "failed") pending.reportFailedCount++ else if (status.isGeneratingStatus()) pending.reportGeneratingCount++
            if (pending.reports.size < 4) {
                pending.reports += WorkbenchReportLine(
                    title = reportName(item),
                    engine = reportEngine(item),
                    status = statusLabel(status),
                    detail = reportPreview(item, status),
                    eta = reportTime(item).ifBlank { "预计稍后完成" },
                )
            }
        }
    }

    private fun dataArray(body: String): JSONArray {
        val root = JSONObject(body.ifBlank { "{}" })
        return root.optJSONArray("data") ?: JSONArray()
    }

    private fun quoteArray(body: String): JSONArray {
        val trimmed = body.trim()
        if (trimmed.startsWith("[")) return JSONArray(trimmed)
        val root = JSONObject(trimmed.ifBlank { "{}" })
        return root.optJSONArray("data")
            ?: root.optJSONArray("records")
            ?: root.optJSONObject("data")?.optJSONArray("records")
            ?: JSONArray()
    }

    private fun isNearThreshold(alert: JSONObject): Boolean {
        if (!alert.optBoolean("enabled", false)) return false
        val threshold = alert.optDouble("thresholdPercent", 0.0)
        if (threshold <= 0.0) return false
        val distance = abs(abs(alert.optDouble("changePercent", 0.0)) - threshold)
        return distance <= threshold * 0.2
    }

    private fun reportName(item: JSONObject): String {
        return item.optString("targetName", "").ifBlank {
            item.optString("targetCode", "").ifBlank { "未命名报告标的" }
        }
    }

    private fun reportTime(item: JSONObject): String {
        val time = item.optString("latestGeneratedAt", "").ifBlank { item.optString("latestCreatedAt", "") }
        return time.replace('T', ' ').take(16)
    }

    private fun reportEngine(item: JSONObject): String {
        val model = item.optString("latestModel", "").ifBlank { "研究模型" }
        val generationType = item.optString("latestGenerationType", "")
        return if (generationType.isBlank()) model else "$model · $generationType"
    }

    private fun reportPreview(item: JSONObject, status: String): String {
        val preview = item.optString("latestReportPreview", "")
        if (preview.isNotBlank()) {
            return preview
        }
        return when {
            status.isGeneratingStatus() -> "正在聚合市场、知识库材料和历史报告，形成可复核的研究摘要。"
            status == "failed" -> "报告生成失败，进入研究页查看任务状态并重新生成。"
            status == "success" -> "报告已生成，可进入研究页查看完整正文、引用证据和风险判断。"
            else -> "暂无报告摘要，进入研究页查看历史版本。"
        }
    }

    private fun statusLabel(status: String): String = when {
        status == "success" -> "已完成"
        status == "failed" -> "失败"
        status.isGeneratingStatus() -> "生成中"
        else -> "暂无状态"
    }

    private inner class WorkbenchPending(
        private val callback: (ApiResult, WorkbenchSummary) -> Unit,
    ) {
        var watchItemCount = 0
        var stockItemCount = 0
        var indexItemCount = 0
        var bondItemCount = 0
        var watchUpCount = 0
        var watchDownCount = 0
        var alertCount = 0
        var enabledAlertCount = 0
        var outAlertCount = 0
        var nearAlertCount = 0
        var reportGeneratingCount = 0
        var reportFailedCount = 0
        var movements = listOf<WorkbenchMovement>()
        var reports = listOf<WorkbenchReportLine>()
        private var remaining = 3
        private var failure: ApiResult? = null

        fun consume(result: ApiResult, fallback: String, reader: (String) -> Unit) {
            if (result.success && (isApiSuccess(result.body) || result.body.contains("\"records\""))) {
                runCatching { reader(result.body) }.onFailure {
                    failure = ApiResult(false, result.statusCode, result.body, "数据解析失败：${it.message ?: it.javaClass.simpleName}")
                }
            } else if (failure == null) {
                failure = result.copy(success = false, message = apiMessage(result.body, result.message.ifBlank { fallback }))
            }
            done()
        }

        private fun done() {
            remaining--
            if (remaining > 0) return
            val focusCount = outAlertCount + nearAlertCount + reportGeneratingCount + reportFailedCount
            val summary = WorkbenchSummary(
                focusCount = focusCount,
                watchItemCount = watchItemCount,
                stockItemCount = stockItemCount,
                indexItemCount = indexItemCount,
                bondItemCount = bondItemCount,
                watchUpCount = watchUpCount,
                watchDownCount = watchDownCount,
                alertCount = alertCount,
                enabledAlertCount = enabledAlertCount,
                outAlertCount = outAlertCount,
                nearAlertCount = nearAlertCount,
                reportGeneratingCount = reportGeneratingCount,
                reportFailedCount = reportFailedCount,
                updatedAt = LocalDateTime.now().format(timeFormat),
                primaryAction = primaryAction(watchItemCount, outAlertCount, nearAlertCount),
                reportAction = reportAction(reportGeneratingCount, reportFailedCount),
                marketAction = marketAction(watchUpCount, watchDownCount),
                movements = movements.sortedByDescending { abs(it.changePercent) }.take(4),
                reports = reports,
            )
            callback(failure ?: ApiResult(true, 200, "", "工作台后端数据已同步。"), summary)
        }
    }
}

fun marketFilters(assetType: MarketAssetType): List<MarketFilter> = when (assetType) {
    MarketAssetType.Stock -> listOf(
        MarketFilter("全部市场", ""),
        MarketFilter("沪市", "SH_MAIN"),
        MarketFilter("深市", "SZ_MAIN"),
        MarketFilter("科创板", "STAR"),
        MarketFilter("创业板", "CHINEXT"),
    )

    MarketAssetType.Index,
    MarketAssetType.Bond -> listOf(
        MarketFilter("全部市场", ""),
        MarketFilter("沪市", "SH_MAIN"),
        MarketFilter("深市", "SZ_MAIN"),
    )
}

private fun primaryAction(watchItemCount: Int, outAlertCount: Int, nearAlertCount: Int): String = when {
    outAlertCount > 0 -> "有 $outAlertCount 条提醒越界，优先检查布控阈值。"
    nearAlertCount > 0 -> "有 $nearAlertCount 条提醒接近阈值，盘后复核价格波动。"
    watchItemCount > 0 -> "观察池 $watchItemCount 个标的已同步，检查涨跌分布。"
    else -> "后端暂无高优先级动作，继续查看观察池和提醒。"
}

private fun reportAction(generating: Int, failed: Int): String = when {
    generating > 0 -> "有 $generating 份报告生成中，等待轮询结果。"
    failed > 0 -> "有 $failed 份报告失败，进入报告研究处理。"
    else -> "报告目标暂无更新，进入报告研究查看历史版本。"
}

private fun marketAction(up: Int, down: Int): String = if (up + down > 0) {
    "上涨 $up、下跌 $down，复核观察池和布控阈值。"
} else {
    "行情异动待刷新，优先处理布控和报告。"
}

private fun String.isGeneratingStatus(): Boolean = this in setOf(
    "pending",
    "processing_current_scenes",
    "current_scenes_ready",
    "retrieving_knowledge",
    "generating_report",
)

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return List(length()) { index -> optString(index, "") }.filter { it.isNotBlank() }
}

private fun JSONArray.toMarketQuotes(assetType: MarketAssetType): List<MarketQuote> =
    List(length()) { index -> optJSONObject(index) }
        .filterNotNull()
        .map { quote ->
            MarketQuote(
                assetType = assetType,
                code = quote.optString(assetType.codeKey, ""),
                name = quote.optString(assetType.nameKey, "未命名标的"),
                marketCode = quote.optString("marketCode", ""),
                latestPrice = quote.optDouble("latestPrice", 0.0),
                changeAmount = quote.optDouble("changeAmount", 0.0),
                changePercent = quote.optDouble("changePercent", 0.0),
                turnoverAmount = quote.optDouble("turnoverAmount", 0.0),
                amplitude = quote.optDouble("amplitude", 0.0),
                syncedAt = quote.optString("syncedAt", ""),
                conversionPremiumRate = quote.optionalDouble("conversionPremiumRate"),
            )
        }

private fun JSONObject.optionalDouble(key: String): Double? =
    if (isNull(key) || !has(key)) null else optDouble(key, 0.0)

private fun marketQuotePath(assetType: MarketAssetType, marketCode: String, sortOrder: String): String {
    val params = mutableListOf(
        "limit" to "100",
        "sortField" to assetType.defaultSortField,
        "sortOrder" to normalizeSortOrder(sortOrder),
    )
    if (marketCode.isNotBlank()) {
        params += "marketCode" to marketCode
    }
    return assetType.apiPath + "?" + params.joinToString("&") { (key, value) ->
        "${key.encodeQuery()}=${value.encodeQuery()}"
    }
}

private fun marketFilterOf(marketCode: String): MarketFilter =
    MarketAssetType.entries
        .asSequence()
        .flatMap { marketFilters(it).asSequence() }
        .firstOrNull { it.marketCode == marketCode }
        ?: MarketFilter("全部市场", "")

private fun String.encodeQuery(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())

fun normalizeSortOrder(sortOrder: String): String =
    if (sortOrder.equals("asc", ignoreCase = true)) "asc" else "desc"
