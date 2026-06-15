package com.scrapider.finance.androidapp.ui

import android.text.method.LinkMovementMethod
import android.widget.TextView
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrapider.finance.androidapp.data.CreateReportFormState
import com.scrapider.finance.androidapp.data.MIN_REPORT_DAILY_KLINE_LIMIT
import com.scrapider.finance.androidapp.data.ReportConfigProfileOption
import com.scrapider.finance.androidapp.data.ReportDetail
import com.scrapider.finance.androidapp.data.ReportResearchUiState
import com.scrapider.finance.androidapp.data.ReportStatusFilter
import com.scrapider.finance.androidapp.data.ReportTargetOption
import com.scrapider.finance.androidapp.data.ReportTargetSummary
import com.scrapider.finance.androidapp.data.ReportTargetType
import com.scrapider.finance.androidapp.data.ReportTypeOption
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportResearchScreen(
    loading: Boolean,
    report: ReportResearchUiState,
    onRefresh: () -> Unit,
    onTargetTypeChange: (ReportTargetType) -> Unit,
    onStatusFilterChange: (ReportStatusFilter) -> Unit,
    onKeywordChange: (String) -> Unit,
    onReportSelected: (ReportTargetSummary) -> Unit,
    onRegenerate: (String) -> Unit,
    onDismissDetailSheet: () -> Unit,
    onOpenCreateSheet: () -> Unit,
    onDismissCreateSheet: () -> Unit,
    onCreateFormChange: (CreateReportFormState) -> Unit,
    onCreateTargetTypeChange: (ReportTargetType) -> Unit,
    onCreateTargetKeywordChange: (String) -> Unit,
    onCreateTargetSelected: (ReportTargetOption) -> Unit,
    onCreateProfileSelected: (Long) -> Unit,
    onSubmitCreateReport: () -> Unit,
    onWorkbenchSelected: () -> Unit,
    onMarketSelected: () -> Unit,
    onObservationSelected: () -> Unit,
) {
    Scaffold(
        containerColor = WorkspaceBackground,
        topBar = {
            ReportTopBar(
                updatedAt = report.updatedAt,
                loading = loading,
                onRefresh = onRefresh,
                onAdd = onOpenCreateSheet,
            )
        },
        bottomBar = {
            ReportBottomNav(
                onWorkbenchSelected = onWorkbenchSelected,
                onMarketSelected = onMarketSelected,
                onObservationSelected = onObservationSelected,
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ReportTargetTypeFilters(
                selected = report.targetType,
                onSelected = onTargetTypeChange,
            )
            ReportStatusFilters(
                selected = report.statusFilter,
                onSelected = onStatusFilterChange,
            )
            ReportSearchBar(
                keyword = report.keyword,
                onKeywordChange = onKeywordChange,
            )
            ReportList(
                loading = loading,
                reports = report.visibleTargets,
                onReportSelected = onReportSelected,
                onRegenerate = onRegenerate,
            )
            Spacer(Modifier.height(72.dp))
        }
    }

    report.selectedDetail?.let { detail ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissDetailSheet,
            sheetState = sheetState,
            containerColor = WorkspaceSurface,
            contentColor = WorkspaceForeground,
            dragHandle = { SheetHandle() },
        ) {
            ReportDetailSheet(
                detail = detail,
                loading = loading,
                onRegenerate = onRegenerate,
                onDismiss = onDismissDetailSheet,
            )
        }
    }

    if (report.showCreateSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissCreateSheet,
            sheetState = sheetState,
            containerColor = WorkspaceSurface,
            contentColor = WorkspaceForeground,
            dragHandle = { SheetHandle() },
        ) {
            CreateReportSheet(
                loading = loading,
                report = report,
                onDismiss = onDismissCreateSheet,
                onFormChange = onCreateFormChange,
                onTargetTypeChange = onCreateTargetTypeChange,
                onTargetKeywordChange = onCreateTargetKeywordChange,
                onTargetSelected = onCreateTargetSelected,
                onProfileSelected = onCreateProfileSelected,
                onSubmit = onSubmitCreateReport,
            )
        }
    }
}

@Composable
private fun ReportTopBar(
    updatedAt: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(WorkspaceSurface)
            .border(BorderStroke(1.dp, WorkspaceBorder.copy(alpha = 0.65f)))
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("▤", color = PrimaryFixedDim, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("报告研究", color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("$updatedAt 更新", color = WorkspaceMuted, fontSize = 11.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRefresh, enabled = !loading) {
                Text(if (loading) "同步中" else "↻", color = WorkspaceMuted, fontSize = 14.sp)
            }
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .background(CommandBlue, RoundedCornerShape(8.dp))
                    .clickable(enabled = !loading) { onAdd() }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("+ 新建", color = Color(0xFFF4F5FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ReportTargetTypeFilters(
    selected: ReportTargetType,
    onSelected: (ReportTargetType) -> Unit,
) {
    FilterRow(label = "标的类型") {
        ReportTargetType.entries.forEach { type ->
            FilterChip(
                text = type.label,
                active = selected == type,
                onClick = { onSelected(type) },
            )
        }
    }
}

@Composable
private fun ReportStatusFilters(
    selected: ReportStatusFilter,
    onSelected: (ReportStatusFilter) -> Unit,
) {
    FilterRow(label = "报告状态") {
        ReportStatusFilter.entries.forEach { status ->
            FilterChip(
                text = status.label,
                active = selected == status,
                onClick = { onSelected(status) },
            )
        }
    }
}

@Composable
private fun FilterRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = WorkspaceMuted, fontSize = 12.sp, modifier = Modifier.padding(end = 2.dp))
        content()
    }
}

@Composable
private fun FilterChip(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
) {
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
private fun ReportSearchBar(
    keyword: String,
    onKeywordChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = keyword,
        onValueChange = onKeywordChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("搜索报告标的名称或代码", color = WorkspaceMuted, fontSize = 13.sp) },
        leadingIcon = { Text("⌕", color = WorkspaceMuted, fontSize = 16.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(color = WorkspaceForeground, fontSize = 13.sp),
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

@Composable
private fun ReportList(
    loading: Boolean,
    reports: List<ReportTargetSummary>,
    onReportSelected: (ReportTargetSummary) -> Unit,
    onRegenerate: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        when {
            loading && reports.isEmpty() -> EmptyReportCard("正在同步报告列表。")
            reports.isEmpty() -> EmptyReportCard("暂无匹配报告。点击新建可提交新的研究报告任务。")
            else -> reports.forEach { item ->
                ReportCard(
                    report = item,
                    loading = loading,
                    onReportSelected = { onReportSelected(item) },
                    onRegenerate = { onRegenerate(item.latestTaskNo) },
                )
            }
        }
    }
}

@Composable
private fun EmptyReportCard(text: String) {
    Panel {
        Text(text, color = WorkspaceMuted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun ReportCard(
    report: ReportTargetSummary,
    loading: Boolean,
    onReportSelected: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val statusColor = reportStatusColor(report.latestStatus)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, WorkspaceBorder),
        colors = CardDefaults.cardColors(containerColor = WorkspaceSurface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = report.latestReportId != null && !report.latestStatus.isGeneratingReportStatus()) {
                    onReportSelected()
                }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(report.targetName, color = WorkspaceForeground, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(report.targetCode, color = WorkspaceMuted, fontSize = 12.sp, maxLines = 1)
                    }
                    Text("最新版本: V${report.latestVersionNo ?: 0} · 累计生成 ${report.reportCount} 次", color = WorkspaceMuted, fontSize = 12.sp)
                }
                StatusChip(statusLabel(report.latestStatus), statusColor)
            }
            val preview = reportPreviewText(report)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (report.latestStatus == "failed") SignalRed.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(6.dp))
                    .border(
                        width = if (report.latestStatus == "failed") 1.dp else 0.dp,
                        color = if (report.latestStatus == "failed") SignalRed.copy(alpha = 0.18f) else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .padding(if (report.latestStatus == "failed") 8.dp else 0.dp),
            ) {
                Text(
                    text = preview,
                    color = if (report.latestStatus == "failed") SignalRed else WorkspaceMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DividerLine()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(reportTime(report), color = WorkspaceMuted, fontSize = 12.sp)
                when {
                    report.latestStatus == "failed" -> TextButton(onClick = onRegenerate, enabled = !loading && report.latestTaskNo.isNotBlank()) {
                        Text("重新生成 ↻", color = PrimaryFixedDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    report.latestStatus.isGeneratingReportStatus() -> Text("生成中", color = WorkspaceMuted, fontSize = 12.sp)
                    else -> TextButton(onClick = onReportSelected, enabled = report.latestReportId != null) {
                        Text("查看详情 ›", color = PrimaryFixedDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (report.latestStatus.isGeneratingReportStatus()) {
                TinyBar(0.45f)
            }
        }
    }
}

@Composable
private fun ReportDetailSheet(
    detail: ReportDetail,
    loading: Boolean,
    onRegenerate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${detail.targetName} 分析报告", color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${detail.targetType.label} ${detail.targetCode} · V${detail.versionNo}", color = WorkspaceMuted, fontSize = 12.sp)
            }
            StatusChip(statusLabel(detail.status), reportStatusColor(detail.status))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DetailMeta("类型", reportTypeLabel(detail.reportType), Modifier.weight(1f))
            DetailMeta("模型", detail.model.ifBlank { "-" }, Modifier.weight(1f))
        }
        DetailMeta("生成时间", formatDateTime(detail.generatedAt.ifBlank { detail.createdAt }), Modifier.fillMaxWidth())
        DividerLine()
        val bodyText = when {
            detail.status == "failed" -> detail.errorMessage.ifBlank { "报告生成失败，可重新生成。" }
            detail.reportText.isBlank() -> "暂无报告正文。"
            else -> detail.reportText
        }
        if (detail.status == "failed") {
            Text(
                text = bodyText,
                color = SignalRed,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        } else {
            MarkdownReportText(markdown = bodyText)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { onRegenerate(detail.taskNo) },
                enabled = !loading && detail.taskNo.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text("重新生成", color = PrimaryFixedDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("关闭", color = WorkspaceMuted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun MarkdownReportText(markdown: String) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .build()
    }
    val textColor = WorkspaceOnSurface.toArgb()
    val linkColor = PrimaryFixedDim.toArgb()
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { viewContext ->
            TextView(viewContext).apply {
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                textSize = 13f
                setLineSpacing(0f, 1.2f)
                includeFontPadding = false
                linksClickable = true
                movementMethod = LinkMovementMethod.getInstance()
                setTextIsSelectable(true)
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.setLinkTextColor(linkColor)
            markwon.setMarkdown(textView, markdown)
        },
    )
}

@Composable
private fun DetailMeta(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .border(1.dp, WorkspaceBorder, RoundedCornerShape(6.dp))
            .background(Color(0xFF191B23), RoundedCornerShape(6.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = WorkspaceMuted, fontSize = 12.sp)
        Text(value, color = WorkspaceForeground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CreateReportSheet(
    loading: Boolean,
    report: ReportResearchUiState,
    onDismiss: () -> Unit,
    onFormChange: (CreateReportFormState) -> Unit,
    onTargetTypeChange: (ReportTargetType) -> Unit,
    onTargetKeywordChange: (String) -> Unit,
    onTargetSelected: (ReportTargetOption) -> Unit,
    onProfileSelected: (Long) -> Unit,
    onSubmit: () -> Unit,
) {
    val form = report.createForm
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("新建报告任务", color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onDismiss) {
                Text("取消", color = WorkspaceMuted, fontSize = 13.sp)
            }
        }
        ReportTargetTypeSelector(selected = form.targetType, onSelected = onTargetTypeChange)
        TargetOptionDropdown(
            keyword = form.targetKeyword,
            options = report.targetOptions.filter { it.matches(form.targetKeyword) }.take(8),
            selectedCode = form.targetCode,
            onKeywordChange = onTargetKeywordChange,
            onSelected = onTargetSelected,
        )
        ProfileDropdown(
            profiles = report.configProfiles.filterProfileOptions(form.targetType),
            selectedProfileId = form.configProfileId,
            onSelected = onProfileSelected,
        )
        ReportTypeDropdown(
            reportTypes = report.reportTypes,
            selectedCode = form.reportType,
            onSelected = { onFormChange(form.copy(reportType = it)) },
        )
        ReportQuantityControls(form = form, onFormChange = onFormChange)
        PrimaryActionButton(
            text = if (loading) "提交中" else "创建报告任务",
            enabled = !loading && form.canSubmit,
            onClick = onSubmit,
        )
        Spacer(Modifier.height(12.dp))
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
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            expanded = true
                        }
                    }
                    .clickable { expanded = true },
                singleLine = true,
                placeholder = { Text("输入名称、代码或 secid 搜索", color = WorkspaceMuted, fontSize = 13.sp) },
                trailingIcon = { Text("⌄", color = WorkspaceMuted, fontSize = 18.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(color = WorkspaceForeground, fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = WorkspaceForeground,
                    unfocusedTextColor = WorkspaceForeground,
                    focusedContainerColor = WorkspaceSurfaceElevated,
                    unfocusedContainerColor = WorkspaceSurfaceElevated,
                    focusedBorderColor = PrimaryFixedDim,
                    unfocusedBorderColor = WorkspaceBorder,
                    cursorColor = PrimaryFixedDim,
                ),
                shape = RoundedCornerShape(8.dp),
            )
            DropdownMenu(
                expanded = expanded && options.isNotEmpty(),
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WorkspaceSurfaceElevated),
            ) {
                options.forEach { option ->
                    val selected = option.targetCode == selectedCode
                    DropdownMenuItem(
                        text = {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(option.targetName, color = WorkspaceForeground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(option.targetCode, color = WorkspaceMuted, fontSize = 11.sp)
                                }
                                Text(if (selected) "已选" else option.marketCode.ifBlank { option.targetType.label }, color = if (selected) PrimaryFixedDim else WorkspaceMuted, fontSize = 12.sp)
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
            Text("未找到可生成报告的标的。", color = WorkspaceMuted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun ProfileDropdown(
    profiles: List<ReportConfigProfileOption>,
    selectedProfileId: Long?,
    onSelected: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("配置模板", color = WorkspaceMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .border(1.dp, WorkspaceBorder, RoundedCornerShape(8.dp))
                .background(WorkspaceSurfaceElevated, RoundedCornerShape(8.dp))
                .clickable(enabled = profiles.isNotEmpty()) { expanded = true }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selected?.name ?: "暂无配置模板，使用系统默认值",
                    color = if (selected == null) WorkspaceMuted else WorkspaceForeground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("⌄", color = WorkspaceMuted, fontSize = 18.sp)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(WorkspaceSurfaceElevated),
            ) {
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
        Text("报告类型", color = WorkspaceMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .border(1.dp, WorkspaceBorder, RoundedCornerShape(8.dp))
                .background(WorkspaceSurfaceElevated, RoundedCornerShape(8.dp))
                .clickable(enabled = reportTypes.isNotEmpty()) { expanded = true }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selected?.label ?: "暂无报告类型",
                    color = if (selected == null) WorkspaceMuted else WorkspaceForeground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("⌄", color = WorkspaceMuted, fontSize = 18.sp)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(WorkspaceSurfaceElevated),
            ) {
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
private fun ReportQuantityControls(
    form: CreateReportFormState,
    onFormChange: (CreateReportFormState) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, WorkspaceBorder, RoundedCornerShape(8.dp))
            .background(Color(0xFF191B23), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("检索范围", color = WorkspaceForeground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        NumberStepper(
            label = "检索材料",
            value = form.totalChunks,
            min = 1,
            step = 1,
            onValueChange = { onFormChange(form.copy(totalChunks = it)) },
        )
        NumberStepper(
            label = "日线数量",
            value = form.dailyKlineLimit,
            min = MIN_REPORT_DAILY_KLINE_LIMIT,
            step = 10,
            onValueChange = { onFormChange(form.copy(dailyKlineLimit = it)) },
        )
        NumberStepper(
            label = "周线数量",
            value = form.weeklyKlineLimit,
            min = 1,
            step = 4,
            onValueChange = { onFormChange(form.copy(weeklyKlineLimit = it)) },
        )
        NumberStepper(
            label = "月线数量",
            value = form.monthlyKlineLimit,
            min = 1,
            step = 4,
            onValueChange = { onFormChange(form.copy(monthlyKlineLimit = it)) },
        )
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = WorkspaceMuted, fontSize = 12.sp)
        Row(
            modifier = Modifier
                .height(34.dp)
                .background(WorkspaceSurfaceMuted, RoundedCornerShape(6.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(text = "−", enabled = value > min, onClick = { onValueChange((value - step).coerceAtLeast(min)) })
            Text(
                value.toString(),
                modifier = Modifier.padding(horizontal = 14.dp),
                color = WorkspaceForeground,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            StepButton(text = "+", enabled = true, onClick = { onValueChange(value + step) })
        }
    }
}

@Composable
private fun StepButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
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
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .padding(top = 8.dp, bottom = 4.dp)
            .height(4.dp)
            .fillMaxWidth(0.12f)
            .background(WorkspaceSurfaceMuted, RoundedCornerShape(2.dp)),
    )
}

@Composable
private fun ReportBottomNav(
    onWorkbenchSelected: () -> Unit,
    onMarketSelected: () -> Unit,
    onObservationSelected: () -> Unit,
) {
    NavigationBar(containerColor = Color(0xFF1D1F27), contentColor = WorkspaceMuted, tonalElevation = 0.dp) {
        listOf("工作台", "行情", "观察", "研究", "我的").forEach { item ->
            NavigationBarItem(
                selected = item == "研究",
                onClick = {
                    when (item) {
                        "工作台" -> onWorkbenchSelected()
                        "行情" -> onMarketSelected()
                        "观察" -> onObservationSelected()
                    }
                },
                icon = { Text(reportNavIcon(item), fontSize = 17.sp) },
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

private fun reportNavIcon(item: String): String = when (item) {
    "工作台" -> "▦"
    "行情" -> "⌁"
    "观察" -> "◉"
    "研究" -> "▤"
    else -> "●"
}

private fun List<ReportConfigProfileOption>.filterProfileOptions(targetType: ReportTargetType): List<ReportConfigProfileOption> {
    val filtered = filter { it.targetType == null || it.targetType == targetType }
    return filtered.ifEmpty { this }
}

private fun reportStatusColor(status: String): Color = when {
    status == "success" -> SignalGreen
    status == "failed" -> SignalRed
    status.isGeneratingReportStatus() -> PrimaryFixedDim
    else -> WorkspaceMuted
}

private fun statusLabel(status: String): String = when {
    status == "success" -> "成功"
    status == "failed" -> "失败"
    status.isGeneratingReportStatus() -> "生成中"
    else -> "暂无"
}

private fun reportTypeLabel(type: String): String = when (type) {
    "quick_analysis" -> "快速分析"
    "valuation_report" -> "估值报告"
    else -> type.ifBlank { "-" }
}

private fun reportPreviewText(report: ReportTargetSummary): String = when {
    report.latestReportPreview.isNotBlank() -> report.latestReportPreview
    report.latestStatus == "failed" -> "报告生成失败，保留上下文后可重新生成。"
    report.latestStatus.isGeneratingReportStatus() -> "智能研究助手正在聚合市场、知识库和历史报告。"
    report.latestStatus == "success" -> "报告已生成，可查看正文、引用证据和风险判断。"
    else -> "暂无报告摘要。"
}

private fun reportTime(report: ReportTargetSummary): String =
    formatDateTime(report.latestGeneratedAt.ifBlank { report.latestCreatedAt })

private fun formatDateTime(value: String): String =
    value.replace('T', ' ').take(16).ifBlank { "-" }

private fun String.isGeneratingReportStatus(): Boolean = this in setOf(
    "pending",
    "processing_current_scenes",
    "current_scenes_ready",
    "retrieving_knowledge",
    "generating_report",
)
