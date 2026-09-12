package com.scrapider.finance.androidapp.feature.workbench.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.designsystem.rememberFinanceSignalColors
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportErrorState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportInfoState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportLoadingState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportTopBar

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImportEditorScreen(
    state: ImportsUiState,
    isManual: Boolean,
    onTitleChanged: (String) -> Unit,
    onParagraphChanged: (Int, String) -> Unit,
    onAddParagraph: () -> Unit,
    onRemoveParagraph: (Int) -> Unit,
    onPreviewPage: (Int) -> Unit,
    onSave: () -> Unit,
    onSubmit: () -> Unit,
    onReload: () -> Unit,
    onReviewUnconfirmed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val draft = state.draft
    val busy = state.isSaving || state.isSubmitting || state.isCreatingManual
    val editable = state.canEditDraft
    val hasContent = draft?.paragraphs?.any { it.text.isNotBlank() } == true
    val sources = remember(state.review) { state.review?.paragraphs.orEmpty().associateBy { it.paragraphNo } }
    val pageNumbers = remember(state.review) { state.review?.pages.orEmpty().map { it.pageNo }.toSet() }
    Column(modifier.fillMaxSize()) {
        ReportTopBar(title = if (isManual) "文本资料" else "核对识别内容", onBack = onBack, actions = {
            TextButton(onClick = onSave, enabled = editable && hasContent && (state.dirty || state.selectedTask == null)) {
                Text(if (state.isSaving || state.isCreatingManual) "正在保存" else "保存")
            }
        })
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = spacing.xl, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            if (state.isLoadingReview) item(key = "editor-loading") { ReportLoadingState("正在读取资料内容") }
            if (state.submissionUnconfirmed) {
                item(key = "editor-unconfirmed") { ReportInfoState("操作结果待确认。请先查看处理记录，避免重复提交。") }
            } else if (state.error != null) {
                item(key = "editor-error") {
                    if (draft == null) ReportErrorState(state.error.userMessage, onRetry = onReload)
                    else Text(state.error.userMessage, style = MaterialTheme.typography.bodyMedium,
                        color = rememberFinanceSignalColors().onNegativeContainer,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            if (draft != null) {
                item(key = "editor-introduction") {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(if (isManual) "整理研究笔记" else state.selectedTask?.originalFilename.orEmpty(),
                            style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                        Text(if (isManual) "分段录入材料。保存仅保留草稿，确认入库后才开始整理。"
                            else "核对段落内容，必要时对照原页。保存草稿后仍可继续修改。",
                            style = MaterialTheme.typography.bodyMedium, color = rememberFinanceSignalColors().onNeutralContainer)
                        Text(if (state.dirty) "有未保存的修改" else if (state.selectedTask != null) "草稿已保存" else "尚未保存",
                            style = MaterialTheme.typography.bodySmall, color = rememberFinanceSignalColors().onNeutralContainer,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
                if (isManual) item(key = "editor-title") {
                    OutlinedTextField(value = draft.title, onValueChange = onTitleChanged,
                        modifier = Modifier.fillMaxWidth(), label = { Text("资料标题") }, enabled = editable,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = MaterialTheme.shapes.medium)
                }
                itemsIndexed(draft.paragraphs, key = { _, paragraph -> paragraph.paragraphNo }, contentType = { _, _ -> "editable-paragraph" }) { index, paragraph ->
                    val source = sources[paragraph.paragraphNo]
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text("第 ${index + 1} 段", modifier = Modifier.weight(1f).padding(top = spacing.md).semantics { heading() },
                                style = MaterialTheme.typography.titleMedium)
                            if (isManual && draft.paragraphs.size > 1) TextButton(
                                onClick = { onRemoveParagraph(paragraph.paragraphNo) }, enabled = editable,
                            ) { Text("移除此段", color = rememberFinanceSignalColors().onNegativeContainer) }
                        }
                        if (source != null && source.warnings.isNotEmpty()) {
                            Text("此段有 ${source.warnings.size} 项识别提示，请重点核对。",
                                style = MaterialTheme.typography.bodySmall, color = rememberFinanceSignalColors().onWarningContainer)
                        }
                        OutlinedTextField(
                            value = paragraph.text,
                            onValueChange = { onParagraphChanged(paragraph.paragraphNo, it) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = editable,
                            label = { Text("第 ${index + 1} 段正文") },
                            minLines = 3,
                            maxLines = 12,
                            shape = MaterialTheme.shapes.medium,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
                        )
                        val sourcePages = source?.sourcePages.orEmpty().filter { it in pageNumbers }
                        if (sourcePages.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            sourcePages.forEach { page ->
                                TextButton(onClick = { onPreviewPage(page) }) { Text("查看第 $page 页原文") }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                if (isManual) item(key = "editor-add") {
                    TextButton(onClick = onAddParagraph, enabled = editable, modifier = Modifier.fillMaxWidth()) { Text("添加一段") }
                }
            }
        }
        ImportActionBar(
            label = when {
                state.submissionUnconfirmed -> "查看处理记录"
                busy -> "正在处理"
                else -> "确认入库"
            },
            onClick = if (state.submissionUnconfirmed) onReviewUnconfirmed else onSubmit,
            enabled = state.submissionUnconfirmed || (editable && hasContent),
            busy = busy,
        )
    }
}
