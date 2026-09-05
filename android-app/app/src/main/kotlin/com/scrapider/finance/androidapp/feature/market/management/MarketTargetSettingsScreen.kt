package com.scrapider.finance.androidapp.feature.market.management

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.feature.market.ALERT_THRESHOLD_STEP_PERCENT
import com.scrapider.finance.androidapp.feature.market.DEFAULT_ALERT_THRESHOLD_PERCENT
import com.scrapider.finance.androidapp.feature.market.MAX_ALERT_THRESHOLD_PERCENT
import com.scrapider.finance.androidapp.feature.market.MIN_ALERT_THRESHOLD_PERCENT
import com.scrapider.finance.androidapp.feature.market.MarketAlert
import com.scrapider.finance.androidapp.feature.market.MarketTargetSettingsInput
import com.scrapider.finance.androidapp.feature.market.MarketWatchItem
import com.scrapider.finance.androidapp.feature.market.marketTargetTypeLabel
import com.scrapider.finance.androidapp.feature.market.supportsAlert
import com.scrapider.finance.androidapp.feature.market.ui.MarketPageTopBar
import java.util.Locale
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val RECORD_REMARK_MIN_LINES = 3

@Composable
internal fun MarketTargetSettingsScreen(
    target: MarketWatchItem,
    groupName: String,
    alert: MarketAlert?,
    isSaving: Boolean,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onSave: (MarketTargetSettingsInput) -> Unit,
    modifier: Modifier = Modifier,
) {
    val supportsAlert = target.targetType.supportsAlert()
    var alertEnabled by rememberSaveable(target.id) { mutableStateOf(alert?.enabled ?: false) }
    var thresholdText by rememberSaveable(target.id) {
        mutableStateOf((alert?.thresholdPercent ?: DEFAULT_ALERT_THRESHOLD_PERCENT).asEditableNumber())
    }
    var buyPriceText by rememberSaveable(target.id) { mutableStateOf(target.buyPrice.asEditableNumber()) }
    var positionText by rememberSaveable(target.id) { mutableStateOf(target.position.asEditableNumber()) }
    var remarkText by rememberSaveable(target.id) { mutableStateOf(target.remark.orEmpty()) }
    val parsedThreshold = thresholdText.toNullableDouble()
    val parsedBuyPrice = buyPriceText.toNullableDouble()
    val parsedPosition = positionText.toNullableDouble()
    val hasInvalidAlertThreshold = supportsAlert && (alert != null || alertEnabled) &&
        (parsedThreshold == null ||
            parsedThreshold < MIN_ALERT_THRESHOLD_PERCENT ||
            parsedThreshold > MAX_ALERT_THRESHOLD_PERCENT)
    val hasInvalidNumericInput =
        hasInvalidAlertThreshold ||
            (buyPriceText.isNotBlank() && parsedBuyPrice == null) ||
            (positionText.isNotBlank() && parsedPosition == null)
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    Scaffold(
        modifier = modifier,
        topBar = { MarketPageTopBar(title = "标的设置", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        bottomBar = {
            Button(
                onClick = {
                    onSave(
                        MarketTargetSettingsInput(
                            item = target,
                            alert = alert,
                            alertEnabled = alertEnabled,
                            thresholdPercent = parsedThreshold,
                            buyPrice = parsedBuyPrice,
                            position = parsedPosition,
                            remark = remarkText.trim().ifBlank { null },
                        ),
                    )
                },
                enabled = !isSaving && !hasInvalidNumericInput,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = spacing.xl,
                        top = spacing.md,
                        end = spacing.xl,
                        bottom = spacing.md,
                    ),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(size = dimensions.iconSize)
                } else {
                    Text("保存设置")
                }
            }
        },
    ) { contentPadding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(
                start = spacing.xl,
                top = spacing.xl,
                end = spacing.xl,
                bottom = spacing.section,
            ),
        ) {
            item {
                Text(
                    text = target.targetName,
                    style = MiuixTheme.textStyles.title1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
            item { Spacer(Modifier.height(spacing.xs)) }
            item {
                Text(
                    text = "${target.targetCode} · ${target.targetType.marketTargetTypeLabel()}",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            item { Spacer(Modifier.height(spacing.xxl)) }
            item { TargetGroupCard(groupName = groupName) }
            item { Spacer(Modifier.height(spacing.section)) }
            item {
                Text(
                    text = "变化提醒",
                    style = MiuixTheme.textStyles.title2,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
            item { Spacer(Modifier.height(spacing.lg)) }
            item {
                TargetAlertSettingsCard(
                    supportsAlert = supportsAlert,
                    enabled = alertEnabled,
                    thresholdText = thresholdText,
                    thresholdError = hasInvalidAlertThreshold,
                    onEnabledChange = { alertEnabled = it },
                    onThresholdChange = { thresholdText = it },
                )
            }
            item { Spacer(Modifier.height(spacing.section)) }
            item {
                Text(
                    text = "个人记录（可选）",
                    style = MiuixTheme.textStyles.title2,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
            item { Spacer(Modifier.height(spacing.lg)) }
            item {
                TargetPersonalRecordCard(
                    buyPrice = buyPriceText,
                    position = positionText,
                    remark = remarkText,
                    buyPriceError = buyPriceText.isNotBlank() && parsedBuyPrice == null,
                    positionError = positionText.isNotBlank() && parsedPosition == null,
                    onBuyPriceChange = { buyPriceText = it },
                    onPositionChange = { positionText = it },
                    onRemarkChange = { remarkText = it },
                )
            }
            if (hasInvalidNumericInput) {
                item {
                    Text(
                        text = "请检查输入的数值",
                        modifier = Modifier.padding(top = spacing.md),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetGroupCard(groupName: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = "所在自选池",
            endActions = {
                Text(
                    text = groupName.ifBlank { "暂无自选池" },
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    }
}

@Composable
private fun TargetAlertSettingsCard(
    supportsAlert: Boolean,
    enabled: Boolean,
    thresholdText: String,
    thresholdError: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onThresholdChange: (String) -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = "开启变化提醒",
            summary = if (supportsAlert) "达到涨跌幅阈值时发送提醒" else "当前标的类型暂不支持提醒",
            endActions = {
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = supportsAlert,
                )
            },
        )
        if (supportsAlert) {
            HorizontalDivider()
            Column(
                modifier = Modifier.padding(top = spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = "提醒阈值",
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                TextField(
                    value = thresholdText,
                    onValueChange = onThresholdChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = "提醒阈值",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    trailingIcon = { Text("%") },
                    colors = numberTextFieldColors(thresholdError),
                )
                Text(
                    text = "默认 ${DEFAULT_ALERT_THRESHOLD_PERCENT}%，步进 ${ALERT_THRESHOLD_STEP_PERCENT}%，范围 ${MIN_ALERT_THRESHOLD_PERCENT}% 至 ${MAX_ALERT_THRESHOLD_PERCENT}%",
                    style = MiuixTheme.textStyles.body2,
                    color = if (thresholdError) {
                        MiuixTheme.colorScheme.error
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            }
        }
    }
}

@Composable
private fun TargetPersonalRecordCard(
    buyPrice: String,
    position: String,
    remark: String,
    buyPriceError: Boolean,
    positionError: Boolean,
    onBuyPriceChange: (String) -> Unit,
    onPositionChange: (String) -> Unit,
    onRemarkChange: (String) -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            RecordTextField(
                label = "买入价",
                hint = "例如：211.80",
                value = buyPrice,
                isError = buyPriceError,
                onValueChange = onBuyPriceChange,
            )
            RecordTextField(
                label = "持仓数量",
                hint = "例如：320",
                value = position,
                isError = positionError,
                onValueChange = onPositionChange,
            )
            TextField(
                value = remark,
                onValueChange = onRemarkChange,
                modifier = Modifier.fillMaxWidth(),
                label = "备注",
                minLines = RECORD_REMARK_MIN_LINES,
            )
        }
    }
}

@Composable
private fun RecordTextField(
    label: String,
    hint: String,
    value: String,
    isError: Boolean,
    onValueChange: (String) -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = label,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = numberTextFieldColors(isError),
        )
        Text(
            text = hint,
            style = MiuixTheme.textStyles.body2,
            color = if (isError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun numberTextFieldColors(isError: Boolean) = TextFieldDefaults.textFieldColors(
    labelColor = if (isError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSecondaryContainer,
    borderColor = if (isError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
)

private fun String.toNullableDouble(): Double? =
    trim().takeIf { value -> value.isNotEmpty() }?.toDoubleOrNull()

private fun Double?.asEditableNumber(): String =
    this?.let { value -> String.format(Locale.CHINA, "%.2f", value).trimEnd('0').trimEnd('.') }.orEmpty()
