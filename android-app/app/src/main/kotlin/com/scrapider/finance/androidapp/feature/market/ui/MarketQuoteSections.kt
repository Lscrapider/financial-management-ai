package com.scrapider.finance.androidapp.feature.market.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.feature.market.MarketAlert
import com.scrapider.finance.androidapp.feature.market.MarketIndexQuote
import com.scrapider.finance.androidapp.feature.market.MarketWatchItem
import com.scrapider.finance.androidapp.feature.market.marketTargetTypeLabel
import com.scrapider.finance.androidapp.feature.market.theme.LocalMarketSignalColors
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class MarketAttentionItem(
    val item: MarketWatchItem,
    val alert: MarketAlert,
)

@Composable
internal fun MarketSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.onSurface,
) {
    Text(
        text = title,
        modifier = modifier.semantics { heading() },
        style = MiuixTheme.textStyles.title2,
        color = color,
    )
}

@Composable
internal fun MarketAttentionSection(
    items: List<MarketAttentionItem>,
    totalItemCount: Int,
    onOpenTargetDetail: (String, String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val signals = LocalMarketSignalColors.current
    Column(modifier = modifier) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            MarketSectionTitle(title = "需要查看", color = signals.onWarningContainer)
            Text(
                text = if (totalItemCount > items.size) {
                    "已触发 $totalItemCount 条 · 优先展示 ${items.size} 条"
                } else {
                    "已触发 $totalItemCount 条"
                },
                style = MiuixTheme.textStyles.body2,
                color = signals.onWarningContainer,
            )
        }
        Spacer(Modifier.height(spacing.lg))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = signals.warningContainer,
                contentColor = signals.onWarningContainer,
            ),
        ) {
            items.forEachIndexed { index, attentionItem ->
                MarketAttentionRow(
                    attentionItem = attentionItem,
                    onOpenDetail = {
                        onOpenTargetDetail(
                            attentionItem.item.targetType,
                            attentionItem.item.targetCode,
                            attentionItem.item.id,
                        )
                    },
                )
                if (index < items.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun MarketAttentionRow(
    attentionItem: MarketAttentionItem,
    onOpenDetail: () -> Unit,
) {
    val item = attentionItem.item
    val alertText = attentionItem.alert.thresholdPercent?.let { threshold ->
        "提醒阈值 ±${threshold.asThresholdText()} 已越界"
    } ?: "提醒阈值已越界"
    val signals = LocalMarketSignalColors.current
    BasicComponent(
        title = item.targetName,
        summary = alertText,
        summaryColor = BasicComponentDefaults.summaryColor(color = signals.onWarningContainer),
        onClick = onOpenDetail,
        onClickLabel = "查看${item.targetName}详情",
        endActions = {
            Column(horizontalAlignment = Alignment.End) {
                MarketChangeLabel(
                    changePercent = item.changePercent,
                    style = MiuixTheme.textStyles.title3,
                )
                Text(
                    text = "已越界",
                    style = MiuixTheme.textStyles.footnote1,
                    color = signals.onWarningContainer,
                )
            }
        },
    )
}

@Composable
internal fun MarketOverviewContent(
    indices: List<MarketIndexQuote>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    when {
        isLoading && indices.isEmpty() -> MarketLoadingPanel(
            text = "正在同步指数行情",
            modifier = modifier,
        )

        indices.isEmpty() -> MarketEmptyPanel(
            text = "暂未获取到指数行情",
            modifier = modifier,
        )

        else -> BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            val spacing = LocalFinanceSpacing.current
            val minColumnWidth = LocalFinanceDimensions.current.toolRowHeight * LocalDensity.current.fontScale
            val columnWidth = (maxWidth - spacing.lg * (indices.size - 1)) / indices.size
            if (columnWidth >= minColumnWidth) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                    indices.forEach { item ->
                        MarketIndexQuoteContent(item = item, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    indices.forEach { item ->
                        MarketIndexQuoteContent(item = item, compact = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketIndexQuoteContent(
    item: MarketIndexQuote,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val spacing = LocalFinanceSpacing.current
    val signals = LocalMarketSignalColors.current
    val quote: @Composable () -> Unit = {
        Text(
            text = item.latestPrice.asPriceText(),
            style = MiuixTheme.textStyles.title3.copy(fontFeatureSettings = "tnum"),
            color = item.changePercent.marketChangeColor(
                positive = signals.onPositiveContainer,
                negative = signals.onNegativeContainer,
                neutral = MiuixTheme.colorScheme.onSurface,
            ),
        )
        MarketChangeLabel(changePercent = item.changePercent)
    }
    if (compact) {
        Row(
            modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.name,
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Column(horizontalAlignment = Alignment.End, content = { quote() })
        }
    } else {
        Column(
            modifier = modifier.semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = item.name,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            quote()
        }
    }
}

@Composable
internal fun MarketCurrentViewHeader(
    title: String,
    itemCount: Int,
    onAddTargets: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            MarketSectionTitle(title = title)
            Text(
                text = "$itemCount 个标的",
                modifier = Modifier.align(Alignment.CenterVertically),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        onAddTargets?.let { onAdd ->
            TextButton(
                onClick = onAdd,
                modifier = Modifier.heightIn(min = dimensions.minTouchTarget),
                contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xs),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MiuixTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = "添加",
                    style = MiuixTheme.textStyles.button,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
internal fun MarketTargetTypeHeader(
    targetType: String,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LocalFinanceSpacing.current.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = targetType.marketTargetTypeLabel(),
            modifier = Modifier.semantics { heading() },
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = "$itemCount 个标的",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
internal fun MarketSyncNotice(
    message: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(
            text = "同步提示",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.error,
        )
        Text(
            text = message,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
