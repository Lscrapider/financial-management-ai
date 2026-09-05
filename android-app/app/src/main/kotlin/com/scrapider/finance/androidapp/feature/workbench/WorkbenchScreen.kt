package com.scrapider.finance.androidapp.feature.workbench

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.session.UserSession
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSemanticColors
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import kotlinx.coroutines.flow.collect
import java.time.LocalTime
import java.util.Locale

@Composable
fun WorkbenchRoute(
    session: UserSession,
    apiClient: FinanceApiClient,
    onMarketSelected: () -> Unit,
    onSessionExpired: () -> Unit,
    onUnavailableFeature: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory = remember(apiClient) { WorkbenchViewModel.Factory(apiClient) }
    val viewModel: WorkbenchViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(session.accessToken) {
        viewModel.loadForSession(session.accessToken)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WorkbenchEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    WorkbenchScreen(
        state = state,
        displayName = session.displayName,
        isAdmin = session.isAdmin,
        onRefresh = viewModel::refresh,
        onFocusSelected = onMarketSelected,
        onReportSelected = { onUnavailableFeature("研究报告详情将在下一份设计稿中重建。") },
        onViewAllReports = { onUnavailableFeature("研究报告列表将在下一份设计稿中重建。") },
        onToolSelected = { tool ->
            onUnavailableFeature(tool.label + "将在下一份设计稿中重建。")
        },
        modifier = modifier,
    )
}

@Composable
fun WorkbenchScreen(
    state: WorkbenchUiState,
    displayName: String,
    isAdmin: Boolean,
    onRefresh: () -> Unit,
    onFocusSelected: () -> Unit,
    onReportSelected: (ReportItem) -> Unit,
    onViewAllReports: () -> Unit,
    onToolSelected: (ResearchTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.xl,
            top = spacing.xl,
            end = spacing.xl,
            bottom = spacing.section,
        ),
    ) {
        item {
            WorkbenchHeader(
                displayName = displayName,
                isRefreshing = state.isLoading,
                onRefresh = onRefresh,
            )
        }
        item { Spacer(Modifier.height(spacing.section)) }
        item { SectionTitle(title = "今日关注") }
        item { Spacer(Modifier.height(spacing.sm)) }

        when {
            state.isLoading && state.focusItems.isEmpty() -> {
                item { LoadingPanel(text = "正在同步关注标的") }
            }

            state.focusItems.isEmpty() -> {
                item {
                    EmptyPanel(
                        text = "暂未获取到关注标的",
                    )
                }
            }

            else -> {
                items(
                    items = state.focusItems,
                    key = FocusItem::id,
                ) { item ->
                    FocusRow(item = item, onClick = onFocusSelected)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        item { Spacer(Modifier.height(spacing.section)) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle(title = "研究报告")
                TextButton(
                    onClick = onViewAllReports,
                    modifier = Modifier.heightIn(min = dimensions.minTouchTarget),
                ) {
                    Text("查看全部")
                }
            }
        }
        item { Spacer(Modifier.height(spacing.sm)) }

        when {
            state.isLoading && state.reportItems.isEmpty() -> {
                item { LoadingPanel(text = "正在同步研究报告") }
            }

            state.reportItems.isEmpty() -> {
                item {
                    EmptyPanel(
                        text = "暂未生成研究报告",
                    )
                }
            }

            else -> {
                items(
                    items = state.reportItems.take(WORKBENCH_PREVIEW_ITEM_LIMIT),
                    key = ReportItem::id,
                ) { item ->
                    ReportRow(item = item, onClick = { onReportSelected(item) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        if (state.syncMessage.isNotBlank()) {
            item {
                Spacer(Modifier.height(spacing.md))
                Text(
                    text = state.syncMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        item { Spacer(Modifier.height(spacing.section)) }
        item { SectionTitle(title = "研究工具") }
        item { Spacer(Modifier.height(spacing.sm)) }
        item {
            ResearchTools(
                isAdmin = isAdmin,
                onToolSelected = onToolSelected,
            )
        }
    }
}

@Composable
private fun WorkbenchHeader(
    displayName: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val dimensions = LocalFinanceDimensions.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "工作台",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = greeting(displayName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(dimensions.iconSize),
                    strokeWidth = dimensions.outlineWidth,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "刷新工作台",
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun FocusRow(
    item: FocusItem,
    onClick: () -> Unit,
) {
    val dimensions = LocalFinanceDimensions.current
    val semanticColors = LocalFinanceSemanticColors.current
    val changeColor = item.changePercent.changeColor(
        positive = semanticColors.positive,
        negative = semanticColors.negative,
        neutral = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimensions.compactRowHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = item.targetName + "，" + item.status.label +
                    item.changePercent.asPercentText()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.targetName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.targetTypeLabel + " · " + item.targetCode,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(LocalFinanceSpacing.current.xxs),
        ) {
            Text(
                text = if (item.status == FocusStatus.ThresholdExceeded) {
                    item.status.label
                } else {
                    item.changePercent.movementLabel()
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (item.status == FocusStatus.ThresholdExceeded) {
                    semanticColors.warning
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = item.changePercent.asPercentText(),
                style = MaterialTheme.typography.titleMedium,
                color = changeColor,
            )
        }
    }
}

@Composable
private fun ReportRow(
    item: ReportItem,
    onClick: () -> Unit,
) {
    val dimensions = LocalFinanceDimensions.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimensions.listRowHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = item.targetName + "，" + item.reportTypeLabel +
                    "，" + item.status.label
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.targetName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.reportTypeLabel + " · " + item.timeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = item.status.label,
            style = MaterialTheme.typography.titleMedium,
            color = item.status.statusColor(),
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResearchTools(
    isAdmin: Boolean,
    onToolSelected: (ResearchTool) -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        ResearchTool.entries.chunked(RESEARCH_TOOL_COLUMN_COUNT).forEach { rowTools ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                rowTools.forEach { tool ->
                    ResearchToolCard(
                        tool = tool,
                        enabled = !tool.adminOnly || isAdmin,
                        onClick = { onToolSelected(tool) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(RESEARCH_TOOL_COLUMN_COUNT - rowTools.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ResearchToolCard(
    tool: ResearchTool,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = LocalFinanceDimensions.current
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = modifier
            .heightIn(min = dimensions.toolRowHeight)
            .then(
                if (enabled) {
                    Modifier
                        .clickable(role = Role.Button, onClick = onClick)
                        .semantics { contentDescription = tool.label }
                } else {
                    Modifier.semantics { contentDescription = tool.label + "，仅管理员可用" }
                },
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = dimensions.outlineWidth,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(LocalFinanceSpacing.current.md),
            horizontalArrangement = Arrangement.spacedBy(LocalFinanceSpacing.current.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = tool.icon(),
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else contentColor,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (enabled) tool.description else "仅管理员可用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LoadingPanel(text: String) {
    val spacing = LocalFinanceSpacing.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalFinanceDimensions.current.compactRowHeight),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(LocalFinanceDimensions.current.iconSize),
                strokeWidth = LocalFinanceDimensions.current.outlineWidth,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyPanel(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalFinanceDimensions.current.compactRowHeight),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = LocalFinanceSpacing.current.lg),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun greeting(displayName: String): String {
    val salutation = when (LocalTime.now().hour) {
        in 5..11 -> "上午好"
        in 12..17 -> "下午好"
        else -> "晚上好"
    }
    return salutation + "，" + displayName
}

private fun Double?.movementLabel(): String = when {
    this == null -> "暂无行情"
    this > 0.0 -> "上涨"
    this < 0.0 -> "下跌"
    else -> "平盘"
}

private fun Double?.asPercentText(): String {
    if (this == null) return "暂无数据"
    val prefix = if (this > 0.0) "+" else ""
    return prefix + String.format(Locale.CHINA, PERCENT_FORMAT, this)
}

@Composable
private fun Double?.changeColor(
    positive: Color,
    negative: Color,
    neutral: Color,
): Color = when {
    this == null -> neutral
    this > 0.0 -> positive
    this < 0.0 -> negative
    else -> neutral
}

@Composable
private fun ReportStatus.statusColor(): Color {
    val semantics = LocalFinanceSemanticColors.current
    return when (this) {
        ReportStatus.Generated -> semantics.positive
        ReportStatus.Failed -> MaterialTheme.colorScheme.error
        ReportStatus.Generating -> MaterialTheme.colorScheme.primary
        ReportStatus.Pending,
        ReportStatus.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun ResearchTool.icon(): ImageVector = when (this) {
    ResearchTool.Report -> Icons.Outlined.Description
    ResearchTool.KnowledgeSearch -> Icons.Outlined.Search
    ResearchTool.MaterialImport -> Icons.Outlined.UploadFile
    ResearchTool.AiAssistant -> Icons.Outlined.AutoAwesome
}

private const val RESEARCH_TOOL_COLUMN_COUNT = 2
private const val PERCENT_FORMAT = "%.2f%%"
