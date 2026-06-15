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
import com.scrapider.finance.androidapp.data.MIN_REPORT_DAILY_KLINE_LIMIT
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
            KnowledgeSegmentedTabs()
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
private fun KnowledgeSegmentedTabs() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WorkspaceSurfaceMuted, RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf("材料", "OCR导入", "手动导入").forEach { item ->
            val active = item == "材料"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .background(if (active) WorkspaceSurfaceElevated else Color.Transparent, RoundedCornerShape(6.dp))
                    .border(
                        width = if (active) 1.dp else 0.dp,
                        color = if (active) WorkspaceBorder else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item,
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
