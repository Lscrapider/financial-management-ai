package com.scrapider.finance.androidapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrapider.finance.androidapp.data.AddWatchTargetFormState
import com.scrapider.finance.androidapp.data.ALERT_THRESHOLD_MAX_PERCENT
import com.scrapider.finance.androidapp.data.ALERT_THRESHOLD_MIN_PERCENT
import com.scrapider.finance.androidapp.data.ALERT_THRESHOLD_STEP_PERCENT
import com.scrapider.finance.androidapp.data.AlertTargetOption
import com.scrapider.finance.androidapp.data.DEFAULT_ALERT_THRESHOLD_PERCENT
import com.scrapider.finance.androidapp.data.ObservationRiskUiState
import com.scrapider.finance.androidapp.data.StockAlertConfig
import com.scrapider.finance.androidapp.data.WatchGroup
import com.scrapider.finance.androidapp.data.WatchItem
import com.scrapider.finance.androidapp.data.WatchTargetType
import java.text.DecimalFormat
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObservationRiskScreen(
    avatarText: String,
    loading: Boolean,
    statusMessage: String,
    observation: ObservationRiskUiState,
    onRefresh: () -> Unit,
    onGroupSelected: (String) -> Unit,
    onTypeFilterSelected: (WatchTargetType?) -> Unit,
    onItemSelected: (String) -> Unit,
    onDismissDetailSheet: () -> Unit,
    onAlertSettingChange: (String, Boolean, Double) -> Unit,
    onDeleteItem: (String) -> Unit,
    onOpenAddSheet: () -> Unit,
    onDismissAddSheet: () -> Unit,
    onFormChange: (AddWatchTargetFormState) -> Unit,
    onTargetTypeChange: (WatchTargetType) -> Unit,
    onSaveTarget: () -> Unit,
    onWorkbenchSelected: () -> Unit,
    onMarketSelected: () -> Unit,
    onReportSelected: () -> Unit,
    onKnowledgeSelected: () -> Unit,
) {
    Scaffold(
        containerColor = WorkspaceBackground,
        topBar = {
            ObservationTopBar(
                updatedAt = observation.updatedAt,
                avatarText = avatarText,
                loading = loading,
                onRefresh = onRefresh,
                onAdd = onOpenAddSheet,
            )
        },
        bottomBar = {
            ObservationBottomNav(
                onWorkbenchSelected = onWorkbenchSelected,
                onMarketSelected = onMarketSelected,
                onReportSelected = onReportSelected,
                onKnowledgeSelected = onKnowledgeSelected,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WorkspaceBackground)
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ObservationSummary(observation)
            MainGroupDropdown(
                groups = observation.groups,
                selectedGroupId = observation.selectedGroup?.id.orEmpty(),
                onSelected = onGroupSelected,
            )
            TypeFilterChips(
                selected = observation.typeFilter,
                onSelected = onTypeFilterSelected,
            )
            WatchItemList(
                loading = loading,
                items = observation.visibleItems,
                alerts = observation.alerts,
                onItemSelected = onItemSelected,
                onDeleteItem = onDeleteItem,
            )
            if (statusMessage.isNotBlank()) {
                Text(statusMessage, color = WorkspaceMuted, fontSize = 12.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(72.dp))
        }
    }

    observation.selectedItem?.let { item ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissDetailSheet,
            sheetState = sheetState,
            containerColor = WorkspaceSurface,
            contentColor = WorkspaceForeground,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp, bottom = 4.dp)
                        .height(4.dp)
                        .fillMaxWidth(0.12f)
                        .background(WorkspaceSurfaceMuted, RoundedCornerShape(2.dp)),
                )
            },
        ) {
            WatchItemDetailSheet(
                loading = loading,
                item = item,
                alert = observation.alerts.findAlert(item),
                onAlertSettingChange = onAlertSettingChange,
                onDismiss = onDismissDetailSheet,
            )
        }
    }

    if (observation.showAddSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissAddSheet,
            sheetState = sheetState,
            containerColor = WorkspaceSurface,
            contentColor = WorkspaceForeground,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp, bottom = 4.dp)
                        .height(4.dp)
                        .fillMaxWidth(0.12f)
                        .background(WorkspaceSurfaceMuted, RoundedCornerShape(2.dp)),
                )
            },
        ) {
            AddWatchTargetSheet(
                loading = loading,
                groups = observation.groups,
                targetOptions = observation.targetOptions,
                form = observation.addForm,
                onFormChange = onFormChange,
                onTargetTypeChange = onTargetTypeChange,
                onDismiss = onDismissAddSheet,
                onSave = onSaveTarget,
            )
        }
    }
}

@Composable
private fun ObservationTopBar(
    updatedAt: String,
    avatarText: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
) {
    ScreenTopBar(
        title = "观察风控",
        avatarText = avatarText,
        loading = loading,
        onRefresh = onRefresh,
        primaryActionText = "+新增",
        onPrimaryAction = onAdd,
    )
}

@Composable
private fun ObservationSummary(observation: ObservationRiskUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        MetricCell("观察标的", observation.watchItemCount.toString(), PrimaryFixedDim, Modifier.weight(1f))
        MetricCell("已预警", observation.enabledAlertCount.toString(), SignalAmber, Modifier.weight(1f))
        MetricCell("越界", observation.outAlertCount.toString(), SignalRed, Modifier.weight(1f))
    }
}

@Composable
private fun MainGroupDropdown(
    groups: List<WatchGroup>,
    selectedGroupId: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = groups.firstOrNull { it.id == selectedGroupId }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .border(1.dp, WorkspaceBorder, RoundedCornerShape(8.dp))
            .background(WorkspaceSurfaceElevated, RoundedCornerShape(8.dp))
            .clickable(enabled = groups.isNotEmpty()) { expanded = true }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selected?.name ?: "暂无观察池分组",
                color = if (selected == null) WorkspaceMuted else WorkspaceForeground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("⌄", color = WorkspaceMuted, fontSize = 18.sp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(WorkspaceSurfaceElevated),
        ) {
            groups.forEach { group ->
                DropdownMenuItem(
                    text = { Text("${group.name} (${group.items.size})", color = WorkspaceForeground, fontSize = 13.sp) },
                    onClick = {
                        expanded = false
                        onSelected(group.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun TypeFilterChips(
    selected: WatchTargetType?,
    onSelected: (WatchTargetType?) -> Unit,
) {
    val filters = listOf(null, WatchTargetType.Stock, WatchTargetType.Bond, WatchTargetType.Index)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        filters.forEach { type ->
            val active = selected == type
            Text(
                text = type?.label ?: "全部",
                modifier = Modifier
                    .background(if (active) CommandBlueSoft else Color(0xFF1D1F27), RoundedCornerShape(12.dp))
                    .border(1.dp, if (active) CommandBlueSoft else WorkspaceBorder, RoundedCornerShape(12.dp))
                    .clickable { onSelected(type) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                color = if (active) PrimaryFixedDim else WorkspaceMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WatchItemList(
    loading: Boolean,
    items: List<WatchItem>,
    alerts: List<StockAlertConfig>,
    onItemSelected: (String) -> Unit,
    onDeleteItem: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, WorkspaceBorder),
        colors = CardDefaults.cardColors(containerColor = WorkspaceSurface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF191B23))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            HeaderCell("标的", 1.25f, TextAlign.Start)
            HeaderCell("行情", 1f, TextAlign.End)
            HeaderCell("持仓", 1f, TextAlign.End)
            HeaderCell("预警", 1f, TextAlign.End)
        }
        when {
            loading -> EmptyObservationRow("正在同步观察池和预警配置。")
            items.isEmpty() -> EmptyObservationRow("当前分组暂无匹配标的。")
            else -> items.forEach { item ->
                WatchItemRow(
                    item = item,
                    alert = alerts.findAlert(item),
                    loading = loading,
                    onClick = { onItemSelected(item.id) },
                    onDelete = { onDeleteItem(item.id) },
                )
            }
        }
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float, align: TextAlign) {
    Text(
        text = text,
        color = WorkspaceMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        textAlign = align,
        modifier = Modifier.weight(weight),
        maxLines = 1,
    )
}

@Composable
private fun WatchItemRow(
    item: WatchItem,
    alert: StockAlertConfig?,
    loading: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var offsetX by remember(item.id) { mutableStateOf(0f) }
    val revealWidth = with(LocalDensity.current) { 82.dp.toPx() }
    Box(modifier = Modifier.fillMaxWidth()) {
        DeleteActionBackground(
            enabled = !loading,
            onDelete = onDelete,
        )
        Column(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(item.id, loading) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            offsetX = if (offsetX < -revealWidth / 2f) -revealWidth else 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!loading) {
                                offsetX = (offsetX + dragAmount).coerceIn(-revealWidth, 0f)
                            }
                        },
                    )
                }
                .background(WorkspaceSurface)
                .clickable { onClick() },
        ) {
            WatchItemContent(item = item, alert = alert)
            DividerLine()
        }
    }
}

@Composable
private fun BoxScope.DeleteActionBackground(
    enabled: Boolean,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color(0xFF251B1F)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier
                .width(82.dp)
                .fillMaxHeight()
                .background(if (enabled) SignalRed.copy(alpha = 0.9f) else WorkspaceSurfaceMuted)
                .clickable(enabled = enabled) { onDelete() },
            contentAlignment = Alignment.Center,
        ) {
            Text("删除", color = Color(0xFFF4F5FF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WatchItemContent(item: WatchItem, alert: StockAlertConfig?) {
    val change = item.changePercent
    val directionColor = marketColor(change)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1.25f)) {
            Text(item.targetName, color = WorkspaceForeground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.targetType.label} ${item.targetCode}", color = WorkspaceMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
            Text(formatNumber(item.latestPrice, 3), color = directionColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(formatPercent(change), color = directionColor, fontSize = 11.sp, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
            Text(formatNumber(item.buyPrice, 3), color = WorkspaceOnSurface, fontSize = 12.sp, maxLines = 1)
            Text(formatNumber(item.position, 2), color = WorkspaceMuted, fontSize = 11.sp, maxLines = 1)
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            AlertChip(item, alert)
        }
    }
}

@Composable
private fun WatchItemDetailSheet(
    loading: Boolean,
    item: WatchItem,
    alert: StockAlertConfig?,
    onAlertSettingChange: (String, Boolean, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val change = item.changePercent ?: alert?.changePercent
    val latestPrice = item.latestPrice ?: alert?.latestPrice
    val directionColor = marketColor(change)
    val marketValue = latestPrice?.let { price -> item.position?.let { position -> price * position } }
    val pnl = latestPrice?.let { price ->
        item.buyPrice?.let { buyPrice -> item.position?.let { position -> (price - buyPrice) * position } }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.targetName, color = WorkspaceForeground, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.targetCode, color = WorkspaceMuted, fontSize = 13.sp)
                }
                Text(item.remark.ifBlank { item.targetType.label }, color = WorkspaceMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatNumber(latestPrice, 2), color = directionColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(formatPercent(change), color = directionColor, fontSize = 12.sp)
            }
        }
        DividerLine()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            DetailMetric("成本价", formatNumber(item.buyPrice, 2), WorkspaceForeground, Modifier.weight(1f))
            DetailMetric("持仓数量", formatNumber(item.position, 0), WorkspaceForeground, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            DetailMetric("市值", formatMoney(marketValue), WorkspaceForeground, Modifier.weight(1f))
            DetailMetric("浮动盈亏", formatMoney(pnl), marketColor(pnl), Modifier.weight(1f))
        }
        AlertDetailCard(alert = alert, item = item)
        RiskSettingEditor(
            loading = loading,
            item = item,
            alert = alert,
            onAlertSettingChange = onAlertSettingChange,
        )
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("关闭", color = WorkspaceMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun DetailMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = WorkspaceMuted, fontSize = 12.sp)
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun AlertDetailCard(alert: StockAlertConfig?, item: WatchItem) {
    val color = when {
        !item.targetType.supportsAlert -> WorkspaceMuted
        alert?.outOfThreshold == true -> SignalRed
        alert?.enabled == true -> SignalAmber
        else -> WorkspaceMuted
    }
    val title = when {
        !item.targetType.supportsAlert -> "仅观察"
        alert?.outOfThreshold == true -> "已触发风险提醒"
        alert?.enabled == true -> "接近阈值提醒"
        else -> "未开启风险预警"
    }
    val detail = when {
        !item.targetType.supportsAlert -> "当前类型不支持预警配置。"
        alert != null -> "距设定阈值 ${formatNumber(alert.thresholdPercent, 2)}% 仅差 ${formatNumber(alertDistance(alert), 2)}%"
        else -> "可通过新增观察标的时同步创建风险预警。"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, WorkspaceBorder, RoundedCornerShape(6.dp))
            .background(Color(0xFF191B23), RoundedCornerShape(6.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("⚠ $title", color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, color = WorkspaceMuted, fontSize = 12.sp)
    }
}

@Composable
private fun RiskSettingEditor(
    loading: Boolean,
    item: WatchItem,
    alert: StockAlertConfig?,
    onAlertSettingChange: (String, Boolean, Double) -> Unit,
) {
    val threshold = alert?.thresholdPercent ?: DEFAULT_ALERT_THRESHOLD_PERCENT
    val enabled = alert?.enabled == true
    val controlsEnabled = item.targetType.supportsAlert && !loading

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("风控阈值设置", color = WorkspaceForeground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Switch(
            checked = enabled,
            enabled = controlsEnabled,
            onCheckedChange = { nextEnabled ->
                onAlertSettingChange(item.id, nextEnabled, threshold)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFF4F5FF),
                checkedTrackColor = CommandBlue,
                uncheckedThumbColor = WorkspaceMuted,
                uncheckedTrackColor = WorkspaceSurfaceMuted,
                disabledCheckedThumbColor = Color(0xFFF4F5FF),
                disabledCheckedTrackColor = CommandBlue.copy(alpha = 0.55f),
                disabledUncheckedThumbColor = WorkspaceMuted,
                disabledUncheckedTrackColor = WorkspaceSurfaceMuted,
            ),
        )
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("涨跌幅阈值", color = WorkspaceMuted, fontSize = 12.sp)
        ThresholdStepper(
            value = threshold,
            enabled = controlsEnabled,
            onValueChange = { nextThreshold ->
                onAlertSettingChange(item.id, enabled, nextThreshold)
            },
        )
    }
}

@Composable
private fun ThresholdStepper(
    value: Double,
    enabled: Boolean,
    onValueChange: (Double) -> Unit,
) {
    Row(
        modifier = Modifier
            .height(34.dp)
            .background(WorkspaceSurfaceMuted, RoundedCornerShape(4.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThresholdStepButton(
            text = "−",
            enabled = enabled,
            onClick = {
                onValueChange((value - ALERT_THRESHOLD_STEP_PERCENT).coerceAtLeast(ALERT_THRESHOLD_MIN_PERCENT))
            },
        )
        Text(
            text = "${formatNumber(value.coerceIn(ALERT_THRESHOLD_MIN_PERCENT, ALERT_THRESHOLD_MAX_PERCENT), 2)}%",
            modifier = Modifier.padding(horizontal = 14.dp),
            color = WorkspaceForeground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        ThresholdStepButton(
            text = "+",
            enabled = enabled,
            onClick = {
                onValueChange((value + ALERT_THRESHOLD_STEP_PERCENT).coerceAtMost(ALERT_THRESHOLD_MAX_PERCENT))
            },
        )
    }
}

@Composable
private fun ThresholdStepButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .background(if (enabled) Color(0xFF252832) else Color(0xFF20232B))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) WorkspaceMuted else WorkspaceBorder, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AlertChip(item: WatchItem, alert: StockAlertConfig?) {
    when {
        !item.targetType.supportsAlert -> StatusChip("仅观察", WorkspaceMuted)
        alert == null -> StatusChip("未预警", WorkspaceMuted)
        alert.outOfThreshold -> StatusChip("越界 ${formatPercent(alert.changePercent)}", SignalRed)
        alert.enabled -> StatusChip("${formatNumber(alert.thresholdPercent, 0)}%", SignalAmber)
        else -> StatusChip("停用", WorkspaceMuted)
    }
}

@Composable
private fun EmptyObservationRow(text: String) {
    Text(
        text = text,
        color = WorkspaceMuted,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 22.dp),
    )
}

@Composable
private fun AddWatchTargetSheet(
    loading: Boolean,
    groups: List<WatchGroup>,
    targetOptions: List<AlertTargetOption>,
    form: AddWatchTargetFormState,
    onFormChange: (AddWatchTargetFormState) -> Unit,
    onTargetTypeChange: (WatchTargetType) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val filteredOptions = targetOptions.filter { it.matches(form.targetKeyword) }.take(8)
    val saveEnabled = !loading && form.selectedGroupId.isNotBlank() &&
        (form.selectedTargetCode.isNotBlank() || form.targetKeyword.trim().length >= 2)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("添加观察标的", color = WorkspaceForeground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onDismiss) {
                Text("取消", color = WorkspaceMuted, fontSize = 13.sp)
            }
        }
        GroupDropdown(
            groups = groups,
            selectedGroupId = form.selectedGroupId,
            onSelected = { onFormChange(form.copy(selectedGroupId = it)) },
        )
        TargetTypeSelector(
            selected = form.targetType,
            onSelected = onTargetTypeChange,
        )
        FinanceTextField(
            label = "标的搜索",
            value = form.targetKeyword,
            onValueChange = {
                onFormChange(
                    form.copy(
                        targetKeyword = it,
                        selectedTargetCode = "",
                        selectedTargetName = "",
                        selectedSecid = "",
                    ),
                )
            },
            isError = saveEnabled.not() && form.targetKeyword.isNotBlank(),
        )
        TargetOptionList(
            form = form,
            options = filteredOptions,
            onSelected = { option ->
                onFormChange(
                    form.copy(
                        targetKeyword = "${option.targetName} ${option.targetCode}",
                        selectedTargetCode = option.targetCode,
                        selectedTargetName = option.targetName,
                        selectedSecid = "",
                    ),
                )
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FinanceTextField(
                label = "买入价格",
                value = form.buyPrice,
                onValueChange = { onFormChange(form.copy(buyPrice = it)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            FinanceTextField(
                label = "买入仓位",
                value = form.position,
                onValueChange = { onFormChange(form.copy(position = it)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        AlertSwitchRow(form = form, onFormChange = onFormChange)
        FinanceTextField(
            label = "备注",
            value = form.remark,
            onValueChange = { onFormChange(form.copy(remark = it)) },
            singleLine = false,
            minLines = 2,
        )
        PrimaryActionButton(
            text = if (loading) "保存中" else "保存观察标的",
            enabled = saveEnabled,
            onClick = onSave,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun GroupDropdown(
    groups: List<WatchGroup>,
    selectedGroupId: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = groups.firstOrNull { it.id == selectedGroupId }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("选择分组", color = WorkspaceMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .border(1.dp, WorkspaceBorder, RoundedCornerShape(8.dp))
                .background(WorkspaceSurfaceElevated, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(selected?.name ?: "请选择分组", color = if (selected == null) WorkspaceMuted else WorkspaceForeground, fontSize = 14.sp)
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(WorkspaceSurfaceElevated),
            ) {
                groups.forEach { group ->
                    DropdownMenuItem(
                        text = { Text("${group.name} (${group.items.size})", color = WorkspaceForeground, fontSize = 13.sp) },
                        onClick = {
                            expanded = false
                            onSelected(group.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetTypeSelector(
    selected: WatchTargetType,
    onSelected: (WatchTargetType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WorkspaceSurfaceMuted, RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(WatchTargetType.Stock, WatchTargetType.Bond, WatchTargetType.Index).forEach { type ->
            val active = type == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .background(if (active) WorkspaceSurfaceElevated else Color.Transparent, RoundedCornerShape(6.dp))
                    .border(if (active) 1.dp else 0.dp, if (active) WorkspaceBorder else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { onSelected(type) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = type.label,
                    color = if (active) WorkspaceForeground else WorkspaceMuted,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TargetOptionList(
    form: AddWatchTargetFormState,
    options: List<AlertTargetOption>,
    onSelected: (AlertTargetOption) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (form.targetKeyword.isBlank()) {
            Text("输入名称或代码后筛选候选标的。", color = WorkspaceMuted, fontSize = 12.sp)
            return@Column
        }
        if (options.isEmpty()) {
            Text("没有匹配的候选标的。", color = WorkspaceMuted, fontSize = 12.sp)
            return@Column
        }
        options.forEach { option ->
            val active = option.targetCode == form.selectedTargetCode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (active) PrimaryFixedDim else WorkspaceBorder, RoundedCornerShape(8.dp))
                    .background(if (active) CommandBlueSoft else Color(0xFF191B23), RoundedCornerShape(8.dp))
                    .clickable { onSelected(option) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(option.targetName, color = WorkspaceForeground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(option.targetCode, color = WorkspaceMuted, fontSize = 11.sp)
                }
                Text(option.targetType.label, color = PrimaryFixedDim, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AlertSwitchRow(
    form: AddWatchTargetFormState,
    onFormChange: (AddWatchTargetFormState) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, WorkspaceBorder, RoundedCornerShape(8.dp))
            .background(Color(0xFF191B23), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("开启风险预警", color = WorkspaceForeground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "阈值按 ${formatNumber(ALERT_THRESHOLD_STEP_PERCENT, 1)}% 调整",
                    color = WorkspaceMuted,
                    fontSize = 12.sp,
                )
            }
            Switch(
                checked = form.effectiveAlertEnabled,
                enabled = form.canEnableAlert,
                onCheckedChange = { onFormChange(form.copy(alertEnabled = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFF4F5FF),
                    checkedTrackColor = CommandBlue,
                    uncheckedThumbColor = WorkspaceMuted,
                    uncheckedTrackColor = WorkspaceSurfaceMuted,
                    disabledCheckedTrackColor = WorkspaceSurfaceMuted,
                    disabledUncheckedTrackColor = WorkspaceSurfaceMuted,
                ),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("涨跌幅阈值", color = WorkspaceMuted, fontSize = 12.sp)
            ThresholdStepper(
                value = form.alertThresholdPercent,
                enabled = form.effectiveAlertEnabled,
                onValueChange = { onFormChange(form.copy(alertThresholdPercent = it)) },
            )
        }
    }
}

@Composable
private fun ObservationBottomNav(
    onWorkbenchSelected: () -> Unit,
    onMarketSelected: () -> Unit,
    onReportSelected: () -> Unit,
    onKnowledgeSelected: () -> Unit,
) {
    WorkspaceBottomNav(
        selectedItem = "观察",
        onWorkbenchSelected = onWorkbenchSelected,
        onMarketSelected = onMarketSelected,
        onReportSelected = onReportSelected,
        onKnowledgeSelected = onKnowledgeSelected,
    )
}

private fun List<StockAlertConfig>.findAlert(item: WatchItem): StockAlertConfig? =
    firstOrNull { it.targetType == item.targetType && it.stockCode == item.targetCode }

private fun marketColor(value: Double?): Color = when {
    value == null -> WorkspaceOnSurface
    value > 0.0 -> SignalRed
    value < 0.0 -> SignalGreen
    else -> WorkspaceOnSurface
}

private fun formatPercent(value: Double?): String {
    if (value == null) return "--"
    val sign = if (value >= 0.0) "+" else ""
    return "$sign${DecimalFormat("0.00").format(value)}%"
}

private fun formatNumber(value: Double?, precision: Int): String {
    if (value == null || value.isNaN() || value.isInfinite()) return "--"
    val pattern = if (precision <= 0) "#,##0" else "#,##0." + "0".repeat(precision)
    return DecimalFormat(pattern).format(value)
}

private fun formatMoney(value: Double?): String = when {
    value == null || value.isNaN() || value.isInfinite() -> "--"
    kotlin.math.abs(value) >= 100_000_000.0 -> "${DecimalFormat("0.##").format(value / 100_000_000.0)} 亿"
    kotlin.math.abs(value) >= 10_000.0 -> "${DecimalFormat("0.##").format(value / 10_000.0)} 万"
    else -> DecimalFormat("#,##0.##").format(value)
}

private fun alertDistance(alert: StockAlertConfig): Double? {
    val change = alert.changePercent ?: return null
    return (alert.thresholdPercent - kotlin.math.abs(change)).coerceAtLeast(0.0)
}
