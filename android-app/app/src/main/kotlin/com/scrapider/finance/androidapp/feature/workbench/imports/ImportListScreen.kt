package com.scrapider.finance.androidapp.feature.workbench.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.scrapider.finance.androidapp.R
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.designsystem.rememberFinanceSignalColors
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportEmptyState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportErrorState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportLoadingState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportInfoState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportTopBar

@Composable
internal fun ImportListScreen(
    state: ImportsUiState,
    onCategory: (ImportCategory) -> Unit,
    onOpen: (ImportTask) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    val list = if (state.selectedCategory == ImportCategory.File) state.fileTasks else state.manualTasks
    val mutating = state.isUploading || state.isSaving || state.isSubmitting || state.isCreatingManual
    Column(modifier.fillMaxSize()) {
        ReportTopBar(title = "资料导入", onBack = onBack, actions = {
            IconButton(onClick = onRefresh, enabled = !list.isLoading) {
                Icon(painterResource(R.drawable.ic_phosphor_arrows_clockwise), "刷新处理记录")
            }
        })
        Row(Modifier.fillMaxWidth().padding(horizontal = spacing.xl)) {
            ImportCategory.entries.forEach { category ->
                val selected = category == state.selectedCategory
                Column(Modifier.weight(1f)) {
                    Tab(selected = selected, onClick = { onCategory(category) }, text = { Text(category.label) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = rememberFinanceSignalColors().onNeutralContainer)
                    HorizontalDivider(
                        thickness = dimensions.outlineWidth,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = spacing.xl, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            item(key = "import-list-heading", contentType = "heading") {
                ImportHeading("处理记录")
                Text(if (list.hasLoaded && list.error == null) "${state.selectedCategory.label}资料 · 共 ${list.total} 份" else "查看识别、复核与入库状态",
                    style = MaterialTheme.typography.bodySmall, color = rememberFinanceSignalColors().onNeutralContainer)
            }
            if (list.error != null) {
                item(key = "import-list-error", contentType = "error") { ReportErrorState(list.error.userMessage, onRefresh) }
            }
            if (state.submissionUnconfirmed) item(key = "import-unconfirmed", contentType = "status") {
                ReportInfoState("上次操作结果待确认，请先刷新处理记录，检查是否已接收。")
            }
            when {
                list.isLoading && list.records.isEmpty() -> item(key = "import-list-loading") { ReportLoadingState("正在加载处理记录") }
                list.records.isEmpty() && list.error == null -> item(key = "import-list-empty") {
                    ReportEmptyState(if (state.selectedCategory == ImportCategory.File)
                        "还没有导入文件。添加研究材料后，可在这里查看处理进度。"
                    else "还没有文本资料。可录入研究笔记，保存草稿后再确认入库。")
                }
                else -> items(list.records, key = { it.taskNo }, contentType = { "import-task" }) { task ->
                    ImportTaskRow(task, onClick = { onOpen(task) }, enabled = !mutating)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            if (list.moreError != null) {
                item(key = "import-more-error") { ReportErrorState(list.moreError.userMessage, onLoadMore) }
            }
            if (list.isLoadingMore) {
                item(key = "import-more-loading") { ReportLoadingState("正在加载更多") }
            } else if (list.canLoadMore) {
                item(key = "import-more") {
                    TextButton(onClick = onLoadMore, enabled = !list.isLoading, modifier = Modifier.fillMaxWidth()) { Text("加载更多") }
                }
            }
        }
        ImportActionBar(label = when {
            mutating -> "正在处理"
            state.draft != null && (state.dirty || state.submissionUnconfirmed) -> "返回编辑草稿"
            else -> "导入资料"
        }, onClick = onImport, busy = mutating)
    }
}

@Composable
private fun ImportTaskRow(task: ImportTask, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val spacing = LocalFinanceSpacing.current
    Row(
        modifier.fillMaxWidth().heightIn(min = LocalFinanceDimensions.current.listRowHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(painterResource(R.drawable.ic_phosphor_file_text_duotone), null,
            Modifier.padding(top = spacing.xs).size(LocalFinanceDimensions.current.iconSize),
            tint = rememberFinanceSignalColors().onNeutralContainer)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(task.originalFilename, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(task.submittedAt, style = MaterialTheme.typography.bodySmall, color = rememberFinanceSignalColors().onNeutralContainer)
            ImportStatusLabel(task.status)
        }
    }
}

@Composable
internal fun ImportStatusLabel(status: ImportTaskStatus, modifier: Modifier = Modifier) {
    val signals = rememberFinanceSignalColors()
    val spacing = LocalFinanceSpacing.current
    val (background, foreground) = when (status) {
        ImportTaskStatus.Finished -> signals.positiveContainer to signals.onPositiveContainer
        ImportTaskStatus.Failed -> signals.negativeContainer to signals.onNegativeContainer
        ImportTaskStatus.ReviewRequired -> signals.warningContainer to signals.onWarningContainer
        ImportTaskStatus.Processing -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else -> signals.neutralContainer to signals.onNeutralContainer
    }
    Text(status.label, modifier.background(background, MaterialTheme.shapes.extraSmall)
        .padding(horizontal = spacing.sm, vertical = spacing.xxs)
        .semantics { liveRegion = LiveRegionMode.Polite },
        color = foreground, style = MaterialTheme.typography.labelMedium)
}
