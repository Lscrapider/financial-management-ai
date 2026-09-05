package com.scrapider.finance.androidapp.feature.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSemanticColors
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing

@Composable
internal fun MarketTargetDetailScreen(
    target: MarketTargetSnapshot,
    onNavigateBack: () -> Unit,
    onOpenTargetSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val semanticColors = LocalFinanceSemanticColors.current
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { MarketTopAppBar(title = "标的详情", onNavigateBack = onNavigateBack) },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(
                start = spacing.xl,
                top = spacing.lg,
                end = spacing.xl,
                bottom = spacing.section,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.section),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Text(
                            text = target.targetName,
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = target.targetCode + " · " + target.targetType.marketTargetTypeLabel(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(spacing.xs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text = target.latestPrice.asPriceText(),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = target.changePercent.asPercentText(),
                                style = MaterialTheme.typography.headlineSmall,
                                color = target.changePercent.marketChangeColor(
                                    positive = semanticColors.positive,
                                    negative = semanticColors.negative,
                                    neutral = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
            item {
                MarketTargetDetailSection(title = "行情摘要") {
                    MarketTargetDetailValue(label = "最新价", value = target.latestPrice.asPriceText())
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MarketTargetDetailValue(label = "当日涨跌幅", value = target.changePercent.asPercentText())
                }
            }
            item {
                MarketTargetDetailSection(title = "自选与提醒") {
                    if (target.watchItemId == null) {
                        MarketTargetDetailValue(label = "自选状态", value = "未加入自选池")
                    } else {
                        MarketTargetDetailValue(
                            label = "所在自选池",
                            value = target.watchGroupName.orEmpty().ifBlank { "当前自选池" },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        MarketTargetDetailValue(
                            label = "提醒",
                            value = target.alert.detailLabel(),
                        )
                        if (target.buyPrice != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            MarketTargetDetailValue(
                                label = "买入价",
                                value = target.buyPrice.asPriceText(),
                            )
                        }
                        if (target.position != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            MarketTargetDetailValue(
                                label = "持仓数量",
                                value = target.position.asPositionText(),
                            )
                        }
                        target.remark?.takeIf(String::isNotBlank)?.let { remark ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            MarketTargetDetailValue(label = "备注", value = remark)
                        }
                        if (onOpenTargetSettings != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            TextButton(
                                onClick = onOpenTargetSettings,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = LocalFinanceDimensions.current.minTouchTarget),
                            ) {
                                Text("标的设置")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MarketMissingTargetDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { MarketTopAppBar(title = "标的详情", onNavigateBack = onNavigateBack) },
    ) { contentPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = spacing.xl, vertical = spacing.section),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = "当前未找到该标的的行情数据",
                modifier = Modifier.padding(spacing.lg),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MarketTargetDetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                width = LocalFinanceDimensions.current.outlineWidth,
                color = MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun MarketTargetDetailValue(
    label: String,
    value: String,
) {
    val spacing = LocalFinanceSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalFinanceDimensions.current.compactRowHeight)
            .padding(horizontal = spacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun MarketAlert?.detailLabel(): String = when {
    this == null -> "未设置提醒"
    !enabled -> "提醒已停用"
    outOfThreshold -> "提醒已触发"
    else -> "提醒已开启"
}

private fun Double.asPositionText(): String =
    String.format(java.util.Locale.CHINA, "%.2f", this).trimEnd('0').trimEnd('.')
