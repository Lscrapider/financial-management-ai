package com.scrapider.finance.androidapp.feature.workbench

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrapider.finance.androidapp.R
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.session.UserSession
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.designsystem.rememberFinanceSignalColors
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
    val reportItems = state.reportItems.take(WORKBENCH_PREVIEW_ITEM_LIMIT)
    val overview = state.watchlistOverview
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.xl,
            top = spacing.md,
            end = spacing.xl,
            bottom = spacing.section,
        ),
    ) {
        item(key = "header", contentType = "header") {
            WorkbenchHeader(
                displayName = displayName,
                isRefreshing = state.isLoading,
                onRefresh = onRefresh,
            )
        }
        if (state.syncMessage.isNotBlank()) {
            item(key = "sync_message", contentType = "sync_message") {
                SyncMessage(
                    message = state.syncMessage,
                    isRefreshing = state.isLoading,
                    onRetry = onRefresh,
                )
            }
        }
        item(key = "tools", contentType = "tools") {
            ResearchTools(isAdmin = isAdmin, onToolSelected = onToolSelected)
        }
        item(key = "overview_heading", contentType = "section_heading") {
            SectionHeading(
                title = "自选概况",
                actionLabel = "查看行情",
                onAction = onFocusSelected,
                prominent = true,
            )
        }
        when {
            overview == null -> {
                item(key = "overview_state", contentType = "content_state") {
                    val waitingForData = state.isLoading || state.syncMessage.isBlank()
                    ContentState(
                        text = if (waitingForData) "正在同步自选行情" else "自选行情暂不可用，请刷新重试",
                        isLoading = waitingForData,
                    )
                }
            }
            overview.totalCount == 0 -> {
                item(key = "overview_state", contentType = "content_state") {
                    ContentState(text = "还没有自选标的，可前往行情添加")
                }
            }
            else -> {
                item(key = "overview_counts", contentType = "overview_counts") {
                    WatchlistCounts(overview = overview)
                }
                item(key = "overview_rankings", contentType = "overview_rankings") {
                    WatchlistRankings(overview = overview, onOpenMarket = onFocusSelected)
                }
            }
        }

        item(key = "reports_heading", contentType = "section_heading") {
            SectionHeading(
                title = "研究报告",
                actionLabel = "查看全部",
                onAction = onViewAllReports,
            )
        }

        when {
            state.isLoading && state.reportItems.isEmpty() -> {
                item(key = "reports_state", contentType = "content_state") {
                    ContentState(text = "正在同步研究报告", isLoading = true)
                }
            }

            state.reportItems.isEmpty() -> {
                item(key = "reports_state", contentType = "content_state") {
                    ContentState(text = "暂未生成研究报告")
                }
            }

            else -> {
                items(
                    items = reportItems,
                    key = { "report:" + it.id },
                    contentType = { "report" },
                ) { item ->
                    CompactReportRow(item = item, onClick = { onReportSelected(item) })
                    if (item.id != reportItems.last().id) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
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
    val signals = rememberFinanceSignalColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(LocalFinanceSpacing.current.xs),
        ) {
            Text(
                text = "工作台",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = greeting(displayName),
                style = MaterialTheme.typography.bodySmall,
                color = signals.onNeutralContainer,
            )
        }
        IconButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
            modifier = Modifier.semantics {
                contentDescription = if (isRefreshing) "正在刷新工作台" else "刷新工作台"
            },
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(dimensions.iconSize),
                    strokeWidth = dimensions.outlineWidth,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_phosphor_arrows_clockwise),
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    prominent: Boolean = false,
) {
    val spacing = LocalFinanceSpacing.current
    Row(
        modifier = Modifier
            .padding(top = spacing.lg)
            .fillMaxWidth()
            .heightIn(min = LocalFinanceDimensions.current.minTouchTarget),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = if (prominent) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (actionLabel != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = LocalFinanceDimensions.current.minTouchTarget),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun WatchlistCounts(overview: WatchlistOverview) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    val signals = rememberFinanceSignalColors()
    val fontScale = LocalDensity.current.fontScale
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(
            text = "全部分组 · 去重后 " + overview.totalCount + " 只",
            style = MaterialTheme.typography.bodySmall,
            color = signals.onNeutralContainer,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val canShowColumns = (maxWidth - spacing.lg) / 2 >= dimensions.toolRowHeight * OVERVIEW_COLUMN_WIDTH_UNITS * fontScale
            if (canShowColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                    MovementCount("上涨", overview.risingCount, signals.onPositiveContainer, Modifier.weight(1f))
                    MovementCount("下跌", overview.fallingCount, signals.onNegativeContainer, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    MovementCount("上涨", overview.risingCount, signals.onPositiveContainer)
                    MovementCount("下跌", overview.fallingCount, signals.onNegativeContainer)
                }
            }
        }
        Text(
            text = "平盘 " + overview.flatCount + " 只 · 暂无行情 " + overview.unavailableCount + " 只",
            style = MaterialTheme.typography.bodySmall,
            color = signals.onNeutralContainer,
        )
    }
}

@Composable
private fun MovementCount(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    val spacing = LocalFinanceSpacing.current
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color)
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
            color = color,
        )
        Text("只", style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun WatchlistRankings(overview: WatchlistOverview, onOpenMarket: () -> Unit) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    val signals = rememberFinanceSignalColors()
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(top = spacing.lg)) {
        val hasBothDirections = overview.topGainers.isNotEmpty() && overview.topLosers.isNotEmpty()
        val canShowColumns = (maxWidth - spacing.lg) / 2 >= dimensions.toolRowHeight * OVERVIEW_COLUMN_WIDTH_UNITS * fontScale
        if (hasBothDirections && canShowColumns) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                RankingGroup(
                    title = "领涨前三",
                    items = overview.topGainers,
                    emptyText = "暂无上涨标的",
                    color = signals.onPositiveContainer,
                    onOpenMarket = onOpenMarket,
                    modifier = Modifier.weight(1f),
                )
                RankingGroup(
                    title = "领跌前三",
                    items = overview.topLosers,
                    emptyText = "暂无下跌标的",
                    color = signals.onNegativeContainer,
                    onOpenMarket = onOpenMarket,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            // 单边行情或大字号不保留空半栏，两组内容按实际高度展开。
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                RankingGroup("领涨前三", overview.topGainers, "暂无上涨标的", signals.onPositiveContainer, onOpenMarket)
                RankingGroup("领跌前三", overview.topLosers, "暂无下跌标的", signals.onNegativeContainer, onOpenMarket)
            }
        }
    }
}

@Composable
private fun RankingGroup(
    title: String,
    items: List<WatchlistMover>,
    emptyText: String,
    color: Color,
    onOpenMarket: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val signals = rememberFinanceSignalColors()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            color = color,
        )
        if (items.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.bodySmall, color = signals.onNeutralContainer)
        } else {
            items.forEachIndexed { index, item ->
                key(item.targetKey) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = LocalFinanceDimensions.current.minTouchTarget)
                            .clickable(role = Role.Button, onClickLabel = "前往行情", onClick = onOpenMarket)
                            .padding(vertical = spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text((index + 1).toString(), style = MaterialTheme.typography.labelMedium, color = color)
                            Text(
                                text = item.targetName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = item.changePercent.asPercentText(),
                                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                color = color,
                            )
                        }
                        Text(
                            text = item.targetTypeLabel + " · " + item.targetCode,
                            style = MaterialTheme.typography.bodySmall,
                            color = signals.onNeutralContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactReportRow(item: ReportItem, onClick: () -> Unit) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    val signals = rememberFinanceSignalColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimensions.compactRowHeight)
            .clickable(role = Role.Button, onClickLabel = "查看报告", onClick = onClick)
            .padding(vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_phosphor_file_text_duotone),
            contentDescription = null,
            modifier = Modifier.size(dimensions.iconSize),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(item.targetName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = item.reportTypeLabel + " · " + item.timeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = signals.onNeutralContainer,
            )
        }
        SignalLabel(
            text = item.status.label,
            containerColor = when (item.status) {
                ReportStatus.Generated -> signals.positiveContainer
                ReportStatus.Generating -> MaterialTheme.colorScheme.primaryContainer
                ReportStatus.Failed -> signals.negativeContainer
                ReportStatus.Pending, ReportStatus.Unknown -> signals.neutralContainer
            },
            contentColor = when (item.status) {
                ReportStatus.Generated -> signals.onPositiveContainer
                ReportStatus.Generating -> MaterialTheme.colorScheme.onPrimaryContainer
                ReportStatus.Failed -> signals.onNegativeContainer
                ReportStatus.Pending, ReportStatus.Unknown -> signals.onNeutralContainer
            },
        )
    }
}

@Composable
private fun SignalLabel(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelMedium,
) {
    val spacing = LocalFinanceSpacing.current
    Text(
        text = text,
        modifier = modifier
            .background(containerColor, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = spacing.sm, vertical = spacing.xxs),
        style = style,
        color = contentColor,
    )
}

@Composable
private fun ResearchTools(isAdmin: Boolean, onToolSelected: (ResearchTool) -> Unit) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().padding(top = spacing.xl),
    ) {
        val minItemWidth = (dimensions.minTouchTarget + spacing.lg) * fontScale
        val toolCount = ResearchTool.entries.size
        val columns = when {
            (maxWidth - spacing.sm * (toolCount - 1)) / toolCount >= minItemWidth -> toolCount
            (maxWidth - spacing.sm) / 2 >= minItemWidth -> 2
            else -> 1
        }
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            ResearchTool.entries.chunked(columns).forEach { tools ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    tools.forEach { tool ->
                        key(tool) {
                            ResearchToolEntry(
                                tool = tool,
                                enabled = !tool.adminOnly || isAdmin,
                                onClick = { onToolSelected(tool) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    repeat(columns - tools.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResearchToolEntry(
    tool: ResearchTool,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    val signals = rememberFinanceSignalColors()
    val description = if (enabled) tool.description else "仅管理员可用"
    // TooltipBox 把 modifier 用于内部 anchor；权重必须挂在 Row 的直接子节点上。
    Box(modifier = modifier) {
        TooltipBox(
            modifier = Modifier.fillMaxWidth(),
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(description) } },
            state = rememberTooltipState(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                    .semantics { contentDescription = tool.label + "，" + description }
                    .padding(spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Box(
                    modifier = Modifier
                        .size(dimensions.minTouchTarget)
                        .background(
                            if (enabled) MaterialTheme.colorScheme.primaryContainer else signals.neutralContainer,
                            MaterialTheme.shapes.medium,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(tool.iconRes()),
                        contentDescription = null,
                        modifier = Modifier.size(spacing.section),
                        tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else signals.onNeutralContainer,
                    )
                    if (!enabled) {
                        Icon(
                            painter = painterResource(R.drawable.ic_phosphor_lock_simple),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(signals.neutralContainer, MaterialTheme.shapes.extraSmall)
                                .size(spacing.lg),
                            tint = signals.onNeutralContainer,
                        )
                    }
                }
                Text(
                    text = tool.label,
                    modifier = Modifier.clearAndSetSemantics {},
                    minLines = 2,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else signals.onNeutralContainer,
                    textAlign = TextAlign.Center,
                )
                if (!enabled) {
                    Text(
                        text = description,
                        modifier = Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.bodySmall,
                        color = signals.onNeutralContainer,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentState(text: String, isLoading: Boolean = false) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    val signals = rememberFinanceSignalColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimensions.listRowHeight)
            .padding(vertical = spacing.md)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(dimensions.iconSize),
                strokeWidth = dimensions.outlineWidth,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = signals.onNeutralContainer,
        )
    }
}

@Composable
private fun SyncMessage(message: String, isRefreshing: Boolean, onRetry: () -> Unit) {
    val spacing = LocalFinanceSpacing.current
    val signals = rememberFinanceSignalColors()
    Row(
        modifier = Modifier
            .padding(top = spacing.lg)
            .fillMaxWidth()
            .background(signals.negativeContainer, MaterialTheme.shapes.small)
            .padding(spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_phosphor_warning_circle),
            contentDescription = null,
            modifier = Modifier.size(LocalFinanceDimensions.current.iconSize),
            tint = signals.onNegativeContainer,
        )
        Column(
            modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
            },
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = "同步未完成",
                style = MaterialTheme.typography.labelLarge,
                color = signals.onNegativeContainer,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = signals.onNegativeContainer,
            )
        }
        TextButton(
            onClick = onRetry,
            enabled = !isRefreshing,
            modifier = Modifier.heightIn(min = LocalFinanceDimensions.current.minTouchTarget),
            colors = ButtonDefaults.textButtonColors(contentColor = signals.onNegativeContainer),
        ) {
            Text("重试")
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

private fun Double?.asPercentText(): String {
    if (this == null) return "暂无数据"
    val prefix = if (this > 0.0) "+" else ""
    return prefix + String.format(Locale.CHINA, PERCENT_FORMAT, this)
}

@DrawableRes
private fun ResearchTool.iconRes(): Int = when (this) {
    ResearchTool.Report -> R.drawable.ic_phosphor_file_text_duotone
    ResearchTool.KnowledgeSearch -> R.drawable.ic_phosphor_magnifying_glass_duotone
    ResearchTool.MaterialImport -> R.drawable.ic_phosphor_upload_simple_duotone
    ResearchTool.AiAssistant -> R.drawable.ic_phosphor_brain_duotone
}

private const val OVERVIEW_COLUMN_WIDTH_UNITS = 1.5f
private const val PERCENT_FORMAT = "%.2f%%"
