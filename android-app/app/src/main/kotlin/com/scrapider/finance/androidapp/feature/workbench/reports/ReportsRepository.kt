package com.scrapider.finance.androidapp.feature.workbench.reports

import com.scrapider.finance.androidapp.core.network.ApiConfig
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.network.NetworkFailure
import com.scrapider.finance.androidapp.core.network.NetworkResult
import com.scrapider.finance.androidapp.core.network.toJsonArrayPayload
import com.scrapider.finance.androidapp.core.network.toJsonPayload
import com.scrapider.finance.androidapp.feature.market.marketTargetTypeLabel
import com.scrapider.finance.androidapp.feature.market.systemTargetTypes
import com.scrapider.finance.androidapp.feature.workbench.ReportStatus
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

internal val reportTargetTypes = listOf(ReportChoice("", "全部")) + systemTargetTypes.map {
    ReportChoice(if (it == "BOND") "CONVERTIBLE_BOND" else it, it.marketTargetTypeLabel())
}

/** 独立承接报告接口，不改变工作台首页的数据聚合和预览契约。 */
internal class ReportsRepository(private val apiClient: FinanceApiClient) {
    private var profilePayloads: Map<Long, String> = emptyMap()

    suspend fun targets(query: String, targetType: String, page: Int? = null, pageSize: Int? = null): NetworkResult<ReportTargetPage> {
        val keyword = query.trim()
        val params = linkedMapOf<String, String>()
        if (keyword.isNotEmpty()) {
            // 当前股票、指数和可转债代码均为数字；名称查询与代码查询使用各自的服务端条件。
            params[if (keyword.all(Char::isDigit)) "targetCode" else "targetName"] = keyword
        }
        if (targetType.isNotBlank()) params["targetType"] = targetType
        page?.let { params["pageNum"] = it.toString() }
        pageSize?.let { params["pageSize"] = it.toString() }
        return apiClient.get(ApiConfig.REPORT_TARGETS_PATH.withQuery(params)).toJsonPayload().mapObject { root ->
            val records = root.getJSONArray("records")
            val currentPage = root.getInt("pageNum")
            val size = root.getInt("pageSize")
            val total = root.getLong("total")
            require(currentPage > 0 && size > 0)
            ReportTargetPage(
                items = records.objects().map(::target),
                nextPage = if (currentPage.toLong() * size < total) currentPage + 1 else null,
                pageSize = size,
            )
        }
    }

    suspend fun history(target: ReportTarget): NetworkResult<List<ReportRecord>> =
        apiClient.get("$TASKS/reports".withQuery(mapOf("targetType" to target.targetType, "targetCode" to target.targetCode)))
            .toJsonArrayPayload().mapArray { array ->
                array.objects().map { item ->
                    ReportRecord(
                        reportId = item.getLong("reportId"), taskNo = item.text("taskNo"),
                        reportTypeLabel = reportTypeLabel(item.text("reportType")),
                        timeLabel = timeLabel(item.text("generatedAt").ifBlank { item.text("createdAt") }),
                        versionLabel = versionLabel(item), status = reportStatus(item.text("status")),
                    )
                }
            }

    suspend fun detail(reportId: Long): NetworkResult<ReportDocument> =
        apiClient.get("$TASKS/reports/$reportId").toJsonPayload().mapObject { item ->
            ReportDocument(
                reportId = item.optionalLong("reportId"), taskNo = item.text("taskNo"),
                targetName = item.text("targetName").ifBlank { item.text("targetCode") }.ifBlank { "未命名标的" },
                targetCode = item.text("targetCode"), targetTypeLabel = targetTypeLabel(item.text("targetType")),
                reportTypeLabel = reportTypeLabel(item.text("reportType")),
                timeLabel = timeLabel(item.text("generatedAt").ifBlank { item.text("createdAt") }),
                versionLabel = versionLabel(item), status = reportStatus(item.text("status")),
                blocks = reportBlocks(item.text("reportText")),
            )
        }

    suspend fun task(context: ReportDocument): NetworkResult<ReportDocument> =
        apiClient.get("$TASKS/${context.taskNo.encoded()}/report").toJsonPayload().mapObject { item ->
            context.copy(
                reportId = item.optionalLong("reportId"),
                status = reportStatus(item.text("status")),
                timeLabel = timeLabel(item.text("generatedAt")),
                versionLabel = versionLabel(item),
                blocks = reportBlocks(item.text("reportText")),
            )
        }

    suspend fun metadata(): NetworkResult<ReportMetadata> = coroutineScope {
        val profilesRequest = async { apiClient.get(PROFILES).toJsonArrayPayload() }
        val typesRequest = async { apiClient.get("$PROFILES/report-types").toJsonArrayPayload() }
        val profiles = profilesRequest.await()
        val types = typesRequest.await()
        if (profiles is NetworkResult.Failure) return@coroutineScope profiles
        if (types is NetworkResult.Failure) return@coroutineScope types
        profiles as NetworkResult.Success
        types as NetworkResult.Success
        profiles.mapArray { array ->
            val payloads = linkedMapOf<Long, String>()
            val options = array.objects().filter { it.optBoolean("enabled", true) }.map { item ->
                val config = JSONObject(item.getJSONObject("configJson").toString())
                val id = item.getLong("id")
                if (config.text("configProfile").isBlank()) config.put("configProfile", item.text("configProfile"))
                val targetType = config.text("targetType").ifBlank { item.text("targetType") }
                val reportType = config.text("reportType").ifBlank { item.text("reportType") }
                payloads[id] = config.toString()
                ReportProfileOption(
                    id = id, name = item.text("name"), group = item.text("configGroup"),
                    targetType = targetType, reportType = reportType,
                    recommended = item.text("configProfile") == "system_recommended",
                )
            }
            val typeOptions = types.data.objects().map { ReportChoice(it.getString("code"), it.getString("label")) }
            profilePayloads = payloads
            ReportMetadata(options, typeOptions)
        }
    }

    suspend fun searchTargets(targetType: String, query: String): NetworkResult<List<ReportTargetOption>> =
        apiClient.get("$BASE/targets/search".withQuery(mapOf("targetType" to targetType, "keyword" to query.trim())))
            .toJsonArrayPayload().mapArray { array ->
                array.objects().map {
                    ReportTargetOption(it.getString("targetType"), it.getString("targetCode"), it.getString("targetName"), targetTypeLabel(it.text("targetType")))
                }
            }

    suspend fun submit(profileId: Long, reportType: String, target: ReportTargetOption): NetworkResult<ReportSubmission> {
        val template = profilePayloads[profileId] ?: return NetworkResult.Failure(NetworkFailure.InvalidResponse)
        // 使用服务端配置原值；不在 Android 重新定义召回数量、K 线窗口或策略阈值。
        val profile = JSONObject(template)
        val payload = JSONObject()
        listOf("configProfile", "totalChunks", "dailyKlineLimit", "weeklyKlineLimit", "monthlyKlineLimit", "userOverrides").forEach { key ->
            if (profile.has(key) && !profile.isNull(key)) payload.put(key, profile.get(key))
        }
        payload.put("targetType", target.targetType).put("targetCode", target.targetCode)
            .put("targetName", target.targetName).put("reportType", reportType)
        // 与已有 Web 表单一致，切换标的类型时同步资产类别，其余覆盖参数保持原值。
        val overrides = profile.optJSONObject("userOverrides")?.let { JSONObject(it.toString()) } ?: JSONObject()
        overrides.put("asset_type", when (target.targetType) {
            "INDEX" -> "index"
            "BOND", "CONVERTIBLE_BOND" -> "convertible_bond"
            else -> "stock"
        })
        payload.put("userOverrides", overrides)
        return apiClient.postJson(TASKS, payload).toJsonPayload().mapObject {
            ReportSubmission(it.getString("taskNo"), reportStatus(it.text("status")))
        }
    }

    suspend fun regenerate(taskNo: String): NetworkResult<Unit> =
        apiClient.postJson("$TASKS/${taskNo.encoded()}/report/regenerate", JSONObject())
            .copy(body = "{}").toJsonPayload().mapObject { }

    private fun target(item: JSONObject) = ReportTarget(
        targetType = item.text("targetType"), targetCode = item.text("targetCode"),
        targetName = item.text("targetName").ifBlank { item.text("targetCode") }.ifBlank { "未命名标的" },
        targetTypeLabel = targetTypeLabel(item.text("targetType")), reportId = item.optionalLong("latestReportId"),
        taskNo = item.text("latestTaskNo"), reportTypeLabel = reportTypeLabel(item.text("latestReportType")),
        timeLabel = timeLabel(item.text("latestGeneratedAt").ifBlank { item.text("latestCreatedAt") }),
        status = reportStatus(item.text("latestStatus")), reportCount = item.optLong("reportCount"),
    )

    private companion object {
        const val BASE = "/api/ai/scene-analysis"
        const val TASKS = "$BASE/tasks"
        const val PROFILES = "$BASE/config-profiles"
    }
}

internal fun ReportTarget.asDocument() = ReportDocument(
    reportId, taskNo, targetName, targetCode, targetTypeLabel, reportTypeLabel, timeLabel, "", status, emptyList(),
)

private fun reportStatus(value: String): ReportStatus = when (value.lowercase(Locale.ROOT)) {
    "success" -> ReportStatus.Generated
    "failed" -> ReportStatus.Failed
    "pending", "queued" -> ReportStatus.Pending
    "processing_current_scenes", "current_scenes_ready", "retrieving_knowledge", "generating_report", "running" -> ReportStatus.Generating
    else -> ReportStatus.Unknown
}

private fun reportTypeLabel(value: String): String = when (value.lowercase(Locale.ROOT)) {
    "quick_analysis" -> "快速分析"
    "risk_check" -> "风险检查"
    "valuation_report" -> "估值报告"
    else -> "未标注类型"
}

private fun targetTypeLabel(value: String): String =
    reportTargetTypes.find { it.value == value || (value == "BOND" && it.value == "CONVERTIBLE_BOND") }?.label ?: "标的"

private fun timeLabel(value: String): String = value.replace('T', ' ').take(16).ifBlank { "暂无时间" }
private fun versionLabel(item: JSONObject): String = item.optionalLong("versionNo")?.let { "第 $it 版" }.orEmpty()
private fun JSONObject.text(key: String): String = optString(key, "").trim().takeUnless { it == "null" }.orEmpty()
private fun JSONObject.optionalLong(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8")
private fun String.withQuery(params: Map<String, String>): String = if (params.isEmpty()) this else
    this + params.entries.joinToString(prefix = "?", separator = "&") { "${it.key}=${it.value.encoded()}" }

private fun <T> NetworkResult<JSONObject>.mapObject(map: (JSONObject) -> T): NetworkResult<T> = when (this) {
    is NetworkResult.Failure -> this
    is NetworkResult.Success -> runCatching { NetworkResult.Success(map(data)) }
        .getOrElse { NetworkResult.Failure(NetworkFailure.InvalidResponse) }
}

private fun <T> NetworkResult<JSONArray>.mapArray(map: (JSONArray) -> T): NetworkResult<T> = when (this) {
    is NetworkResult.Failure -> this
    is NetworkResult.Success -> runCatching { NetworkResult.Success(map(data)) }
        .getOrElse { NetworkResult.Failure(NetworkFailure.InvalidResponse) }
}

/** 后端当前输出标题、段落和嵌套列表；保留内容和顺序，只转换排版标记与引用的产品文案。 */
private fun reportBlocks(markdown: String): List<ReportTextBlock> = markdown.lines().mapNotNull { line ->
    val text = line.trim()
    if (text.isEmpty()) return@mapNotNull null
    val heading = Regex("^(#{1,6})\\s+(.+)$").matchEntire(text)
    val isBullet = text.startsWith("- ") || text.startsWith("* ")
    val content = heading?.groupValues?.get(2) ?: if (isBullet) text.drop(2) else text
    ReportTextBlock(
        text = content.replace(Regex("chunkId\\s*:\\s*(\\d+)"), "片段 $1"),
        headingLevel = heading?.groupValues?.get(1)?.length ?: 0,
        isBullet = isBullet, isNested = isBullet && line.startsWith("  "),
    )
}
