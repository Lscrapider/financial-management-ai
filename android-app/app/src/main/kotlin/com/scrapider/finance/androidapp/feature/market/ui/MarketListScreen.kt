package com.scrapider.finance.androidapp.feature.market.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.feature.market.MARKET_OVERVIEW_ITEM_LIMIT
import com.scrapider.finance.androidapp.feature.market.MarketSortOption
import com.scrapider.finance.androidapp.feature.market.MarketSystemTarget
import com.scrapider.finance.androidapp.feature.market.MarketTargetTypeFilter
import com.scrapider.finance.androidapp.feature.market.MarketUiState
import com.scrapider.finance.androidapp.feature.market.MarketWatchGroup
import com.scrapider.finance.androidapp.feature.market.MarketWatchItem
import com.scrapider.finance.androidapp.feature.market.alertFor
import com.scrapider.finance.androidapp.feature.market.findWatchItem
import com.scrapider.finance.androidapp.feature.market.marketSorted
import com.scrapider.finance.androidapp.feature.market.systemTargetTypes
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState

private const val MARKET_ATTENTION_PREVIEW_ITEM_LIMIT = MARKET_OVERVIEW_ITEM_LIMIT

@Composable
internal fun MarketListScreen(
    state: MarketUiState,
    snackbarHostState: SnackbarHostState,
    bottomBar: @Composable () -> Unit,
    onSelectGroup: (String?) -> Unit,
    onSelectTargetTypeFilter: (MarketTargetTypeFilter) -> Unit,
    onSelectSortOption: (MarketSortOption) -> Unit,
    onOpenManageGroups: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAddTargets: (String?) -> Unit,
    onOpenTargetSettings: (String) -> Unit,
    onOpenTargetDetail: (String, String, String?) -> Unit,
    onDeleteTarget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val isAllTargetsSelected = state.selectedGroupId == null
    val selectedGroup = state.groups.firstOrNull { group -> group.id == state.selectedGroupId }
    val visibleItems = selectedGroup?.items.orEmpty()
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val filteredSystemTargets = remember(
        state.systemTargets,
        state.targetTypeFilter,
        state.sortOption,
    ) {
        state.systemTargets
            .filter { target -> state.targetTypeFilter.includes(target.targetType) }
            .marketSorted(
                option = state.sortOption,
                targetCode = MarketSystemTarget::targetCode,
                latestPrice = MarketSystemTarget::latestPrice,
                changePercent = MarketSystemTarget::changePercent,
            )
    }
    val filteredVisibleItems = remember(
        visibleItems,
        state.targetTypeFilter,
        state.sortOption,
    ) {
        visibleItems
            .filter { item -> state.targetTypeFilter.includes(item.targetType) }
            .marketSorted(
                option = state.sortOption,
                targetCode = MarketWatchItem::targetCode,
                latestPrice = MarketWatchItem::latestPrice,
                changePercent = MarketWatchItem::changePercent,
            )
    }
    val attentionItems = remember(state.groups, state.alerts, selectedGroup?.id) {
        val scopedItems = selectedGroup?.items ?: state.groups.flatMap(MarketWatchGroup::items)
        scopedItems
            .distinctBy(MarketWatchItem::targetKey)
            .mapNotNull { item ->
                state.alertFor(item)
                    ?.takeIf { alert -> alert.enabled && alert.outOfThreshold }
                    ?.let { alert -> MarketAttentionItem(item = item, alert = alert) }
            }
    }
    val targetsByType = remember(filteredSystemTargets) {
        filteredSystemTargets.groupBy(MarketSystemTarget::targetType)
    }
    val orderedSystemTargetTypes = remember(targetsByType) {
        (systemTargetTypes + targetsByType.keys.filterNot(systemTargetTypes::contains).sorted())
            .filter(targetsByType::containsKey)
    }

    Scaffold(
        modifier = modifier,
        topBar = { MarketHomeTopBar(onSearch = onOpenSearch) },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
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
        ) {
            item {
                MarketGroupTabs(
                    groups = state.groups,
                    selectedGroupId = state.selectedGroupId,
                    onSelectGroup = onSelectGroup,
                    onManageGroups = onOpenManageGroups,
                )
            }
            item { Spacer(Modifier.height(spacing.md)) }
            item {
                MarketFilterBar(
                    selectedFilter = state.targetTypeFilter,
                    sortOption = state.sortOption,
                    onSelectFilter = onSelectTargetTypeFilter,
                    onOpenSort = { showSortSheet = true },
                )
            }
            item { Spacer(Modifier.height(spacing.xxl)) }
            item {
                MarketCurrentViewHeader(
                    title = if (isAllTargetsSelected) {
                        if (state.targetTypeFilter == MarketTargetTypeFilter.All) {
                            "全部标的"
                        } else {
                            "${state.targetTypeFilter.label}标的"
                        }
                    } else {
                        selectedGroup?.name ?: "自选池"
                    },
                    itemCount = if (isAllTargetsSelected) {
                        filteredSystemTargets.size
                    } else {
                        filteredVisibleItems.size
                    },
                    onAddTargets = if (isAllTargetsSelected) {
                        null
                    } else {
                        { onOpenAddTargets(selectedGroup?.id) }
                    },
                )
            }
            if (state.syncMessage.isNotBlank()) {
                item { Spacer(Modifier.height(spacing.md)) }
                item { MarketSyncNotice(message = state.syncMessage) }
            }
            if (attentionItems.isNotEmpty()) {
                item { Spacer(Modifier.height(spacing.section)) }
                item {
                    MarketAttentionSection(
                        items = attentionItems.take(MARKET_ATTENTION_PREVIEW_ITEM_LIMIT),
                        totalItemCount = attentionItems.size,
                        onOpenTargetDetail = onOpenTargetDetail,
                    )
                }
            }
            item { Spacer(Modifier.height(spacing.section)) }
            item { MarketSectionTitle(title = "市场概览") }
            item { Spacer(Modifier.height(spacing.lg)) }
            item {
                MarketOverviewContent(
                    indices = state.marketOverview,
                    isLoading = state.isLoading,
                )
            }
            item { Spacer(Modifier.height(spacing.section)) }

            if (isAllTargetsSelected) {
                systemTargetItems(
                    state = state,
                    filteredTargets = filteredSystemTargets,
                    targetsByType = targetsByType,
                    orderedTargetTypes = orderedSystemTargetTypes,
                    onOpenTargetDetail = onOpenTargetDetail,
                )
            } else {
                watchTargetItems(
                    state = state,
                    visibleItems = visibleItems,
                    filteredItems = filteredVisibleItems,
                    onOpenTargetSettings = onOpenTargetSettings,
                    onOpenTargetDetail = onOpenTargetDetail,
                    onRequestDelete = { itemId -> pendingDeleteId = itemId },
                )
            }
        }
    }

    if (showSortSheet) {
        MarketSortSheet(
            selectedOption = state.sortOption,
            onDismiss = { showSortSheet = false },
            onSelectOption = { option ->
                onSelectSortOption(option)
                showSortSheet = false
            },
        )
    }
    val targetToDelete = state.findWatchItem(pendingDeleteId)
    DeleteMarketTargetDialog(
        target = targetToDelete,
        isSaving = state.isSaving,
        onDismiss = { pendingDeleteId = null },
        onConfirm = {
            targetToDelete?.let { target ->
                pendingDeleteId = null
                onDeleteTarget(target.id)
            }
        },
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.systemTargetItems(
    state: MarketUiState,
    filteredTargets: List<MarketSystemTarget>,
    targetsByType: Map<String, List<MarketSystemTarget>>,
    orderedTargetTypes: List<String>,
    onOpenTargetDetail: (String, String, String?) -> Unit,
) {
    when {
        state.isLoading && state.systemTargets.isEmpty() -> {
            item { MarketLoadingPanel(text = "正在同步系统标的") }
        }

        state.systemTargets.isEmpty() -> {
            item { MarketEmptyPanel(text = "暂未获取到系统标的") }
        }

        filteredTargets.isEmpty() -> {
            item { MarketEmptyPanel(text = "当前类型暂无标的") }
        }

        else -> {
            val shouldGroupByType =
                state.targetTypeFilter == MarketTargetTypeFilter.All &&
                    state.sortOption == MarketSortOption.Default
            if (shouldGroupByType) {
                orderedTargetTypes.forEach { targetType ->
                    val targets = targetsByType[targetType].orEmpty()
                    item(key = "type-header:$targetType") {
                        MarketTargetTypeHeader(
                            targetType = targetType,
                            itemCount = targets.size,
                            modifier = Modifier.padding(top = LocalFinanceSpacing.current.xl),
                        )
                    }
                    itemsIndexed(
                        items = targets,
                        key = { _, target -> target.targetKey },
                        contentType = { _, _ -> "system-market-target" },
                    ) { index, target ->
                        MarketSystemTargetRow(
                            target = target,
                            onOpenDetail = {
                                onOpenTargetDetail(target.targetType, target.targetCode, null)
                            },
                        )
                        if (index < targets.lastIndex) HorizontalDivider()
                    }
                }
            } else {
                itemsIndexed(
                    items = filteredTargets,
                    key = { _, target -> target.targetKey },
                    contentType = { _, _ -> "system-market-target" },
                ) { index, target ->
                    MarketSystemTargetRow(
                        target = target,
                        onOpenDetail = {
                            onOpenTargetDetail(target.targetType, target.targetCode, null)
                        },
                    )
                    if (index < filteredTargets.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.watchTargetItems(
    state: MarketUiState,
    visibleItems: List<MarketWatchItem>,
    filteredItems: List<MarketWatchItem>,
    onOpenTargetSettings: (String) -> Unit,
    onOpenTargetDetail: (String, String, String?) -> Unit,
    onRequestDelete: (String) -> Unit,
) {
    when {
        state.isLoading && visibleItems.isEmpty() -> {
            item { MarketLoadingPanel(text = "正在同步自选行情") }
        }

        visibleItems.isEmpty() -> {
            item { MarketEmptyPanel(text = "当前自选池暂无标的") }
        }

        filteredItems.isEmpty() -> {
            item { MarketEmptyPanel(text = "当前类型暂无标的") }
        }

        else -> {
            itemsIndexed(
                items = filteredItems,
                key = { _, item -> item.id },
                contentType = { _, _ -> "watch-market-target" },
            ) { index, item ->
                MarketWatchTargetRow(
                    item = item,
                    alert = state.alertFor(item),
                    onOpenDetail = {
                        onOpenTargetDetail(item.targetType, item.targetCode, item.id)
                    },
                    onOpenSettings = { onOpenTargetSettings(item.id) },
                    onDelete = { onRequestDelete(item.id) },
                )
                if (index < filteredItems.lastIndex) HorizontalDivider()
            }
        }
    }
}
