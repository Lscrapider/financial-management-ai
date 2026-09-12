package com.scrapider.finance.androidapp.feature.workbench.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.scrapider.finance.androidapp.R
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.designsystem.rememberFinanceSignalColors
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportEmptyState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportErrorState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportInfoState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportLoadingState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportTopBar

@Composable
internal fun ImportDetailScreen(
    state: ImportsUiState,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val task = state.selectedTask
    Column(modifier.fillMaxSize()) {
        ReportTopBar(title = "资料详情", onBack = onBack, actions = {
            IconButton(onClick = onRefresh, enabled = !state.isLoadingDetail) {
                Icon(painterResource(R.drawable.ic_phosphor_arrows_clockwise), "刷新资料状态")
            }
        })
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = spacing.xl, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            if (task != null) {
                item(key = "import-detail-header", contentType = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                        Text(task.originalFilename, style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
                        ImportStatusLabel(task.status)
                        Text("${task.category.label}资料 · ${task.submittedAt}", style = MaterialTheme.typography.bodySmall,
                            color = rememberFinanceSignalColors().onNeutralContainer)
                        Text(buildList {
                            if (task.category == ImportCategory.File) add("${task.pageCount} 页")
                            add("${task.segmentCount} 段")
                        }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item(key = "import-detail-divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
                when (task.status) {
                    ImportTaskStatus.Pending, ImportTaskStatus.Processing -> item(key = "import-progress", contentType = "status") {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                            Text(task.currentStage.ifBlank { "等待后台处理" }, style = MaterialTheme.typography.titleMedium)
                            LinearProgressIndicator(progress = { task.progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
                            Text("处理进度 ${task.progress}% · 离开页面后仍会继续", style = MaterialTheme.typography.bodySmall,
                                color = rememberFinanceSignalColors().onNeutralContainer)
                        }
                    }
                    ImportTaskStatus.ReviewRequired -> item(key = "import-review-info", contentType = "status") {
                        ReportInfoState(if (task.category == ImportCategory.Text) "草稿尚未入库。检查内容后确认提交，系统将继续整理资料。"
                        else "部分识别内容需要核对。可按段修改，并对照原页后确认入库。")
                    }
                    ImportTaskStatus.Finished -> item(key = "import-success-info", contentType = "status") {
                        ReportInfoState("资料已完成处理，可在知识检索中查找相关内容。")
                    }
                    ImportTaskStatus.Failed -> item(key = "import-failure-info", contentType = "status") {
                        ReportInfoState("本次处理未完成。可先刷新确认状态，检查原材料后再重新导入。")
                    }
                    ImportTaskStatus.Unknown -> item(key = "import-unknown-info", contentType = "status") {
                        ReportInfoState("暂时无法确认处理状态，请刷新后查看。")
                    }
                }
            }
            if (state.error != null) item(key = "import-detail-error", contentType = "error") {
                ReportErrorState(state.error.userMessage, onRefresh)
            }
            if (state.isLoadingDetail) item(key = "import-detail-loading") { ReportLoadingState("正在更新处理状态") }
            item(key = "import-stages-heading", contentType = "heading") { ImportHeading("处理阶段") }
            if (state.stages.isEmpty() && !state.isLoadingDetail) {
                item(key = "import-stages-empty") { ReportEmptyState("暂无阶段记录") }
            }
            itemsIndexed(state.stages, key = { index, _ -> index }, contentType = { _, _ -> "import-stage" }) { _, stage ->
                val signals = rememberFinanceSignalColors()
                val color = when (stage.status) {
                    ImportStageStatus.Finished -> signals.onPositiveContainer
                    ImportStageStatus.Failed -> signals.onNegativeContainer
                    ImportStageStatus.Running -> MaterialTheme.colorScheme.primary
                    else -> signals.onNeutralContainer
                }
                Row(Modifier.fillMaxWidth().padding(vertical = spacing.xs), verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(stage.stage, style = MaterialTheme.typography.titleMedium)
                        stage.finishedAt?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = signals.onNeutralContainer) }
                    }
                    Text(stage.status.label, style = MaterialTheme.typography.labelMedium, color = color)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if (canEdit) ImportActionBar(
            label = if (task?.category == ImportCategory.Text) "继续编辑" else "核对识别内容",
            onClick = onEdit,
            enabled = !state.isLoadingReview && !state.isSubmitting,
        )
    }
}
