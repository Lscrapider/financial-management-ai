package com.scrapider.finance.androidapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrapider.finance.androidapp.data.KnowledgeMaterialChunk
import com.scrapider.finance.androidapp.data.KnowledgeMaterialFormState
import com.scrapider.finance.androidapp.data.KnowledgeMaterialTask
import com.scrapider.finance.androidapp.data.KnowledgeMaterialUiState
import com.scrapider.finance.androidapp.data.KnowledgeSection
import com.scrapider.finance.androidapp.data.ManualKnowledgeUiState
import com.scrapider.finance.androidapp.data.MIN_REPORT_DAILY_KLINE_LIMIT
import com.scrapider.finance.androidapp.data.OcrReviewDetail
import com.scrapider.finance.androidapp.data.OcrTask
import com.scrapider.finance.androidapp.data.OcrUploadFile
import com.scrapider.finance.androidapp.data.ReportConfigProfileOption
import com.scrapider.finance.androidapp.data.ReportTargetOption
import com.scrapider.finance.androidapp.data.ReportTargetType
import com.scrapider.finance.androidapp.data.ReportTypeOption
import java.text.DecimalFormat

@Composable
fun KnowledgeMaterialScreen(
    loading: Boolean,
    statusMessage: String,
    knowledge: KnowledgeMaterialUiState,
    onRefresh: () -> Unit,
    onSectionChange: (KnowledgeSection) -> Unit,
    onTargetTypeChange: (ReportTargetType) -> Unit,
    onTargetKeywordChange: (String) -> Unit,
    onTargetSelected: (ReportTargetOption) -> Unit,
    onProfileSelected: (Long) -> Unit,
    onFormChange: (KnowledgeMaterialFormState) -> Unit,
    onSubmitTarget: () -> Unit,
    onSubmitNaturalLanguage: () -> Unit,
    onSceneFilterChange: (String) -> Unit,
    onTagFilterChange: (String) -> Unit,
    onSourceKeywordChange: (String) -> Unit,
    onResetFilters: () -> Unit,
    onPickOcrPdf: () -> Unit,
    onTakeOcrPhoto: () -> Unit,
    onPickOcrGallery: () -> Unit,
    onRemoveOcrFile: (Int) -> Unit,
    onClearOcrFiles: () -> Unit,
    onSubmitOcrFiles: () -> Unit,
    onSelectOcrTask: (String) -> Unit,
    onOpenOcrReview: (String) -> Unit,
    onDismissOcrReview: () -> Unit,
    onOcrReviewParagraphChange: (Int, String) -> Unit,
    onMoveOcrReviewParagraph: (Int, Int) -> Unit,
    onMergeOcrReviewParagraph: (Int) -> Unit,
    onCopyOcrReviewParagraph: (Int) -> Unit,
    onDeleteOcrReviewParagraph: (Int) -> Unit,
    onSaveOcrReviewDraft: () -> Unit,
    onSubmitOcrReview: () -> Unit,
    onManualTitleChange: (String) -> Unit,
    onManualChunkChange: (Int, String) -> Unit,
    onAddManualChunk: () -> Unit,
    onRemoveManualChunk: (Int) -> Unit,
    onNewManualDraft: () -> Unit,
    onSaveManualDraft: () -> Unit,
    onSubmitManualDraft: () -> Unit,
    onSelectManualTask: (String) -> Unit,
    onOpenManualTask: (String) -> Unit,
    onDeleteManualTask: (String) -> Unit,
    onWorkbenchSelected: () -> Unit,
    onMarketSelected: () -> Unit,
    onObservationSelected: () -> Unit,
    onReportSelected: () -> Unit,
) {
    Scaffold(
        containerColor = WorkspaceBackground,
        topBar = {
            KnowledgeTopBar(
                updatedAt = knowledge.updatedAt,
                loading = loading,
                onRefresh = onRefresh,
            )
        },
        bottomBar = {
            KnowledgeBottomNav(
                onWorkbenchSelected = onWorkbenchSelected,
                onMarketSelected = onMarketSelected,
                onObservationSelected = onObservationSelected,
                onReportSelected = onReportSelected,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WorkspaceBackground)
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KnowledgeSegmentedTabs(selected = knowledge.section, onSelected = onSectionChange)
            when (knowledge.section) {
                KnowledgeSection.Materials -> {
                    RecallConfigCard(
                        loading = loading,
                        knowledge = knowledge,
                        onTargetTypeChange = onTargetTypeChange,
                        onTargetKeywordChange = onTargetKeywordChange,
                        onTargetSelected = onTargetSelected,
                        onProfileSelected = onProfileSelected,
                        onFormChange = onFormChange,
                        onSubmit = onSubmitTarget,
                    )
                    NaturalLanguageCard(
                        loading = loading,
                        form = knowledge.form,
                        onFormChange = onFormChange,
                        onSubmit = onSubmitNaturalLanguage,
                    )
                    ResultSection(
                        loading = loading,
                        knowledge = knowledge,
                        onSceneFilterChange = onSceneFilterChange,
                        onTagFilterChange = onTagFilterChange,
                        onSourceKeywordChange = onSourceKeywordChange,
                        onResetFilters = onResetFilters,
                    )
                }

                KnowledgeSection.OcrImport -> {
                    if (knowledge.ocr.selectedReview == null) {
                        OcrImportSection(
                            loading = loading,
                            selectedFiles = knowledge.ocr.selectedFiles,
                            tasks = knowledge.ocr.tasks,
                            selectedTask = knowledge.ocr.selectedTask,
                            runningCount = knowledge.ocr.runningCount,
                            finishedCount = knowledge.ocr.finishedCount,
                            reviewCount = knowledge.ocr.reviewCount,
                            failedCount = knowledge.ocr.failedCount,
                            onPickPdf = onPickOcrPdf,
                            onTakePhoto = onTakeOcrPhoto,
                            onPickGallery = onPickOcrGallery,
                            onRemoveFile = onRemoveOcrFile,
                            onClearFiles = onClearOcrFiles,
                            onSubmitFiles = onSubmitOcrFiles,
                            onSelectTask = onSelectOcrTask,
                            onOpenReview = onOpenOcrReview,
                        )
                    } else {
                        OcrReviewSection(
                            loading = loading,
                            review = knowledge.ocr.selectedReview,
                            onBack = onDismissOcrReview,
                            onParagraphChange = onOcrReviewParagraphChange,
                            onMoveParagraph = onMoveOcrReviewParagraph,
                            onMergeParagraph = onMergeOcrReviewParagraph,
                            onCopyParagraph = onCopyOcrReviewParagraph,
                            onDeleteParagraph = onDeleteOcrReviewParagraph,
                            onSaveDraft = onSaveOcrReviewDraft,
                            onSubmitReview = onSubmitOcrReview,
                        )
                    }
                }

                KnowledgeSection.ManualImport -> ManualImportSection(
                    loading = loading,
                    manual = knowledge.manual,
                    onTitleChange = onManualTitleChange,
                    onChunkChange = onManualChunkChange,
                    onAddChunk = onAddManualChunk,
                    onRemoveChunk = onRemoveManualChunk,
                    onNewDraft = onNewManualDraft,
                    onSaveDraft = onSaveManualDraft,
                    onSubmitDraft = onSubmitManualDraft,
                    onSelectTask = onSelectManualTask,
                    onOpenTask = onOpenManualTask,
                    onDeleteTask = onDeleteManualTask,
                )
            }
            if (statusMessage.isNotBlank()) {
                Text(statusMessage, color = WorkspaceMuted, fontSize = 12.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun KnowledgeTopBar(
    updatedAt: String,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .background(WorkspaceBackground)
            .border(BorderStroke(1.dp, WorkspaceBorder.copy(alpha = 0.65f)))
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("知识", color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("材料召回与证据检索 · $updatedAt", color = WorkspaceMuted, fontSize = 11.sp)
        }
        TextButton(onClick = onRefresh, enabled = !loading) {
            Text(if (loading) "同步中" else "↻", color = WorkspaceMuted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun KnowledgeSegmentedTabs(
    selected: KnowledgeSection,
    onSelected: (KnowledgeSection) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WorkspaceSurfaceMuted, RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KnowledgeSection.entries.forEach { item ->
            val active = item == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .background(if (active) WorkspaceSurfaceElevated else Color.Transparent, RoundedCornerShape(6.dp))
                    .border(
                        width = if (active) 1.dp else 0.dp,
                        color = if (active) WorkspaceBorder else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .clickable { onSelected(item) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.label,
                    color = if (active) PrimaryFixedDim else WorkspaceMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RecallConfigCard(
    loading: Boolean,
    knowledge: KnowledgeMaterialUiState,
    onTargetTypeChange: (ReportTargetType) -> Unit,
    onTargetKeywordChange: (String) -> Unit,
    onTargetSelected: (ReportTargetOption) -> Unit,
    onProfileSelected: (Long) -> Unit,
    onFormChange: (KnowledgeMaterialFormState) -> Unit,
    onSubmit: () -> Unit,
) {
    val form = knowledge.form
    Panel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("召回配置", color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            StatusChip("${form.totalChunks} 条", PrimaryFixedDim)
        }
        ConfigProfileDropdown(
            profiles = knowledge.configProfiles,
            selectedProfileId = form.configProfileId,
            onSelected = onProfileSelected,
        )
        ReportTargetTypeSelector(selected = form.targetType, onSelected = onTargetTypeChange)
        TargetOptionDropdown(
            keyword = form.targetKeyword,
            options = knowledge.targetOptions.filter { it.matches(form.targetKeyword) }.take(8),
            selectedCode = form.targetCode,
            onKeywordChange = onTargetKeywordChange,
            onSelected = onTargetSelected,
        )
        ReportTypeDropdown(
            reportTypes = knowledge.reportTypes,
            selectedCode = form.reportType,
            onSelected = { onFormChange(form.copy(reportType = it)) },
        )
        RecallQuantityControls(form = form, onFormChange = onFormChange)
        PrimaryActionButton(
            text = if (loading) "检索中" else "检索材料",
            enabled = !loading && form.canSubmitTarget,
            onClick = onSubmit,
        )
    }
}

@Composable
private fun NaturalLanguageCard(
    loading: Boolean,
    form: KnowledgeMaterialFormState,
    onFormChange: (KnowledgeMaterialFormState) -> Unit,
    onSubmit: () -> Unit,
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("✦", color = PrimaryFixedDim, fontSize = 18.sp)
            Text("自然语言召回", color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "只提交自然语言问题和上方召回数量，当前召回 ${form.totalChunks} 条。",
            color = WorkspaceMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        FinanceTextField(
            label = "检索问题",
            value = form.queryText,
            onValueChange = { onFormChange(form.copy(queryText = it)) },
            singleLine = false,
            minLines = 4,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        PrimaryActionButton(
            text = if (loading) "召回中" else "召回知识",
            enabled = !loading && form.canSubmitNaturalLanguage,
            onClick = onSubmit,
        )
    }
}

@Composable
private fun OcrImportSection(
    loading: Boolean,
    selectedFiles: List<OcrUploadFile>,
    tasks: List<OcrTask>,
    selectedTask: OcrTask?,
    runningCount: Int,
    finishedCount: Int,
    reviewCount: Int,
    failedCount: Int,
    onPickPdf: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onRemoveFile: (Int) -> Unit,
    onClearFiles: () -> Unit,
    onSubmitFiles: () -> Unit,
    onSelectTask: (String) -> Unit,
    onOpenReview: (String) -> Unit,
) {
    Panel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("提交OCR任务", color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("上传 PDF、拍照或从图库导入图片，进入 OCR、清洗、切分和入库队列。", color = WorkspaceMuted, fontSize = 12.sp, lineHeight = 18.sp)
            }
            StatusChip("${selectedFiles.size} 个待提交", PrimaryFixedDim)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UploadOption("▣", "上传PDF", "研报/文档", Modifier.weight(1f), onPickPdf)
            UploadOption("◉", "拍照", "调用相机", Modifier.weight(1f), onTakePhoto)
            UploadOption("▧", "图库", "截图/照片", Modifier.weight(1f), onPickGallery)
        }
        if (selectedFiles.isEmpty()) {
            EmptyResultText("尚未选择文件。支持 PDF、PNG、JPG、JPEG、WEBP，单个文件大小由后端限制。")
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("已选择文件", color = WorkspaceForeground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClearFiles) {
                    Text("清空", color = WorkspaceMuted, fontSize = 12.sp)
                }
            }
            selectedFiles.forEachIndexed { index, file ->
                SelectedOcrFileRow(file = file, onRemove = { onRemoveFile(index) })
            }
        }
        PrimaryActionButton(
            text = if (loading) "提交中" else "提交OCR任务",
            enabled = !loading && selectedFiles.isNotEmpty(),
            onClick = onSubmitFiles,
        )
    }

    Panel {
        Text("处理概览", color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCell("处理中", runningCount.toString(), SignalAmber, Modifier.weight(1f))
            MetricCell("已完成", finishedCount.toString(), SignalGreen, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCell("待复核", reviewCount.toString(), PrimaryFixedDim, Modifier.weight(1f))
            MetricCell("失败", failedCount.toString(), SignalRed, Modifier.weight(1f))
        }
    }

    Panel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("处理队列", color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(selectedTask?.updatedAt?.replace('T', ' ')?.take(16) ?: "暂无队列任务", color = WorkspaceMuted, fontSize = 12.sp)
            }
            selectedTask?.let { StatusChip(ocrStatusLabel(it.status), ocrStatusColor(it.status)) }
        }
        if (tasks.isEmpty()) {
            EmptyResultText("暂无 OCR 导入任务。")
        } else {
            tasks.forEach { task ->
                OcrTaskRow(
                    task = task,
                    selected = task.taskNo == selectedTask?.taskNo,
                    onClick = { onSelectTask(task.taskNo) },
                    onOpenReview = { onOpenReview(task.taskNo) },
                )
            }
        }
    }
}

@Composable
private fun UploadOption(
    icon: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(96.dp)
            .border(1.dp, WorkspaceBorder, RoundedCornerShape(8.dp))
            .background(WorkspaceSurfaceElevated, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(icon, color = PrimaryFixedDim, fontSize = 20.sp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = WorkspaceForeground, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(subtitle, color = WorkspaceMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SelectedOcrFileRow(file: OcrUploadFile, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, WorkspaceBorder, RoundedCornerShape(8.dp))
            .background(Color(0xFF191B23), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (file.contentType.contains("pdf")) "PDF" else "IMG", color = PrimaryFixedDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(file.name, color = WorkspaceForeground, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatBytes(file.sizeBytes), color = WorkspaceMuted, fontSize = 11.sp)
        }
        TextButton(onClick = onRemove) {
            Text("移除", color = SignalRed, fontSize = 12.sp)
        }
    }
}

@Composable
private fun OcrTaskRow(
    task: OcrTask,
    selected: Boolean,
    onClick: () -> Unit,
    onOpenReview: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (selected) PrimaryFixedDim.copy(alpha = 0.45f) else WorkspaceBorder, RoundedCornerShape(8.dp))
            .background(if (selected) CommandBlueSoft.copy(alpha = 0.42f) else Color(0xFF191B23), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(task.originalFilename, color = WorkspaceForeground, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(task.taskNo.ifBlank { "暂无任务编号" }, color = WorkspaceMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StatusChip(ocrStatusLabel(task.status), ocrStatusColor(task.status))
        }
        TinyBar(progress = task.progress / 100f)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${ocrStageLabel(task.currentStage)} · ${task.pageCount}页 · ${task.segmentCount}段", color = WorkspaceMuted, fontSize = 12.sp)
            if (task.needsReview) {
                TextButton(onClick = onOpenReview) {
                    Text("进入复核", color = PrimaryFixedDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (task.status == "failed" && task.errorMessage.isNotBlank()) {
            Text(task.errorMessage, color = SignalRed, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun OcrReviewSection(
    loading: Boolean,
    review: OcrReviewDetail,
    onBack: () -> Unit,
    onParagraphChange: (Int, String) -> Unit,
    onMoveParagraph: (Int, Int) -> Unit,
    onMergeParagraph: (Int) -> Unit,
    onCopyParagraph: (Int) -> Unit,
    onDeleteParagraph: (Int) -> Unit,
    onSaveDraft: () -> Unit,
    onSubmitReview: () -> Unit,
) {
    Panel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("OCR人工复核", color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(review.taskNo, color = WorkspaceMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onBack) {
                Text("返回队列", color = WorkspaceMuted, fontSize = 12.sp)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCell("段落", review.draftContent.paragraphs.size.toString(), PrimaryFixedDim, Modifier.weight(1f))
            MetricCell("警告", review.warningCount.toString(), SignalAmber, Modifier.weight(1f))
            MetricCell("置信度", "${(review.overallConfidence * 100).toInt()}%", confidenceColor(review.overallConfidence), Modifier.weight(1f))
        }
        Text("逐段校正识别文本，确认后提交入库处理。", color = WorkspaceMuted, fontSize = 12.sp, lineHeight = 18.sp)
    }
    review.draftContent.paragraphs.forEachIndexed { index, paragraph ->
        Panel {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("#${paragraph.paragraphNo.toString().padStart(2, '0')}", color = WorkspaceForeground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    StatusChip("第 ${paragraph.sourcePages.firstOrNull() ?: "-"} 页", PrimaryFixedDim)
                    Text("编辑中", color = WorkspaceMuted, fontSize = 12.sp, maxLines = 1)
                    Text("${paragraph.text.length}字", color = WorkspaceMuted, fontSize = 12.sp, maxLines = 1)
                }
                StatusChip("${(paragraph.avgConfidence * 100).toInt()}%", confidenceColor(paragraph.avgConfidence))
            }
            if (paragraph.sourcePages.isNotEmpty()) {
                Text("来源页：${paragraph.sourcePages.joinToString("、")}", color = WorkspaceMuted, fontSize = 12.sp)
            }
            FinanceTextField(
                label = "复核文本",
                value = paragraph.text,
                onValueChange = { onParagraphChange(paragraph.paragraphNo, it) },
                singleLine = false,
                minLines = 4,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )
            if (paragraph.warnings.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    paragraph.warnings.forEach { warning ->
                        StatusChip(warning.type, SignalAmber)
                    }
                }
            }
            OcrParagraphToolbar(
                canMoveUp = index > 0,
                canMoveDown = index < review.draftContent.paragraphs.lastIndex,
                canMerge = index < review.draftContent.paragraphs.lastIndex,
                canDelete = review.draftContent.paragraphs.size > 1,
                onMoveUp = { onMoveParagraph(paragraph.paragraphNo, -1) },
                onMoveDown = { onMoveParagraph(paragraph.paragraphNo, 1) },
                onMerge = { onMergeParagraph(paragraph.paragraphNo) },
                onCopy = { onCopyParagraph(paragraph.paragraphNo) },
                onDelete = { onDeleteParagraph(paragraph.paragraphNo) },
            )
        }
    }
    Panel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryActionButton(
                text = if (loading) "保存中" else "保存草稿",
                modifier = Modifier.weight(1f),
                enabled = !loading,
                onClick = onSaveDraft,
            )
            PrimaryActionButton(
                text = if (loading) "提交中" else "确认提交",
                modifier = Modifier.weight(1f),
                enabled = !loading,
                onClick = onSubmitReview,
            )
        }
    }
}

@Composable
private fun OcrParagraphToolbar(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canMerge: Boolean,
    canDelete: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMerge: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, WorkspaceBorder, RoundedCornerShape(8.dp))
            .background(Color(0xFF182C44), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarActionButton("↑", "上移", canMoveUp, PrimaryFixedDim, onMoveUp)
        ToolbarActionButton("↓", "下移", canMoveDown, PrimaryFixedDim, onMoveDown)
        ToolbarActionButton("合", "合并下一段", canMerge, PrimaryFixedDim, onMerge)
        ToolbarActionButton("⧉", "复制", true, PrimaryFixedDim, onCopy)
        ToolbarActionButton("删", "删除", canDelete, SignalRed, onDelete)
    }
}

@Composable
private fun ToolbarActionButton(
    symbol: String,
    label: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                symbol,
                color = if (enabled) color else WorkspaceMuted.copy(alpha = 0.38f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                label,
                color = if (enabled) WorkspaceMuted else WorkspaceMuted.copy(alpha = 0.38f),
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ManualImportSection(
    loading: Boolean,
    manual: ManualKnowledgeUiState,
    onTitleChange: (String) -> Unit,
    onChunkChange: (Int, String) -> Unit,
    onAddChunk: () -> Unit,
    onRemoveChunk: (Int) -> Unit,
    onNewDraft: () -> Unit,
    onSaveDraft: () -> Unit,
    onSubmitDraft: () -> Unit,
    onSelectTask: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
) {
    Panel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("手动知识导入", color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("按 chunk 录入文本，保存为草稿后可继续编辑，提交后进入场景打标和向量入库。", color = WorkspaceMuted, fontSize = 12.sp, lineHeight = 18.sp)
            }
            StatusChip(if (manual.readonly) "查看模式" else "${manual.validChunkCount} 条有效", if (manual.readonly) WorkspaceMuted else PrimaryFixedDim)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryActionButton(
                text = "新增草稿",
                modifier = Modifier.weight(1f),
                enabled = !loading,
                onClick = onNewDraft,
            )
            PrimaryActionButton(
                text = if (loading) "保存中" else "保存草稿",
                modifier = Modifier.weight(1f),
                enabled = !loading && manual.canSubmit,
                onClick = onSaveDraft,
            )
        }
        FinanceTextField(
            label = "标题",
            value = manual.title,
            onValueChange = { if (manual.canEdit) onTitleChange(it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Chunk 列表", color = WorkspaceForeground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("有效 ${manual.validChunkCount} / ${manual.chunks.size}", color = WorkspaceMuted, fontSize = 12.sp)
        }
        manual.chunks.forEachIndexed { index, chunk ->
            ManualChunkEditor(
                index = index,
                text = chunk,
                readonly = manual.readonly,
                canRemove = manual.canEdit && manual.chunks.size > 1,
                onChange = { onChunkChange(index, it) },
                onRemove = { onRemoveChunk(index) },
            )
        }
        if (manual.canEdit) {
            TextButton(onClick = onAddChunk, enabled = !loading) {
                Text("添加 Chunk", color = PrimaryFixedDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        PrimaryActionButton(
            text = if (loading) "提交中" else "提交入库",
            enabled = !loading && manual.canSubmit,
            onClick = onSubmitDraft,
        )
    }

    Panel {
        Text("手动导入概览", color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCell("草稿", manual.draftCount.toString(), SignalAmber, Modifier.weight(1f))
            MetricCell("处理中", manual.runningCount.toString(), PrimaryFixedDim, Modifier.weight(1f))
            MetricCell("已完成", manual.finishedCount.toString(), SignalGreen, Modifier.weight(1f))
        }
    }

    Panel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("手动导入队列", color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(manual.updatedAt.takeIf { it != "--:--" }?.let { "最近同步 $it" } ?: "暂无队列同步", color = WorkspaceMuted, fontSize = 12.sp)
            }
            manual.selectedTask?.let { StatusChip(ocrStatusLabel(it.status), ocrStatusColor(it.status)) }
        }
        if (manual.tasks.isEmpty()) {
            EmptyResultText("暂无手动导入任务。")
        } else {
            manual.tasks.forEach { task ->
                ManualTaskRow(
                    task = task,
                    selected = task.taskNo == manual.selectedTaskNo,
                    loading = loading,
                    onClick = { onSelectTask(task.taskNo) },
                    onOpen = { onOpenTask(task.taskNo) },
                    onDelete = { onDeleteTask(task.taskNo) },
                )
            }
        }
    }
}

@Composable
private fun ManualChunkEditor(
    index: Int,
    text: String,
    readonly: Boolean,
    canRemove: Boolean,
    onChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, WorkspaceBorder, RoundedCornerShape(8.dp))
            .background(Color(0xFF191B23), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Chunk ${index + 1}", color = WorkspaceForeground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (!readonly) {
                TextButton(onClick = onRemove, enabled = canRemove) {
                    Text("删除", color = if (canRemove) SignalRed else WorkspaceMuted.copy(alpha = 0.42f), fontSize = 12.sp)
                }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !readonly,
            singleLine = false,
            minLines = 5,
            placeholder = { Text("输入这一段 chunk 的文本", color = WorkspaceMuted, fontSize = 13.sp) },
            textStyle = TextStyle(color = WorkspaceForeground, fontSize = 13.sp, lineHeight = 20.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = WorkspaceForeground,
                unfocusedTextColor = WorkspaceForeground,
                disabledTextColor = WorkspaceOnSurface,
                focusedContainerColor = WorkspaceSurfaceElevated,
                unfocusedContainerColor = WorkspaceSurfaceElevated,
                disabledContainerColor = WorkspaceSurfaceElevated,
                focusedBorderColor = PrimaryFixedDim,
                unfocusedBorderColor = WorkspaceBorder,
                disabledBorderColor = WorkspaceBorder,
                cursorColor = PrimaryFixedDim,
            ),
            shape = RoundedCornerShape(8.dp),
        )
    }
}

@Composable
private fun ManualTaskRow(
    task: OcrTask,
    selected: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (selected) PrimaryFixedDim.copy(alpha = 0.45f) else WorkspaceBorder, RoundedCornerShape(8.dp))
            .background(if (selected) CommandBlueSoft.copy(alpha = 0.42f) else Color(0xFF191B23), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(task.originalFilename, color = WorkspaceForeground, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(task.taskNo.ifBlank { "暂无任务编号" }, color = WorkspaceMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StatusChip(ocrStatusLabel(task.status), ocrStatusColor(task.status))
        }
        TinyBar(progress = task.progress / 100f)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${ocrStageLabel(task.currentStage)} · ${task.segmentCount} 个Chunk", color = WorkspaceMuted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpen, enabled = !loading) {
                    Text(if (task.needsReview) "编辑" else "查看", color = PrimaryFixedDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDelete, enabled = !loading) {
                    Text("删除", color = SignalRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (task.status == "failed" && task.errorMessage.isNotBlank()) {
            Text(task.errorMessage, color = SignalRed, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun ResultSection(
    loading: Boolean,
    knowledge: KnowledgeMaterialUiState,
    onSceneFilterChange: (String) -> Unit,
    onTagFilterChange: (String) -> Unit,
    onSourceKeywordChange: (String) -> Unit,
    onResetFilters: () -> Unit,
) {
    Panel {
        val task = knowledge.activeTask
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("召回结果", color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(task?.title ?: "提交检索后展示知识库材料", color = WorkspaceMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (task != null) {
                StatusChip(materialStatusLabel(task.status), materialStatusColor(task.status))
            }
        }
        when {
            task == null -> EmptyResultText("还没有检索任务。")
            task.status == "failed" -> EmptyResultText(task.errorMessage.ifBlank { "材料检索失败。" }, SignalRed)
            task.status == "success" && knowledge.chunks.isEmpty() -> EmptyResultText("没有匹配到知识库材料。")
            knowledge.chunks.isEmpty() -> EmptyResultText(materialWaitingText(task, loading))
            else -> {
                ResultFilters(
                    knowledge = knowledge,
                    onSceneFilterChange = onSceneFilterChange,
                    onTagFilterChange = onTagFilterChange,
                    onSourceKeywordChange = onSourceKeywordChange,
                    onResetFilters = onResetFilters,
                )
                Text(
                    "当前展示 ${knowledge.visibleChunks.size} / ${knowledge.chunks.size} 条",
                    color = PrimaryFixedDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (knowledge.visibleChunks.isEmpty()) {
                    EmptyResultText("当前筛选条件下没有匹配材料。")
                } else {
                    knowledge.groupedVisibleChunks.forEach { (scene, chunks) ->
                        SceneChunkGroup(scene = scene, chunks = chunks)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultFilters(
    knowledge: KnowledgeMaterialUiState,
    onSceneFilterChange: (String) -> Unit,
    onTagFilterChange: (String) -> Unit,
    onSourceKeywordChange: (String) -> Unit,
    onResetFilters: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterRow(label = "场景") {
            FilterChip("全部", knowledge.sceneFilter.isBlank()) { onSceneFilterChange("") }
            knowledge.sceneOptions.forEach { (scene, count) ->
                FilterChip("${sceneLabel(scene)} $count", knowledge.sceneFilter == scene) {
                    onSceneFilterChange(scene)
                }
            }
        }
        FilterRow(label = "标签") {
            FilterChip("全部", knowledge.tagFilter.isBlank()) { onTagFilterChange("") }
            knowledge.tagOptions.forEach { (tag, count) ->
                FilterChip("${tagLabel(tag)} $count", knowledge.tagFilter == tag) {
                    onTagFilterChange(tag)
                }
            }
        }
        OutlinedTextField(
            value = knowledge.sourceKeyword,
            onValueChange = onSourceKeywordChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("按材料文件名筛选", color = WorkspaceMuted, fontSize = 13.sp) },
            leadingIcon = { Text("⌕", color = WorkspaceMuted, fontSize = 16.sp) },
            trailingIcon = {
                TextButton(onClick = onResetFilters) {
                    Text("重置", color = WorkspaceMuted, fontSize = 12.sp)
                }
            },
            textStyle = TextStyle(color = WorkspaceForeground, fontSize = 13.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = WorkspaceForeground,
                unfocusedTextColor = WorkspaceForeground,
                focusedContainerColor = Color(0xFF191B23),
                unfocusedContainerColor = Color(0xFF191B23),
                focusedBorderColor = PrimaryFixedDim,
                unfocusedBorderColor = WorkspaceBorder,
                cursorColor = PrimaryFixedDim,
            ),
            shape = RoundedCornerShape(8.dp),
        )
    }
}

@Composable
private fun SceneChunkGroup(scene: String, chunks: List<KnowledgeMaterialChunk>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(sceneLabel(scene), color = WorkspaceForeground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            StatusChip("${chunks.size} 条", WorkspaceMuted)
        }
        chunks.forEach { chunk ->
            ChunkCard(chunk)
        }
    }
}

@Composable
private fun ChunkCard(chunk: KnowledgeMaterialChunk) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, WorkspaceBorder),
        colors = CardDefaults.cardColors(containerColor = WorkspaceSurfaceElevated),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(chunk.filename.ifBlank { "知识库材料" }, color = WorkspaceForeground, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    chunk.chunkIndex?.let { index ->
                        Text("第 ${index} 段", color = WorkspaceMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                StatusChip(scoreText(chunk.finalScore ?: chunk.semanticScore), PrimaryFixedDim)
            }
            if (chunk.matchedTags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    chunk.matchedTags.forEach { tag ->
                        StatusChip(tagLabel(tag), WorkspaceMuted)
                    }
                }
            }
            Text(
                text = chunk.text.ifBlank { "该材料分块暂无正文。" },
                color = WorkspaceOnSurface,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun EmptyResultText(text: String, color: Color = WorkspaceMuted) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, WorkspaceBorder, RoundedCornerShape(8.dp))
            .background(Color(0xFF191B23), RoundedCornerShape(8.dp))
            .padding(14.dp),
    ) {
        Text(text, color = color, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun ReportTargetTypeSelector(
    selected: ReportTargetType,
    onSelected: (ReportTargetType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("标的类型", color = WorkspaceMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReportTargetType.entries.forEach { type ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .background(if (selected == type) CommandBlueSoft else Color(0xFF1D1F27), RoundedCornerShape(8.dp))
                        .border(1.dp, if (selected == type) PrimaryFixedDim.copy(alpha = 0.35f) else WorkspaceBorder, RoundedCornerShape(8.dp))
                        .clickable { onSelected(type) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(type.label, color = if (selected == type) PrimaryFixedDim else WorkspaceMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun TargetOptionDropdown(
    keyword: String,
    options: List<ReportTargetOption>,
    selectedCode: String,
    onKeywordChange: (String) -> Unit,
    onSelected: (ReportTargetOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("标的", color = WorkspaceMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = keyword,
                onValueChange = {
                    expanded = true
                    onKeywordChange(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) expanded = true },
                singleLine = true,
                placeholder = { Text("输入名称或代码", color = WorkspaceMuted, fontSize = 13.sp) },
                trailingIcon = { Text("⌄", color = WorkspaceMuted, fontSize = 18.sp) },
                textStyle = TextStyle(color = WorkspaceForeground, fontSize = 13.sp),
                colors = inputColors(),
                shape = RoundedCornerShape(8.dp),
            )
            DropdownMenu(expanded = expanded && options.isNotEmpty(), onDismissRequest = { expanded = false }, modifier = Modifier.background(WorkspaceSurfaceElevated)) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(option.targetName, color = WorkspaceForeground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(option.targetCode, color = WorkspaceMuted, fontSize = 11.sp)
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        },
                    )
                }
            }
        }
        if (keyword.isNotBlank() && selectedCode.isBlank() && options.isEmpty()) {
            Text("未找到可检索的标的。", color = WorkspaceMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ConfigProfileDropdown(
    profiles: List<ReportConfigProfileOption>,
    selectedProfileId: Long?,
    onSelected: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("配置模板", color = WorkspaceMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        SelectBox(
            text = selected?.let { "${it.configGroup} · ${it.name}" } ?: "暂无配置模板，使用系统默认值",
            enabled = profiles.isNotEmpty(),
            onClick = { expanded = true },
        ) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(WorkspaceSurfaceElevated)) {
                profiles.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text("${profile.configGroup} · ${profile.name}", color = WorkspaceForeground, fontSize = 13.sp) },
                        onClick = {
                            expanded = false
                            onSelected(profile.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportTypeDropdown(
    reportTypes: List<ReportTypeOption>,
    selectedCode: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = reportTypes.firstOrNull { it.code == selectedCode } ?: reportTypes.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("场景口径", color = WorkspaceMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        SelectBox(text = selected?.label ?: "暂无场景口径", enabled = reportTypes.isNotEmpty(), onClick = { expanded = true }) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(WorkspaceSurfaceElevated)) {
                reportTypes.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.label, color = WorkspaceForeground, fontSize = 13.sp) },
                        onClick = {
                            expanded = false
                            onSelected(type.code)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectBox(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    menu: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .border(1.dp, WorkspaceBorder, RoundedCornerShape(8.dp))
            .background(WorkspaceSurfaceElevated, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = if (enabled) WorkspaceForeground else WorkspaceMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("⌄", color = WorkspaceMuted, fontSize = 18.sp)
        }
        menu()
    }
}

@Composable
private fun RecallQuantityControls(
    form: KnowledgeMaterialFormState,
    onFormChange: (KnowledgeMaterialFormState) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, WorkspaceBorder, RoundedCornerShape(8.dp))
            .background(Color(0xFF191B23), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("召回范围", color = WorkspaceForeground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        NumberStepper("召回数量", form.totalChunks, 1, 1) { onFormChange(form.copy(totalChunks = it.coerceAtMost(50))) }
        NumberStepper("日线数", form.dailyKlineLimit, MIN_REPORT_DAILY_KLINE_LIMIT, 10) { onFormChange(form.copy(dailyKlineLimit = it)) }
        NumberStepper("周线数", form.weeklyKlineLimit, 1, 4) { onFormChange(form.copy(weeklyKlineLimit = it)) }
        NumberStepper("月线数", form.monthlyKlineLimit, 1, 4) { onFormChange(form.copy(monthlyKlineLimit = it)) }
    }
}

@Composable
private fun NumberStepper(
    label: String,
    value: Int,
    min: Int,
    step: Int,
    onValueChange: (Int) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = WorkspaceMuted, fontSize = 12.sp)
        Row(modifier = Modifier.height(34.dp).background(WorkspaceSurfaceMuted, RoundedCornerShape(6.dp)), verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", value > min) { onValueChange((value - step).coerceAtLeast(min)) }
            Text(value.toString(), modifier = Modifier.padding(horizontal = 14.dp), color = WorkspaceForeground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            StepButton("+", true) { onValueChange(value + step) }
        }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .background(if (enabled) Color(0xFF252832) else Color(0xFF20232B))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) WorkspaceMuted else WorkspaceBorder, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FilterRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = WorkspaceMuted, fontSize = 12.sp)
        content()
    }
}

@Composable
private fun FilterChip(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .background(if (active) CommandBlueSoft else Color(0xFF1D1F27), RoundedCornerShape(12.dp))
            .border(1.dp, if (active) PrimaryFixedDim.copy(alpha = 0.35f) else WorkspaceBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        color = if (active) PrimaryFixedDim else WorkspaceMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}

@Composable
private fun KnowledgeBottomNav(
    onWorkbenchSelected: () -> Unit,
    onMarketSelected: () -> Unit,
    onObservationSelected: () -> Unit,
    onReportSelected: () -> Unit,
) {
    NavigationBar(containerColor = Color(0xFF1D1F27), contentColor = WorkspaceMuted, tonalElevation = 0.dp, modifier = Modifier.navigationBarsPadding()) {
        listOf("工作台", "行情", "观察", "研究", "知识").forEach { item ->
            NavigationBarItem(
                selected = item == "知识",
                onClick = {
                    when (item) {
                        "工作台" -> onWorkbenchSelected()
                        "行情" -> onMarketSelected()
                        "观察" -> onObservationSelected()
                        "研究" -> onReportSelected()
                    }
                },
                icon = { Text(knowledgeNavIcon(item), fontSize = 17.sp) },
                label = { Text(item, fontSize = 12.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryFixedDim,
                    selectedTextColor = PrimaryFixedDim,
                    indicatorColor = CommandBlueSoft,
                    unselectedIconColor = WorkspaceMuted,
                    unselectedTextColor = WorkspaceMuted,
                ),
            )
        }
    }
}

@Composable
private fun inputColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = WorkspaceForeground,
    unfocusedTextColor = WorkspaceForeground,
    focusedContainerColor = WorkspaceSurfaceElevated,
    unfocusedContainerColor = WorkspaceSurfaceElevated,
    focusedBorderColor = PrimaryFixedDim,
    unfocusedBorderColor = WorkspaceBorder,
    cursorColor = PrimaryFixedDim,
)

private fun materialStatusLabel(status: String): String = when (status) {
    "success" -> "完成"
    "failed" -> "失败"
    "retrieving_knowledge" -> "检索中"
    "processing_current_scenes" -> "计算场景"
    "current_scenes_ready" -> "场景已计算"
    "pending" -> "等待中"
    else -> status.ifBlank { "暂无" }
}

private fun materialStatusColor(status: String): Color = when (status) {
    "success" -> SignalGreen
    "failed" -> SignalRed
    "retrieving_knowledge" -> PrimaryFixedDim
    "processing_current_scenes", "current_scenes_ready" -> SignalAmber
    else -> WorkspaceMuted
}

private fun materialWaitingText(task: KnowledgeMaterialTask, loading: Boolean): String = when (task.status) {
    "processing_current_scenes" -> "正在计算标的相关场景，场景完成后会自动召回材料。"
    "current_scenes_ready" -> "场景已计算完成，正在准备知识库召回。"
    "retrieving_knowledge" -> "正在检索知识库材料。"
    "pending" -> "任务已提交，等待后端处理。"
    else -> if (loading) "正在检索材料。" else "等待检索结果。"
}

private fun ocrStatusLabel(status: String): String = when (status) {
    "ready" -> "等待中"
    "running" -> "处理中"
    "manual_review_required" -> "待复核"
    "finished" -> "完成"
    "failed" -> "失败"
    else -> status.ifBlank { "暂无" }
}

private fun ocrStatusColor(status: String): Color = when (status) {
    "finished" -> SignalGreen
    "failed" -> SignalRed
    "manual_review_required" -> SignalAmber
    "running", "ready" -> PrimaryFixedDim
    else -> WorkspaceMuted
}

private fun ocrStageLabel(stage: String): String = when (stage) {
    "document.normalize" -> "格式校验"
    "ocr.recognize" -> "OCR识别"
    "text.clean" -> "文本清洗"
    "quality.validate" -> "质量校验"
    "chunk.tag", "chunk.tag.rule", "chunk.tag.llm", "chunk.tag.correct" -> "场景打标"
    "embedding.index" -> "向量入库"
    else -> stage.ifBlank { "待处理" }
}

private fun confidenceColor(value: Double): Color = when {
    value < 0.7 -> SignalRed
    value < 0.85 -> SignalAmber
    else -> SignalGreen
}

private fun formatBytes(sizeBytes: Long): String {
    val mb = sizeBytes / 1024.0 / 1024.0
    return if (mb >= 1.0) {
        "${DecimalFormat("0.0").format(mb)}MB"
    } else {
        "${(sizeBytes / 1024).coerceAtLeast(1)}KB"
    }
}

private fun sceneLabel(scene: String): String = when (scene) {
    "knowledge" -> "自然语言"
    "price" -> "价格"
    "risk_strategy" -> "风险策略"
    "sentiment" -> "情绪"
    "trend" -> "趋势"
    "valuation" -> "估值"
    "volume" -> "量能"
    "" -> "未分类"
    else -> "其他场景"
}

private fun tagLabel(tag: String): String = when (tag) {
    "general" -> "通用标的"
    "stock" -> "股票"
    "index" -> "指数"
    "convertible_bond" -> "可转债"
    "fund" -> "基金"
    "bank_stock" -> "银行股"
    "low_price_stock" -> "低价股"
    "large_cap_stock" -> "大盘股"
    "small_cap_stock" -> "小盘股"
    "price_rise" -> "价格上涨"
    "price_drop" -> "价格下跌"
    "sideways" -> "横盘震荡"
    "near_recent_high" -> "接近近期高位"
    "near_recent_low" -> "接近近期低位"
    "breakout" -> "价格突破"
    "break_recent_low" -> "跌破近期低位"
    "pullback" -> "回调"
    "gap_up" -> "跳空高开"
    "gap_down" -> "跳空低开"
    "convertible_high_price_risk" -> "转债高价风险"
    "convertible_low_price_defensive" -> "转债低价防御"
    "volume_expand" -> "放量"
    "volume_shrink" -> "缩量"
    "high_turnover" -> "高换手"
    "low_turnover" -> "低换手"
    "volume_price_confirm" -> "量价确认"
    "volume_price_divergence" -> "量价背离"
    "volume_spike" -> "成交量突增"
    "volume_dry_up" -> "成交萎缩"
    "uptrend" -> "上升趋势"
    "downtrend" -> "下降趋势"
    "range_bound" -> "区间震荡"
    "rebound" -> "反弹"
    "repair" -> "修复"
    "trend_reversal" -> "趋势反转"
    "breakout_from_range" -> "区间突破"
    "breakdown_from_range" -> "区间破位"
    "continuation" -> "趋势延续"
    "turn_weak" -> "转弱"
    "turn_strong" -> "转强"
    "failed_breakout" -> "突破失败"
    "low_pe" -> "低PE"
    "high_pe" -> "高PE"
    "low_pb" -> "低PB"
    "high_pb" -> "高PB"
    "high_dividend" -> "高股息"
    "valuation_repair" -> "估值修复"
    "valuation_trap" -> "估值陷阱"
    "fundamental_risk" -> "基本面风险"
    "convertible_low_premium" -> "转股低溢价"
    "convertible_high_premium" -> "转股高溢价"
    "convertible_premium_compression" -> "溢价压缩"
    "convertible_premium_expansion" -> "溢价扩张"
    "convertible_debt_floor_support" -> "债底支撑"
    "convertible_high_ytm" -> "到期收益率较高"
    "convertible_low_ytm" -> "到期收益率较低"
    "convertible_high_conversion_value" -> "转股价值较高"
    "market_attention_rise" -> "市场关注度提升"
    "short_term_emotion" -> "短线情绪"
    "panic_selling" -> "恐慌抛售"
    "news_driven" -> "消息驱动"
    "policy_driven" -> "政策驱动"
    "sector_rotation" -> "板块轮动"
    "weak_sentiment" -> "情绪偏弱"
    "herding_effect" -> "羊群效应"
    "institutional_behavior" -> "机构行为"
    "convertible_stock_linkage" -> "正股联动"
    "convertible_independent_strength" -> "转债独立走强"
    "chase_high_risk" -> "追高风险"
    "false_breakout_risk" -> "假突破风险"
    "liquidity_risk" -> "流动性风险"
    "drawdown_risk" -> "回撤风险"
    "valuation_trap_risk" -> "估值陷阱风险"
    "overheated_risk" -> "过热风险"
    "risk_control" -> "风险控制"
    "position_control" -> "仓位管理"
    "wait_confirm" -> "等待确认"
    "observe_next_day" -> "观察次日确认"
    "avoid_emotional_trade" -> "避免情绪化交易"
    "take_profit_plan" -> "止盈计划"
    "stop_loss_plan" -> "止损计划"
    "convertible_forced_redeem_risk" -> "强赎风险"
    "convertible_putback_risk" -> "回售相关风险"
    "convertible_low_rating_risk" -> "低评级风险"
    "convertible_small_balance_risk" -> "剩余规模过小风险"
    "convertible_liquidity_risk" -> "转债流动性风险"
    else -> "其他标签"
}

private fun scoreText(score: Double?): String {
    if (score == null) return "--"
    return "${DecimalFormat("0.0").format(score * 100)}%"
}

private fun knowledgeNavIcon(item: String): String = when (item) {
    "工作台" -> "▦"
    "行情" -> "⌁"
    "观察" -> "◉"
    "研究" -> "▤"
    "知识" -> "▣"
    else -> "●"
}
