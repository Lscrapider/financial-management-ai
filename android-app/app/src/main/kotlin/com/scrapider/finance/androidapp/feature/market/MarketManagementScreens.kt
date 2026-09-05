package com.scrapider.finance.androidapp.feature.market

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import java.util.Locale

@Composable
fun MarketGroupManagementScreen(
    groups: List<MarketWatchGroup>,
    isSaving: Boolean,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onCreateGroup: () -> Unit,
    onRenameGroup: (String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { MarketTopAppBar(title = "管理自选池", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        LazyColumn(
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
                    text = "我的分组",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            item { Spacer(Modifier.height(spacing.lg)) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        width = LocalFinanceDimensions.current.outlineWidth,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Column {
                        SystemGroupRow()
                        if (groups.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        groups.forEachIndexed { index, group ->
                            MarketGroupManagementRow(
                                group = group,
                                onRename = { onRenameGroup(group.id) },
                                onDelete = { onDeleteGroup(group.id) },
                            )
                            if (index < groups.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(spacing.xl)) }
            item {
                OutlinedButton(
                    onClick = onCreateGroup,
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = LocalFinanceDimensions.current.controlHeight),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddCircleOutline,
                        contentDescription = null,
                    )
                    Spacer(Modifier.size(spacing.sm))
                    Text("新建自选池")
                }
            }
        }
    }
}

@Composable
private fun SystemGroupRow() {
    val spacing = LocalFinanceSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalFinanceDimensions.current.listRowHeight)
            .padding(horizontal = spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "全部",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = "系统分组 · 显示所有已关注标的",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = "系统分组不可编辑",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MarketGroupManagementRow(
    group: MarketWatchGroup,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalFinanceDimensions.current.listRowHeight)
            .padding(start = spacing.lg, end = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = "${group.items.size} 个标的",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MarketGroupActions(
            groupName = group.name,
            onRename = onRename,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun MarketGroupActions(
    groupName: String,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "更多操作：$groupName",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("重命名") },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text("删除自选池") },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMarketGroupBottomSheet(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var groupName by rememberSaveable { mutableStateOf("") }
    val spacing = LocalFinanceSpacing.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = spacing.xl,
                    top = spacing.lg,
                    end = spacing.xl,
                    bottom = spacing.section,
                ),
            verticalArrangement = Arrangement.spacedBy(spacing.xl),
        ) {
            Text(
                text = "新建自选池",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("自选池名称") },
                singleLine = true,
                enabled = !isSaving,
            )
            Text(
                text = "创建后可从行情添加标的",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = !isSaving) {
                    Text("取消")
                }
                Spacer(Modifier.size(spacing.sm))
                Button(
                    onClick = { onConfirm(groupName) },
                    enabled = !isSaving && groupName.isNotBlank(),
                ) {
                    Text(if (isSaving) "创建中" else "创建")
                }
            }
        }
    }
}

@Composable
fun RenameMarketGroupDialog(
    group: MarketWatchGroup,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var groupName by rememberSaveable(group.id) { mutableStateOf(group.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名自选池") },
        text = {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("自选池名称") },
                singleLine = true,
                enabled = !isSaving,
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(groupName) },
                enabled = !isSaving && groupName.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("取消")
            }
        },
    )
}

@Composable
fun DeleteMarketGroupDialog(
    group: MarketWatchGroup,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除自选池") },
        text = { Text("删除“${group.name}”会同时移除其中的 ${group.items.size} 个标的。") },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !isSaving) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("取消")
            }
        },
    )
}

@Composable
fun DeleteMarketTargetDialog(
    target: MarketWatchItem,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移除标的") },
        text = { Text("确定从当前自选池移除“${target.targetName}”吗？") },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !isSaving) {
                Text("移除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("取消")
            }
        },
    )
}

@Composable
fun AddMarketTargetScreen(
    groups: List<MarketWatchGroup>,
    selectedGroupId: String,
    options: List<MarketTargetOption>,
    isLoading: Boolean,
    isSaving: Boolean,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onAddTarget: (MarketTargetOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val filteredOptions = remember(options, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            emptyList()
        } else {
            options.filter { option ->
                option.targetName.contains(normalizedQuery, ignoreCase = true) ||
                    option.targetCode.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }
    val selectedGroup = groups.firstOrNull { it.id == selectedGroupId }
    val spacing = LocalFinanceSpacing.current
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { MarketTopAppBar(title = "添加标的", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        LazyColumn(
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
                MarketTargetGroupPicker(
                    groups = groups,
                    selectedGroup = selectedGroup,
                    onSelectGroup = onSelectGroup,
                )
            }
            item { Spacer(Modifier.height(spacing.xl)) }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("搜索名称或代码") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = if (query.isNotBlank()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "清除搜索内容",
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
            }
            item { Spacer(Modifier.height(spacing.section)) }
            item {
                Text(
                    text = "搜索结果",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            item { Spacer(Modifier.height(spacing.lg)) }

            when {
                isLoading -> {
                    item { MarketPageLoadingPanel(text = "正在加载可添加标的") }
                }

                normalizedQuery.isBlank() -> {
                    item { MarketPageEmptyPanel(text = "输入名称或代码开始搜索") }
                }

                filteredOptions.isEmpty() -> {
                    item { MarketPageEmptyPanel(text = "未找到匹配标的") }
                }

                else -> {
                    items(
                        items = filteredOptions,
                        key = { option -> option.targetType + ":" + option.targetCode },
                        contentType = { "market-target-option" },
                    ) { option ->
                        MarketTargetOptionRow(
                            option = option,
                            alreadyAdded = selectedGroup?.items?.any {
                                it.targetKey == option.targetType + ":" + option.targetCode
                            } == true,
                            isSaving = isSaving,
                            onAdd = { onAddTarget(option) },
                        )
                        Spacer(Modifier.height(spacing.xs))
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketTargetGroupPicker(
    groups: List<MarketWatchGroup>,
    selectedGroup: MarketWatchGroup?,
    onSelectGroup: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val spacing = LocalFinanceSpacing.current
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.heightIn(min = LocalFinanceDimensions.current.minTouchTarget),
        ) {
            Text(
                text = "添加至",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(spacing.lg))
            Text(
                text = selectedGroup?.name ?: "请选择自选池",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = "选择自选池",
                modifier = Modifier.padding(start = spacing.xs),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            groups.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.name) },
                    onClick = {
                        expanded = false
                        onSelectGroup(group.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun MarketTargetOptionRow(
    option: MarketTargetOption,
    alreadyAdded: Boolean,
    isSaving: Boolean,
    onAdd: () -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = LocalFinanceDimensions.current.outlineWidth,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LocalFinanceDimensions.current.listRowHeight)
                .padding(horizontal = spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.targetName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = option.targetCode + " · " + option.targetType.marketTargetTypeLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (alreadyAdded) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(spacing.sm))
                Text(
                    text = "已在自选池",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TextButton(
                    onClick = onAdd,
                    enabled = !isSaving,
                    modifier = Modifier.heightIn(min = LocalFinanceDimensions.current.minTouchTarget),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddCircleOutline,
                        contentDescription = null,
                    )
                    Spacer(Modifier.size(spacing.xs))
                    Text("添加")
                }
            }
        }
    }
}

@Composable
fun TargetSettingsScreen(
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
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { MarketTopAppBar(title = "标的设置", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
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
                        )
                        .heightIn(min = LocalFinanceDimensions.current.controlHeight),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(LocalFinanceDimensions.current.iconSize),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = LocalFinanceDimensions.current.outlineWidth,
                        )
                    } else {
                        Text("保存设置")
                    }
                }
            }
        },
    ) { contentPadding ->
        LazyColumn(
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
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            item { Spacer(Modifier.height(spacing.xs)) }
            item {
                Text(
                    text = target.targetCode + " · " + target.targetType.marketTargetTypeLabel(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Spacer(Modifier.height(spacing.xxl)) }
            item { TargetGroupCard(groupName = groupName) }
            item { Spacer(Modifier.height(spacing.section)) }
            item {
                Text(
                    text = "变化提醒",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
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
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetGroupCard(groupName: String) {
    val spacing = LocalFinanceSpacing.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = LocalFinanceDimensions.current.outlineWidth,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LocalFinanceDimensions.current.listRowHeight)
                .padding(horizontal = spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "所在自选池",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = groupName.ifBlank { "暂无自选池" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = LocalFinanceDimensions.current.outlineWidth,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "开启变化提醒",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = if (supportsAlert) "达到涨跌幅阈值时发送提醒" else "当前标的类型暂不支持提醒",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = supportsAlert,
                )
            }
            if (supportsAlert) {
                Spacer(Modifier.height(spacing.lg))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(spacing.lg))
                Text(
                    text = "提醒阈值",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.sm))
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = onThresholdChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = thresholdError,
                    trailingIcon = { Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(spacing.sm))
                Text(
                    text = "默认 ${DEFAULT_ALERT_THRESHOLD_PERCENT}%，步进 ${ALERT_THRESHOLD_STEP_PERCENT}%，范围 ${MIN_ALERT_THRESHOLD_PERCENT}% 至 ${MAX_ALERT_THRESHOLD_PERCENT}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (thresholdError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = LocalFinanceDimensions.current.outlineWidth,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            RecordTextField(
                label = "买入价",
                value = buyPrice,
                placeholder = "例如：211.80",
                isError = buyPriceError,
                keyboardType = KeyboardType.Decimal,
                onValueChange = onBuyPriceChange,
            )
            RecordTextField(
                label = "持仓数量",
                value = position,
                placeholder = "例如：320",
                isError = positionError,
                keyboardType = KeyboardType.Decimal,
                onValueChange = onPositionChange,
            )
            OutlinedTextField(
                value = remark,
                onValueChange = onRemarkChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注") },
                placeholder = { Text("写下需要持续验证的条件") },
                minLines = RECORD_REMARK_MIN_LINES,
            )
        }
    }
}

@Composable
private fun RecordTextField(
    label: String,
    value: String,
    placeholder: String,
    isError: Boolean,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

@Composable
fun MarketMissingTargetScreen(
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { MarketTopAppBar(title = "标的设置", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                text = "该标的已不在当前自选池中",
                modifier = Modifier.padding(spacing.lg),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketTopAppBar(
    title: String,
    onNavigateBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                )
            }
        },
    )
}

@Composable
private fun MarketPageLoadingPanel(text: String) {
    val spacing = LocalFinanceSpacing.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalFinanceDimensions.current.compactRowHeight),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(LocalFinanceDimensions.current.iconSize),
                strokeWidth = LocalFinanceDimensions.current.outlineWidth,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MarketPageEmptyPanel(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalFinanceDimensions.current.compactRowHeight),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = LocalFinanceSpacing.current.lg),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun String.toNullableDouble(): Double? =
    trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

private fun Double?.asEditableNumber(): String =
    this?.let { value -> String.format(Locale.CHINA, "%.2f", value).trimEnd('0').trimEnd('.') }.orEmpty()

private const val RECORD_REMARK_MIN_LINES = 3
