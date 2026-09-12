package com.scrapider.finance.androidapp.feature.workbench.knowledge

import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.network.NetworkFailure
import com.scrapider.finance.androidapp.core.network.NetworkResult
import com.scrapider.finance.androidapp.core.network.toJsonArrayPayload
import com.scrapider.finance.androidapp.core.network.toJsonPayload
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportChoice
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportTargetOption
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

internal class KnowledgeRepository(
    private val apiClient: FinanceApiClient,
) {
    private val reportRepository = ReportsRepository(apiClient)

    suspend fun metadata(): NetworkResult<KnowledgeMetadata> = coroutineScope {
        val profilesRequest = async { apiClient.get(PROFILES_PATH).toJsonArrayPayload() }
        val reportTypesRequest = async { apiClient.get(REPORT_TYPES_PATH).toJsonArrayPayload() }
        val profilesResult = profilesRequest.await()
        val reportTypesResult = reportTypesRequest.await()
        if (profilesResult is NetworkResult.Failure) return@coroutineScope profilesResult
        if (reportTypesResult is NetworkResult.Failure) return@coroutineScope reportTypesResult

        val profiles = (profilesResult as NetworkResult.Success).data
        val reportTypes = (reportTypesResult as NetworkResult.Success).data
        runCatching {
            val reportTypeChoices = reportTypes.objects().map { item ->
                ReportChoice(item.text("code"), item.text("label"))
            }
            val labels = reportTypeChoices.associate { it.value to it.label }
            KnowledgeMetadata(
                profiles = profiles.objects()
                    .filter { it.optBoolean("enabled", true) }
                    .map { item -> profile(item, labels) },
                reportTypes = reportTypeChoices,
            )
        }.fold(
            onSuccess = { NetworkResult.Success(it) },
            onFailure = { NetworkResult.Failure(NetworkFailure.InvalidResponse) },
        )
    }

    /** 标的搜索沿用报告模块已核对的路径、类型映射和服务端搜索参数。 */
    suspend fun searchTargets(
        targetType: String,
        query: String,
    ): NetworkResult<List<ReportTargetOption>> = reportRepository.searchTargets(targetType, query)

    suspend fun submit(
        mode: KnowledgeSearchMode,
        profile: KnowledgeProfileOption,
        targetType: String,
        target: ReportTargetOption?,
        queryText: String,
    ): NetworkResult<KnowledgeSubmitResult> {
        val totalChunks = profile.totalChunks
            ?: return NetworkResult.Failure(NetworkFailure.InvalidResponse)
        val payload = JSONObject()
            .put("searchMode", mode.code)
            .put("totalChunks", totalChunks)

        when (mode) {
            KnowledgeSearchMode.Target -> {
                if (target == null || targetType.isBlank()) {
                    return NetworkResult.Failure(NetworkFailure.InvalidResponse)
                }
                payload.put("targetType", targetType)
                    .put("targetCode", target.targetCode)
                    .put("targetName", target.targetName)
                profile.configProfile.takeIf { it.isNotBlank() }?.let { payload.put("configProfile", it) }
                profile.reportType.takeIf { it.isNotBlank() }?.let { payload.put("reportType", it) }
                profile.dailyKlineLimit?.let { payload.put("dailyKlineLimit", it) }
                profile.weeklyKlineLimit?.let { payload.put("weeklyKlineLimit", it) }
                profile.monthlyKlineLimit?.let { payload.put("monthlyKlineLimit", it) }
                profile.userOverridesJson
                    .takeIf { it.isNotBlank() }
                    ?.let { raw ->
                        runCatching {
                            payload.put(
                                "userOverrides",
                                JSONObject(raw).put("asset_type", assetTypeForTargetType(targetType)),
                            )
                        }
                    }
            }

            KnowledgeSearchMode.NaturalLanguage -> {
                val query = queryText.trim()
                if (query.isBlank()) {
                    return NetworkResult.Failure(NetworkFailure.InvalidResponse)
                }
                payload.put("queryText", query)
            }
        }

        return apiClient.postJson(TASKS_PATH, payload).toJsonPayload().mapObject { root ->
            KnowledgeSubmitResult(
                taskNo = root.text("taskNo"),
                searchMode = KnowledgeSearchMode.fromCode(root.text("searchMode")),
                targetType = root.text("targetType"),
                targetCode = root.text("targetCode"),
                targetName = root.text("targetName"),
                queryText = root.text("queryText"),
                rewrittenQuery = root.text("rewrittenQuery"),
                status = KnowledgeTaskStatus.fromCode(root.text("status")),
            )
        }
    }

    suspend fun task(taskNo: String): NetworkResult<KnowledgeTask> {
        if (taskNo.isBlank()) {
            return NetworkResult.Failure(NetworkFailure.InvalidResponse)
        }
        return apiClient.get("$TASKS_PATH/${taskNo.encoded()}").toJsonPayload().mapObject { root -> task(root) }
    }

    private fun profile(item: JSONObject, reportTypeLabels: Map<String, String>): KnowledgeProfileOption {
        val config = item.optJSONObject("configJson") ?: JSONObject()
        val reportType = config.text("reportType").ifBlank { item.text("reportType") }
        val targetType = config.text("targetType").ifBlank { item.text("targetType") }
        return KnowledgeProfileOption(
            id = item.getLong("id"),
            name = item.text("name"),
            group = item.text("configGroup"),
            configProfile = config.text("configProfile").ifBlank { item.text("configProfile") },
            targetType = targetType,
            reportType = reportType,
            reportTypeLabel = reportTypeLabels[reportType].orEmpty(),
            totalChunks = config.positiveInt("totalChunks"),
            dailyKlineLimit = config.positiveInt("dailyKlineLimit"),
            weeklyKlineLimit = config.positiveInt("weeklyKlineLimit"),
            monthlyKlineLimit = config.positiveInt("monthlyKlineLimit"),
            userOverridesJson = config.optJSONObject("userOverrides")?.toString().orEmpty(),
            recommended = item.optBoolean("systemDefault", false) || item.text("configProfile") == "system_recommended",
        )
    }

    private fun task(root: JSONObject): KnowledgeTask {
        val taskNo = root.text("taskNo")
        val chunks = root.optJSONArray("chunks")?.objects()?.map { item ->
            chunk(item, taskNo)
        } ?: flattenContext(root.optJSONObject("knowledgeContext"), taskNo)
        return KnowledgeTask(
            taskNo = taskNo,
            searchMode = KnowledgeSearchMode.fromCode(root.text("searchMode")),
            targetType = root.text("targetType"),
            targetCode = root.text("targetCode"),
            targetName = root.text("targetName"),
            queryText = root.text("queryText"),
            rewrittenQuery = root.text("rewrittenQuery"),
            status = KnowledgeTaskStatus.fromCode(root.text("status")),
            errorMessage = root.text("errorMessage"),
            submittedAt = root.text("submittedAt"),
            finishedAt = root.text("finishedAt"),
            chunks = chunks.distinctBy(KnowledgeChunk::key),
        )
    }

    private fun chunk(item: JSONObject, taskNo: String): KnowledgeChunk = KnowledgeChunk(
        chunkId = item.optionalLong("chunkId"),
        taskNo = item.text("taskNo").ifBlank { taskNo },
        chunkIndex = item.optionalInt("chunkIndex"),
        scene = item.text("scene"),
        filename = item.text("filename").ifBlank { "知识库材料" },
        text = item.text("text"),
        matchedTags = item.optJSONArray("matchedTags")?.strings().orEmpty(),
        semanticScore = item.optionalDouble("semanticScore"),
        tagMatchScore = item.optionalDouble("tagMatchScore"),
        crossSceneScore = item.optionalDouble("crossSceneScore"),
        finalScore = item.optionalDouble("finalScore"),
    )

    private fun flattenContext(context: JSONObject?, taskNo: String): List<KnowledgeChunk> {
        if (context == null) return emptyList()
        val result = mutableListOf<KnowledgeChunk>()
        context.keys().forEach { scene ->
            context.optJSONArray(scene)?.objects()?.forEach { item ->
                result += chunk(item.put("scene", item.text("scene").ifBlank { scene }), taskNo)
            }
        }
        return result
    }

    private companion object {
        const val BASE_PATH = "/api/ai/knowledge-material/tasks"
        const val TASKS_PATH = BASE_PATH
        const val PROFILES_PATH = "/api/ai/scene-analysis/config-profiles"
        const val REPORT_TYPES_PATH = "$PROFILES_PATH/report-types"
    }

    private fun assetTypeForTargetType(targetType: String): String = when (targetType.uppercase(Locale.ROOT)) {
        "INDEX" -> "index"
        "CONVERTIBLE_BOND", "BOND" -> "convertible_bond"
        else -> "stock"
    }
}

private fun JSONObject.text(key: String): String = optString(key, "")
    .trim()
    .takeUnless { it == "null" }
    .orEmpty()

private fun JSONObject.positiveInt(key: String): Int? = when (val value = opt(key)) {
    null -> null
    is Number -> value.toInt().takeIf { it > 0 }
    is String -> value.trim().toIntOrNull()?.takeIf { it > 0 }
    else -> null
}

private fun JSONObject.optionalLong(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null

private fun JSONObject.optionalInt(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONObject.optionalDouble(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key) else null

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { optString(it, null)?.takeIf(String::isNotBlank) }

private fun <T> NetworkResult<JSONObject>.mapObject(map: (JSONObject) -> T): NetworkResult<T> = when (this) {
    is NetworkResult.Failure -> this
    is NetworkResult.Success -> runCatching { NetworkResult.Success(map(data)) }
        .getOrElse { NetworkResult.Failure(NetworkFailure.InvalidResponse) }
}

private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8")
