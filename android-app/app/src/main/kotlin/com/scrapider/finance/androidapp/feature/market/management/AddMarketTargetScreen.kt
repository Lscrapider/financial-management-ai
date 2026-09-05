package com.scrapider.finance.androidapp.feature.market.management

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.feature.market.MarketTargetOption
import com.scrapider.finance.androidapp.feature.market.MarketWatchGroup
import com.scrapider.finance.androidapp.feature.market.marketTargetTypeLabel
import com.scrapider.finance.androidapp.feature.market.ui.MarketEmptyPanel
import com.scrapider.finance.androidapp.feature.market.ui.MarketLoadingPanel
import com.scrapider.finance.androidapp.feature.market.ui.MarketPageTopBar
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AddMarketTargetScreen(
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
    val selectedGroup = groups.firstOrNull { group -> group.id == selectedGroupId }
    val spacing = LocalFinanceSpacing.current
    Scaffold(
        modifier = modifier,
        topBar = { MarketPageTopBar(title = "添加标的", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
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
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "搜索名称或代码",
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
                    style = MiuixTheme.textStyles.title2,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
            item { Spacer(Modifier.height(spacing.lg)) }
            when {
                isLoading -> item {
                    MarketLoadingPanel(text = "正在加载可添加标的")
                }

                normalizedQuery.isBlank() -> item {
                    MarketEmptyPanel(text = "输入名称或代码开始搜索")
                }

                filteredOptions.isEmpty() -> item {
                    MarketEmptyPanel(text = "未找到匹配标的")
                }

                else -> items(
                    items = filteredOptions,
                    key = { option -> option.targetKey },
                    contentType = { "market-target-option" },
                ) { option ->
                    MarketTargetOptionRow(
                        option = option,
                        alreadyAdded = selectedGroup?.items?.any { item ->
                            item.targetKey == option.targetKey
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

@Composable
private fun MarketTargetGroupPicker(
    groups: List<MarketWatchGroup>,
    selectedGroup: MarketWatchGroup?,
    onSelectGroup: (String) -> Unit,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = "添加至",
            summary = selectedGroup?.name ?: "请选择自选池",
            onClick = { showPicker = true },
            onClickLabel = "选择自选池",
            endActions = {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "选择自选池",
                )
            },
        )
    }
    OverlayBottomSheet(
        show = showPicker,
        title = "选择自选池",
        onDismissRequest = { showPicker = false },
    ) {
        groups.forEach { group ->
            BasicComponent(
                title = group.name,
                summary = "${group.items.size} 个标的",
                onClick = {
                    showPicker = false
                    onSelectGroup(group.id)
                },
            )
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
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = option.targetName,
            summary = "${option.targetCode} · ${option.targetType.marketTargetTypeLabel()}",
            endActions = {
                if (alreadyAdded) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "已在自选池",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        text = "已在自选池",
                        modifier = Modifier.padding(start = LocalFinanceSpacing.current.xs),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    TextButton(
                        text = "添加",
                        onClick = onAdd,
                        enabled = !isSaving,
                    )
                }
            },
        )
    }
}
