package com.scrapider.finance.androidapp.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

enum class AppFontScale(
    val storageValue: String,
    val label: String,
    val scale: Float,
) {
    Compact("compact", "紧凑", 0.92f),
    Standard("standard", "标准", 1.0f),
    Large("large", "放大", 1.14f);

    companion object {
        fun fromStorage(value: String): AppFontScale =
            entries.firstOrNull { it.storageValue == value } ?: Standard
    }
}

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

const val DEFAULT_ALERT_THRESHOLD_PERCENT = 5.0
const val ALERT_THRESHOLD_STEP_PERCENT = 0.5
const val ALERT_THRESHOLD_MIN_PERCENT = 0.01
const val ALERT_THRESHOLD_MAX_PERCENT = 100.0

enum class WatchTargetType(
    val apiValue: String,
    val label: String,
    val alertTargetType: String?,
) {
    Stock("STOCK", "股票", "STOCK"),
    Bond("BOND", "可转债", "BOND"),
    Index("INDEX", "指数", "INDEX"),
    Fund("FUND", "基金", null),
    Sector("SECTOR", "板块", null);

    val supportsAlert: Boolean
        get() = alertTargetType != null

    companion object {
        fun fromApi(value: String): WatchTargetType =
            entries.firstOrNull { it.apiValue == value } ?: Stock
    }
}

data class WatchItem(
    val id: String,
    val groupId: String,
    val targetType: WatchTargetType,
    val targetCode: String,
    val targetName: String,
    val secid: String = "",
    val remark: String = "",
    val buyPrice: Double? = null,
    val position: Double? = null,
    val latestPrice: Double? = null,
    val changePercent: Double? = null,
    val syncedAt: String = "",
) {
    fun matches(typeFilter: WatchTargetType?): Boolean =
        typeFilter == null || targetType == typeFilter
}

data class WatchGroup(
    val id: String,
    val name: String,
    val items: List<WatchItem> = emptyList(),
)

data class StockAlertConfig(
    val id: String,
    val targetType: WatchTargetType,
    val stockCode: String,
    val stockName: String,
    val thresholdPercent: Double,
    val enabled: Boolean,
    val outOfThreshold: Boolean,
    val latestPrice: Double? = null,
    val changePercent: Double? = null,
    val syncedAt: String = "",
)

data class AlertTargetOption(
    val targetType: WatchTargetType,
    val targetCode: String,
    val targetName: String,
    val marketCode: String = "",
    val exchangeCode: String = "",
) {
    fun matches(keyword: String): Boolean {
        val normalized = keyword.trim()
        if (normalized.isBlank()) return true
        return targetName.contains(normalized, ignoreCase = true) ||
            targetCode.contains(normalized, ignoreCase = true) ||
            marketCode.contains(normalized, ignoreCase = true)
    }
}

data class AddWatchTargetFormState(
    val targetType: WatchTargetType = WatchTargetType.Stock,
    val selectedGroupId: String = "",
    val targetKeyword: String = "",
    val selectedTargetCode: String = "",
    val selectedTargetName: String = "",
    val selectedSecid: String = "",
    val buyPrice: String = "",
    val position: String = "",
    val alertEnabled: Boolean = true,
    val alertThresholdPercent: Double = DEFAULT_ALERT_THRESHOLD_PERCENT,
    val remark: String = "",
) {
    val canEnableAlert: Boolean
        get() = targetType.supportsAlert

    val effectiveAlertEnabled: Boolean
        get() = alertEnabled && canEnableAlert
}

data class ObservationRiskUiState(
    val groups: List<WatchGroup> = emptyList(),
    val selectedGroupId: String = "",
    val selectedItemId: String = "",
    val typeFilter: WatchTargetType? = null,
    val alerts: List<StockAlertConfig> = emptyList(),
    val targetOptions: List<AlertTargetOption> = emptyList(),
    val updatedAt: String = "--:--",
    val showAddSheet: Boolean = false,
    val addForm: AddWatchTargetFormState = AddWatchTargetFormState(),
) {
    val selectedGroup: WatchGroup?
        get() = groups.firstOrNull { it.id == selectedGroupId } ?: groups.firstOrNull()

    val selectedItem: WatchItem?
        get() = groups.asSequence()
            .flatMap { it.items.asSequence() }
            .firstOrNull { it.id == selectedItemId }

    val visibleItems: List<WatchItem>
        get() = selectedGroup?.items.orEmpty().filter { it.matches(typeFilter) }

    val watchItemCount: Int
        get() = groups.sumOf { it.items.size }

    val enabledAlertCount: Int
        get() = alerts.count { it.enabled }

    val outAlertCount: Int
        get() = alerts.count { it.outOfThreshold }
}

const val DEFAULT_REPORT_PAGE_SIZE = 20
const val DEFAULT_REPORT_TOTAL_CHUNKS = 10
const val DEFAULT_REPORT_DAILY_KLINE_LIMIT = 90
const val DEFAULT_REPORT_WEEKLY_KLINE_LIMIT = 52
const val DEFAULT_REPORT_MONTHLY_KLINE_LIMIT = 60
const val MIN_REPORT_DAILY_KLINE_LIMIT = 60

enum class ReportTargetType(
    val apiValue: String,
    val label: String,
) {
    Stock("STOCK", "股票"),
    Index("INDEX", "指数"),
    ConvertibleBond("CONVERTIBLE_BOND", "可转债");

    companion object {
        fun fromApi(value: String): ReportTargetType =
            entries.firstOrNull { it.apiValue == value } ?: Stock
    }
}

enum class ReportStatusFilter(
    val apiValue: String?,
    val label: String,
) {
    All(null, "全部"),
    Generating("generating", "生成中"),
    Success("success", "成功"),
    Failed("failed", "失败");

    fun matches(status: String): Boolean = when (this) {
        All -> true
        Generating -> status.isGeneratingStatus()
        Success -> status == "success"
        Failed -> status == "failed"
    }
}

data class ReportTargetSummary(
    val targetType: ReportTargetType,
    val targetCode: String,
    val targetName: String,
    val latestTaskNo: String,
    val latestReportId: Long?,
    val latestStatus: String,
    val latestVersionNo: Int?,
    val latestReportType: String,
    val latestGenerationType: String,
    val latestModel: String,
    val latestReportPreview: String,
    val latestCreatedAt: String,
    val latestGeneratedAt: String,
    val reportCount: Int,
) {
    fun matches(keyword: String, statusFilter: ReportStatusFilter): Boolean {
        val normalized = keyword.trim()
        val keywordMatched = normalized.isBlank() ||
            targetName.contains(normalized, ignoreCase = true) ||
            targetCode.contains(normalized, ignoreCase = true)
        return keywordMatched && statusFilter.matches(latestStatus)
    }
}

data class ReportDetail(
    val reportId: Long,
    val taskNo: String,
    val targetType: ReportTargetType,
    val targetCode: String,
    val targetName: String,
    val reportType: String,
    val generationType: String,
    val versionNo: Int,
    val status: String,
    val model: String,
    val createdAt: String,
    val generatedAt: String,
    val errorMessage: String,
    val reportText: String,
)

data class ReportTypeOption(
    val code: String,
    val label: String,
)

data class ReportConfigProfileOption(
    val id: Long,
    val name: String,
    val configProfile: String,
    val configGroup: String,
    val reportType: String,
    val targetType: ReportTargetType?,
    val totalChunks: Int,
    val dailyKlineLimit: Int,
    val weeklyKlineLimit: Int,
    val monthlyKlineLimit: Int,
    val systemDefault: Boolean,
)

data class ReportTargetOption(
    val targetType: ReportTargetType,
    val targetCode: String,
    val targetName: String,
    val marketCode: String = "",
) {
    fun matches(keyword: String): Boolean {
        val normalized = keyword.trim()
        if (normalized.isBlank()) return true
        return targetName.contains(normalized, ignoreCase = true) ||
            targetCode.contains(normalized, ignoreCase = true) ||
            marketCode.contains(normalized, ignoreCase = true)
    }
}

data class CreateReportFormState(
    val targetType: ReportTargetType = ReportTargetType.Stock,
    val targetKeyword: String = "",
    val targetCode: String = "",
    val targetName: String = "",
    val reportType: String = "quick_analysis",
    val configProfile: String = "system_recommended",
    val configProfileId: Long? = null,
    val totalChunks: Int = DEFAULT_REPORT_TOTAL_CHUNKS,
    val dailyKlineLimit: Int = DEFAULT_REPORT_DAILY_KLINE_LIMIT,
    val weeklyKlineLimit: Int = DEFAULT_REPORT_WEEKLY_KLINE_LIMIT,
    val monthlyKlineLimit: Int = DEFAULT_REPORT_MONTHLY_KLINE_LIMIT,
) {
    val canSubmit: Boolean
        get() = targetCode.isNotBlank()
}

data class ReportResearchUiState(
    val targets: List<ReportTargetSummary> = emptyList(),
    val targetType: ReportTargetType = ReportTargetType.Stock,
    val statusFilter: ReportStatusFilter = ReportStatusFilter.All,
    val keyword: String = "",
    val updatedAt: String = "--:--",
    val selectedDetail: ReportDetail? = null,
    val showCreateSheet: Boolean = false,
    val createForm: CreateReportFormState = CreateReportFormState(),
    val targetOptions: List<ReportTargetOption> = emptyList(),
    val reportTypes: List<ReportTypeOption> = listOf(
        ReportTypeOption("quick_analysis", "快速分析"),
        ReportTypeOption("valuation_report", "估值报告"),
    ),
    val configProfiles: List<ReportConfigProfileOption> = emptyList(),
) {
    val visibleTargets: List<ReportTargetSummary>
        get() = targets.filter { it.matches(keyword, statusFilter) }

    val hasAnyData: Boolean
        get() = targets.isNotEmpty()
}

const val DEFAULT_KNOWLEDGE_MATERIAL_TOTAL_CHUNKS = DEFAULT_REPORT_TOTAL_CHUNKS

enum class KnowledgeMaterialSearchMode(
    val apiValue: String,
    val label: String,
) {
    Target("target", "按标的"),
    NaturalLanguage("natural_language", "自然语言");

    companion object {
        fun fromApi(value: String): KnowledgeMaterialSearchMode =
            entries.firstOrNull { it.apiValue == value } ?: Target
    }
}

data class KnowledgeMaterialFormState(
    val targetType: ReportTargetType = ReportTargetType.Stock,
    val targetKeyword: String = "",
    val targetCode: String = "",
    val targetName: String = "",
    val reportType: String = "quick_analysis",
    val configProfile: String = "system_recommended",
    val configProfileId: Long? = null,
    val totalChunks: Int = DEFAULT_KNOWLEDGE_MATERIAL_TOTAL_CHUNKS,
    val dailyKlineLimit: Int = DEFAULT_REPORT_DAILY_KLINE_LIMIT,
    val weeklyKlineLimit: Int = DEFAULT_REPORT_WEEKLY_KLINE_LIMIT,
    val monthlyKlineLimit: Int = DEFAULT_REPORT_MONTHLY_KLINE_LIMIT,
    val queryText: String = "",
) {
    val canSubmitTarget: Boolean
        get() = targetCode.isNotBlank()

    val canSubmitNaturalLanguage: Boolean
        get() = queryText.trim().isNotBlank()
}

data class KnowledgeMaterialChunk(
    val chunkId: Long,
    val taskNo: String,
    val chunkIndex: Int?,
    val scene: String,
    val filename: String,
    val text: String,
    val matchedTags: List<String>,
    val semanticScore: Double?,
    val tagMatchScore: Double?,
    val crossSceneScore: Double?,
    val finalScore: Double?,
) {
    fun matches(sceneFilter: String, tagFilter: String, sourceKeyword: String): Boolean {
        if (sceneFilter.isNotBlank() && scene != sceneFilter) return false
        if (tagFilter.isNotBlank() && !matchedTags.contains(tagFilter)) return false
        val normalized = sourceKeyword.trim()
        if (normalized.isBlank()) return true
        return filename.contains(normalized, ignoreCase = true)
    }
}

data class KnowledgeMaterialTask(
    val taskNo: String = "",
    val searchMode: KnowledgeMaterialSearchMode = KnowledgeMaterialSearchMode.Target,
    val targetType: ReportTargetType? = null,
    val targetCode: String = "",
    val targetName: String = "",
    val queryText: String = "",
    val rewrittenQuery: String = "",
    val status: String = "",
    val errorMessage: String = "",
    val submittedAt: String = "",
    val finishedAt: String = "",
    val chunks: List<KnowledgeMaterialChunk> = emptyList(),
) {
    val terminal: Boolean
        get() = status == "success" || status == "failed"

    val title: String
        get() = when (searchMode) {
            KnowledgeMaterialSearchMode.Target ->
                targetName.ifBlank { targetCode.ifBlank { "标的材料检索" } } +
                    targetCode.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()

            KnowledgeMaterialSearchMode.NaturalLanguage -> queryText.ifBlank { "自然语言召回" }
        }
}

enum class KnowledgeSection(
    val label: String,
) {
    Materials("材料"),
    OcrImport("OCR导入"),
    ManualImport("手动导入"),
}

data class OcrUploadFile(
    val name: String,
    val contentType: String,
    val sizeBytes: Long,
    val bytes: ByteArray,
)

data class OcrTask(
    val taskNo: String = "",
    val originalFilename: String = "",
    val fileType: String = "",
    val status: String = "",
    val currentStage: String = "",
    val progress: Int = 0,
    val pageCount: Int = 0,
    val segmentCount: Int = 0,
    val submittedAt: String = "",
    val updatedAt: String = "",
    val errorMessage: String = "",
) {
    val needsReview: Boolean
        get() = status == "manual_review_required"

    val terminal: Boolean
        get() = status == "finished" || status == "failed"
}

data class OcrReviewWarning(
    val type: String,
    val confidence: Double? = null,
)

data class OcrReviewParagraph(
    val paragraphNo: Int,
    val text: String,
    val sourcePages: List<Int>,
    val avgConfidence: Double,
    val warnings: List<OcrReviewWarning> = emptyList(),
)

data class OcrReviewDraftContent(
    val taskNo: String,
    val paragraphCount: Int,
    val paragraphs: List<OcrReviewParagraph>,
)

data class OcrReviewDetail(
    val taskNo: String,
    val status: String,
    val overallConfidence: Double,
    val paragraphCount: Int,
    val warningCount: Int,
    val draftContent: OcrReviewDraftContent,
)

data class OcrImportUiState(
    val selectedFiles: List<OcrUploadFile> = emptyList(),
    val tasks: List<OcrTask> = emptyList(),
    val selectedTaskNo: String = "",
    val selectedReview: OcrReviewDetail? = null,
    val updatedAt: String = "--:--",
) {
    val selectedTask: OcrTask?
        get() = tasks.firstOrNull { it.taskNo == selectedTaskNo } ?: tasks.firstOrNull()

    val runningCount: Int
        get() = tasks.count { it.status == "running" || it.status == "ready" }

    val finishedCount: Int
        get() = tasks.count { it.status == "finished" }

    val reviewCount: Int
        get() = tasks.count { it.needsReview }

    val failedCount: Int
        get() = tasks.count { it.status == "failed" }
}

data class ManualKnowledgeUiState(
    val taskNo: String = "",
    val title: String = "",
    val chunks: List<String> = listOf(""),
    val tasks: List<OcrTask> = emptyList(),
    val selectedTaskNo: String = "",
    val readonly: Boolean = false,
    val updatedAt: String = "--:--",
) {
    val selectedTask: OcrTask?
        get() = tasks.firstOrNull { it.taskNo == selectedTaskNo } ?: tasks.firstOrNull()

    val validChunks: List<String>
        get() = chunks.map { it.trim() }.filter { it.isNotBlank() }

    val validChunkCount: Int
        get() = validChunks.size

    val canEdit: Boolean
        get() = !readonly

    val canSubmit: Boolean
        get() = canEdit && validChunkCount > 0

    val draftCount: Int
        get() = tasks.count { it.needsReview }

    val runningCount: Int
        get() = tasks.count { it.status == "running" || it.status == "ready" }

    val finishedCount: Int
        get() = tasks.count { it.status == "finished" }
}

data class KnowledgeMaterialUiState(
    val section: KnowledgeSection = KnowledgeSection.Materials,
    val form: KnowledgeMaterialFormState = KnowledgeMaterialFormState(),
    val targetOptions: List<ReportTargetOption> = emptyList(),
    val reportTypes: List<ReportTypeOption> = listOf(
        ReportTypeOption("quick_analysis", "快速分析"),
        ReportTypeOption("valuation_report", "估值报告"),
    ),
    val configProfiles: List<ReportConfigProfileOption> = emptyList(),
    val activeTask: KnowledgeMaterialTask? = null,
    val sceneFilter: String = "",
    val tagFilter: String = "",
    val sourceKeyword: String = "",
    val ocr: OcrImportUiState = OcrImportUiState(),
    val manual: ManualKnowledgeUiState = ManualKnowledgeUiState(),
    val updatedAt: String = "--:--",
) {
    val chunks: List<KnowledgeMaterialChunk>
        get() = activeTask?.chunks.orEmpty()

    val sceneOptions: List<Pair<String, Int>>
        get() = chunks.groupingBy { it.scene }.eachCount().toList().sortedBy { it.first }

    val tagOptions: List<Pair<String, Int>>
        get() = chunks
            .filter { it.matches(sceneFilter, "", sourceKeyword) }
            .flatMap { it.matchedTags }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedBy { it.first }

    val visibleChunks: List<KnowledgeMaterialChunk>
        get() = chunks.filter { it.matches(sceneFilter, tagFilter, sourceKeyword) }

    val groupedVisibleChunks: List<Pair<String, List<KnowledgeMaterialChunk>>>
        get() = visibleChunks.groupBy { it.scene }.toList()

    val hasAnyData: Boolean
        get() = activeTask != null || chunks.isNotEmpty()
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

    fun changePassword(
        oldPassword: String,
        newPassword: String,
        confirmPassword: String,
        callback: (ApiResult) -> Unit,
    ) {
        val normalizedOldPassword = oldPassword.trim()
        val normalizedNewPassword = newPassword.trim()
        val normalizedConfirmPassword = confirmPassword.trim()
        if (normalizedOldPassword.isBlank() || normalizedNewPassword.isBlank() || normalizedConfirmPassword.isBlank()) {
            callback(ApiResult(false, 0, "", "请输入旧密码、新密码和确认密码。"))
            return
        }
        if (normalizedNewPassword != normalizedConfirmPassword) {
            callback(ApiResult(false, 0, "", "两次输入的新密码不一致。"))
            return
        }
        val payload = JSONObject()
            .put("oldPassword", normalizedOldPassword)
            .put("newPassword", normalizedNewPassword)
            .put("confirmPassword", normalizedConfirmPassword)
        apiClient.putJson(ApiConfig.USER_PASSWORD_PATH, payload) { result ->
            callback(
                if (result.success && isApiSuccess(result.body)) {
                    result.copy(message = "密码修改成功。")
                } else {
                    result.copy(success = false, message = apiMessage(result.body, result.message))
                },
            )
        }
    }

    fun addStockConfig(stockCode: String, stockName: String, callback: (ApiResult) -> Unit) {
        val normalizedCode = stockCode.trim()
        val normalizedName = stockName.trim()
        if (!normalizedCode.matches(Regex("\\d{6}")) || normalizedName.isBlank()) {
            callback(ApiResult(false, 0, "", "请输入 6 位股票代码和股票名称。"))
            return
        }
        val payload = JSONObject()
            .put("stockCode", normalizedCode)
            .put("stockName", normalizedName)
        apiClient.postJson(ApiConfig.SYSTEM_CONFIG_STOCKS_PATH, payload, ApiConfig.SYSTEM_CONFIG_READ_TIMEOUT_MS) { result ->
            if (!result.success || !isApiSuccess(result.body)) {
                callback(result.copy(success = false, message = apiMessage(result.body, result.message)))
                return@postJson
            }
            runCatching {
                val data = dataObject(result.body)
                val name = data.cleanString("stockName").ifBlank { normalizedName }
                val trendMessage = if (data.optBoolean("trendSynced", false)) "分时同步完成" else "分时同步未完成"
                callback(result.copy(message = "$name 快照同步完成，$trendMessage。"))
            }.onFailure {
                callback(result.copy(message = "$normalizedName 已新增，返回结果解析失败：${it.message ?: it.javaClass.simpleName}"))
            }
        }
    }

    fun addBondConfig(bondCode: String, bondName: String, callback: (ApiResult) -> Unit) {
        val normalizedCode = bondCode.trim()
        val normalizedName = bondName.trim()
        if (!normalizedCode.matches(Regex("\\d{6}")) || normalizedName.isBlank()) {
            callback(ApiResult(false, 0, "", "请输入 6 位可转债代码和可转债名称。"))
            return
        }
        val payload = JSONObject()
            .put("bondCode", normalizedCode)
            .put("bondName", normalizedName)
        apiClient.postJson(ApiConfig.SYSTEM_CONFIG_BONDS_PATH, payload, ApiConfig.SYSTEM_CONFIG_READ_TIMEOUT_MS) { result ->
            if (!result.success || !isApiSuccess(result.body)) {
                callback(result.copy(success = false, message = apiMessage(result.body, result.message)))
                return@postJson
            }
            runCatching {
                val data = dataObject(result.body)
                val name = data.cleanString("bondName").ifBlank { normalizedName }
                val stockText = data.cleanString("underlyingStockName").takeIf { it.isNotBlank() }?.let { "，正股 $it 已同步" }.orEmpty()
                callback(result.copy(message = "$name 同步完成$stockText。"))
            }.onFailure {
                callback(result.copy(message = "$normalizedName 已新增，返回结果解析失败：${it.message ?: it.javaClass.simpleName}"))
            }
        }
    }

    fun deleteTargetConfig(targetType: String, targetCode: String, callback: (ApiResult) -> Unit) {
        val normalizedType = targetType.trim().uppercase(Locale.ROOT)
        val normalizedCode = targetCode.trim()
        if (normalizedType !in setOf("STOCK", "BOND", "INDEX") || !normalizedCode.matches(Regex("\\d{6}"))) {
            callback(ApiResult(false, 0, "", "请选择标的类型并输入 6 位标的代码。"))
            return
        }
        val payload = JSONObject()
            .put("targetType", normalizedType)
            .put("targetCode", normalizedCode)
        apiClient.postJson(ApiConfig.SYSTEM_CONFIG_TARGET_DELETE_PATH, payload, ApiConfig.SYSTEM_CONFIG_READ_TIMEOUT_MS) { result ->
            callback(
                if (result.success && isApiSuccess(result.body)) {
                    result.copy(message = "${targetTypeLabel(normalizedType)} $normalizedCode 已物理删除。")
                } else {
                    result.copy(success = false, message = apiMessage(result.body, result.message))
                },
            )
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

    fun loadObservationRisk(callback: (ApiResult, ObservationRiskUiState) -> Unit) {
        val pending = ObservationPending(callback)
        apiClient.get(ApiConfig.WATCH_GROUPS_PATH) { result ->
            pending.consumeGroups(result)
        }
        apiClient.get(ApiConfig.STOCK_ALERTS_PATH) { result ->
            pending.consumeAlerts(result)
        }
    }

    fun loadAlertTargetOptions(
        targetType: WatchTargetType,
        callback: (ApiResult, List<AlertTargetOption>) -> Unit,
    ) {
        val alertType = targetType.alertTargetType
        if (alertType == null) {
            callback(ApiResult(true, 200, "", "${targetType.label}暂未接入预警候选标的。"), emptyList())
            return
        }
        apiClient.get("${ApiConfig.STOCK_ALERT_TARGET_OPTIONS_PATH}?targetType=${alertType.encodeQuery()}") { result ->
            if (!result.success || !isApiSuccess(result.body)) {
                callback(result.copy(success = false, message = apiMessage(result.body, result.message)), emptyList())
                return@get
            }
            runCatching {
                callback(result.copy(message = "${targetType.label}候选标的已同步。"), readTargetOptions(result.body))
            }.onFailure {
                callback(
                    ApiResult(false, result.statusCode, result.body, "候选标的解析失败：${it.message ?: it.javaClass.simpleName}"),
                    emptyList(),
                )
            }
        }
    }

    fun saveWatchTarget(
        form: AddWatchTargetFormState,
        callback: (ApiResult, WatchItem?) -> Unit,
    ) {
        val targetCode = form.selectedTargetCode.ifBlank { form.targetKeyword.trim() }
        val targetName = form.selectedTargetName.ifBlank { form.targetKeyword.trim() }
        if (form.selectedGroupId.isBlank() || targetCode.isBlank() || targetName.isBlank()) {
            callback(ApiResult(false, 0, "", "请选择分组并搜索标的。"), null)
            return
        }
        val payload = JSONObject()
            .put("groupId", form.selectedGroupId)
            .put("targetType", form.targetType.apiValue)
            .put("targetCode", targetCode)
            .put("targetName", targetName)
            .put("secid", form.selectedSecid)
            .put("remark", form.remark.trim())
        form.buyPrice.toDoubleOrNull()?.let { payload.put("buyPrice", it) }
        form.position.toDoubleOrNull()?.let { payload.put("position", it) }

        apiClient.postJson(ApiConfig.WATCH_ITEMS_PATH, payload) { result ->
            if (!result.success || !isApiSuccess(result.body)) {
                callback(result.copy(success = false, message = apiMessage(result.body, result.message)), null)
                return@postJson
            }
            runCatching {
                val item = JSONObject(result.body).optJSONObject("data")?.toWatchItem()
                callback(result.copy(message = "观察标的已保存。"), item)
            }.onFailure {
                callback(
                    ApiResult(false, result.statusCode, result.body, "观察标的解析失败：${it.message ?: it.javaClass.simpleName}"),
                    null,
                )
            }
        }
    }

    fun saveStockAlert(
        targetType: WatchTargetType,
        stockCode: String,
        thresholdPercent: Double = DEFAULT_ALERT_THRESHOLD_PERCENT,
        enabled: Boolean = true,
        id: String = "",
        callback: (ApiResult) -> Unit,
    ) {
        val alertType = targetType.alertTargetType
        if (alertType == null) {
            callback(ApiResult(false, 0, "", "${targetType.label}暂不支持风险预警。"))
            return
        }
        val payload = JSONObject()
            .put("targetType", alertType)
            .put("stockCode", stockCode)
            .put("thresholdPercent", thresholdPercent)
            .put("enabled", enabled)
        if (id.isNotBlank()) {
            payload.put("id", id)
        }
        apiClient.postJson(ApiConfig.STOCK_ALERTS_PATH, payload) { result ->
            callback(
                if (result.success && isApiSuccess(result.body)) {
                    result.copy(message = "风险预警已保存。")
                } else {
                    result.copy(success = false, message = apiMessage(result.body, result.message))
                },
            )
        }
    }

    fun deleteWatchTargetWithAlert(
        item: WatchItem,
        alert: StockAlertConfig?,
        callback: (ApiResult) -> Unit,
    ) {
        if (item.id.isBlank()) {
            callback(ApiResult(false, 0, "", "观察标的缺少 ID，无法删除。"))
            return
        }
        if (alert != null && alert.id.isNotBlank()) {
            apiClient.delete("${ApiConfig.STOCK_ALERTS_PATH}/${alert.id}") { alertResult ->
                if (!alertResult.success || !isApiSuccess(alertResult.body)) {
                    callback(alertResult.copy(success = false, message = apiMessage(alertResult.body, alertResult.message)))
                    return@delete
                }
                deleteWatchItem(item.id, callback)
            }
            return
        }
        deleteWatchItem(item.id, callback)
    }

    private fun deleteWatchItem(itemId: String, callback: (ApiResult) -> Unit) {
        apiClient.delete("${ApiConfig.WATCH_ITEMS_PATH}/$itemId") { result ->
            callback(
                if (result.success && isApiSuccess(result.body)) {
                    result.copy(message = "观察标的已删除。")
                } else {
                    result.copy(success = false, message = apiMessage(result.body, result.message))
                },
            )
        }
    }

    fun loadReportResearch(
        targetType: ReportTargetType,
        callback: (ApiResult, ReportResearchUiState) -> Unit,
    ) {
        apiClient.get(reportTargetPath(targetType)) { result ->
            if (!result.success) {
                callback(result, ReportResearchUiState(targetType = targetType))
                return@get
            }
            runCatching {
                val targets = readReportTargets(result.body)
                callback(
                    result.copy(message = "报告研究已同步：${targets.size} 个报告标的。"),
                    ReportResearchUiState(
                        targets = targets,
                        targetType = targetType,
                        updatedAt = LocalDateTime.now().format(timeFormat),
                    ),
                )
            }.onFailure {
                callback(
                    ApiResult(false, result.statusCode, result.body, "报告列表解析失败：${it.message ?: it.javaClass.simpleName}"),
                    ReportResearchUiState(targetType = targetType),
                )
            }
        }
    }

    fun loadReportCreateOptions(callback: (ApiResult, List<ReportTypeOption>, List<ReportConfigProfileOption>) -> Unit) {
        val pending = ReportCreateOptionsPending(callback)
        apiClient.get(ApiConfig.SCENE_ANALYSIS_REPORT_TYPES_PATH) { result ->
            pending.consumeTypes(result)
        }
        apiClient.get(ApiConfig.SCENE_ANALYSIS_CONFIG_PROFILES_PATH) { result ->
            pending.consumeProfiles(result)
        }
    }

    fun loadReportTargetOptions(
        targetType: ReportTargetType,
        keyword: String,
        callback: (ApiResult, List<ReportTargetOption>) -> Unit,
    ) {
        val params = listOf(
            "targetType" to targetType.apiValue,
            "keyword" to keyword.trim(),
            "limit" to "20",
        ).filterNot { (key, value) -> key == "keyword" && value.isBlank() }
        val path = ApiConfig.SCENE_ANALYSIS_TARGET_SEARCH_PATH + "?" + params.joinToString("&") { (key, value) ->
            "${key.encodeQuery()}=${value.encodeQuery()}"
        }
        apiClient.get(path) { result ->
            if (!result.success || !result.isBusinessSuccessOrRaw()) {
                callback(result.copy(success = false, message = apiMessage(result.body, result.message)), emptyList())
                return@get
            }
            runCatching {
                callback(result.copy(message = "${targetType.label}候选标的已同步。"), readReportTargetOptions(result.body))
            }.onFailure {
                callback(
                    ApiResult(false, result.statusCode, result.body, "报告候选标的解析失败：${it.message ?: it.javaClass.simpleName}"),
                    emptyList(),
                )
            }
        }
    }

    fun submitReportTask(
        form: CreateReportFormState,
        callback: (ApiResult) -> Unit,
    ) {
        if (!form.canSubmit) {
            callback(ApiResult(false, 0, "", "请选择标的后再创建报告。"))
            return
        }
        val payload = JSONObject()
            .put("configProfile", form.configProfile.ifBlank { "system_recommended" })
            .put("dailyKlineLimit", form.dailyKlineLimit.coerceAtLeast(MIN_REPORT_DAILY_KLINE_LIMIT))
            .put("monthlyKlineLimit", form.monthlyKlineLimit)
            .put("reportType", form.reportType.ifBlank { "quick_analysis" })
            .put("targetCode", form.targetCode.trim())
            .put("targetName", form.targetName.trim().ifBlank { form.targetCode.trim() })
            .put("targetType", form.targetType.apiValue)
            .put("totalChunks", form.totalChunks.coerceAtLeast(1))
            .put("weeklyKlineLimit", form.weeklyKlineLimit)
            .put("userOverrides", JSONObject().put("asset_type", form.targetType.assetTypeValue()))

        apiClient.postJson(ApiConfig.SCENE_ANALYSIS_TASKS_PATH, payload) { result ->
            callback(
                if (result.success && result.isBusinessSuccessOrRaw()) {
                    result.copy(message = "报告生成任务已提交。")
                } else {
                    result.copy(success = false, message = apiMessage(result.body, result.message))
                },
            )
        }
    }

    fun loadKnowledgeMaterialOptions(callback: (ApiResult, List<ReportTypeOption>, List<ReportConfigProfileOption>) -> Unit) {
        loadReportCreateOptions(callback)
    }

    fun loadKnowledgeMaterialTargetOptions(
        targetType: ReportTargetType,
        keyword: String,
        callback: (ApiResult, List<ReportTargetOption>) -> Unit,
    ) {
        loadReportTargetOptions(targetType, keyword, callback)
    }

    fun submitKnowledgeMaterialTarget(
        form: KnowledgeMaterialFormState,
        callback: (ApiResult, KnowledgeMaterialTask?) -> Unit,
    ) {
        if (!form.canSubmitTarget) {
            callback(ApiResult(false, 0, "", "请选择标的后再检索材料。"), null)
            return
        }
        val payload = JSONObject()
            .put("searchMode", KnowledgeMaterialSearchMode.Target.apiValue)
            .put("configProfile", form.configProfile.ifBlank { "system_recommended" })
            .put("dailyKlineLimit", form.dailyKlineLimit.coerceAtLeast(MIN_REPORT_DAILY_KLINE_LIMIT))
            .put("monthlyKlineLimit", form.monthlyKlineLimit)
            .put("reportType", form.reportType.ifBlank { "quick_analysis" })
            .put("targetCode", form.targetCode.trim())
            .put("targetName", form.targetName.trim().ifBlank { form.targetCode.trim() })
            .put("targetType", form.targetType.apiValue)
            .put("totalChunks", form.totalChunks.coerceAtLeast(1))
            .put("weeklyKlineLimit", form.weeklyKlineLimit)
            .put("userOverrides", JSONObject().put("asset_type", form.targetType.assetTypeValue()))

        submitKnowledgeMaterial(payload, callback)
    }

    fun submitKnowledgeMaterialNaturalLanguage(
        queryText: String,
        totalChunks: Int,
        callback: (ApiResult, KnowledgeMaterialTask?) -> Unit,
    ) {
        val normalizedQuery = queryText.trim()
        if (normalizedQuery.isBlank()) {
            callback(ApiResult(false, 0, "", "请输入自然语言检索问题。"), null)
            return
        }
        val payload = JSONObject()
            .put("searchMode", KnowledgeMaterialSearchMode.NaturalLanguage.apiValue)
            .put("queryText", normalizedQuery)
            .put("totalChunks", totalChunks.coerceAtLeast(1))

        submitKnowledgeMaterial(payload, callback)
    }

    fun loadKnowledgeMaterialTask(
        taskNo: String,
        callback: (ApiResult, KnowledgeMaterialTask?) -> Unit,
    ) {
        if (taskNo.isBlank()) {
            callback(ApiResult(false, 0, "", "任务编号为空，无法刷新材料。"), null)
            return
        }
        apiClient.get("${ApiConfig.KNOWLEDGE_MATERIAL_TASKS_PATH}/${taskNo.encodePath()}") { result ->
            if (!result.success || !result.isBusinessSuccessOrRaw()) {
                callback(result.copy(success = false, message = apiMessage(result.body, result.message)), null)
                return@get
            }
            runCatching {
                callback(result.copy(message = "材料任务已刷新。"), readKnowledgeMaterialTask(result.body))
            }.onFailure {
                callback(
                    ApiResult(false, result.statusCode, result.body, "材料任务解析失败：${it.message ?: it.javaClass.simpleName}"),
                    null,
                )
            }
        }
    }

    fun loadOcrTasks(callback: (ApiResult, List<OcrTask>) -> Unit) {
        val payload = JSONObject()
            .put("pageNum", 1)
            .put("pageSize", 20)
        apiClient.postJson(ApiConfig.OCR_TASKS_PAGE_PATH, payload) { result ->
            if (!result.success || !result.isBusinessSuccessOrRaw()) {
                callback(result.copy(success = false, message = apiMessage(result.body, result.message)), emptyList())
                return@postJson
            }
            runCatching {
                callback(result.copy(message = "OCR队列已同步。"), readOcrTasks(result.body))
            }.onFailure {
                callback(
                    ApiResult(false, result.statusCode, result.body, "OCR队列解析失败：${it.message ?: it.javaClass.simpleName}"),
                    emptyList(),
                )
            }
        }
    }

    fun submitOcrFiles(
        files: List<OcrUploadFile>,
        callback: (ApiResult, List<OcrTask>) -> Unit,
    ) {
        if (files.isEmpty()) {
            callback(ApiResult(false, 0, "", "请选择 PDF 或照片后再提交。"), emptyList())
            return
        }
        apiClient.postMultipartFiles(ApiConfig.OCR_TASKS_PATH, files) { result ->
            if (!result.success || !result.isBusinessSuccessOrRaw()) {
                callback(result.copy(success = false, message = apiMessage(result.body, result.message)), emptyList())
                return@postMultipartFiles
            }
            runCatching {
                callback(result.copy(message = "OCR任务已提交。"), readOcrTaskArray(result.body))
            }.onFailure {
                callback(
                    ApiResult(false, result.statusCode, result.body, "OCR提交结果解析失败：${it.message ?: it.javaClass.simpleName}"),
                    emptyList(),
                )
            }
        }
    }

    fun loadOcrReview(taskNo: String, callback: (ApiResult, OcrReviewDetail?) -> Unit) {
        if (taskNo.isBlank()) {
            callback(ApiResult(false, 0, "", "任务编号为空，无法进入复核。"), null)
            return
        }
        apiClient.get("${ApiConfig.OCR_REVIEWS_PATH}/${taskNo.encodePath()}") { result ->
            if (!result.success || !result.isBusinessSuccessOrRaw()) {
                callback(result.copy(success = false, message = apiMessage(result.body, result.message)), null)
                return@get
            }
            runCatching {
                callback(result.copy(message = "OCR复核内容已加载。"), readOcrReview(result.body))
            }.onFailure {
                callback(
                    ApiResult(false, result.statusCode, result.body, "OCR复核内容解析失败：${it.message ?: it.javaClass.simpleName}"),
                    null,
                )
            }
        }
    }

    fun saveOcrReviewDraft(taskNo: String, draft: OcrReviewDraftContent, callback: (ApiResult) -> Unit) {
        val payload = JSONObject().put("draftContent", draft.toJson())
        apiClient.putJson("${ApiConfig.OCR_REVIEWS_PATH}/${taskNo.encodePath()}/draft", payload) { result ->
            callback(
                if (result.success && result.isBusinessSuccessOrRaw()) {
                    result.copy(message = "OCR复核草稿已保存。")
                } else {
                    result.copy(success = false, message = apiMessage(result.body, result.message))
                },
            )
        }
    }

    fun submitOcrReview(taskNo: String, draft: OcrReviewDraftContent, callback: (ApiResult) -> Unit) {
        val payload = JSONObject().put("draftContent", draft.toJson())
        apiClient.postJson("${ApiConfig.OCR_REVIEWS_PATH}/${taskNo.encodePath()}/submit", payload) { result ->
            callback(
                if (result.success && result.isBusinessSuccessOrRaw()) {
                    result.copy(message = "OCR复核结果已提交。")
                } else {
                    result.copy(success = false, message = apiMessage(result.body, result.message))
                },
            )
        }
    }

    fun loadManualKnowledgeTasks(callback: (ApiResult, List<OcrTask>) -> Unit) {
        val payload = JSONObject()
            .put("pageNum", 1)
            .put("pageSize", 20)
        apiClient.postJson(ApiConfig.MANUAL_KNOWLEDGE_TASKS_PAGE_PATH, payload) { result ->
            if (!result.success || !result.isBusinessSuccessOrRaw()) {
                callback(result.copy(success = false, message = apiMessage(result.body, result.message)), emptyList())
                return@postJson
            }
            runCatching {
                callback(result.copy(message = "手动导入队列已同步。"), readOcrTasks(result.body))
            }.onFailure {
                callback(
                    ApiResult(false, result.statusCode, result.body, "手动导入队列解析失败：${it.message ?: it.javaClass.simpleName}"),
                    emptyList(),
                )
            }
        }
    }

    fun loadManualKnowledgeDetail(taskNo: String, callback: (ApiResult, OcrReviewDetail?) -> Unit) {
        if (taskNo.isBlank()) {
            callback(ApiResult(false, 0, "", "任务编号为空，无法查看手动导入内容。"), null)
            return
        }
        apiClient.get("${ApiConfig.MANUAL_KNOWLEDGE_TASKS_PATH}/${taskNo.encodePath()}") { result ->
            if (!result.success || !result.isBusinessSuccessOrRaw()) {
                callback(result.copy(success = false, message = apiMessage(result.body, result.message)), null)
                return@get
            }
            runCatching {
                callback(result.copy(message = "手动导入内容已加载。"), readOcrReview(result.body))
            }.onFailure {
                callback(
                    ApiResult(false, result.statusCode, result.body, "手动导入内容解析失败：${it.message ?: it.javaClass.simpleName}"),
                    null,
                )
            }
        }
    }

    fun saveManualKnowledgeDraft(
        taskNo: String,
        title: String,
        chunks: List<String>,
        callback: (ApiResult, OcrTask?) -> Unit,
    ) {
        val payload = manualKnowledgePayload(title, chunks)
        if (payload == null) {
            callback(ApiResult(false, 0, "", "至少需要一个非空文本分段。"), null)
            return
        }
        if (taskNo.isBlank()) {
            apiClient.postJson(ApiConfig.MANUAL_KNOWLEDGE_TASKS_PATH, payload) { result ->
                if (!result.success || !result.isBusinessSuccessOrRaw()) {
                    callback(result.copy(success = false, message = apiMessage(result.body, result.message)), null)
                    return@postJson
                }
                runCatching {
                    callback(result.copy(message = "手动导入草稿已保存。"), readOcrTask(result.body))
                }.onFailure {
                    callback(
                        ApiResult(false, result.statusCode, result.body, "手动导入草稿解析失败：${it.message ?: it.javaClass.simpleName}"),
                        null,
                    )
                }
            }
            return
        }
        apiClient.putJson("${ApiConfig.MANUAL_KNOWLEDGE_TASKS_PATH}/${taskNo.encodePath()}/draft", payload) { result ->
            callback(
                if (result.success && result.isBusinessSuccessOrRaw()) {
                    result.copy(message = "手动导入草稿已保存。")
                } else {
                    result.copy(success = false, message = apiMessage(result.body, result.message))
                },
                null,
            )
        }
    }

    fun submitManualKnowledgeDraft(
        taskNo: String,
        title: String,
        chunks: List<String>,
        callback: (ApiResult) -> Unit,
    ) {
        val payload = manualKnowledgePayload(title, chunks)
        if (payload == null) {
            callback(ApiResult(false, 0, "", "至少需要一个非空文本分段。"))
            return
        }
        if (taskNo.isBlank()) {
            apiClient.postJson(ApiConfig.MANUAL_KNOWLEDGE_TASKS_PATH, payload) { result ->
                if (!result.success || !result.isBusinessSuccessOrRaw()) {
                    callback(result.copy(success = false, message = apiMessage(result.body, result.message)))
                    return@postJson
                }
                runCatching {
                    val createdTaskNo = readOcrTask(result.body).taskNo
                    submitManualKnowledgeTask(createdTaskNo, payload, callback)
                }.onFailure {
                    callback(ApiResult(false, result.statusCode, result.body, "手动导入草稿解析失败：${it.message ?: it.javaClass.simpleName}"))
                }
            }
            return
        }
        submitManualKnowledgeTask(taskNo, payload, callback)
    }

    fun deleteManualKnowledgeTask(taskNo: String, callback: (ApiResult) -> Unit) {
        if (taskNo.isBlank()) {
            callback(ApiResult(false, 0, "", "任务编号为空，无法删除手动导入任务。"))
            return
        }
        val payload = JSONObject().put("taskNo", taskNo.trim())
        apiClient.postJson("${ApiConfig.MANUAL_KNOWLEDGE_TASKS_PATH}/delete", payload) { result ->
            callback(
                if (result.success && result.isBusinessSuccessOrRaw()) {
                    result.copy(message = "手动导入任务已删除。")
                } else {
                    result.copy(success = false, message = apiMessage(result.body, result.message))
                },
            )
        }
    }

    private fun submitKnowledgeMaterial(
        payload: JSONObject,
        callback: (ApiResult, KnowledgeMaterialTask?) -> Unit,
    ) {
        apiClient.postJson(ApiConfig.KNOWLEDGE_MATERIAL_TASKS_PATH, payload) { result ->
            if (!result.success || !result.isBusinessSuccessOrRaw()) {
                callback(result.copy(success = false, message = apiMessage(result.body, result.message)), null)
                return@postJson
            }
            runCatching {
                callback(result.copy(message = "材料检索任务已提交。"), readKnowledgeMaterialTask(result.body))
            }.onFailure {
                callback(
                    ApiResult(false, result.statusCode, result.body, "材料提交结果解析失败：${it.message ?: it.javaClass.simpleName}"),
                    null,
                )
            }
        }
    }

    private fun submitManualKnowledgeTask(taskNo: String, payload: JSONObject, callback: (ApiResult) -> Unit) {
        if (taskNo.isBlank()) {
            callback(ApiResult(false, 0, "", "手动导入任务创建后未返回任务编号。"))
            return
        }
        apiClient.postJson("${ApiConfig.MANUAL_KNOWLEDGE_TASKS_PATH}/${taskNo.encodePath()}/submit", payload) { result ->
            callback(
                if (result.success && result.isBusinessSuccessOrRaw()) {
                    result.copy(message = "手动知识已提交，开始打标入库。")
                } else {
                    result.copy(success = false, message = apiMessage(result.body, result.message))
                },
            )
        }
    }

    fun loadReportDetail(reportId: Long, callback: (ApiResult, ReportDetail?) -> Unit) {
        if (reportId <= 0L) {
            callback(ApiResult(false, 0, "", "报告缺少 ID，无法查看详情。"), null)
            return
        }
        apiClient.get("${ApiConfig.SCENE_REPORTS_PATH}/$reportId") { result ->
            if (!result.success || !result.isBusinessSuccessOrRaw()) {
                callback(result.copy(success = false, message = apiMessage(result.body, result.message)), null)
                return@get
            }
            runCatching {
                callback(result.copy(message = "报告详情已加载。"), readReportDetail(result.body))
            }.onFailure {
                callback(
                    ApiResult(false, result.statusCode, result.body, "报告详情解析失败：${it.message ?: it.javaClass.simpleName}"),
                    null,
                )
            }
        }
    }

    fun regenerateReport(taskNo: String, callback: (ApiResult) -> Unit) {
        if (taskNo.isBlank()) {
            callback(ApiResult(false, 0, "", "任务编号为空，无法重新生成。"))
            return
        }
        apiClient.postJson("${ApiConfig.SCENE_ANALYSIS_TASKS_PATH}/${taskNo.encodePath()}/report/regenerate", JSONObject()) { result ->
            callback(
                if (result.success && result.isBusinessSuccessOrRaw()) {
                    result.copy(message = "已提交重新生成任务。")
                } else {
                    result.copy(success = false, message = apiMessage(result.body, result.message))
                },
            )
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
        val trimmed = body.trim()
        if (trimmed.startsWith("[")) return JSONArray(trimmed)
        val root = JSONObject(body.ifBlank { "{}" })
        return root.optJSONArray("data") ?: JSONArray()
    }

    private fun readObservationGroups(body: String): List<WatchGroup> {
        val groups = dataArray(body)
        return List(groups.length()) { index -> groups.optJSONObject(index) }
            .filterNotNull()
            .map { group ->
                val items = group.optJSONArray("items") ?: JSONArray()
                WatchGroup(
                    id = group.optString("id", ""),
                    name = group.cleanString("groupName").ifBlank { group.cleanString("name").ifBlank { "未命名分组" } },
                    items = List(items.length()) { itemIndex -> items.optJSONObject(itemIndex) }
                        .filterNotNull()
                        .map { it.toWatchItem() },
                )
            }
    }

    private fun readStockAlerts(body: String): List<StockAlertConfig> {
        val alerts = dataArray(body)
        return List(alerts.length()) { index -> alerts.optJSONObject(index) }
            .filterNotNull()
            .map { alert ->
                StockAlertConfig(
                    id = alert.optString("id", ""),
                    targetType = WatchTargetType.fromApi(alert.optString("targetType", "")),
                    stockCode = alert.optString("stockCode", ""),
                    stockName = alert.cleanString("stockName").ifBlank { "未命名标的" },
                    thresholdPercent = alert.optDouble("thresholdPercent", DEFAULT_ALERT_THRESHOLD_PERCENT),
                    enabled = alert.optBoolean("enabled", false),
                    outOfThreshold = alert.optBoolean("outOfThreshold", false),
                    latestPrice = alert.optionalDouble("latestPrice"),
                    changePercent = alert.optionalDouble("changePercent"),
                    syncedAt = alert.optString("syncedAt", ""),
                )
            }
    }

    private fun readTargetOptions(body: String): List<AlertTargetOption> {
        val options = dataArray(body)
        return List(options.length()) { index -> options.optJSONObject(index) }
            .filterNotNull()
            .map { option ->
                AlertTargetOption(
                    targetType = WatchTargetType.fromApi(option.optString("targetType", "")),
                    targetCode = option.optString("targetCode", ""),
                    targetName = option.cleanString("targetName").ifBlank { "未命名标的" },
                    marketCode = option.cleanString("marketCode"),
                    exchangeCode = option.cleanString("exchangeCode"),
                )
            }
    }

    private fun readReportTargets(body: String): List<ReportTargetSummary> {
        val root = JSONObject(body.ifBlank { "{}" })
        val data = root.optJSONObject("data")
        val records = data?.optJSONArray("records")
            ?: root.optJSONArray("records")
            ?: root.optJSONArray("data")
            ?: JSONArray()
        return List(records.length()) { index -> records.optJSONObject(index) }
            .filterNotNull()
            .map { item ->
                ReportTargetSummary(
                    targetType = ReportTargetType.fromApi(item.cleanString("targetType")),
                    targetCode = item.cleanString("targetCode"),
                    targetName = item.cleanString("targetName").ifBlank { item.cleanString("targetCode").ifBlank { "未命名标的" } },
                    latestTaskNo = item.cleanString("latestTaskNo"),
                    latestReportId = item.optionalLong("latestReportId"),
                    latestStatus = item.cleanString("latestStatus"),
                    latestVersionNo = item.optionalInt("latestVersionNo"),
                    latestReportType = item.cleanString("latestReportType"),
                    latestGenerationType = item.cleanString("latestGenerationType"),
                    latestModel = item.cleanString("latestModel"),
                    latestReportPreview = item.cleanString("latestReportPreview"),
                    latestCreatedAt = item.cleanString("latestCreatedAt"),
                    latestGeneratedAt = item.cleanString("latestGeneratedAt"),
                    reportCount = item.optInt("reportCount", 0),
                )
            }
    }

    private fun readReportDetail(body: String): ReportDetail {
        val detail = dataObject(body)
        return ReportDetail(
            reportId = detail.optLong("reportId", 0L),
            taskNo = detail.cleanString("taskNo"),
            targetType = ReportTargetType.fromApi(detail.cleanString("targetType")),
            targetCode = detail.cleanString("targetCode"),
            targetName = detail.cleanString("targetName").ifBlank { detail.cleanString("targetCode").ifBlank { "未命名标的" } },
            reportType = detail.cleanString("reportType"),
            generationType = detail.cleanString("generationType"),
            versionNo = detail.optInt("versionNo", 0),
            status = detail.cleanString("status"),
            model = detail.cleanString("model"),
            createdAt = detail.cleanString("createdAt"),
            generatedAt = detail.cleanString("generatedAt"),
            errorMessage = detail.cleanString("errorMessage"),
            reportText = detail.cleanString("reportText"),
        )
    }

    private fun readReportTypes(body: String): List<ReportTypeOption> {
        val types = dataArray(body)
        val parsed = List(types.length()) { index -> types.optJSONObject(index) }
            .filterNotNull()
            .map { type ->
                ReportTypeOption(
                    code = type.cleanString("code").ifBlank { "quick_analysis" },
                    label = type.cleanString("label").ifBlank { type.cleanString("code").ifBlank { "快速分析" } },
                )
            }
        return parsed.ifEmpty {
            listOf(
                ReportTypeOption("quick_analysis", "快速分析"),
                ReportTypeOption("valuation_report", "估值报告"),
            )
        }
    }

    private fun readReportConfigProfiles(body: String): List<ReportConfigProfileOption> {
        val profiles = dataArray(body)
        return List(profiles.length()) { index -> profiles.optJSONObject(index) }
            .filterNotNull()
            .map { profile ->
                val config = profile.optJSONObject("configJson") ?: JSONObject()
                ReportConfigProfileOption(
                    id = profile.optLong("id", 0L),
                    name = profile.cleanString("name").ifBlank { profile.cleanString("configProfile").ifBlank { "系统推荐" } },
                    configProfile = profile.cleanString("configProfile").ifBlank { "system_recommended" },
                    configGroup = profile.cleanString("configGroup").ifBlank { "默认" },
                    reportType = config.cleanString("reportType").ifBlank { profile.cleanString("reportType").ifBlank { "quick_analysis" } },
                    targetType = reportTargetTypeOrNull(
                        config.cleanString("targetType").ifBlank { profile.cleanString("targetType") },
                    ),
                    totalChunks = config.optionalInt("totalChunks") ?: DEFAULT_REPORT_TOTAL_CHUNKS,
                    dailyKlineLimit = config.optionalInt("dailyKlineLimit") ?: DEFAULT_REPORT_DAILY_KLINE_LIMIT,
                    weeklyKlineLimit = config.optionalInt("weeklyKlineLimit") ?: DEFAULT_REPORT_WEEKLY_KLINE_LIMIT,
                    monthlyKlineLimit = config.optionalInt("monthlyKlineLimit") ?: DEFAULT_REPORT_MONTHLY_KLINE_LIMIT,
                    systemDefault = profile.optBoolean("systemDefault", false),
                )
            }
    }

    private fun readReportTargetOptions(body: String): List<ReportTargetOption> {
        val options = dataArray(body)
        return List(options.length()) { index -> options.optJSONObject(index) }
            .filterNotNull()
            .map { option ->
                ReportTargetOption(
                    targetType = ReportTargetType.fromApi(option.cleanString("targetType")),
                    targetCode = option.cleanString("targetCode"),
                    targetName = option.cleanString("targetName").ifBlank { option.cleanString("targetCode").ifBlank { "未命名标的" } },
                    marketCode = option.cleanString("marketCode"),
                )
            }
    }

    private fun readKnowledgeMaterialTask(body: String): KnowledgeMaterialTask {
        val task = dataObject(body)
        return KnowledgeMaterialTask(
            taskNo = task.cleanString("taskNo"),
            searchMode = KnowledgeMaterialSearchMode.fromApi(task.cleanString("searchMode")),
            targetType = reportTargetTypeOrNull(task.cleanString("targetType")),
            targetCode = task.cleanString("targetCode"),
            targetName = task.cleanString("targetName"),
            queryText = task.cleanString("queryText"),
            rewrittenQuery = task.cleanString("rewrittenQuery"),
            status = task.cleanString("status"),
            errorMessage = task.cleanString("errorMessage"),
            submittedAt = task.cleanString("submittedAt"),
            finishedAt = task.cleanString("finishedAt"),
            chunks = readKnowledgeMaterialChunks(task),
        )
    }

    private fun readOcrTasks(body: String): List<OcrTask> {
        val root = JSONObject(body.ifBlank { "{}" })
        val page = root.optJSONObject("data") ?: root
        val records = page.optJSONArray("records")
            ?: root.optJSONArray("records")
            ?: root.optJSONArray("data")
            ?: JSONArray()
        return List(records.length()) { index -> records.optJSONObject(index) }
            .filterNotNull()
            .map { it.toOcrTask() }
    }

    private fun readOcrTaskArray(body: String): List<OcrTask> {
        val array = dataArray(body)
        return List(array.length()) { index -> array.optJSONObject(index) }
            .filterNotNull()
            .map { it.toOcrTask() }
    }

    private fun readOcrTask(body: String): OcrTask =
        dataObject(body).toOcrTask()

    private fun readOcrReview(body: String): OcrReviewDetail {
        val detail = dataObject(body)
        val draft = detail.optJSONObject("draftContent") ?: JSONObject()
        val draftContent = readOcrReviewDraft(
            draft = draft,
            fallbackTaskNo = detail.cleanString("taskNo"),
        )
        return OcrReviewDetail(
            taskNo = detail.cleanString("taskNo").ifBlank { draftContent.taskNo },
            status = detail.cleanString("status"),
            overallConfidence = detail.optDouble("overallConfidence", 0.0),
            paragraphCount = detail.optionalInt("paragraphCount") ?: draftContent.paragraphCount,
            warningCount = detail.optionalInt("warningCount") ?: draftContent.paragraphs.sumOf { it.warnings.size },
            draftContent = draftContent,
        )
    }

    private fun readOcrReviewDraft(draft: JSONObject, fallbackTaskNo: String): OcrReviewDraftContent {
        val paragraphs = draft.optJSONArray("paragraphs") ?: JSONArray()
        val parsedParagraphs = List(paragraphs.length()) { index -> paragraphs.optJSONObject(index) }
            .filterNotNull()
            .map { paragraph ->
                val warnings = paragraph.optJSONArray("warnings") ?: JSONArray()
                OcrReviewParagraph(
                    paragraphNo = paragraph.optInt("paragraphNo", 0),
                    text = paragraph.cleanString("text"),
                    sourcePages = paragraph.optJSONArray("sourcePages").toIntList(),
                    avgConfidence = paragraph.optDouble("avgConfidence", 0.0),
                    warnings = List(warnings.length()) { index -> warnings.optJSONObject(index) }
                        .filterNotNull()
                        .map { warning ->
                            OcrReviewWarning(
                                type = warning.cleanString("type"),
                                confidence = warning.optionalDouble("confidence"),
                            )
                        },
                )
            }
        return OcrReviewDraftContent(
            taskNo = draft.cleanString("taskNo").ifBlank { fallbackTaskNo },
            paragraphCount = draft.optionalInt("paragraphCount") ?: parsedParagraphs.size,
            paragraphs = parsedParagraphs,
        )
    }

    private fun readKnowledgeMaterialChunks(task: JSONObject): List<KnowledgeMaterialChunk> {
        val directChunks = task.optJSONArray("chunks")
        if (directChunks != null && directChunks.length() > 0) {
            return List(directChunks.length()) { index -> directChunks.optJSONObject(index) }
                .filterNotNull()
                .map { it.toKnowledgeMaterialChunk() }
        }
        val context = task.optJSONObject("knowledgeContext") ?: return emptyList()
        val result = mutableListOf<KnowledgeMaterialChunk>()
        val scenes = context.keys()
        while (scenes.hasNext()) {
            val scene = scenes.next()
            val chunks = context.optJSONArray(scene) ?: continue
            for (index in 0 until chunks.length()) {
                chunks.optJSONObject(index)?.let { result += it.toKnowledgeMaterialChunk(scene) }
            }
        }
        return result
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

    private inner class ObservationPending(
        private val callback: (ApiResult, ObservationRiskUiState) -> Unit,
    ) {
        private var remaining = 2
        private var failure: ApiResult? = null
        private var groups: List<WatchGroup> = emptyList()
        private var alerts: List<StockAlertConfig> = emptyList()

        fun consumeGroups(result: ApiResult) {
            consume(result, "观察池分组同步失败。") { body -> groups = readObservationGroups(body) }
        }

        fun consumeAlerts(result: ApiResult) {
            consume(result, "风险预警同步失败。") { body -> alerts = readStockAlerts(body) }
        }

        private fun consume(result: ApiResult, fallback: String, reader: (String) -> Unit) {
            if (result.success && isApiSuccess(result.body)) {
                runCatching { reader(result.body) }.onFailure {
                    failure = ApiResult(false, result.statusCode, result.body, "观察风控解析失败：${it.message ?: it.javaClass.simpleName}")
                }
            } else if (failure == null) {
                failure = result.copy(success = false, message = apiMessage(result.body, result.message.ifBlank { fallback }))
            }
            done()
        }

        private fun done() {
            remaining--
            if (remaining > 0) return
            callback(
                failure ?: ApiResult(true, 200, "", "观察风控数据已同步。"),
                ObservationRiskUiState(
                    groups = groups,
                    selectedGroupId = groups.firstOrNull()?.id.orEmpty(),
                    alerts = alerts,
                    updatedAt = LocalDateTime.now().format(timeFormat),
                ),
            )
        }
    }

    private inner class ReportCreateOptionsPending(
        private val callback: (ApiResult, List<ReportTypeOption>, List<ReportConfigProfileOption>) -> Unit,
    ) {
        private var remaining = 2
        private var failure: ApiResult? = null
        private var reportTypes: List<ReportTypeOption> = emptyList()
        private var configProfiles: List<ReportConfigProfileOption> = emptyList()

        fun consumeTypes(result: ApiResult) {
            consume(result, "报告类型同步失败。") { body -> reportTypes = readReportTypes(body) }
        }

        fun consumeProfiles(result: ApiResult) {
            consume(result, "报告配置同步失败。") { body -> configProfiles = readReportConfigProfiles(body) }
        }

        private fun consume(result: ApiResult, fallback: String, reader: (String) -> Unit) {
            if (result.success && result.isBusinessSuccessOrRaw()) {
                runCatching { reader(result.body) }.onFailure {
                    failure = ApiResult(false, result.statusCode, result.body, "新建报告配置解析失败：${it.message ?: it.javaClass.simpleName}")
                }
            } else if (failure == null) {
                failure = result.copy(success = false, message = apiMessage(result.body, result.message.ifBlank { fallback }))
            }
            done()
        }

        private fun done() {
            remaining--
            if (remaining > 0) return
            callback(
                failure ?: ApiResult(true, 200, "", "新建报告配置已同步。"),
                reportTypes.ifEmpty {
                    listOf(
                        ReportTypeOption("quick_analysis", "快速分析"),
                        ReportTypeOption("valuation_report", "估值报告"),
                    )
                },
                configProfiles,
            )
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

private fun JSONObject.toWatchItem(): WatchItem =
    WatchItem(
        id = optString("id", ""),
        groupId = optString("groupId", ""),
        targetType = WatchTargetType.fromApi(optString("targetType", "")),
        targetCode = optString("targetCode", ""),
        targetName = cleanString("targetName").ifBlank { "未命名标的" },
        secid = cleanString("secid"),
        remark = cleanString("remark"),
        buyPrice = optionalDouble("buyPrice"),
        position = optionalDouble("position"),
        latestPrice = optionalDouble("latestPrice"),
        changePercent = optionalDouble("changePercent"),
        syncedAt = optString("syncedAt", ""),
    )

private fun JSONObject.toKnowledgeMaterialChunk(sceneFallback: String = "knowledge"): KnowledgeMaterialChunk =
    KnowledgeMaterialChunk(
        chunkId = optLong("chunkId", 0L),
        taskNo = cleanString("taskNo"),
        chunkIndex = optionalInt("chunkIndex"),
        scene = cleanString("scene").ifBlank { sceneFallback.ifBlank { "knowledge" } },
        filename = cleanString("filename").ifBlank { "知识库材料" },
        text = cleanString("text"),
        matchedTags = optJSONArray("matchedTags").toStringList(),
        semanticScore = optionalDouble("semanticScore"),
        tagMatchScore = optionalDouble("tagMatchScore"),
        crossSceneScore = optionalDouble("crossSceneScore"),
        finalScore = optionalDouble("finalScore"),
    )

private fun JSONObject.toOcrTask(): OcrTask =
    OcrTask(
        taskNo = cleanString("taskNo"),
        originalFilename = cleanString("originalFilename").ifBlank { "未命名材料" },
        fileType = cleanString("fileType"),
        status = cleanString("status"),
        currentStage = cleanString("currentStage"),
        progress = optInt("progress", 0),
        pageCount = optInt("pageCount", 0),
        segmentCount = optInt("segmentCount", 0),
        submittedAt = cleanString("submittedAt"),
        updatedAt = cleanString("updatedAt"),
        errorMessage = cleanString("errorMessage"),
    )

private fun JSONArray?.toIntList(): List<Int> {
    if (this == null) return emptyList()
    return List(length()) { index -> optInt(index, 0) }.filter { it > 0 }
}

private fun OcrReviewDraftContent.toJson(): JSONObject =
    JSONObject()
        .put("taskNo", taskNo)
        .put("paragraphCount", paragraphs.size)
        .put(
            "paragraphs",
            JSONArray().also { array ->
                paragraphs.forEachIndexed { index, paragraph ->
                    array.put(
                        JSONObject()
                            .put("paragraphNo", index + 1)
                            .put("text", paragraph.text)
                            .put("sourcePages", JSONArray(paragraph.sourcePages))
                            .put("avgConfidence", paragraph.avgConfidence)
                            .put(
                                "warnings",
                                JSONArray().also { warnings ->
                                    paragraph.warnings.forEach { warning ->
                                        warnings.put(
                                            JSONObject()
                                                .put("type", warning.type)
                                                .apply {
                                                    warning.confidence?.let { put("confidence", it) }
                                                },
                                        )
                                    }
                                },
                            ),
                    )
                }
            },
        )

private fun manualKnowledgePayload(title: String, chunks: List<String>): JSONObject? {
    val normalizedChunks = chunks.map { it.trim() }.filter { it.isNotBlank() }
    if (normalizedChunks.isEmpty()) return null
    val payload = JSONObject().put("chunks", JSONArray(normalizedChunks))
    val normalizedTitle = title.trim()
    if (normalizedTitle.isNotBlank()) {
        payload.put("title", normalizedTitle)
    }
    return payload
}

private fun JSONObject.optionalDouble(key: String): Double? =
    if (isNull(key) || !has(key)) null else optDouble(key, 0.0)

private fun JSONObject.optionalInt(key: String): Int? =
    if (isNull(key) || !has(key)) null else optInt(key, 0)

private fun JSONObject.optionalLong(key: String): Long? =
    if (isNull(key) || !has(key)) null else optLong(key, 0L)

private fun JSONObject.cleanString(key: String): String {
    if (isNull(key) || !has(key)) return ""
    val value = optString(key, "")
    return if (value.equals("null", ignoreCase = true)) "" else value
}

private fun dataObject(body: String): JSONObject {
    val root = JSONObject(body.ifBlank { "{}" })
    return root.optJSONObject("data") ?: root
}

private fun ApiResult.isBusinessSuccessOrRaw(): Boolean {
    val trimmed = body.trim()
    if (trimmed.isBlank() || trimmed.startsWith("[")) return true
    if (!trimmed.startsWith("{")) return false
    return runCatching {
        val root = JSONObject(trimmed)
        !root.has("code") || root.optInt("code", -1) == 0
    }.getOrDefault(false)
}

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

private fun String.encodePath(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())

private fun reportTargetPath(targetType: ReportTargetType): String {
    val params = listOf(
        "pageNum" to "1",
        "pageSize" to DEFAULT_REPORT_PAGE_SIZE.toString(),
        "targetType" to targetType.apiValue,
    )
    return ApiConfig.SCENE_REPORT_TARGETS_PATH + "?" + params.joinToString("&") { (key, value) ->
        "${key.encodeQuery()}=${value.encodeQuery()}"
    }
}

private fun reportTargetTypeOrNull(value: String): ReportTargetType? =
    value.takeIf { it.isNotBlank() }?.let { ReportTargetType.fromApi(it) }

private fun targetTypeLabel(value: String): String = when (value.uppercase(Locale.ROOT)) {
    "STOCK" -> "股票"
    "BOND" -> "可转债"
    "INDEX" -> "指数"
    else -> value
}

private fun ReportTargetType.assetTypeValue(): String = when (this) {
    ReportTargetType.Index -> "index"
    ReportTargetType.ConvertibleBond -> "convertible_bond"
    ReportTargetType.Stock -> "stock"
}

fun normalizeSortOrder(sortOrder: String): String =
    if (sortOrder.equals("asc", ignoreCase = true)) "asc" else "desc"
