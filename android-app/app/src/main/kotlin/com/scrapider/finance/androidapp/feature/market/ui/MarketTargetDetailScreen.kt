package com.scrapider.finance.androidapp.feature.market.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.feature.market.MarketAlert
import com.scrapider.finance.androidapp.feature.market.MarketChartAdjust
import com.scrapider.finance.androidapp.feature.market.MarketChartPeriod
import com.scrapider.finance.androidapp.feature.market.MarketDetailQuote
import com.scrapider.finance.androidapp.feature.market.MarketDetailState
import com.scrapider.finance.androidapp.feature.market.MarketQuoteMetric
import com.scrapider.finance.androidapp.feature.market.MarketTargetSnapshot
import com.scrapider.finance.androidapp.feature.market.marketTargetTypeLabel
import com.scrapider.finance.androidapp.feature.market.theme.LocalMarketSignalColors
import java.util.Locale
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val COLLAPSED_METRIC_COUNT = 9

@Composable
internal fun MarketTargetDetailScreen(
    target: MarketTargetSnapshot,
    detail: MarketDetailState,
    onNavigateBack: () -> Unit,
    onOpenTargetSettings: (() -> Unit)?,
    onPeriodSelected: (MarketChartPeriod) -> Unit,
    onAdjustSelected: (MarketChartAdjust) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    val fontScale = LocalDensity.current.fontScale
    val targetKey = detail.targetKey.ifBlank { target.targetKey }
    var extraMetricsExpanded by rememberSaveable(targetKey) { mutableStateOf(false) }
    val quote = detail.quote
    val preferred = if (target.targetType == "BOND") listOf("均价", "转股溢价率") else listOf("均价", "量比")
    val allMetrics = quote?.let {
        (it.primaryMetrics + it.extraMetrics.filter { metric -> metric.label in preferred } + it.extraMetrics)
            .distinctBy(MarketQuoteMetric::label)
    }.orEmpty()
    val visibleMetrics = if (extraMetricsExpanded) allMetrics else allMetrics.take(COLLAPSED_METRIC_COUNT)
    val chartInteraction = rememberMarketChartInteraction(detail)
    var chartExpanded by rememberSaveable(targetKey) { mutableStateOf(false) }
    MarketChartOrientation(expanded = chartExpanded)

    if (chartExpanded) MarketFullscreenChart(
        targetName = target.targetName,
        targetCode = target.targetCode,
        targetType = target.targetType,
        detail = detail,
        interaction = chartInteraction,
        onPeriodSelected = onPeriodSelected,
        onAdjustSelected = onAdjustSelected,
        onDismiss = { chartExpanded = false },
    )

    Scaffold(
        modifier = modifier,
        topBar = { MarketPageTopBar(title = "标的详情", onNavigateBack = onNavigateBack) },
    ) { contentPadding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(
                start = spacing.xl,
                top = spacing.lg,
                end = spacing.xl,
                bottom = spacing.section,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item(key = "target-quote") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(
                            text = target.targetName,
                            modifier = Modifier.semantics { heading() },
                            style = MiuixTheme.textStyles.title2,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${target.targetCode} · ${target.targetType.marketTargetTypeLabel()}",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val columnMinWidth = dimensions.toolRowHeight * fontScale
                        val columnCount = if (maxWidth >= columnMinWidth * 2 + spacing.section) 2 else 1
                        if (columnCount == 1) {
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
                                MarketDetailPrimaryQuote(
                                    quote = quote,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                MarketDetailChangeQuote(
                                    quote = quote,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                MarketQuoteState(
                                    detail = detail,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(spacing.section),
                                ) {
                                    MarketDetailPrimaryQuote(
                                        quote = quote,
                                        modifier = Modifier.weight(1f),
                                    )
                                    MarketDetailChangeQuote(
                                        quote = quote,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                MarketQuoteState(
                                    detail = detail,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
            if (visibleMetrics.isNotEmpty()) {
                item(key = "metrics") {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = quote?.timeLabel?.takeIf(String::isNotBlank)?.let { "数据同步 $it" } ?: "行情数据",
                                modifier = Modifier.weight(1f),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            if (allMetrics.size > COLLAPSED_METRIC_COUNT) {
                                androidx.compose.foundation.layout.Box(
                                    Modifier.heightIn(min = dimensions.minTouchTarget).widthIn(min = dimensions.minTouchTarget)
                                        .clickable(role = Role.Button) { extraMetricsExpanded = !extraMetricsExpanded }
                                        .semantics { stateDescription = if (extraMetricsExpanded) "已展开" else "已收起" }
                                        .padding(horizontal = spacing.md),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(if (extraMetricsExpanded) "收起" else "更多",
                                        style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.primary)
                                }
                            }
                        }
                        MarketPrimaryMetrics(metrics = visibleMetrics)
                    }
                }
            }
            item(key = "market-chart") {
                HorizontalDivider(Modifier.padding(bottom = spacing.sm))
                MarketChartSection(
                    targetName = target.targetName,
                    targetType = target.targetType,
                    detail = detail,
                    interaction = chartInteraction,
                    onPeriodSelected = onPeriodSelected,
                    onAdjustSelected = onAdjustSelected,
                    onExpand = { chartExpanded = true },
                )
            }
            item(key = "watch-details") {
                MarketTargetDetailSection(title = "自选与提醒") {
                    if (target.watchItemId == null) {
                        MarketTargetDetailValue(label = "自选状态", value = "未加入自选池")
                    } else {
                        MarketTargetDetailValue(
                            label = "所在自选池",
                            value = target.watchGroupName.orEmpty().ifBlank { "当前自选池" },
                        )
                        HorizontalDivider()
                        MarketTargetDetailValue(label = "提醒", value = target.alert.detailLabel())
                        target.buyPrice?.let { buyPrice ->
                            HorizontalDivider()
                            MarketTargetDetailValue(label = "买入价", value = buyPrice.asPriceText())
                        }
                        target.position?.let { position ->
                            HorizontalDivider()
                            MarketTargetDetailValue(label = "持仓数量", value = position.asPositionText())
                        }
                        target.remark?.takeIf(String::isNotBlank)?.let { remark ->
                            HorizontalDivider()
                            MarketTargetDetailValue(label = "备注", value = remark, stacked = true)
                        }
                        if (onOpenTargetSettings != null) {
                            TextButton(
                                text = "标的设置",
                                onClick = onOpenTargetSettings,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = spacing.lg)
                                    .heightIn(min = dimensions.minTouchTarget),
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketDetailPrimaryQuote(
    quote: MarketDetailQuote?,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = "最新价",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = quote?.latestPrice?.asPriceText() ?: "—",
            style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
            color = when {
                (quote?.changePercent ?: 0.0) > 0 -> LocalMarketSignalColors.current.onPositiveContainer
                (quote?.changePercent ?: 0.0) < 0 -> LocalMarketSignalColors.current.onNegativeContainer
                else -> MiuixTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun MarketDetailChangeQuote(
    quote: MarketDetailQuote?,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = "当日涨跌幅",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        if (quote?.changePercent == null) {
            Text(
                text = "—",
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        } else {
            MarketChangeLabel(
                changePercent = quote.changePercent,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}

@Composable
private fun MarketQuoteState(
    detail: MarketDetailState,
    modifier: Modifier,
) {
    val message = when {
        detail.quoteUnavailable && detail.quote != null -> "暂未获得该标的最新报价，显示上次数据"
        detail.quoteUnavailable -> "该标的暂无报价"
        detail.quoteLoading && detail.quote != null -> "正在更新报价，暂时显示上次数据"
        detail.quoteError.isNotBlank() && detail.quote != null ->
            "报价更新失败，已保留上次数据：${detail.quoteError}"
        detail.quoteLoading -> "正在加载报价"
        detail.quoteError.isNotBlank() -> "报价加载失败：${detail.quoteError} · 将自动重试"
        detail.quote == null -> "暂无报价"
        else -> ""
    }
    if (message.isNotBlank()) {
        Text(
            text = message,
            modifier = modifier,
            style = MiuixTheme.textStyles.body2,
            color = if (detail.quoteError.isNotBlank()) {
                MiuixTheme.colorScheme.error
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
        )
    }
}

@Composable
private fun MarketPrimaryMetrics(metrics: List<MarketQuoteMetric>) {
    val spacing = LocalFinanceSpacing.current
    Column {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val minCellWidth = LocalFinanceDimensions.current.controlHeight * 1.5f * fontScale
            val columns = when {
                maxWidth >= minCellWidth * 3 + spacing.sm * 2 -> 3
                maxWidth >= minCellWidth * 2 + spacing.sm -> 2
                else -> 1
            }
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                metrics.chunked(columns).forEach { rowMetrics ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        rowMetrics.forEach { metric ->
                            MarketMetricValue(
                                metric = metric,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - rowMetrics.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketMetricValue(
    metric: MarketQuoteMetric,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = metric.label.ifBlank { "指标" },
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = metric.value.ifBlank { "—" },
            style = MiuixTheme.textStyles.body1.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Medium),
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MarketTargetDetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        MarketSectionTitle(title = title)
        Column(content = { content() })
    }
}

@Composable
private fun MarketTargetDetailValue(
    label: String,
    value: String,
    stacked: Boolean = false,
) {
    val spacing = LocalFinanceSpacing.current
    val valueModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = spacing.md)
        .semantics(mergeDescendants = true) {}
    if (stacked) {
        Column(
            modifier = valueModifier,
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(text = value, style = MiuixTheme.textStyles.body1)
        }
    } else {
        Row(
            modifier = valueModifier,
            horizontalArrangement = Arrangement.spacedBy(spacing.lg),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = value,
                modifier = Modifier.weight(2f),
                textAlign = TextAlign.End,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun MarketAlert?.detailLabel(): String = when {
    this == null -> "未设置提醒"
    !enabled -> "提醒已停用"
    outOfThreshold -> "提醒已触发"
    else -> "提醒已开启"
}

private fun Double.asPositionText(): String =
    String.format(Locale.CHINA, "%.2f", this).trimEnd('0').trimEnd('.')
