package com.scrapider.finance.androidapp.feature.workbench.reports

import androidx.compose.runtime.Immutable
import com.scrapider.finance.androidapp.feature.workbench.ReportStatus

@Immutable
internal data class ReportTarget(
    val targetType: String,
    val targetCode: String,
    val targetName: String,
    val targetTypeLabel: String,
    val reportId: Long?,
    val taskNo: String,
    val reportTypeLabel: String,
    val timeLabel: String,
    val status: ReportStatus,
    val reportCount: Long,
) {
    val key: String get() = "$targetType:$targetCode"
}

@Immutable
internal data class ReportRecord(
    val reportId: Long,
    val taskNo: String,
    val reportTypeLabel: String,
    val timeLabel: String,
    val versionLabel: String,
    val status: ReportStatus,
)

@Immutable
internal data class ReportDocument(
    val reportId: Long?,
    val taskNo: String,
    val targetName: String,
    val targetCode: String,
    val targetTypeLabel: String,
    val reportTypeLabel: String,
    val timeLabel: String,
    val versionLabel: String,
    val status: ReportStatus,
    val blocks: List<ReportTextBlock>,
)

@Immutable
internal data class ReportTextBlock(
    val text: String,
    val headingLevel: Int = 0,
    val isBullet: Boolean = false,
    val isNested: Boolean = false,
)

@Immutable
internal data class ReportChoice(val value: String, val label: String)

@Immutable
internal data class ReportProfileOption(
    val id: Long,
    val name: String,
    val group: String,
    val targetType: String,
    val reportType: String,
    val recommended: Boolean,
)

@Immutable
internal data class ReportTargetOption(
    val targetType: String,
    val targetCode: String,
    val targetName: String,
    val typeLabel: String,
) {
    val key: String get() = "$targetType:$targetCode"
}

@Immutable
internal data class ReportCreateState(
    val isLoading: Boolean = false,
    val errorMessage: String = "",
    val profiles: List<ReportProfileOption> = emptyList(),
    val reportTypes: List<ReportChoice> = emptyList(),
    val selectedProfileId: Long? = null,
    val selectedReportType: String = "",
    val targetType: String = "",
    val targetQuery: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val searchError: String = "",
    val targetOptions: List<ReportTargetOption> = emptyList(),
    val selectedTarget: ReportTargetOption? = null,
    val isSubmitting: Boolean = false,
    val submissionUnconfirmed: Boolean = false,
) {
    val selectedProfile: ReportProfileOption? get() = profiles.find { it.id == selectedProfileId }
    val canSubmit: Boolean get() = !isLoading && !isSubmitting && !submissionUnconfirmed && selectedProfile != null &&
        selectedTarget != null && reportTypes.any { it.value == selectedReportType }
}

internal enum class ReportPage { List, History, Detail, Create }

@Immutable
internal data class ReportsUiState(
    val page: ReportPage = ReportPage.List,
    val targets: List<ReportTarget> = emptyList(),
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val errorMessage: String = "",
    val query: String = "",
    val targetType: String = "",
    val nextPage: Int? = null,
    val isLoadingMore: Boolean = false,
    val moreError: String = "",
    val historyTarget: ReportTarget? = null,
    val history: List<ReportRecord> = emptyList(),
    val isLoadingHistory: Boolean = false,
    val historyError: String = "",
    val document: ReportDocument? = null,
    val isLoadingDocument: Boolean = false,
    val documentError: String = "",
    val isRegenerating: Boolean = false,
    val unconfirmedRegenerationTasks: Set<String> = emptySet(),
    val create: ReportCreateState = ReportCreateState(),
)

internal data class ReportTargetPage(val items: List<ReportTarget>, val nextPage: Int?, val pageSize: Int)
internal data class ReportMetadata(val profiles: List<ReportProfileOption>, val reportTypes: List<ReportChoice>)
internal data class ReportSubmission(val taskNo: String, val status: ReportStatus)

internal sealed interface ReportsEvent {
    data object SessionExpired : ReportsEvent
    data object ReportsChanged : ReportsEvent
    data class Notice(val message: String) : ReportsEvent
}
