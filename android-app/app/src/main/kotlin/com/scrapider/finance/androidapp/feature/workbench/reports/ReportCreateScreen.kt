package com.scrapider.finance.androidapp.feature.workbench.reports

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import com.scrapider.finance.androidapp.R
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.financeChromeColor
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.designsystem.rememberFinanceSignalColors

/**
 * 报告生成表单。业务状态和请求行为由 ViewModel 提供，页面只负责呈现和发出用户事件。
 *
 * [targetTypeOptions] 由调用方传入可生成报告的标的类型，调用方应在传入前排除“全部”筛选项。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportCreateScreen(
    state: ReportCreateState,
    targetTypeOptions: List<ReportChoice>,
    onBack: () -> Unit,
    onProfileSelected: (Long) -> Unit,
    onReportTypeSelected: (String) -> Unit,
    onTargetTypeSelected: (String) -> Unit,
    onTargetQueryChanged: (String) -> Unit,
    onSearchTargets: () -> Unit,
    onTargetSelected: (ReportTargetOption) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onRetrySearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheet by remember { mutableStateOf<ReportCreateSheet?>(null) }
    val spacing = LocalFinanceSpacing.current

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        // 系统栏由 AppShell 消费，IME 由 ReportsRoute 统一处理。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ReportTopBar(
                title = "生成报告",
                onBack = onBack,
            )
        },
        bottomBar = {
            ReportSubmitBar(
                isSubmitting = state.isSubmitting,
                submissionUnconfirmed = state.submissionUnconfirmed,
                enabled = state.canSubmit || state.submissionUnconfirmed,
                onSubmit = onSubmit,
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            when {
                state.isLoading -> ReportLoadingState(
                    text = "正在加载报告配置",
                    modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.lg),
                )

                state.errorMessage.isNotBlank() && state.profiles.isEmpty() -> ReportErrorState(
                    text = state.errorMessage,
                    onRetry = onRetry,
                    modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.lg),
                )

                else -> ReportCreateContent(
                    state = state,
                    targetTypeOptions = targetTypeOptions,
                    errorMessage = state.errorMessage,
                    onOpenProfileSheet = { sheet = ReportCreateSheet.Profile },
                    onOpenReportTypeSheet = { sheet = ReportCreateSheet.ReportType },
                    onOpenTargetTypeSheet = { sheet = ReportCreateSheet.TargetType },
                    onTargetQueryChanged = onTargetQueryChanged,
                    onSearchTargets = onSearchTargets,
                    onTargetSelected = onTargetSelected,
                    onRetrySearch = onRetrySearch,
                )
            }
        }
    }

    when (sheet) {
        ReportCreateSheet.Profile -> ReportProfileSheet(
            profiles = state.profiles,
            selectedId = state.selectedProfileId,
            onDismiss = { sheet = null },
            onSelected = { id ->
                sheet = null
                onProfileSelected(id)
            },
        )

        ReportCreateSheet.ReportType -> ReportChoiceSheet(
            title = "报告类型",
            choices = state.reportTypes,
            selectedValue = state.selectedReportType,
            emptyText = "暂无可用报告类型",
            onDismiss = { sheet = null },
            onSelected = { value ->
                sheet = null
                onReportTypeSelected(value)
            },
        )

        ReportCreateSheet.TargetType -> ReportChoiceSheet(
            title = "标的类型",
            choices = targetTypeOptions,
            selectedValue = state.targetType,
            emptyText = "暂无可用标的类型",
            onDismiss = { sheet = null },
            onSelected = { value ->
                sheet = null
                onTargetTypeSelected(value)
            },
        )

        null -> Unit
    }
}

@Composable
private fun ReportCreateContent(
    state: ReportCreateState,
    targetTypeOptions: List<ReportChoice>,
    errorMessage: String,
    onOpenProfileSheet: () -> Unit,
    onOpenReportTypeSheet: () -> Unit,
    onOpenTargetTypeSheet: () -> Unit,
    onTargetQueryChanged: (String) -> Unit,
    onSearchTargets: () -> Unit,
    onTargetSelected: (ReportTargetOption) -> Unit,
    onRetrySearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val selectedProfile = state.selectedProfile
    val reportTypeLabel = state.reportTypes
        .find { it.value == state.selectedReportType }
        ?.label
        .orEmpty()
    val targetTypeLabel = targetTypeOptions
        .find { it.value == state.targetType }
        ?.label
        .orEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.xl,
            top = spacing.md,
            end = spacing.xl,
            bottom = spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        item(key = "create-intro", contentType = "intro") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = "选择配置和标的",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "使用已有配置方案生成一份新的研究报告。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = rememberFinanceSignalColors().onNeutralContainer,
                )
            }
        }

        if (errorMessage.isNotBlank()) {
            item(key = "create-error", contentType = "error") {
                ReportInlineErrorState(text = errorMessage)
            }
        }

        item(key = "create-profile", contentType = "selection") {
            ReportFormSection(label = "配置方案") {
                ReportSelectionButton(
                    value = selectedProfile?.name ?: "请选择配置方案",
                    supportingText = selectedProfile?.let { profile ->
                        if (profile.group.isBlank()) {
                            if (profile.recommended) "推荐方案" else "已有配置"
                        } else if (profile.recommended) {
                            "${profile.group} · 推荐方案"
                        } else {
                            profile.group
                        }
                    } ?: "从已有配置方案中选择",
                    onClick = onOpenProfileSheet,
                    enabled = state.profiles.isNotEmpty() && !state.isSubmitting && !state.submissionUnconfirmed,
                )
                if (state.profiles.isEmpty()) {
                    ReportEmptyState(text = "暂无可用配置方案")
                }
            }
        }

        item(key = "create-report-type", contentType = "selection") {
            ReportFormSection(label = "报告类型") {
                ReportSelectionButton(
                    value = reportTypeLabel.ifBlank { "请选择报告类型" },
                    supportingText = if (reportTypeLabel.isBlank()) {
                        "选择要生成的报告内容"
                    } else {
                        ""
                    },
                    onClick = onOpenReportTypeSheet,
                    enabled = state.reportTypes.isNotEmpty() && !state.isSubmitting && !state.submissionUnconfirmed,
                )
            }
        }

        item(key = "create-target-type", contentType = "selection") {
            ReportFormSection(label = "标的类型") {
                ReportSelectionButton(
                    value = targetTypeLabel.ifBlank { "请选择标的类型" },
                    supportingText = if (targetTypeOptions.isEmpty()) {
                        "暂无可用标的类型"
                    } else if (targetTypeLabel.isBlank()) {
                        "选择后搜索标的"
                    } else {
                        "搜索将限定在此类型内"
                    },
                    onClick = onOpenTargetTypeSheet,
                    enabled = targetTypeOptions.isNotEmpty() && !state.isSubmitting && !state.submissionUnconfirmed,
                )
            }
        }

        item(key = "create-target-search", contentType = "target-search") {
            ReportFormSection(label = "选择标的") {
                if (state.targetType.isBlank()) {
                    ReportInfoState(text = "先选择标的类型，再搜索名称或代码")
                } else {
                    ReportTargetSearchField(
                        query = state.targetQuery,
                        isSearching = state.isSearching,
                        enabled = !state.isSubmitting && !state.submissionUnconfirmed,
                        onQueryChange = onTargetQueryChanged,
                        onSearch = onSearchTargets,
                    )
                    Spacer(Modifier.heightIn(min = spacing.xs))

                    when {
                        state.searchError.isNotBlank() -> ReportErrorState(
                            text = state.searchError,
                            onRetry = onRetrySearch,
                        )

                        state.isSearching -> ReportLoadingState(text = "正在搜索标的")

                        state.hasSearched && state.targetOptions.isEmpty() -> ReportEmptyState(
                            text = "没有找到匹配标的，换个名称或代码试试",
                        )

                        !state.hasSearched && state.selectedTarget == null -> Text(
                            text = "输入名称或代码后点击搜索",
                            style = MaterialTheme.typography.bodySmall,
                            color = rememberFinanceSignalColors().onNeutralContainer,
                        )
                    }

                    state.selectedTarget?.let { target ->
                        Spacer(Modifier.heightIn(min = spacing.sm))
                        ReportSelectedTarget(target = target)
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
                                key(target.key) {
                                    ReportTargetOptionRow(
                                        target = target,
                                        selected = target.key == state.selectedTarget?.key,
                                        enabled = !state.isSubmitting && !state.submissionUnconfirmed,
                                        onClick = { onTargetSelected(target) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.isSubmitting) {
            item(key = "create-submitting", contentType = "submitting") {
                ReportInfoState(text = "正在提交生成任务，请稍候")
            }
        }
    }
}

@Composable
private fun ReportFormSection(
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
private fun ReportInlineErrorState(
    text: String,
) {
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
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun ReportSelectionButton(
    value: String,
    supportingText: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimensions.controlHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    rememberFinanceSignalColors().onNeutralContainer
                },
            )
            if (supportingText.isNotBlank()) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = rememberFinanceSignalColors().onNeutralContainer,
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_phosphor_arrow_right),
            contentDescription = null,
            modifier = Modifier.size(dimensions.iconSize),
            tint = rememberFinanceSignalColors().onNeutralContainer,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportTargetSearchField(
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
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimensions.controlHeight),
        enabled = enabled,
        placeholder = {
            Text(
                text = "输入标的名称或代码",
                style = MaterialTheme.typography.bodyMedium,
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
        textStyle = MaterialTheme.typography.bodyMedium,
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
private fun ReportSelectedTarget(
    target: ReportTargetOption,
) {
    val spacing = LocalFinanceSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
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
            Text(
                text = target.targetName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "${target.targetCode} · ${target.typeLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = "已选择",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun ReportTargetOptionRow(
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
            .semantics {
                stateDescription = if (selected) "已选择" else "未选择"
            }
            .padding(horizontal = spacing.sm, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = target.targetName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (selected) {
                Text(
                    text = "已选择",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = "${target.targetCode} · ${target.typeLabel}",
            style = MaterialTheme.typography.bodySmall,
            color = rememberFinanceSignalColors().onNeutralContainer,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ReportSubmitBar(
    isSubmitting: Boolean,
    submissionUnconfirmed: Boolean,
    enabled: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(financeChromeColor())
            .padding(horizontal = spacing.xl, vertical = spacing.md),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Button(
            onClick = onSubmit,
            enabled = enabled && !isSubmitting,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimensions.controlHeight),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(dimensions.iconSize),
                    strokeWidth = dimensions.outlineWidth,
                    color = rememberFinanceSignalColors().onNeutralContainer,
                )
                Spacer(Modifier.size(spacing.sm))
            }
            Text(when {
                isSubmitting -> "正在提交"
                submissionUnconfirmed -> "查看报告列表"
                else -> "生成报告"
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportProfileSheet(
    profiles: List<ReportProfileOption>,
    selectedId: Long?,
    onDismiss: () -> Unit,
    onSelected: (Long) -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = "配置方案",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "选择已有方案，不在此处修改配置参数。",
                    style = MaterialTheme.typography.bodySmall,
                    color = rememberFinanceSignalColors().onNeutralContainer,
                )
            }
            if (profiles.isEmpty()) {
                ReportEmptyState(
                    text = "暂无可用配置方案",
                    modifier = Modifier.padding(horizontal = spacing.xl),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = spacing.xl,
                        top = spacing.md,
                        end = spacing.xl,
                        bottom = spacing.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    items(profiles, key = { it.id }, contentType = { "profile" }) { profile ->
                        ReportSheetRow(
                            title = profile.name,
                            supportingText = profile.group.ifBlank {
                                if (profile.recommended) "推荐方案" else "已有配置"
                            },
                            selected = profile.id == selectedId,
                            onClick = { onSelected(profile.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportChoiceSheet(
    title: String,
    choices: List<ReportChoice>,
    selectedValue: String,
    emptyText: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xl),
        ) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (choices.isEmpty()) {
                ReportEmptyState(text = emptyText)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(top = spacing.md, bottom = spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    items(choices, key = { it.value }, contentType = { "choice" }) { choice ->
                        ReportSheetRow(
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
}

@Composable
private fun ReportSheetRow(
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
            .semantics {
                stateDescription = if (selected) "已选择" else "未选择"
            }
            .padding(horizontal = spacing.sm, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            supportingText?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = rememberFinanceSignalColors().onNeutralContainer,
                )
            }
        }
        if (selected) {
            Text(
                text = "当前",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private enum class ReportCreateSheet {
    Profile,
    ReportType,
    TargetType,
}
