package com.scrapider.finance.androidapp.feature.workbench.knowledge

import androidx.compose.runtime.Immutable
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportChoice
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportTargetOption
import java.util.Locale

internal enum class KnowledgeSearchMode(
    val code: String,
    val label: String,
) {
    Target("target", "按标的"),
    NaturalLanguage("natural_language", "自然语言"),
    ;

    companion object {
        fun fromCode(value: String?): KnowledgeSearchMode = when (value?.trim()?.lowercase(Locale.ROOT)) {
            Target.code -> Target
            NaturalLanguage.code, "query", "natural", "text" -> NaturalLanguage
            else -> Target
        }
    }
}

internal enum class KnowledgeTaskStatus(
    val code: String,
    val label: String,
    val isWorking: Boolean,
) {
    CurrentScenesReady("current_scenes_ready", "场景已计算", true),
    Failed("failed", "失败", false),
    Pending("pending", "等待中", true),
    ProcessingCurrentScenes("processing_current_scenes", "计算场景", true),
    RetrievingKnowledge("retrieving_knowledge", "检索中", true),
    Success("success", "完成", false),
    Unknown("", "状态未知", false),
    ;

    companion object {
        fun fromCode(value: String?): KnowledgeTaskStatus = entries.firstOrNull {
            it.code == value?.trim()?.lowercase(Locale.ROOT)
        } ?: Unknown
    }
}

@Immutable
internal data class KnowledgeProfileOption(
    val id: Long,
    val name: String,
    val group: String,
    val configProfile: String,
    val targetType: String,
    val reportType: String,
    val reportTypeLabel: String,
    val totalChunks: Int?,
    val dailyKlineLimit: Int?,
    val weeklyKlineLimit: Int?,
    val monthlyKlineLimit: Int?,
    val userOverridesJson: String,
    val recommended: Boolean,
)

@Immutable
internal data class KnowledgeChunk(
    val chunkId: Long?,
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
    val key: String get() = "$taskNo:${chunkId ?: chunkIndex ?: text.hashCode()}"

    val sceneLabel: String
        get() = when (scene.trim().lowercase(Locale.ROOT)) {
            "knowledge" -> "自然语言"
            "price" -> "价格"
            "risk_strategy" -> "风险策略"
            "sentiment" -> "情绪"
            "trend" -> "趋势"
            "valuation" -> "估值"
            "volume" -> "量能"
            else -> "其他场景"
        }
}

@Immutable
internal data class KnowledgeTask(
    val taskNo: String,
    val searchMode: KnowledgeSearchMode,
    val targetType: String,
    val targetCode: String,
    val targetName: String,
    val queryText: String,
    val rewrittenQuery: String,
    val status: KnowledgeTaskStatus,
    val errorMessage: String,
    val submittedAt: String,
    val finishedAt: String,
    val chunks: List<KnowledgeChunk>,
)

@Immutable
internal data class KnowledgeUiState(
    val searchMode: KnowledgeSearchMode = KnowledgeSearchMode.Target,
    val queryText: String = "",
    val targetType: String = "",
    val targetQuery: String = "",
    val targetOptions: List<ReportTargetOption> = emptyList(),
    val selectedTarget: ReportTargetOption? = null,
    val isSearchingTargets: Boolean = false,
    val hasSearchedTargets: Boolean = false,
    val targetSearchError: String = "",
    val profiles: List<KnowledgeProfileOption> = emptyList(),
    val selectedProfileId: Long? = null,
    val isLoadingMetadata: Boolean = false,
    val isSubmitting: Boolean = false,
    val submissionUnconfirmed: Boolean = false,
    val errorMessage: String = "",
    val task: KnowledgeTask? = null,
    val isLoadingTask: Boolean = false,
    val taskErrorMessage: String = "",
) {
    val selectedProfile: KnowledgeProfileOption?
        get() = profiles.firstOrNull { it.id == selectedProfileId }

    val canSubmit: Boolean
        get() {
            val profile = selectedProfile ?: return false
            if (isLoadingMetadata || isSubmitting || submissionUnconfirmed || profile.totalChunks == null) {
                return false
            }
            return when (searchMode) {
                KnowledgeSearchMode.Target -> selectedTarget != null
                KnowledgeSearchMode.NaturalLanguage -> queryText.trim().isNotEmpty()
            }
        }
}

internal data class KnowledgeMetadata(
    val profiles: List<KnowledgeProfileOption>,
    val reportTypes: List<ReportChoice>,
)

internal data class KnowledgeSubmitResult(
    val taskNo: String,
    val searchMode: KnowledgeSearchMode,
    val targetType: String,
    val targetCode: String,
    val targetName: String,
    val queryText: String,
    val rewrittenQuery: String,
    val status: KnowledgeTaskStatus,
)

internal sealed interface KnowledgeEvent {
    data object SessionExpired : KnowledgeEvent
    data class Notice(val message: String) : KnowledgeEvent
}

internal fun KnowledgeTaskStatus.isTerminal(): Boolean = !isWorking
