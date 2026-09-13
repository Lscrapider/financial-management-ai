package com.scrapider.finance.androidapp.feature.workbench.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import com.scrapider.finance.androidapp.R
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.financeChromeColor
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.designsystem.rememberFinanceSignalColors
import com.scrapider.finance.androidapp.feature.workbench.ReportStatus

@Composable
internal fun ReportTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val spacing = LocalFinanceSpacing.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LocalFinanceDimensions.current.controlHeight)
            .background(financeChromeColor())
            .padding(horizontal = spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            ReportBackButton(onClick = onBack)
        }
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        actions()
    }
}

@Composable
private fun ReportBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = LocalFinanceDimensions.current
    val layoutDirection = LocalLayoutDirection.current
    IconButton(
        onClick = onClick,
        modifier = modifier
            .sizeIn(
                minWidth = dimensions.minTouchTarget,
                minHeight = dimensions.minTouchTarget,
            )
            .semantics {
                contentDescription = "返回"
                role = Role.Button
            },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_phosphor_arrow_right),
            contentDescription = null,
            modifier = Modifier.rotate(
                if (layoutDirection == LayoutDirection.Ltr) 180f else 0f,
            ),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimensions.controlHeight),
        placeholder = {
            Text(
                text = "搜索标的名称或代码",
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
                modifier = Modifier.heightIn(min = dimensions.minTouchTarget),
                contentPadding = PaddingValues(horizontal = spacing.sm),
            ) {
                Text("搜索")
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            errorContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
internal fun ReportStatusLabel(
    status: ReportStatus,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val signals = rememberFinanceSignalColors()
    val (background, foreground) = when (status) {
        ReportStatus.Generated -> signals.positiveContainer to signals.onPositiveContainer
        ReportStatus.Generating -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        ReportStatus.Pending -> signals.neutralContainer to signals.onNeutralContainer
        ReportStatus.Failed -> signals.negativeContainer to signals.onNegativeContainer
        ReportStatus.Unknown -> signals.neutralContainer to signals.onNeutralContainer
    }
    Text(
        text = status.label,
        modifier = modifier
            .background(background, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = spacing.xs, vertical = spacing.xxs)
            .semantics { stateDescription = status.label },
        style = MaterialTheme.typography.labelMedium,
        color = foreground,
    )
}

@Composable
internal fun ReportTargetRow(
    target: ReportTarget,
    onOpenLatest: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimensions.minTouchTarget)
                .clickable(role = Role.Button, onClickLabel = "查看${target.targetName}最新报告", onClick = onOpenLatest)
                .padding(vertical = spacing.md),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            ReportDocumentIcon()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = target.targetName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ReportStatusLabel(status = target.status)
                }
                Text(
                    text = "${target.targetCode} · ${target.targetTypeLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = rememberFinanceSignalColors().onNeutralContainer,
                )
                Text(
                    text = "${target.reportTypeLabel} · ${target.timeLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = rememberFinanceSignalColors().onNeutralContainer,
                )
            }
        }
        if (target.reportCount > 1) {
            TextButton(
                onClick = onOpenHistory,
                modifier = Modifier
                    .align(Alignment.Start)
                    .heightIn(min = dimensions.minTouchTarget)
                    .padding(start = dimensions.controlHeight + spacing.md),
                contentPadding = PaddingValues(horizontal = spacing.sm),
            ) {
                Text("查看 ${target.reportCount} 份记录")
                Icon(
                    painter = painterResource(R.drawable.ic_phosphor_arrow_right),
                    contentDescription = null,
                    modifier = Modifier.size(dimensions.iconSize),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
internal fun ReportRecordRow(
    record: ReportRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimensions.minTouchTarget)
            .clickable(role = Role.Button, onClickLabel = "查看报告详情", onClick = onClick)
            .padding(vertical = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        ReportDocumentIcon()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = record.reportTypeLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ReportStatusLabel(status = record.status)
            }
            Text(
                text = record.timeLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = rememberFinanceSignalColors().onNeutralContainer,
            )
            if (record.versionLabel.isNotBlank()) {
                Text(
                    text = record.versionLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = rememberFinanceSignalColors().onNeutralContainer,
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ReportDocumentIcon(
    modifier: Modifier = Modifier,
) {
    val dimensions = LocalFinanceDimensions.current
    Box(
        modifier = modifier
            .size(dimensions.controlHeight)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_phosphor_file_text_duotone),
            contentDescription = null,
            modifier = Modifier.size(dimensions.iconSize),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun ReportLoadingState(
    text: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(spacing.xxs),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = rememberFinanceSignalColors().onNeutralContainer,
        )
    }
}

@Composable
internal fun ReportEmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = LocalFinanceSpacing.current.lg),
        style = MaterialTheme.typography.bodyMedium,
        color = rememberFinanceSignalColors().onNeutralContainer,
    )
}

@Composable
internal fun ReportErrorState(
    text: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val signals = rememberFinanceSignalColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(signals.negativeContainer, MaterialTheme.shapes.small)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_phosphor_warning_circle),
            contentDescription = null,
            modifier = Modifier.size(LocalFinanceDimensions.current.iconSize),
            tint = signals.onNegativeContainer,
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = signals.onNegativeContainer,
        )
        TextButton(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = LocalFinanceDimensions.current.minTouchTarget),
        ) {
            Text("重试", color = signals.onNegativeContainer)
        }
    }
}

@Composable
internal fun ReportInfoState(
    text: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val signals = rememberFinanceSignalColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(signals.neutralContainer, MaterialTheme.shapes.small)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = signals.onNeutralContainer,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportFilterSheet(
    choices: List<ReportChoice>,
    selectedValue: String,
    onDismiss: () -> Unit,
    onTypeSelected: (String) -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xl, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            item {
              Text(
                text = "筛选标的类型",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            items(choices, key = { it.value }, contentType = { "filter-choice" }) { choice ->
                val selected = choice.value == selectedValue
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = dimensions.minTouchTarget)
                        .padding(horizontal = spacing.sm)
                        .clickable {
                            onTypeSelected(choice.value)
                            onDismiss()
                        }
                        .semantics {
                            stateDescription = if (selected) "已选择" else "未选择"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = choice.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (selected) {
                        Text(
                            text = "当前",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReportMoreMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    canRegenerate: Boolean,
    onRegenerate: () -> Unit,
) {
    Box {
        TextButton(
            onClick = { onExpandedChange(true) },
            modifier = Modifier.heightIn(min = LocalFinanceDimensions.current.minTouchTarget),
        ) {
            Text("更多")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            DropdownMenuItem(
                text = { Text("重新生成") },
                enabled = canRegenerate,
                onClick = {
                    onExpandedChange(false)
                    onRegenerate()
                },
            )
        }
    }
}
