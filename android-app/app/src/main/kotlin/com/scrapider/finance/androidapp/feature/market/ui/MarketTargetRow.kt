package com.scrapider.finance.androidapp.feature.market.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.scrapider.finance.androidapp.designsystem.FinanceSemanticColors
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSemanticColors
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.feature.market.MarketAlert
import com.scrapider.finance.androidapp.feature.market.MarketSystemTarget
import com.scrapider.finance.androidapp.feature.market.MarketWatchItem
import com.scrapider.finance.androidapp.feature.market.marketTargetTypeLabel
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MarketSystemTargetRow(
    target: MarketSystemTarget,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicComponent(
        modifier = modifier,
        title = target.targetName,
        summary = "${target.targetCode} · ${target.targetType.marketTargetTypeLabel()}",
        onClick = onOpenDetail,
        onClickLabel = "查看${target.targetName}详情",
        endActions = {
            MarketPriceChange(
                latestPrice = target.latestPrice,
                changePercent = target.changePercent,
            )
        },
    )
}

@Composable
internal fun MarketWatchTargetRow(
    item: MarketWatchItem,
    alert: MarketAlert?,
    onOpenDetail: () -> Unit,
    onOpenSettings: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicComponent(
        modifier = modifier,
        title = item.targetName,
        summary = "${item.targetCode} · ${item.targetType.marketTargetTypeLabel()}",
        onClick = onOpenDetail,
        onClickLabel = "查看${item.targetName}详情",
        bottomAction = { MarketAlertSummary(alert = alert) },
        endActions = {
            MarketPriceChange(
                latestPrice = item.latestPrice,
                changePercent = item.changePercent,
            )
            MarketTargetActions(
                targetName = item.targetName,
                onOpenSettings = onOpenSettings,
                onDelete = onDelete,
            )
        },
    )
}

@Composable
internal fun DeleteMarketTargetDialog(
    target: MarketWatchItem?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    MarketConfirmationDialog(
        show = target != null,
        title = "移除标的",
        message = target?.let { item -> "确定从当前自选池移除“${item.targetName}”吗？" }.orEmpty(),
        confirmText = "移除",
        isSaving = isSaving,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
private fun MarketPriceChange(
    latestPrice: Double?,
    changePercent: Double?,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val semanticColors = LocalFinanceSemanticColors.current
    Column(
        modifier = modifier.padding(end = spacing.sm),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(
            text = latestPrice.asPriceText(),
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = changePercent.asPercentText(),
            modifier = Modifier.widthIn(min = spacing.xxl * 3),
            textAlign = TextAlign.End,
            style = MiuixTheme.textStyles.body2,
            color = changePercent.marketChangeColor(
                positive = semanticColors.positive,
                negative = semanticColors.negative,
                neutral = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            ),
        )
    }
}

@Composable
private fun MarketTargetActions(
    targetName: String,
    onOpenSettings: () -> Unit,
    onDelete: () -> Unit,
) {
    var showActions by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { showActions = true }) {
        Icon(
            imageVector = Icons.Outlined.MoreVert,
            contentDescription = "更多操作：$targetName",
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
    }
    OverlayBottomSheet(
        show = showActions,
        title = targetName,
        onDismissRequest = { showActions = false },
    ) {
        BasicComponent(
            title = "标的设置",
            summary = "配置提醒和个人记录",
            onClick = {
                showActions = false
                onOpenSettings()
            },
        )
        BasicComponent(
            title = "从自选池移除",
            summary = "移除后不再出现在当前自选池",
            onClick = {
                showActions = false
                onDelete()
            },
            titleColor = top.yukonga.miuix.kmp.basic.BasicComponentDefaults.titleColor(
                color = MiuixTheme.colorScheme.error,
            ),
        )
    }
}

@Composable
private fun MarketAlertSummary(alert: MarketAlert?) {
    val spacing = LocalFinanceSpacing.current
    val semanticColors = LocalFinanceSemanticColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when {
                alert == null -> "未设置提醒"
                alert.enabled -> "提醒已开启 · 阈值 ±${alert.thresholdPercent.asThresholdText()}"
                else -> "提醒已停用 · 阈值 ±${alert.thresholdPercent.asThresholdText()}"
            },
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (alert?.enabled == true && alert.outOfThreshold) {
            Text(
                text = "已越界",
                style = MiuixTheme.textStyles.footnote1,
                color = semanticColors.warning,
            )
        }
    }
}

internal fun Double?.asPriceText(): String {
    if (this == null) return "暂无数据"
    return DecimalFormat("#,##0.00#", DecimalFormatSymbols.getInstance(Locale.CHINA)).format(this)
}

internal fun Double?.asPercentText(): String {
    if (this == null) return "暂无数据"
    val prefix = if (this > 0.0) "+" else ""
    return prefix + String.format(Locale.CHINA, "%.2f%%", this)
}

internal fun Double?.asThresholdText(): String =
    this?.let { value -> String.format(Locale.CHINA, "%.2f%%", value) } ?: "暂无数据"

internal fun Double?.marketChangeColor(
    positive: Color,
    negative: Color,
    neutral: Color,
): Color = when {
    this == null -> neutral
    this > 0.0 -> positive
    this < 0.0 -> negative
    else -> neutral
}
