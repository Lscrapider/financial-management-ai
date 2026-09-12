package com.scrapider.finance.androidapp.feature.workbench.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.scrapider.finance.androidapp.R
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.designsystem.rememberFinanceSignalColors
import com.scrapider.finance.androidapp.feature.workbench.ReportStatus
import kotlinx.coroutines.launch

@Composable
internal fun ReportHistoryScreen(
    state: ReportsUiState,
    onBack: () -> Unit,
    onSelectReport: (ReportRecord) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    Column(modifier = modifier.fillMaxSize()) {
        ReportTopBar(
            title = "历史记录",
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = onRefresh,
                    enabled = !state.isLoadingHistory,
                ) {
                    if (state.isLoadingHistory) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(dimensions.iconSize),
                            strokeWidth = dimensions.outlineWidth,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_phosphor_arrows_clockwise),
                            contentDescription = "刷新历史记录",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            },
        )

        state.historyTarget?.let { target ->
            Column(
                modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = target.targetName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${target.targetCode} · ${target.targetTypeLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = rememberFinanceSignalColors().onNeutralContainer,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = spacing.xl, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            if (state.historyError.isNotBlank()) {
                item(key = "history-error", contentType = "error") {
                    ReportErrorState(text = state.historyError, onRetry = onRefresh)
                }
            }
            when {
                state.isLoadingHistory && state.history.isEmpty() -> {
                    item(key = "history-loading", contentType = "loading") {
                        ReportLoadingState(text = "正在加载历史记录")
                    }
                }

                !state.isLoadingHistory && state.history.isEmpty() && state.historyError.isBlank() -> {
                    item(key = "history-empty", contentType = "empty") {
                        ReportEmptyState(text = "暂未找到历史报告")
                    }
                }

                else -> {
                    itemsIndexed(
                        items = state.history,
                        key = { _, record -> "report-record:${record.reportId}" },
                        contentType = { _, _ -> "report-record" },
                    ) { _, record ->
                        ReportRecordRow(
                            record = record,
                            onClick = { onSelectReport(record) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReportReaderScreen(
    state: ReportsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    val document = state.document
    var showMoreMenu by rememberSaveable { mutableStateOf(false) }
    var showContents by rememberSaveable { mutableStateOf(false) }
    var showRegenerateConfirmation by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val hasRequestError = state.documentError.isNotBlank()
    val regenerationUnconfirmed = document?.let { it.taskNo in state.unconfirmedRegenerationTasks } == true
    val bodyStartIndex = remember(document, hasRequestError, regenerationUnconfirmed) {
        var index = 2 // document header + divider
        if (hasRequestError) index++
        if (regenerationUnconfirmed) index++
        if (document?.status == ReportStatus.Generating || document?.status == ReportStatus.Pending) index++
        if (document?.status == ReportStatus.Failed) index++
        index + 1 // 报告正文标题之后才是第一个正文块
    }
    val contents = remember(document, bodyStartIndex) {
        document?.blocks.orEmpty().mapIndexedNotNull { index, block ->
            if (block.headingLevel > 0) {
                ReportContentsItem(
                    title = block.text,
                    lazyListIndex = bodyStartIndex + index,
                )
            } else {
                null
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ReportTopBar(
            title = "报告详情",
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = onRefresh,
                    enabled = !state.isLoadingDocument,
                ) {
                    if (state.isLoadingDocument) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(dimensions.iconSize),
                            strokeWidth = dimensions.outlineWidth,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_phosphor_arrows_clockwise),
                            contentDescription = "刷新报告详情",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                ReportMoreMenu(
                    expanded = showMoreMenu,
                    onExpandedChange = { showMoreMenu = it },
                    canRegenerate = document?.let { current ->
                        current.taskNo.isNotBlank() &&
                            current.status != ReportStatus.Pending &&
                            current.status != ReportStatus.Generating &&
                            !state.isLoadingDocument &&
                            current.taskNo !in state.unconfirmedRegenerationTasks &&
                            !state.isRegenerating
                    } == true,
                    onRegenerate = { showRegenerateConfirmation = true },
                )
            },
        )

        when {
            document == null && state.isLoadingDocument -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = spacing.xl),
                    contentAlignment = Alignment.TopStart,
                ) {
                    ReportLoadingState(text = "正在加载报告正文")
                }
            }

            document == null && state.documentError.isNotBlank() -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = spacing.xl, vertical = spacing.md),
                ) {
                    ReportErrorState(text = state.documentError, onRetry = onRefresh)
                }
            }

            document == null -> {
                ReportEmptyState(
                    text = "报告正文暂不可用",
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = spacing.xl),
                )
            }

            else -> {
                val currentDocument = document
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = spacing.xl,
                            top = spacing.sm,
                            end = spacing.xl,
                            bottom = spacing.xxl,
                        ),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        item(key = "document-header", contentType = "metadata") {
                            ReportDocumentHeader(document = currentDocument)
                        }
                        item(key = "document-divider", contentType = "divider") {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if (hasRequestError) {
                            item(key = "document-request-error", contentType = "error") {
                                ReportErrorState(text = state.documentError, onRetry = onRefresh)
                            }
                        }
                        if (regenerationUnconfirmed) {
                            item(key = "document-unconfirmed", contentType = "error") {
                                ReportErrorState(
                                    text = "提交结果待确认，请先刷新报告记录，再决定是否生成新版本。",
                                    onRetry = onRefresh,
                                )
                            }
                        }
                        when (currentDocument.status) {
                            ReportStatus.Generating -> {
                                item(key = "document-generating", contentType = "status") {
                                    ReportInfoState(text = "报告正在生成，请稍后刷新")
                                }
                            }

                            ReportStatus.Pending -> {
                                item(key = "document-pending", contentType = "status") {
                                    ReportInfoState(text = "报告已进入生成队列，请稍后刷新")
                                }
                            }

                            ReportStatus.Failed -> {
                                item(key = "document-failed", contentType = "status") {
                                    ReportErrorState(
                                        text = "生成未完成，可刷新状态或返回列表重新发起研究",
                                        onRetry = onRefresh,
                                    )
                                }
                            }

                            else -> Unit
                        }
                        item(key = "document-body-heading", contentType = "body-heading") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = dimensions.minTouchTarget),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "报告正文",
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics { heading() },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (contents.isNotEmpty()) {
                                    TextButton(
                                        onClick = { showContents = true },
                                        modifier = Modifier.heightIn(min = dimensions.minTouchTarget),
                                    ) {
                                        Text("目录")
                                    }
                                }
                            }
                        }
                        if (currentDocument.blocks.isEmpty()) {
                            when (currentDocument.status) {
                                ReportStatus.Generating,
                                ReportStatus.Pending,
                                -> item(key = "document-processing", contentType = "status") {
                                    ReportInfoState(text = "生成完成后会在此显示")
                                }
                                ReportStatus.Failed -> Unit
                                else -> item(key = "document-empty", contentType = "empty") {
                                    ReportEmptyState(text = "报告正文暂为空")
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = currentDocument.blocks,
                                key = { index, _ -> "report-block:$index" },
                                contentType = { _, _ -> "report-block" },
                            ) { _, block ->
                                ReportTextBlockView(block = block)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showContents) {
        ReportContentsSheet(
            contents = contents,
            onDismiss = { showContents = false },
            onSelect = { item ->
                showContents = false
                coroutineScope.launch { listState.animateScrollToItem(item.lazyListIndex) }
            },
        )
    }

    if (showRegenerateConfirmation) {
        AlertDialog(
            onDismissRequest = { showRegenerateConfirmation = false },
            title = { Text("重新生成报告") },
            text = { Text("使用已有研究上下文生成新版本，原版本保留") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRegenerateConfirmation = false
                        onRegenerate()
                    },
                ) {
                    Text("重新生成")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateConfirmation = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ReportDocumentHeader(
    document: ReportDocument,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = document.targetName,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "${document.targetCode} · ${document.targetTypeLabel}",
            style = MaterialTheme.typography.titleMedium,
            color = rememberFinanceSignalColors().onNeutralContainer,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = document.reportTypeLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text("·", color = MaterialTheme.colorScheme.outline)
            ReportStatusLabel(status = document.status)
        }
        Text(
            text = listOf(document.timeLabel, document.versionLabel).filter(String::isNotBlank).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = rememberFinanceSignalColors().onNeutralContainer,
        )
    }
}

@Composable
private fun ReportTextBlockView(
    block: ReportTextBlock,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val headingStyle = when {
        block.headingLevel == 1 -> MaterialTheme.typography.headlineSmall
        block.headingLevel > 1 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.bodyMedium
    }
    val contentModifier = modifier
        .fillMaxWidth()
        .padding(
            start = if (block.isNested) spacing.lg else if (block.isBullet) spacing.sm else spacing.xs,
            top = if (block.headingLevel > 0) spacing.lg else spacing.xxs,
            bottom = if (block.headingLevel > 0) spacing.sm else spacing.xs,
        )
    if (block.headingLevel > 0) {
        Text(
            text = block.text,
            modifier = contentModifier.semantics { heading() },
            style = headingStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
    } else if (block.isBullet) {
        Row(
            modifier = contentModifier,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyMedium,
                color = rememberFinanceSignalColors().onNeutralContainer,
            )
            Text(
                text = block.text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    } else {
        Text(
            text = block.text,
            modifier = contentModifier,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private data class ReportContentsItem(
    val title: String,
    val lazyListIndex: Int,
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReportContentsSheet(
    contents: List<ReportContentsItem>,
    onDismiss: () -> Unit,
    onSelect: (ReportContentsItem) -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xl, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            item {
              Text(
                text = "目录",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
              )
            }
            itemsIndexed(contents, key = { index, _ -> index }, contentType = { _, _ -> "contents-heading" }) { _, item ->
                TextButton(
                    onClick = { onSelect(item) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = LocalFinanceDimensions.current.minTouchTarget),
                ) {
                    Text(
                        text = item.title,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    )
                }
            }
        }
    }
}
