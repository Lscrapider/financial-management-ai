package com.scrapider.finance.androidapp.feature.workbench.knowledge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.scrapider.finance.androidapp.R
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.designsystem.rememberFinanceSignalColors
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportTargetOption
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportTopBar
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportChoice
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportEmptyState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportErrorState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportInfoState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportLoadingState
import com.scrapider.finance.androidapp.feature.workbench.reports.reportTargetTypes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KnowledgeScreen(
    state: KnowledgeUiState,
    targetTypeOptions: List<ReportChoice>,
    onBack: () -> Unit,
    onModeSelected: (KnowledgeSearchMode) -> Unit,
    onQueryTextChanged: (String) -> Unit,
    onProfileSelected: (Long) -> Unit,
    onTargetTypeSelected: (String) -> Unit,
    onTargetQueryChanged: (String) -> Unit,
    onSearchTargets: () -> Unit,
    onTargetSelected: (ReportTargetOption) -> Unit,
    onSubmit: () -> Unit,
    onRetryMetadata: () -> Unit,
    onRetryTargetSearch: () -> Unit,
    onRefreshTask: () -> Unit,
    onRetryAfterUnconfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheet by remember { mutableStateOf<KnowledgeSheet?>(null) }
    var selectedChunk by remember(state.task?.taskNo) { mutableStateOf<KnowledgeChunk?>(null) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val openSheet: (KnowledgeSheet) -> Unit = {
        keyboard?.hide()
        focus.clearFocus()
        sheet = it
    }
    var showUnconfirmedDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val spacing = LocalFinanceSpacing.current

    LaunchedEffect(state.task?.taskNo) {
        if (state.task != null) {
            listState.animateScrollToItem(knowledgeResultHeadingIndex(state))
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        // Route 已统一消费外层系统栏与 IME，页面只提供自己的固定提交栏。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ReportTopBar(
                title = "知识检索",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = onRefreshTask,
                        enabled = state.task != null && !state.isLoadingTask,
                        modifier = Modifier.semantics {
                            contentDescription = if (state.isLoadingTask) "正在刷新材料任务" else "刷新材料任务"
                        },
                    ) {
                        if (state.isLoadingTask) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(LocalFinanceDimensions.current.iconSize),
                                strokeWidth = LocalFinanceDimensions.current.outlineWidth,
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_phosphor_arrows_clockwise),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            KnowledgeSubmitBar(
                isSubmitting = state.isSubmitting,
                submissionUnconfirmed = state.submissionUnconfirmed,
                enabled = state.canSubmit || state.submissionUnconfirmed,
                onSubmit = {
                    if (state.submissionUnconfirmed) {
                        showUnconfirmedDialog = true
                    } else {
                        onSubmit()
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            when {
                state.isLoadingMetadata && state.profiles.isEmpty() -> ReportLoadingState(
                    text = "正在加载知识检索配置",
                    modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.lg),
                )

                state.errorMessage.isNotBlank() && state.profiles.isEmpty() -> ReportErrorState(
                    text = state.errorMessage,
                    onRetry = onRetryMetadata,
                    modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.lg),
                )

                else -> KnowledgeContent(
                    state = state,
                    listState = listState,
                    targetTypeOptions = targetTypeOptions,
                    onOpenModeSheet = { openSheet(KnowledgeSheet.Mode) },
                    onOpenProfileSheet = { openSheet(KnowledgeSheet.Profile) },
                    onOpenTargetTypeSheet = { openSheet(KnowledgeSheet.TargetType) },
                    onQueryTextChanged = onQueryTextChanged,
                    onTargetQueryChanged = onTargetQueryChanged,
                    onSearchTargets = onSearchTargets,
                    onTargetSelected = onTargetSelected,
                    onRetryMetadata = onRetryMetadata,
                    onRetryTargetSearch = onRetryTargetSearch,
                    onRefreshTask = onRefreshTask,
                    onOpenChunk = { keyboard?.hide(); focus.clearFocus(); selectedChunk = it },
                )
            }
        }
    }

    selectedChunk?.let { chunk -> KnowledgeChunkReader(chunk, onClose = { selectedChunk = null }) }
    when (sheet) {
        KnowledgeSheet.Mode -> KnowledgeModeSheet(
            selectedMode = state.searchMode,
            onDismiss = { sheet = null },
            onSelected = { mode ->
                sheet = null
                onModeSelected(mode)
            },
        )

        KnowledgeSheet.Profile -> KnowledgeProfileSheet(
            profiles = state.profiles,
            selectedId = state.selectedProfileId,
            onDismiss = { sheet = null },
            onSelected = { id ->
                sheet = null
                onProfileSelected(id)
            },
        )

        KnowledgeSheet.TargetType -> KnowledgeChoiceSheet(
            title = "标的类型",
            choices = targetTypeOptions,
            selectedValue = state.targetType,
            onDismiss = { sheet = null },
            onSelected = { value ->
                sheet = null
                onTargetTypeSelected(value)
            },
        )

        null -> Unit
    }

    if (showUnconfirmedDialog) {
        AlertDialog(
            onDismissRequest = { showUnconfirmedDialog = false },
            title = { Text("提交结果未确认") },
            text = {
                Text("上一次请求可能已经创建检索任务，重新检索可能产生重复任务。确认后将再次提交。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnconfirmedDialog = false
                        onRetryAfterUnconfirmed()
                    },
                ) {
                    Text("确认再次检索")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnconfirmedDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun KnowledgeContent(
    state: KnowledgeUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    targetTypeOptions: List<ReportChoice>,
    onOpenModeSheet: () -> Unit,
    onOpenProfileSheet: () -> Unit,
    onOpenTargetTypeSheet: () -> Unit,
    onQueryTextChanged: (String) -> Unit,
    onTargetQueryChanged: (String) -> Unit,
    onSearchTargets: () -> Unit,
    onTargetSelected: (ReportTargetOption) -> Unit,
    onRetryMetadata: () -> Unit,
    onRetryTargetSearch: () -> Unit,
    onRefreshTask: () -> Unit,
    onOpenChunk: (KnowledgeChunk) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val profile = state.selectedProfile
    val interactionEnabled = !state.isSubmitting && !state.submissionUnconfirmed
    val targetTypeLabel = targetTypeOptions.firstOrNull { it.value == state.targetType }?.label.orEmpty()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.xl,
            top = spacing.md,
            end = spacing.xl,
            bottom = spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        item(key = "knowledge-intro", contentType = "intro") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = "知识检索",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "按标的或自然语言查找原文片段，用于阅读和核对研究依据。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = rememberFinanceSignalColors().onNeutralContainer,
                )
            }
        }

        if (state.errorMessage.isNotBlank() && state.profiles.isNotEmpty()) {
            item(key = "knowledge-error", contentType = "error") {
                KnowledgeInlineError(text = state.errorMessage)
            }
        }

        item(key = "knowledge-mode", contentType = "selection") {
            KnowledgeFormSection(label = "检索方式") {
                KnowledgeSelectionButton(
                    value = state.searchMode.label,
                    supportingText = if (state.searchMode == KnowledgeSearchMode.Target) {
                        "选择标的后检索相关材料"
                    } else {
                        "用一句话描述想查找的内容"
                    },
                    onClick = onOpenModeSheet,
                    enabled = interactionEnabled,
                )
            }
        }

        item(key = "knowledge-profile", contentType = "selection") {
            KnowledgeFormSection(label = "检索配置") {
                KnowledgeSelectionButton(
                    value = profile?.name ?: "请选择配置方案",
                    supportingText = when {
                        profile == null -> "召回数量沿用服务端配置"
                        profile.group.isNotBlank() && profile.totalChunks != null -> {
                            "${profile.group} · 召回 ${profile.totalChunks} 条"
                        }
                        profile.totalChunks != null -> "召回 ${profile.totalChunks} 条"
                        else -> "该方案未提供召回数量"
                    },
                    onClick = onOpenProfileSheet,
                    enabled = interactionEnabled && state.profiles.isNotEmpty(),
                )
                if (state.profiles.isEmpty() && !state.isLoadingMetadata) {
                    ReportEmptyState(text = "暂无可用检索配置")
                    if (state.errorMessage.isBlank()) {
                        TextButton(
                            onClick = onRetryMetadata,
                            modifier = Modifier.heightIn(min = LocalFinanceDimensions.current.minTouchTarget),
                        ) {
                            Text("重新加载")
                        }
                    }
                }
            }
        }

        if (state.searchMode == KnowledgeSearchMode.Target) {
            item(key = "knowledge-target-type", contentType = "selection") {
                KnowledgeFormSection(label = "标的类型") {
                    KnowledgeSelectionButton(
                        value = targetTypeLabel.ifBlank { "请选择标的类型" },
                        supportingText = if (targetTypeLabel.isBlank()) {
                            "限定搜索范围后再查找标的"
                        } else {
                            "搜索将限定在此类型内"
                        },
                        onClick = onOpenTargetTypeSheet,
                        enabled = interactionEnabled && targetTypeOptions.isNotEmpty(),
                    )
                    if (targetTypeOptions.isEmpty()) {
                        ReportEmptyState(text = "暂无可用标的类型")
                    }
                }
            }

            item(key = "knowledge-target-search", contentType = "target-search") {
                KnowledgeFormSection(label = "选择标的") {
                    if (state.targetType.isBlank()) {
                        ReportInfoState(text = "先选择标的类型，再搜索名称或代码")
                    } else {
                        KnowledgeTargetSearchField(
                            query = state.targetQuery,
                            isSearching = state.isSearchingTargets,
                            enabled = interactionEnabled,
                            onQueryChange = onTargetQueryChanged,
                            onSearch = onSearchTargets,
                        )
                        Spacer(Modifier.heightIn(min = spacing.xs))
                        when {
                            state.targetSearchError.isNotBlank() -> ReportErrorState(
                                text = state.targetSearchError,
                                onRetry = onRetryTargetSearch,
                            )
                            state.isSearchingTargets -> ReportLoadingState(text = "正在搜索标的")
                            state.hasSearchedTargets && state.targetOptions.isEmpty() -> ReportEmptyState(
                                text = "没有找到匹配标的，换个名称或代码试试",
                            )
                            !state.hasSearchedTargets && state.selectedTarget == null -> Text(
                                text = "输入名称或代码后点击搜索",
                                style = MaterialTheme.typography.bodySmall,
                                color = rememberFinanceSignalColors().onNeutralContainer,
                            )
                        }

                        state.selectedTarget?.let { target ->
                            Spacer(Modifier.heightIn(min = spacing.sm))
                            KnowledgeSelectedTarget(target = target)
                        }
                        if (state.targetOptions.isNotEmpty()) {
                            Spacer(Modifier.heightIn(min = spacing.md))
                            Text(
                                text = "搜索结果",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.heightIn(min = spacing.xs))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                            ) {
                                state.targetOptions.forEach { target ->
                                    KnowledgeTargetRow(
                                        target = target,
                                        selected = target.key == state.selectedTarget?.key,
                                        enabled = interactionEnabled,
                                        onClick = { onTargetSelected(target) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item(key = "knowledge-query", contentType = "query") {
                KnowledgeFormSection(label = "检索问题") {
                    OutlinedTextField(
                        value = state.queryText,
                        onValueChange = onQueryTextChanged,
                        enabled = interactionEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        placeholder = {
                            Text(
                                "例如：低估值银行股有哪些风险控制材料",
                                color = rememberFinanceSignalColors().onNeutralContainer,
                            )
                        },
                        supportingText = {
                            Text(
                                "用自然语言描述想查找的知识内容",
                                color = rememberFinanceSignalColors().onNeutralContainer,
                            )
                        },
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            }
        }

        item(key = "knowledge-config-note", contentType = "info") {
            when {
                profile == null -> ReportInfoState(text = "选择配置方案后才可开始检索")
                profile.totalChunks == null -> ReportInfoState(text = "当前配置未提供召回数量，请选择其他方案")
                state.searchMode == KnowledgeSearchMode.Target && state.selectedTarget == null -> {
                    ReportInfoState(text = "选择一个标的后开始检索")
                }
                state.searchMode == KnowledgeSearchMode.NaturalLanguage && state.queryText.trim().isEmpty() -> {
                    ReportInfoState(text = "输入检索问题后开始检索")
                }
            }
        }

        item(key = "knowledge-result-heading", contentType = "result-heading") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = "检索结果",
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (state.task != null) {
                    TextButton(
                        onClick = onRefreshTask,
                        enabled = !state.isLoadingTask,
                        modifier = Modifier.heightIn(min = LocalFinanceDimensions.current.minTouchTarget),
                    ) {
                        Text("刷新")
                    }
                }
            }
        }

        if (state.task == null) {
            item(key = "knowledge-no-task", contentType = "empty") {
                ReportEmptyState(text = "提交检索后展示知识库材料")
            }
        } else {
            val task = requireNotNull(state.task)
            item(key = "knowledge-task-summary", contentType = "task-summary") {
                KnowledgeTaskSummary(task = task)
            }
            if (state.taskErrorMessage.isNotBlank()) {
                item(key = "knowledge-task-error", contentType = "error") {
                    ReportErrorState(text = state.taskErrorMessage, onRetry = onRefreshTask)
                }
            }
            when {
                state.isLoadingTask && task.chunks.isEmpty() -> {
                    item(key = "knowledge-task-loading", contentType = "loading") {
                        ReportLoadingState(text = "正在读取检索结果")
                    }
                }
                task.status == KnowledgeTaskStatus.Failed -> {
                    item(key = "knowledge-task-failed", contentType = "error") {
                        KnowledgeInlineError(text = "材料检索失败，请刷新任务状态或重新检索。")
                    }
                }
                task.status.isWorking -> {
                    item(key = "knowledge-task-working", contentType = "loading") {
                        ReportLoadingState(text = task.status.label)
                    }
                }
                task.status == KnowledgeTaskStatus.Success && task.chunks.isEmpty() -> {
                    item(key = "knowledge-task-empty", contentType = "empty") {
                        ReportEmptyState(text = "没有匹配到知识库材料")
                    }
                }
                task.status == KnowledgeTaskStatus.Unknown -> {
                    item(key = "knowledge-task-unknown", contentType = "info") {
                        ReportInfoState(text = "任务状态暂不可用，请刷新后重试")
                    }
                }
            }

            if (task.chunks.isNotEmpty()) {
                item(key = "knowledge-chunks-heading", contentType = "section-heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(
                            text = "来源片段",
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "共 ${task.chunks.size} 条，正文来自知识库原文。",
                            style = MaterialTheme.typography.bodySmall,
                            color = rememberFinanceSignalColors().onNeutralContainer,
                        )
                    }
                }
                items(
                    items = task.chunks,
                    key = KnowledgeChunk::key,
                    contentType = { "knowledge-chunk" },
                ) { chunk ->
                    KnowledgeChunkRow(chunk = chunk, onClick = { onOpenChunk(chunk) })
                }
            }
        }
    }
}

@Composable
private fun KnowledgeFormSection(
    label: String,
    content: @Composable () -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

@Composable
private fun KnowledgeSelectionButton(
    value: String,
    supportingText: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimensions.controlHeight),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = rememberFinanceSignalColors().onNeutralContainer,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_phosphor_arrow_right),
            contentDescription = "打开${value}选项",
            modifier = Modifier.size(dimensions.iconSize),
            tint = rememberFinanceSignalColors().onNeutralContainer,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KnowledgeTargetSearchField(
    query: String,
    isSearching: Boolean,
    enabled: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        enabled = enabled && !isSearching,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimensions.controlHeight),
        placeholder = {
            Text(
                "输入标的名称或代码",
                color = rememberFinanceSignalColors().onNeutralContainer,
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_phosphor_magnifying_glass_duotone),
                contentDescription = null,
                tint = rememberFinanceSignalColors().onNeutralContainer,
            )
        },
        trailingIcon = {
            TextButton(
                onClick = onSearch,
                enabled = enabled && !isSearching,
                modifier = Modifier.heightIn(min = dimensions.minTouchTarget),
                contentPadding = PaddingValues(horizontal = spacing.sm),
            ) {
                Text(if (isSearching) "搜索中" else "搜索")
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { if (enabled && !isSearching) onSearch() }),
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            errorContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun KnowledgeSelectedTarget(target: ReportTargetOption) {
    val spacing = LocalFinanceSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(horizontal = spacing.md, vertical = spacing.sm)
            .semantics {
                contentDescription = "已选择 ${target.targetName} ${target.targetCode}"
                stateDescription = "已选择"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(target.targetName, style = MaterialTheme.typography.titleMedium)
            Text(
                "${target.targetCode} · ${target.typeLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = rememberFinanceSignalColors().onNeutralContainer,
            )
        }
        Text("已选择", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun KnowledgeTargetRow(
    target: ReportTargetOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimensions.minTouchTarget)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "选择${target.targetName}",
                onClick = onClick,
            )
            .semantics { stateDescription = if (selected) "已选择" else "未选择" }
            .padding(horizontal = spacing.sm, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                target.targetName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (selected) Text("已选择", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            "${target.targetCode} · ${target.typeLabel}",
            style = MaterialTheme.typography.bodySmall,
            color = rememberFinanceSignalColors().onNeutralContainer,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun KnowledgeTaskSummary(task: KnowledgeTask) {
    val spacing = LocalFinanceSpacing.current
    val signals = rememberFinanceSignalColors()
    val taskTitle = if (task.searchMode == KnowledgeSearchMode.Target) {
        task.targetName.ifBlank { task.targetCode.ifBlank { "按标的检索" } }
    } else {
        task.queryText.ifBlank { "自然语言检索" }
    }
    val context = if (task.searchMode == KnowledgeSearchMode.Target) {
        listOf(task.targetCode, task.targetTypeLabel()).filter(String::isNotBlank).joinToString(" · ")
    } else {
        "自然语言"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(horizontal = spacing.md, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(taskTitle, style = MaterialTheme.typography.titleMedium)
                Text(context, style = MaterialTheme.typography.bodySmall, color = signals.onNeutralContainer)
            }
            KnowledgeStatusLabel(status = task.status)
        }
        if (task.rewrittenQuery.isNotBlank() && task.rewrittenQuery != task.queryText) {
            Text(
                text = "检索理解：${task.rewrittenQuery}",
                style = MaterialTheme.typography.bodySmall,
                color = signals.onNeutralContainer,
            )
        }
        val timeText = listOf(
            task.submittedAt.takeIf(String::isNotBlank)?.let { "提交 ${formatKnowledgeTime(it)}" },
            task.finishedAt.takeIf(String::isNotBlank)?.let { "完成 ${formatKnowledgeTime(it)}" },
        ).filterNotNull().joinToString(" · ")
        if (timeText.isNotBlank()) {
            Text(timeText, style = MaterialTheme.typography.bodySmall, color = signals.onNeutralContainer)
        }
    }
}

@Composable
private fun KnowledgeStatusLabel(status: KnowledgeTaskStatus) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        KnowledgeTaskStatus.Success -> colors.primaryContainer to colors.onPrimaryContainer
        KnowledgeTaskStatus.Failed -> colors.errorContainer to colors.onErrorContainer
        KnowledgeTaskStatus.CurrentScenesReady,
        KnowledgeTaskStatus.Pending,
        KnowledgeTaskStatus.ProcessingCurrentScenes,
        KnowledgeTaskStatus.RetrievingKnowledge,
        -> colors.primaryContainer to colors.onPrimaryContainer
        KnowledgeTaskStatus.Unknown -> colors.surface to rememberFinanceSignalColors().onNeutralContainer
    }
    Text(
        text = status.label,
        modifier = Modifier
            .background(container, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = LocalFinanceSpacing.current.xs, vertical = LocalFinanceSpacing.current.xxs)
            .semantics { stateDescription = status.label },
        style = MaterialTheme.typography.labelMedium,
        color = content,
    )
}

@Composable
private fun KnowledgeChunkRow(chunk: KnowledgeChunk, onClick: () -> Unit) {
    val spacing = LocalFinanceSpacing.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Icon(painterResource(R.drawable.ic_phosphor_file_text_duotone), null,
                Modifier.size(LocalFinanceDimensions.current.iconSize), tint = rememberFinanceSignalColors().onNeutralContainer)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(chunk.filename, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = buildString {
                        append(chunk.sceneLabel)
                        chunk.chunkIndex?.let { append(" · 片段 ").append(it) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = rememberFinanceSignalColors().onNeutralContainer,
                )
            }
            chunk.finalScore?.let {
                Text(
                    text = "相关度 ${scoreText(it)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = rememberFinanceSignalColors().onNeutralContainer,
                )
            }
        }
        Text(
            text = chunk.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Text("阅读片段", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (chunk.matchedTags.isNotEmpty()) {
            Text(
                text = "相关标签 ${chunk.matchedTags.size} 个",
                style = MaterialTheme.typography.bodySmall,
                color = rememberFinanceSignalColors().onNeutralContainer,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun KnowledgeInlineError(text: String) {
    val spacing = LocalFinanceSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_phosphor_warning_circle),
            contentDescription = null,
            modifier = Modifier.size(LocalFinanceDimensions.current.iconSize),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun KnowledgeSubmitBar(
    isSubmitting: Boolean,
    submissionUnconfirmed: Boolean,
    enabled: Boolean,
    onSubmit: () -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = spacing.xl, vertical = spacing.md),
    ) {
        Button(
            onClick = onSubmit,
            enabled = enabled && !isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimensions.controlHeight),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(dimensions.iconSize),
                    strokeWidth = dimensions.outlineWidth,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(spacing.sm))
            }
            Text(
                when {
                    isSubmitting -> "正在检索"
                    submissionUnconfirmed -> "再次检索"
                    else -> "检索材料"
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KnowledgeModeSheet(
    selectedMode: KnowledgeSearchMode,
    onDismiss: () -> Unit,
    onSelected: (KnowledgeSearchMode) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        KnowledgeSheetContainer(title = "检索方式") {
            KnowledgeSearchMode.entries.forEach { mode ->
                KnowledgeSheetRow(
                    title = mode.label,
                    supportingText = if (mode == KnowledgeSearchMode.Target) "按已配置的标的类型检索" else "按问题描述检索知识库",
                    selected = mode == selectedMode,
                    onClick = { onSelected(mode) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KnowledgeProfileSheet(
    profiles: List<KnowledgeProfileOption>,
    selectedId: Long?,
    onDismiss: () -> Unit,
    onSelected: (Long) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        KnowledgeSheetContainer(title = "检索配置") {
            if (profiles.isEmpty()) {
                ReportEmptyState(text = "暂无可用检索配置")
            } else {
                profiles.forEach { profile ->
                    KnowledgeSheetRow(
                        title = profile.name,
                        supportingText = listOfNotNull(
                            profile.group.takeIf(String::isNotBlank),
                            profile.reportTypeLabel.takeIf(String::isNotBlank),
                        ).joinToString(" · ").ifBlank { "已有服务端配置" },
                        selected = profile.id == selectedId,
                        onClick = { onSelected(profile.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KnowledgeChoiceSheet(
    title: String,
    choices: List<ReportChoice>,
    selectedValue: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        KnowledgeSheetContainer(title = title) {
            if (choices.isEmpty()) {
                ReportEmptyState(text = "暂无可用选项")
            } else {
                choices.forEach { choice ->
                    KnowledgeSheetRow(
                        title = choice.label,
                        supportingText = null,
                        selected = choice.value == selectedValue,
                        onClick = { onSelected(choice.value) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KnowledgeSheetContainer(
    title: String,
    content: @Composable () -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.xl, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
        )
        content()
        Spacer(Modifier.size(spacing.xxl))
    }
}

@Composable
private fun KnowledgeSheetRow(
    title: String,
    supportingText: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimensions.minTouchTarget)
            .clickable(
                role = Role.Button,
                onClickLabel = "选择$title",
                onClick = onClick,
            )
            .semantics { stateDescription = if (selected) "已选择" else "未选择" }
            .padding(horizontal = spacing.sm, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            supportingText?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = rememberFinanceSignalColors().onNeutralContainer,
                )
            }
        }
        if (selected) Text("当前", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

private fun KnowledgeTask.targetTypeLabel(): String = reportTargetTypes
    .firstOrNull { it.value == targetType || (targetType == "BOND" && it.value == "CONVERTIBLE_BOND") }
    ?.label
    .orEmpty()

private fun formatKnowledgeTime(value: String): String = value.replace('T', ' ').take(16)

private fun scoreText(score: Double): String = "${(score * 100).let { kotlin.math.round(it * 10) / 10 }}%"

private fun knowledgeResultHeadingIndex(state: KnowledgeUiState): Int {
    var index = 1 // intro
    if (state.errorMessage.isNotBlank() && state.profiles.isNotEmpty()) index++
    index++ // mode
    index++ // profile
    index += if (state.searchMode == KnowledgeSearchMode.Target) 2 else 1 // target type/search or query
    index++ // config note
    return index
}

private enum class KnowledgeSheet {
    Mode,
    Profile,
    TargetType,
}
