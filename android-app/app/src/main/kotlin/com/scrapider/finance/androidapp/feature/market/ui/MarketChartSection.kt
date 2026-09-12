package com.scrapider.finance.androidapp.feature.market.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.scrapider.finance.androidapp.R
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.feature.market.MarketChartAdjust
import com.scrapider.finance.androidapp.feature.market.MarketChartPeriod
import com.scrapider.finance.androidapp.feature.market.MarketChartPoint
import com.scrapider.finance.androidapp.feature.market.MarketDetailState
import com.scrapider.finance.androidapp.feature.market.theme.LocalMarketSignalColors
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val DEFAULT_CHART_WINDOW = 60
private const val MAX_PRICE_GRID_LINES = 4

/** 详情报价与图表中的数字统一使用短横线表达缺失值，不借用列表的“暂无数据”文案。 */
internal fun marketDetailNumber(value: Double?): String {
    if (value == null || !value.isFinite()) return "—"
    return DecimalFormat(
        "#,##0.00#",
        DecimalFormatSymbols.getInstance(Locale.CHINA),
    ).format(value)
}

@Stable
internal class MarketChartInteraction(historyEndTime: String = "", selectedTime: String = "") {
    var historyEndTime by mutableStateOf(historyEndTime)
    var selectedTime by mutableStateOf(selectedTime)
}

@Composable
internal fun rememberMarketChartInteraction(detail: MarketDetailState): MarketChartInteraction =
    rememberSaveable(detail.targetKey, detail.period, detail.adjust,
        saver = listSaver(save = { listOf(it.historyEndTime, it.selectedTime) },
            restore = { MarketChartInteraction(it[0], it[1]) })) { MarketChartInteraction() }

@Composable
internal fun MarketChartSection(
    targetName: String,
    targetType: String,
    detail: MarketDetailState,
    interaction: MarketChartInteraction,
    onPeriodSelected: (MarketChartPeriod) -> Unit,
    onAdjustSelected: (MarketChartAdjust) -> Unit,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
    onExpand: (() -> Unit)? = null,
) {
    val dimensions = LocalFinanceDimensions.current
    val signals = LocalMarketSignalColors.current
    val points = remember(detail.points) { detail.points.filter { it.close.isFinite() } }
    val isIntraday = detail.period == MarketChartPeriod.Intraday
    val end = interaction.historyEndTime.takeIf(String::isNotBlank)?.let { anchor ->
        points.indexOfFirst { it.time == anchor }.takeIf { it > 0 }
    } ?: points.size
    val start = if (isIntraday) 0 else (end - DEFAULT_CHART_WINDOW).coerceAtLeast(0)
    val visible = points.subList(start, end)
    val selectedIndex = visible.indexOfFirst { it.time == interaction.selectedTime }
        .takeIf { it >= 0 } ?: visible.lastIndex.coerceAtLeast(0)
    val selected = visible.getOrNull(selectedIndex)
    val selectPoint: (Int) -> Unit = { index -> visible.getOrNull(index)?.let { interaction.selectedTime = it.time } }
    val earlier: () -> Unit = { visible.firstOrNull()?.let { interaction.historyEndTime = it.time } }
    val later: () -> Unit = {
        interaction.historyEndTime = points.getOrNull((end + DEFAULT_CHART_WINDOW).coerceAtMost(points.size))?.time.orEmpty()
    }
    MarketChartContentLayout(modifier, fullscreen) {
        MarketChartToolbar(detail, targetType, onPeriodSelected, onAdjustSelected)
        if (detail.chartError.isNotBlank()) MarketChartMessage(
            if (visible.isEmpty()) "${detail.chartError} 将自动重试" else "更新失败，暂时显示上次数据",
            MiuixTheme.colorScheme.error,
        )
        if (selected != null) MarketSelectedPoint(detail.period, selected)
        if (visible.isEmpty()) {
            Box(
                if (fullscreen) Modifier.fillMaxWidth().layoutId("plot")
                else Modifier.fillMaxWidth().height(dimensions.toolRowHeight * 2.5f),
                contentAlignment = Alignment.Center,
            ) {
                if (detail.chartLoading) MarketChartLoading()
                else Text("暂无${detail.period.label}数据", style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        } else {
            MarketChartCanvas(
                period = detail.period, points = visible, selectedIndex = selectedIndex,
                chartDescription = "$targetName，" + buildChartDescription(detail.period, visible, requireNotNull(selected)),
                dimensions = dimensions, signals = signals, onPointSelected = selectPoint,
                modifier = if (fullscreen) Modifier.fillMaxWidth().layoutId("plot")
                    else Modifier.fillMaxWidth().height(dimensions.toolRowHeight * 2.75f),
            )
            MarketChartRange(detail.period, visible)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!isIntraday && points.size > DEFAULT_CHART_WINDOW) {
                IconButton(onClick = earlier, enabled = start > 0) {
                    Icon(painterResource(R.drawable.ic_phosphor_arrow_right), "查看更早区间",
                        Modifier.size(dimensions.iconSize).rotate(180f),
                        tint = if (start > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                }
                Text("${start + 1}–$end / ${points.size}", style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                IconButton(onClick = later, enabled = end < points.size) {
                    Icon(painterResource(R.drawable.ic_phosphor_arrow_right), "查看更新区间",
                        Modifier.size(dimensions.iconSize),
                        tint = if (end < points.size) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                }
            } else {
                Text(if (isIntraday) points.firstOrNull()?.time?.take(10).orEmpty() else detail.period.label,
                    style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Spacer(Modifier.weight(1f))
            if (detail.chartLoading && visible.isNotEmpty()) {
                Text("更新中", style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            if (onExpand != null) {
                IconButton(onClick = onExpand) {
                    Icon(painterResource(R.drawable.ic_phosphor_arrows_out), "横向全屏查看图表",
                        Modifier.size(dimensions.iconSize), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** 先测量工具栏与读数，再分配绘图区；小窗口保留最小图高并自然滚动。 */
@Composable
private fun MarketChartContentLayout(modifier: Modifier, fullscreen: Boolean, content: @Composable () -> Unit) {
    val gap = LocalFinanceSpacing.current.xs
    val minimumPlotHeight = LocalFinanceDimensions.current.toolRowHeight * 1.5f
    if (!fullscreen) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) { content() }
        return
    }
    BoxWithConstraints(modifier) {
        val viewportHeight = maxHeight
        Layout(content = content, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { measurables, constraints ->
            val plotIndex = measurables.indexOfFirst { it.layoutId == "plot" }
            val contentConstraints = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            val placeables = measurables.mapIndexed { index, measurable ->
                if (index == plotIndex) null else measurable.measure(contentConstraints)
            }.toMutableList()
            val fixedHeight = placeables.sumOf { it?.height ?: 0 } + gap.roundToPx() * (measurables.size - 1)
            val plotHeight = (viewportHeight.roundToPx() - fixedHeight).coerceAtLeast(minimumPlotHeight.roundToPx())
            placeables[plotIndex] = measurables[plotIndex].measure(
                constraints.copy(minHeight = plotHeight, maxHeight = plotHeight),
            )
            layout(constraints.maxWidth, fixedHeight + plotHeight) {
                var top = 0
                placeables.forEach { placeable ->
                    requireNotNull(placeable).placeRelative(0, top)
                    top += placeable.height + gap.roundToPx()
                }
            }
        }
    }
}

@Composable
private fun MarketChartToolbar(
    detail: MarketDetailState,
    targetType: String,
    onPeriodSelected: (MarketChartPeriod) -> Unit,
    onAdjustSelected: (MarketChartAdjust) -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    val hasAdjust = targetType.equals("STOCK", ignoreCase = true) && detail.period != MarketChartPeriod.Intraday
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < dimensions.minTouchTarget * (4 * LocalDensity.current.fontScale + 1.5f)
        if (hasAdjust && narrow) {
            Column {
                MarketPeriodTabs(detail.period, onPeriodSelected, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MarketAdjustMenu(detail.adjust, onAdjustSelected)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                MarketPeriodTabs(detail.period, onPeriodSelected, Modifier.weight(1f))
                if (hasAdjust) MarketAdjustMenu(detail.adjust, onAdjustSelected)
            }
        }
    }
}

@Composable
private fun MarketPeriodTabs(selected: MarketChartPeriod, onSelected: (MarketChartPeriod) -> Unit, modifier: Modifier) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    Row(modifier.selectableGroup().clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        MarketChartPeriod.entries.forEach { period ->
            Box(Modifier.weight(1f).heightIn(min = dimensions.minTouchTarget)
                .selectable(period == selected, role = Role.Tab, onClick = { onSelected(period) })
                .padding(spacing.xs), contentAlignment = Alignment.Center) {
                Text(period.label, Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                    .background(if (period == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .padding(horizontal = spacing.xxs, vertical = spacing.sm),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MiuixTheme.textStyles.body1.copy(fontWeight = if (period == selected) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (period == selected) MaterialTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }
}

@Composable
private fun MarketAdjustMenu(selected: MarketChartAdjust, onSelected: (MarketChartAdjust) -> Unit) {
    val spacing = LocalFinanceSpacing.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.heightIn(min = LocalFinanceDimensions.current.minTouchTarget)
            .clickable(role = Role.Button, onClickLabel = "选择复权方式") { open = true }.padding(horizontal = spacing.sm),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(selected.label, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.primary)
            Icon(painterResource(R.drawable.ic_phosphor_arrow_right), null,
                Modifier.size(LocalFinanceDimensions.current.iconSize * 0.65f).rotate(90f), tint = MiuixTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = MaterialTheme.colorScheme.surface) {
            MarketChartAdjust.entries.forEach { adjust ->
                DropdownMenuItem(text = { Text(adjust.label, color = if (adjust == selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface) },
                    onClick = { open = false; onSelected(adjust) },
                    modifier = Modifier.semantics { this.selected = adjust == selected })
            }
        }
    }
}

@Composable
private fun MarketChartLoading(modifier: Modifier = Modifier) {
    val spacing = LocalFinanceSpacing.current
    Row(
        modifier = modifier.heightIn(min = LocalFinanceDimensions.current.compactRowHeight),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(size = LocalFinanceDimensions.current.iconSize)
        Text(
            text = "正在加载图表",
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun MarketChartMessage(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MiuixTheme.textStyles.body2,
        color = color,
    )
}

@Composable
private fun MarketChartCanvas(
    period: MarketChartPeriod,
    points: List<MarketChartPoint>,
    selectedIndex: Int,
    chartDescription: String,
    dimensions: com.scrapider.finance.androidapp.designsystem.FinanceDimensions,
    signals: com.scrapider.finance.androidapp.designsystem.FinanceSignalColors,
    onPointSelected: (Int) -> Unit,
    modifier: Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val chartUnit = dimensions.toolRowHeight / 12f
    val backgroundColor = MiuixTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    val primaryColor = MiuixTheme.colorScheme.primary
    val neutralColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val ma5Color = MaterialTheme.colorScheme.primary
    val ma10Color = MaterialTheme.colorScheme.secondary
    val ma20Color = MaterialTheme.colorScheme.tertiary
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = MiuixTheme.textStyles.body2.copy(color = neutralColor, fontFeatureSettings = "tnum")
    val bounds = remember(points, period) { priceBounds(points, period) }
    val axisLabels = (0 until MAX_PRICE_GRID_LINES).map { index ->
        val value = bounds.second - (bounds.second - bounds.first) * index / (MAX_PRICE_GRID_LINES - 1)
        textMeasurer.measure(AnnotatedString(marketDetailNumber(value)), style = axisStyle)
    }
    val axisWidthPx = (axisLabels.maxOfOrNull { it.size.width } ?: 0) + with(density) { spacing.sm.toPx() }
    Canvas(
        modifier = modifier
            .background(backgroundColor)
            .semantics {
                contentDescription = chartDescription
                progressBarRangeInfo = ProgressBarRangeInfo(selectedIndex.toFloat(), 0f..points.lastIndex.coerceAtLeast(0).toFloat())
                setProgress { value -> onPointSelected(value.toInt().coerceIn(0, points.lastIndex)); true }
                customActions = listOf(
                    CustomAccessibilityAction("查看前一个数据点") {
                        if (selectedIndex > 0) { onPointSelected(selectedIndex - 1); true } else false
                    },
                    CustomAccessibilityAction("查看后一个数据点") {
                        if (selectedIndex < points.lastIndex) { onPointSelected(selectedIndex + 1); true } else false
                    },
                )
            }
            .pointerInput(points, axisWidthPx) {
                detectHorizontalDragGestures { change, _ ->
                    if (points.isNotEmpty()) {
                        onPointSelected(pointIndexForX(change.position.x, size.width - axisWidthPx, points.size))
                    }
                }
            }
            .pointerInput(points, axisWidthPx) {
                detectTapGestures { offset ->
                    if (points.isNotEmpty()) {
                        onPointSelected(pointIndexForX(offset.x, size.width - axisWidthPx, points.size))
                    }
                }
            }
            .padding(vertical = spacing.xs),
    ) {
        drawMarketChart(
            period = period,
            points = points,
            selectedIndex = selectedIndex,
            gridColor = gridColor,
            primaryColor = primaryColor,
            neutralColor = neutralColor,
            positiveColor = signals.onPositiveContainer,
            negativeColor = signals.onNegativeContainer,
            ma5Color = ma5Color,
            ma10Color = ma10Color,
            ma20Color = ma20Color,
            chartUnit = chartUnit,
            axisWidth = axisWidthPx,
            axisLabels = axisLabels,
        )
    }
}

@Composable
private fun MarketChartRange(period: MarketChartPeriod, points: List<MarketChartPoint>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatChartTime(points.firstOrNull()?.time.orEmpty(), period), Modifier.weight(1f),
            style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(formatChartTime(points.lastOrNull()?.time.orEmpty(), period), Modifier.weight(1f),
            style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun MarketSelectedPoint(period: MarketChartPeriod, point: MarketChartPoint) {
    val spacing = LocalFinanceSpacing.current
    val signals = LocalMarketSignalColors.current
    val chartColors = MaterialTheme.colorScheme
    val entries = if (period == MarketChartPeriod.Intraday) listOf("价格" to point.close, "均价" to point.average)
        else listOf("开" to point.open, "高" to point.high, "低" to point.low, "收" to point.close)
    Column {
        FlowRow(Modifier.fillMaxWidth().padding(vertical = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(formatChartTime(point.time, period), style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            entries.forEach { (label, number) ->
                val reference = point.open
                val color = if (period == MarketChartPeriod.Intraday) {
                    if (label == "价格") MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
                }
                    else if (reference != null && number != null && number > reference) signals.onPositiveContainer
                    else if (reference != null && number != null && number < reference) signals.onNegativeContainer
                    else MiuixTheme.colorScheme.onSurface
                Text("$label ${marketDetailNumber(number)}", style = MiuixTheme.textStyles.body2.copy(fontFeatureSettings = "tnum"), color = color)
            }
            Text("量 ${marketDetailNumber(point.volume)}", style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        if (period != MarketChartPeriod.Intraday) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                listOf(
                    Triple("MA5", point.ma5, chartColors.primary),
                    Triple("MA10", point.ma10, chartColors.secondary),
                    Triple("MA20", point.ma20, chartColors.tertiary),
                ).forEach { (label, number, color) ->
                    Text(
                        "$label ${marketDetailNumber(number)}",
                        style = MiuixTheme.textStyles.body2.copy(fontFeatureSettings = "tnum"),
                        color = color,
                    )
                }
            }
        }
    }
}

private fun pointIndexForX(x: Float, width: Float, count: Int): Int {
    if (count <= 1 || width <= 0) return 0
    return (x / (width / count)).toInt().coerceIn(0, count - 1)
}

private fun priceBounds(points: List<MarketChartPoint>, period: MarketChartPeriod): Pair<Double, Double> {
    val values = points.flatMap { it.rangeValues(period) }
    val minimum = values.minOrNull() ?: 0.0
    val maximum = values.maxOrNull() ?: 0.0
    val range = maximum - minimum
    val pad = if (range == 0.0) max(abs(maximum) * 0.02, 0.01) else range * 0.04
    return minimum - pad to maximum + pad
}

private fun DrawScope.drawMarketChart(
    period: MarketChartPeriod,
    points: List<MarketChartPoint>,
    selectedIndex: Int,
    gridColor: Color,
    primaryColor: Color,
    neutralColor: Color,
    positiveColor: Color,
    negativeColor: Color,
    ma5Color: Color,
    ma10Color: Color,
    ma20Color: Color,
    chartUnit: androidx.compose.ui.unit.Dp,
    axisWidth: Float,
    axisLabels: List<TextLayoutResult>,
) {
    if (points.isEmpty() || size.height <= chartUnit.toPx() * 5f || size.width <= axisWidth) return
    val plotWidth = (size.width - axisWidth).coerceAtLeast(1f)
    val slotWidth = plotWidth / max(points.size, 1)
    val left = slotWidth / 2f
    val right = plotWidth - slotWidth / 2f
    val top = chartUnit.toPx()
    val bottom = size.height - chartUnit.toPx() * 4.25f
    val volumeTop = bottom + chartUnit.toPx() * 1.5f
    val volumeBottom = size.height - chartUnit.toPx() / 2f
    val (lower, upper) = priceBounds(points, period)
    val priceSpan = upper - lower
    val xFor = { index: Int ->
        if (points.size == 1) left else left + (right - left) * index / (points.size - 1).toFloat()
    }
    val yFor = { value: Double ->
        bottom - ((value - lower) / priceSpan).toFloat() * (bottom - top)
    }
    repeat(MAX_PRICE_GRID_LINES) { lineIndex ->
        val ratio = lineIndex.toFloat() / (MAX_PRICE_GRID_LINES - 1)
        val y = top + (bottom - top) * ratio
        drawLine(
            color = gridColor,
            start = Offset(left, y),
            end = Offset(right, y),
            strokeWidth = chartUnit.toPx() / 8f,
        )
    }
    drawLine(
        color = gridColor,
        start = Offset(left, volumeTop),
        end = Offset(right, volumeTop),
        strokeWidth = chartUnit.toPx() / 8f,
    )
    val volumeMax = points.mapNotNull { point ->
        point.volume?.takeIf { value -> value.isFinite() && value > 0.0 }
    }.maxOrNull() ?: 0.0
    val plotSlotWidth = slotWidth
    points.forEachIndexed { index, point ->
        val x = xFor(index)
        val volume = point.volume?.takeIf { value -> value.isFinite() && value > 0.0 } ?: 0.0
        if (volumeMax > 0.0 && volume > 0.0) {
            val barHeight = ((volume / volumeMax).toFloat() * (volumeBottom - volumeTop))
                .coerceAtLeast(chartUnit.toPx() / 8f)
            val color = neutralColor
            drawRect(
                color = color,
                topLeft = Offset(x - plotSlotWidth * 0.32f, volumeBottom - barHeight),
                size = Size((plotSlotWidth * 0.64f).coerceAtLeast(chartUnit.toPx() / 8f), barHeight),
            )
        }
    }
    if (period == MarketChartPeriod.Intraday) {
        drawSeries(
            points = points,
            value = { point -> point.close },
            xFor = xFor,
            yFor = yFor,
            color = primaryColor,
            strokeWidth = chartUnit.toPx() * 0.31f,
        )
        if (points.any { point -> point.average?.isFinite() == true }) {
            drawSeries(
                points = points,
                value = { point -> point.average },
                xFor = xFor,
                yFor = yFor,
                color = neutralColor,
                strokeWidth = chartUnit.toPx() * 0.19f,
            )
        }
    } else {
        val candleWidth = (plotSlotWidth * 0.58f).coerceIn(chartUnit.toPx() / 4f, chartUnit.toPx() * 1.25f)
        points.forEachIndexed { index, point ->
            val open = point.open?.takeIf { value -> value.isFinite() } ?: point.close
            val high = point.high?.takeIf { value -> value.isFinite() } ?: max(open, point.close)
            val low = point.low?.takeIf { value -> value.isFinite() } ?: min(open, point.close)
            val color = candleColor(point, positiveColor, negativeColor)
            val x = xFor(index)
            drawLine(
                color = color,
                start = Offset(x, yFor(low)),
                end = Offset(x, yFor(high)),
                strokeWidth = chartUnit.toPx() * 0.19f,
                cap = StrokeCap.Round,
            )
            val bodyTop = yFor(max(open, point.close))
            val bodyBottom = yFor(min(open, point.close))
            drawRect(
                color = color,
                topLeft = Offset(x - candleWidth / 2f, bodyTop),
                size = Size(candleWidth, (bodyBottom - bodyTop).coerceAtLeast(chartUnit.toPx() * 0.19f)),
            )
        }
        if (points.any { point -> point.average?.isFinite() == true }) {
            drawSeries(
                points = points,
                value = { point -> point.average },
                xFor = xFor,
                yFor = yFor,
                color = neutralColor,
                strokeWidth = chartUnit.toPx() * 0.19f,
            )
        }
        drawMovingAverageSeries(points, { it.ma5 }, xFor, yFor, ma5Color, chartUnit)
        drawMovingAverageSeries(points, { it.ma10 }, xFor, yFor, ma10Color, chartUnit)
        drawMovingAverageSeries(points, { it.ma20 }, xFor, yFor, ma20Color, chartUnit)
    }
    val safeSelected = selectedIndex.coerceIn(0, points.lastIndex)
    val selectedX = xFor(safeSelected)
    val selectedY = yFor(points[safeSelected].close)
    drawLine(
        color = primaryColor.copy(alpha = 0.72f),
        start = Offset(selectedX, top),
        end = Offset(selectedX, bottom),
        strokeWidth = chartUnit.toPx() / 8f,
    )
    drawCircle(
        color = primaryColor,
        radius = chartUnit.toPx() / 2f,
        center = Offset(selectedX, selectedY),
    )
    axisLabels.forEachIndexed { index, label ->
        val y = top + (bottom - top) * index / (MAX_PRICE_GRID_LINES - 1)
        drawText(label, topLeft = Offset(plotWidth + chartUnit.toPx() / 2f,
            (y - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)))
    }

}

private fun DrawScope.drawMovingAverageSeries(
    points: List<MarketChartPoint>,
    value: (MarketChartPoint) -> Double?,
    xFor: (Int) -> Float,
    yFor: (Double) -> Float,
    color: Color,
    chartUnit: androidx.compose.ui.unit.Dp,
) {
    if (points.none { value(it)?.isFinite() == true }) return
    drawSeries(
        points = points,
        value = value,
        xFor = xFor,
        yFor = yFor,
        color = color,
        strokeWidth = chartUnit.toPx() * 0.2f,
    )
}

private fun DrawScope.drawSeries(
    points: List<MarketChartPoint>,
    value: (MarketChartPoint) -> Double?,
    xFor: (Int) -> Float,
    yFor: (Double) -> Float,
    color: Color,
    strokeWidth: Float,
) {
    val path = Path()
    var started = false
    points.forEachIndexed { index, point ->
        val number = value(point)?.takeIf { item -> item.isFinite() }
        if (number == null) {
            started = false
        } else if (!started) {
            path.moveTo(xFor(index), yFor(number))
            started = true
        } else {
            path.lineTo(xFor(index), yFor(number))
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
}

private fun candleColor(
    point: MarketChartPoint,
    positiveColor: Color,
    negativeColor: Color,
): Color {
    val open = point.open?.takeIf { value -> value.isFinite() } ?: point.close
    return if (point.close >= open) positiveColor else negativeColor
}

private fun MarketChartPoint.rangeValues(period: MarketChartPeriod): List<Double> {
    if (period == MarketChartPeriod.Intraday) {
        return listOfNotNull(
            close.takeIf { it.isFinite() },
            average?.takeIf { it.isFinite() },
        )
    }
    return listOfNotNull(
        close.takeIf { it.isFinite() },
        open?.takeIf { it.isFinite() },
        high?.takeIf { it.isFinite() },
        low?.takeIf { it.isFinite() },
        ma5?.takeIf { it.isFinite() },
        ma10?.takeIf { it.isFinite() },
        ma20?.takeIf { it.isFinite() },
    )
}

private fun buildChartDescription(
    period: MarketChartPeriod,
    points: List<MarketChartPoint>,
    selected: MarketChartPoint,
): String {
    val range = points.flatMap { it.rangeValues(period) }
    return "${period.label}图表，${formatChartTime(points.firstOrNull()?.time.orEmpty(), period)}至${formatChartTime(points.lastOrNull()?.time.orEmpty(), period)}，价格范围${marketDetailNumber(range.minOrNull())}至${marketDetailNumber(range.maxOrNull())}，${buildPointSummary(period, selected)}。可点按或横向拖动查看，读屏操作菜单可切换前后数据点。"
}

private fun buildPointSummary(period: MarketChartPeriod, point: MarketChartPoint): String {
    return if (period == MarketChartPeriod.Intraday) {
        "${formatChartTime(point.time, period)}，价格 ${marketDetailNumber(point.close)}，均价 ${marketDetailNumber(point.average)}，成交量 ${marketDetailNumber(point.volume)}"
    } else {
        "${formatChartTime(point.time, period)}，开 ${marketDetailNumber(point.open)}，高 ${marketDetailNumber(point.high)}，低 ${marketDetailNumber(point.low)}，收 ${marketDetailNumber(point.close)}，MA5 ${marketDetailNumber(point.ma5)}，MA10 ${marketDetailNumber(point.ma10)}，MA20 ${marketDetailNumber(point.ma20)}，成交量 ${marketDetailNumber(point.volume)}"
    }
}

private fun formatChartTime(value: String, period: MarketChartPeriod): String {
    val normalized = value.trim().replace('T', ' ').removeSuffix("Z")
    if (normalized.isBlank()) return "—"
    return if (period == MarketChartPeriod.Intraday) {
        normalized.substringAfter(' ', normalized).takeLast(8)
    } else {
        normalized.substringBefore(' ').take(10)
    }
}
