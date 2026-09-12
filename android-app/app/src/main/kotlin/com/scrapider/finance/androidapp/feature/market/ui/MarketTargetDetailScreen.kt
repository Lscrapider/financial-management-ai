package com.scrapider.finance.androidapp.feature.market.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.feature.market.MarketAlert
import com.scrapider.finance.androidapp.feature.market.MarketTargetSnapshot
import com.scrapider.finance.androidapp.feature.market.marketTargetTypeLabel
import java.util.Locale
import top.yukonga.miuix.kmp.basic.ButtonDefaults
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
    val dimensions = LocalFinanceDimensions.current
    val fontScale = LocalDensity.current.fontScale
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
            item(key = "target-quote") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xxl)) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(
                            text = target.targetName,
                            modifier = Modifier.semantics { heading() },
                            style = MiuixTheme.textStyles.title1,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${target.targetCode} · ${target.targetType.marketTargetTypeLabel()}",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.section),
                        verticalArrangement = Arrangement.spacedBy(spacing.lg),
                    ) {
                        Column(
                            modifier = Modifier.widthIn(min = dimensions.toolRowHeight * fontScale),
                            verticalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            Text(
                                text = "最新价",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Text(
                                text = target.latestPrice.asPriceText(),
                                style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                        Column(
                            modifier = Modifier.widthIn(min = dimensions.toolRowHeight * fontScale),
                            verticalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            Text(
                                text = "当日涨跌幅",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            MarketChangeLabel(
                                changePercent = target.changePercent,
                                style = MaterialTheme.typography.headlineLarge,
                            )
                        }
                    }
                }
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
