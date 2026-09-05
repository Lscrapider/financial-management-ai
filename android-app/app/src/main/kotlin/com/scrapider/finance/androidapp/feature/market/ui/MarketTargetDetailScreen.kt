package com.scrapider.finance.androidapp.feature.market.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.scrapider.finance.androidapp.feature.market.MarketTargetSnapshot
import com.scrapider.finance.androidapp.feature.market.marketTargetTypeLabel
import java.util.Locale
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
            verticalArrangement = Arrangement.spacedBy(spacing.section),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(
                            text = target.targetName,
                            modifier = Modifier.semantics { heading() },
                            style = MiuixTheme.textStyles.title1,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${target.targetCode} · ${target.targetType.marketTargetTypeLabel()}",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(spacing.xs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text = target.latestPrice.asPriceText(),
                                style = MiuixTheme.textStyles.title1,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = target.changePercent.asPercentText(),
                                style = MiuixTheme.textStyles.title2,
                                color = target.changePercent.marketChangeColor(
                                    positive = semanticColors.positive,
                                    negative = semanticColors.negative,
                                    neutral = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                ),
                            )
                        }
                    }
                }
            }
            item {
                MarketTargetDetailSection(title = "行情摘要") {
                    MarketTargetDetailValue(label = "最新价", value = target.latestPrice.asPriceText())
                    HorizontalDivider()
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
                            MarketTargetDetailValue(label = "备注", value = remark)
                        }
                        if (onOpenTargetSettings != null) {
                            HorizontalDivider()
                            TextButton(
                                text = "标的设置",
                                onClick = onOpenTargetSettings,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
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
        MarketSectionTitle(title = title)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(content = { content() })
        }
    }
}

@Composable
private fun MarketTargetDetailValue(
    label: String,
    value: String,
) {
    BasicComponent(
        title = label,
        endActions = {
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

private fun MarketAlert?.detailLabel(): String = when {
    this == null -> "未设置提醒"
    !enabled -> "提醒已停用"
    outOfThreshold -> "提醒已触发"
    else -> "提醒已开启"
}

private fun Double.asPositionText(): String =
    String.format(Locale.CHINA, "%.2f", this).trimEnd('0').trimEnd('.')
