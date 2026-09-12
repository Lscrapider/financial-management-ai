package com.scrapider.finance.androidapp.feature.workbench.imports

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import com.scrapider.finance.androidapp.core.network.NetworkFailure

enum class ImportCategory(
    val sourceType: String,
    val label: String,
) {
    File(sourceType = "ocr", label = "文件"),
    Text(sourceType = "manual_text", label = "文本"),
}

enum class ImportTaskStatus(
    val label: String,
) {
    Pending("待处理"),
    Processing("处理中"),
    ReviewRequired("需复核"),
    Finished("已完成"),
    Failed("失败"),
    Unknown("状态未知");

    companion object {
        fun fromBackend(value: String?): ImportTaskStatus = when (value.orEmpty().trim().lowercase()) {
            "ready", "pending", "queued" -> Pending
            "running",
            "processing",
            "processing_current_scenes",
            "current_scenes_ready",
            "retrieving_knowledge",
            "generating_report" -> Processing
            "manual_review_required" -> ReviewRequired
            "finished", "success" -> Finished
            "failed" -> Failed
            else -> Unknown
        }
    }
}

enum class ImportStageStatus(
    val label: String,
) {
    Pending("待处理"),
    Running("处理中"),
    Finished("已完成"),
    Failed("失败"),
    Unknown("状态未知");

    companion object {
        fun fromBackend(value: String?): ImportStageStatus = when (value.orEmpty().trim().lowercase()) {
            "pending", "ready" -> Pending
            "running", "processing" -> Running
            "finished", "success" -> Finished
            "failed" -> Failed
            else -> Unknown
        }
    }
}

@Immutable
data class ImportTask(
    val id: Long,
    val taskNo: String,
    val originalFilename: String,
    val fileType: String,
    val sourceType: String,
    val category: ImportCategory,
    val fileSizeBytes: Long,
    val status: ImportTaskStatus,
    val currentStage: String,
    val progress: Int,
    val pageCount: Int,
    val segmentCount: Int,
    val submittedAt: String,
    val updatedAt: String,
) {
    val canReview: Boolean
        get() = status == ImportTaskStatus.ReviewRequired

    val canEdit: Boolean
        get() = canReview
}

@Immutable
data class ImportTaskPage(
    val records: List<ImportTask>,
    val total: Long,
    val pageNum: Int,
    val pageSize: Int,
    val pages: Int,
)

@Immutable
data class ImportTaskListState(
    val records: List<ImportTask> = emptyList(),
    val total: Long = 0,
    val pageNum: Int = 1,
    val pageSize: Int = DEFAULT_IMPORT_PAGE_SIZE,
    val pages: Int = 0,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: NetworkFailure? = null,
    val moreError: NetworkFailure? = null,
) {
    val canLoadMore: Boolean
        get() = pageNum < pages
}

@Immutable
data class ImportStage(
    val stage: String,
    val status: ImportStageStatus,
    val attemptCount: Int,
    val maxAttempts: Int,
    val inputReferenceAvailable: Boolean,
    val outputReferenceAvailable: Boolean,
    val metrics: Map<String, String>,
    val errorMessage: String?,
    val startedAt: String?,
    val finishedAt: String?,
    val updatedAt: String?,
)

@Immutable
data class ImportSourceSegment(
    val pageNo: Int?,
    val segmentNo: Int?,
)

@Immutable
data class ImportParagraph(
    val paragraphNo: Int,
    val text: String,
    val sourcePages: List<Int>,
    val sourceSegments: List<ImportSourceSegment>,
    val confidence: Double?,
    val warnings: List<String>,
)

@Immutable
data class ImportPage(
    val pageNo: Int,
    val imagePath: String,
)

enum class ImportReviewStatus(
    val label: String,
) {
    Pending("待复核"),
    Saved("已保存"),
    Approved("已提交"),
    Rejected("需修改"),
    Unknown("状态未知");

    companion object {
        fun fromBackend(value: String?): ImportReviewStatus = when (value.orEmpty().trim().lowercase()) {
            "pending", "manual_review_required" -> Pending
            "saved", "draft" -> Saved
            "approved", "submitted" -> Approved
            "rejected" -> Rejected
            else -> Unknown
        }
    }
}

@Immutable
data class ImportReview(
    val taskNo: String,
    val status: ImportReviewStatus,
    val overallConfidence: Double,
    val paragraphCount: Int,
    val warningCount: Int,
    val paragraphs: List<ImportParagraph>,
    val pages: List<ImportPage>,
)

@Immutable
data class ImportDraft(
    val title: String,
    val paragraphs: List<ImportParagraphDraft>,
)

@Immutable
data class ImportParagraphDraft(
    val paragraphNo: Int,
    val text: String,
)

data class ImportPagePreview(
    val pageNo: Int,
    val bitmap: Bitmap,
)

data class ImportsUiState(
    val fileTasks: ImportTaskListState = ImportTaskListState(),
    val manualTasks: ImportTaskListState = ImportTaskListState(),
    val selectedCategory: ImportCategory = ImportCategory.File,
    val selectedTask: ImportTask? = null,
    val stages: List<ImportStage> = emptyList(),
    val review: ImportReview? = null,
    val draft: ImportDraft? = null,
    val isLoadingDetail: Boolean = false,
    val isLoadingReview: Boolean = false,
    val isUploading: Boolean = false,
    val isCreatingManual: Boolean = false,
    val isPreparingFile: Boolean = false,
    val preparedFileName: String? = null,
    val preparedFileDescription: String = "",
    val fileError: String? = null,
    val isSaving: Boolean = false,
    val isSubmitting: Boolean = false,
    val submissionUnconfirmed: Boolean = false,
    val isPolling: Boolean = false,
    val preview: ImportPagePreview? = null,
    val selectedPreviewPageNo: Int? = null,
    val isLoadingPreview: Boolean = false,
    val previewError: NetworkFailure? = null,
    val error: NetworkFailure? = null,
    val notice: String? = null,
    val dirty: Boolean = false,
) {
    val canEditSelectedTask: Boolean
        get() = selectedTask?.canEdit == true &&
            review?.status != ImportReviewStatus.Approved &&
            !isLoadingReview && !isSaving && !isSubmitting && !isCreatingManual &&
            !isUploading && !isPreparingFile && !submissionUnconfirmed

    val canEditDraft: Boolean
        get() = draft != null && if (selectedTask != null) canEditSelectedTask else
            selectedCategory == ImportCategory.Text && !isLoadingReview && !isSaving &&
                !isSubmitting && !isCreatingManual && !isUploading && !isPreparingFile && !submissionUnconfirmed
}

sealed interface ImportsEvent {
    data object SessionExpired : ImportsEvent
    data class Imported(val taskNos: List<String>) : ImportsEvent
    data class Created(val taskNo: String) : ImportsEvent
    data class Submitted(val taskNo: String) : ImportsEvent
}

const val DEFAULT_IMPORT_PAGE_SIZE = 20
const val MAX_IMPORT_PAGE_SIZE = 200
const val MAX_IMPORT_FILE_SIZE_BYTES = 50L * 1024L * 1024L
const val MAX_IMPORT_PDF_PAGE_COUNT = 20
const val DEFAULT_IMPORT_POLL_INTERVAL_MS = 5_000L
