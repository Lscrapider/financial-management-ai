package com.scrapider.finance.androidapp.feature.workbench

import androidx.compose.runtime.Immutable
import com.scrapider.finance.androidapp.core.network.NetworkFailure

const val WORKBENCH_PREVIEW_ITEM_LIMIT = 3

@Immutable
data class WorkbenchUiState(
    val isLoading: Boolean = false,
    val focusItems: List<FocusItem> = emptyList(),
    val reportItems: List<ReportItem> = emptyList(),
    val syncMessage: String = "",
)

@Immutable
data class WorkbenchContent(
    val focusItems: List<FocusItem>,
    val reportItems: List<ReportItem>,
    val partialFailure: NetworkFailure? = null,
)

@Immutable
data class FocusItem(
    val id: String,
    val targetName: String,
    val targetCode: String,
    val targetTypeLabel: String,
    val changePercent: Double?,
    val status: FocusStatus,
)

enum class FocusStatus(
    val label: String,
) {
    Movement("行情变动"),
    ThresholdExceeded("已越界"),
}

@Immutable
data class ReportItem(
    val id: String,
    val targetName: String,
    val reportTypeLabel: String,
    val timeLabel: String,
    val status: ReportStatus,
)

enum class ReportStatus(
    val label: String,
) {
    Generated("已生成"),
    Generating("生成中"),
    Pending("待处理"),
    Failed("失败"),
    Unknown("暂无状态"),
}

enum class ResearchTool(
    val label: String,
    val description: String,
    val adminOnly: Boolean,
) {
    Report("研究报告", "生成并管理分析报告", false),
    KnowledgeSearch("知识检索", "按问题或标的检索材料", true),
    MaterialImport("资料导入", "OCR 与手动导入资料", true),
    AiAssistant("AI 研究助手", "梳理问题并辅助研究", false),
}

sealed interface WorkbenchEvent {
    data object SessionExpired : WorkbenchEvent
}
