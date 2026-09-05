package com.scrapider.finance.androidapp.feature.market.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSemanticColors
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.feature.market.MarketAlert
import com.scrapider.finance.androidapp.feature.market.MarketIndexQuote
import com.scrapider.finance.androidapp.feature.market.MarketWatchItem
import com.scrapider.finance.androidapp.feature.market.marketTargetTypeLabel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class MarketAttentionItem(
    val item: MarketWatchItem,
    val alert: MarketAlert,
)

@Composable
internal fun MarketSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier.semantics { heading() },
        style = MiuixTheme.textStyles.title2,
        color = MiuixTheme.colorScheme.onSurface,
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
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MarketSectionTitle(title = "需要查看")
            Text(
                text = if (totalItemCount > items.size) {
                    "已触发 $totalItemCount 条 · 优先展示 ${items.size} 条"
                } else {
                    "已触发 $totalItemCount 条"
                },
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(spacing.lg))
        Card(modifier = Modifier.fillMaxWidth()) {
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
    val semanticColors = LocalFinanceSemanticColors.current
    BasicComponent(
        title = item.targetName,
        summary = alertText,
        onClick = onOpenDetail,
        onClickLabel = "查看${item.targetName}详情",
        endActions = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = item.changePercent.asPercentText(),
                    style = MiuixTheme.textStyles.title3,
                    color = item.changePercent.marketChangeColor(
                        positive = semanticColors.positive,
                        negative = semanticColors.negative,
                        neutral = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    ),
                )
                Text(
                    text = "已越界",
                    style = MiuixTheme.textStyles.footnote1,
                    color = semanticColors.warning,
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

        else -> Column(modifier = modifier.fillMaxWidth()) {
            indices.forEachIndexed { index, item ->
                MarketIndexRow(item = item)
                if (index < indices.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun MarketIndexRow(item: MarketIndexQuote) {
    val spacing = LocalFinanceSpacing.current
    val semanticColors = LocalFinanceSemanticColors.current
    BasicComponent(
        title = item.name,
        endActions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.latestPrice.asPriceText(),
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = item.changePercent.asPercentText(),
                    modifier = Modifier.widthIn(min = spacing.xxl * 3),
                    textAlign = TextAlign.End,
                    style = MiuixTheme.textStyles.body1,
                    color = item.changePercent.marketChangeColor(
                        positive = semanticColors.positive,
                        negative = semanticColors.negative,
                        neutral = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    ),
                )
            }
        },
    )
}

@Composable
internal fun MarketCurrentViewHeader(
    title: String,
    itemCount: Int,
    onAddTargets: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(LocalFinanceSpacing.current.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MarketSectionTitle(title = title)
            Text(
                text = "$itemCount 个标的",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        onAddTargets?.let { onAdd ->
            TextButton(text = "添加", onClick = onAdd)
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
