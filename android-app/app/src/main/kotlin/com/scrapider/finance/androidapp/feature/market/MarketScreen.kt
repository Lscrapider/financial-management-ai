package com.scrapider.finance.androidapp.feature.market

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.session.UserSession
import com.scrapider.finance.androidapp.designsystem.FinanceSemanticColors
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSemanticColors
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private const val MARKET_ATTENTION_PREVIEW_ITEM_LIMIT = MARKET_OVERVIEW_ITEM_LIMIT

@Composable
fun MarketRoute(
    session: UserSession,
    apiClient: FinanceApiClient,
    onSessionExpired: () -> Unit,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory = remember(apiClient) { MarketViewModel.Factory(apiClient) }
    val viewModel: MarketViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateGroupSheet by rememberSaveable { mutableStateOf(false) }
    var renameGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTargetId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(session.accessToken) {
        viewModel.loadForSession(session.accessToken)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                MarketEvent.GroupSaved -> {
                    showCreateGroupSheet = false
                    renameGroupId = null
                }

                MarketEvent.SessionExpired -> onSessionExpired()
                is MarketEvent.Notice -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    BackHandler(enabled = state.destination != MarketDestination.List) {
        viewModel.navigateBack()
    }

    MarketScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        bottomBar = bottomBar,
        onSelectGroup = viewModel::selectGroup,
        onSelectTargetTypeFilter = viewModel::selectTargetTypeFilter,
        onSelectSortOption = viewModel::selectSortOption,
        onOpenManageGroups = viewModel::openManageGroups,
        onOpenSearch = viewModel::openSearch,
        onUpdateSearchQuery = viewModel::updateSearchQuery,
        onOpenAddTargets = viewModel::openAddTargets,
        onSelectAddTargetGroup = viewModel::selectAddTargetGroup,
        onAddTarget = viewModel::addTarget,
        onOpenTargetSettings = viewModel::openTargetSettings,
        onOpenTargetDetail = viewModel::openTargetDetail,
        onSaveTargetSettings = viewModel::saveTargetSettings,
        onNavigateBack = viewModel::navigateBack,
        onShowCreateGroup = { showCreateGroupSheet = true },
        onRenameGroup = { groupId -> renameGroupId = groupId },
        onDeleteGroup = { groupId -> deleteGroupId = groupId },
        onDeleteTarget = { itemId -> deleteTargetId = itemId },
        modifier = modifier,
    )

    if (showCreateGroupSheet) {
        NewMarketGroupBottomSheet(
            isSaving = state.isSaving,
            onDismiss = { showCreateGroupSheet = false },
            onConfirm = { name -> viewModel.saveGroup(groupId = null, name = name) },
        )
    }

    val groupToRename = state.groups.firstOrNull { it.id == renameGroupId }
    if (groupToRename != null) {
        RenameMarketGroupDialog(
            group = groupToRename,
            isSaving = state.isSaving,
            onDismiss = { renameGroupId = null },
            onConfirm = { name -> viewModel.saveGroup(groupToRename.id, name) },
        )
    }

    val groupToDelete = state.groups.firstOrNull { it.id == deleteGroupId }
    if (groupToDelete != null) {
        DeleteMarketGroupDialog(
            group = groupToDelete,
            isSaving = state.isSaving,
            onDismiss = { deleteGroupId = null },
            onConfirm = {
                deleteGroupId = null
                viewModel.deleteGroup(groupToDelete.id)
            },
        )
    }

    val targetToDelete = state.findTarget(deleteTargetId)
    if (targetToDelete != null) {
        DeleteMarketTargetDialog(
            target = targetToDelete,
            isSaving = state.isSaving,
            onDismiss = { deleteTargetId = null },
            onConfirm = {
                deleteTargetId = null
                viewModel.deleteTarget(targetToDelete.id)
            },
        )
    }
}

@Composable
private fun MarketScreen(
    state: MarketUiState,
    snackbarHostState: SnackbarHostState,
    bottomBar: @Composable () -> Unit,
    onSelectGroup: (String?) -> Unit,
    onSelectTargetTypeFilter: (MarketTargetTypeFilter) -> Unit,
    onSelectSortOption: (MarketSortOption) -> Unit,
    onOpenManageGroups: () -> Unit,
    onOpenSearch: () -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onOpenAddTargets: (String?) -> Unit,
    onSelectAddTargetGroup: (String) -> Unit,
    onAddTarget: (MarketTargetOption) -> Unit,
    onOpenTargetSettings: (String) -> Unit,
    onOpenTargetDetail: (String, String, String?) -> Unit,
    onSaveTargetSettings: (MarketTargetSettingsInput) -> Unit,
    onNavigateBack: () -> Unit,
    onShowCreateGroup: () -> Unit,
    onRenameGroup: (String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onDeleteTarget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val destination = state.destination) {
        MarketDestination.List -> {
            MarketListScreen(
                state = state,
                snackbarHostState = snackbarHostState,
                bottomBar = bottomBar,
                onSelectGroup = onSelectGroup,
                onSelectTargetTypeFilter = onSelectTargetTypeFilter,
                onSelectSortOption = onSelectSortOption,
                onOpenManageGroups = onOpenManageGroups,
                onOpenSearch = onOpenSearch,
                onOpenAddTargets = onOpenAddTargets,
                onOpenTargetSettings = onOpenTargetSettings,
                onOpenTargetDetail = onOpenTargetDetail,
                onDeleteTarget = onDeleteTarget,
                modifier = modifier,
            )
        }

        MarketDestination.ManageGroups -> {
            MarketGroupManagementScreen(
                groups = state.groups,
                isSaving = state.isSaving,
                snackbarHostState = snackbarHostState,
                onNavigateBack = onNavigateBack,
                onCreateGroup = onShowCreateGroup,
                onRenameGroup = onRenameGroup,
                onDeleteGroup = onDeleteGroup,
                modifier = modifier,
            )
        }

        is MarketDestination.AddTargets -> {
            AddMarketTargetScreen(
                groups = state.groups,
                selectedGroupId = destination.groupId,
                options = state.targetOptions,
                isLoading = state.isLoadingTargetOptions,
                isSaving = state.isSaving,
                snackbarHostState = snackbarHostState,
                onNavigateBack = onNavigateBack,
                onSelectGroup = onSelectAddTargetGroup,
                onAddTarget = onAddTarget,
                modifier = modifier,
            )
        }

        is MarketDestination.TargetSettings -> {
            val target = state.findTarget(destination.itemId)
            if (target == null) {
                MarketMissingTargetScreen(
                    snackbarHostState = snackbarHostState,
                    onNavigateBack = onNavigateBack,
                    modifier = modifier,
                )
            } else {
                TargetSettingsScreen(
                    target = target,
                    groupName = state.groups.firstOrNull { it.id == target.groupId }?.name.orEmpty(),
                    alert = state.alertFor(target),
                    isSaving = state.isSaving,
                    snackbarHostState = snackbarHostState,
                    onNavigateBack = onNavigateBack,
                    onSave = onSaveTargetSettings,
                    modifier = modifier,
                )
            }
        }

        MarketDestination.Search -> {
            MarketSearchScreen(
                targets = state.availableTargetSnapshots(),
                query = state.searchQuery,
                onNavigateBack = onNavigateBack,
                onQueryChange = onUpdateSearchQuery,
                onTargetSelected = { target ->
                    onOpenTargetDetail(
                        target.targetType,
                        target.targetCode,
                        target.watchItemId,
                    )
                },
                modifier = modifier,
            )
        }

        is MarketDestination.TargetDetail -> {
            val target = state.findTargetSnapshot(
                targetType = destination.targetType,
                targetCode = destination.targetCode,
                watchItemId = destination.watchItemId,
            )
            if (target == null) {
                MarketMissingTargetDetailScreen(
                    onNavigateBack = onNavigateBack,
                    modifier = modifier,
                )
            } else {
                MarketTargetDetailScreen(
                    target = target,
                    onNavigateBack = onNavigateBack,
                    onOpenTargetSettings = target.watchItemId?.let { itemId ->
                        { onOpenTargetSettings(itemId) }
                    },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun MarketListScreen(
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
    val selectedGroup = state.groups.firstOrNull { it.id == state.selectedGroupId }
    val visibleItems = selectedGroup?.items.orEmpty()
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
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
    val attentionItems = remember(
        state.groups,
        state.alerts,
        selectedGroup?.id,
    ) {
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
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(bottom = spacing.section),
        ) {
            item {
                MarketPageHeader(
                    onSearch = onOpenSearch,
                )
            }
            item {
                MarketGroupTabs(
                    groups = state.groups,
                    selectedGroupId = state.selectedGroupId,
                    onSelectGroup = onSelectGroup,
                    onManageGroups = onOpenManageGroups,
                )
            }
            item { Spacer(Modifier.height(spacing.sm)) }
            item {
                MarketFilterToolbar(
                    selectedFilter = state.targetTypeFilter,
                    sortOption = state.sortOption,
                    onSelectFilter = onSelectTargetTypeFilter,
                    onOpenSort = { showSortSheet = true },
                    modifier = Modifier.padding(horizontal = spacing.xl),
                )
            }
            if (attentionItems.isNotEmpty()) {
                item { Spacer(Modifier.height(spacing.section)) }
                item {
                    MarketAttentionSection(
                        items = attentionItems.take(MARKET_ATTENTION_PREVIEW_ITEM_LIMIT),
                        totalItemCount = attentionItems.size,
                        onOpenTargetDetail = onOpenTargetDetail,
                        modifier = Modifier.padding(horizontal = spacing.xl),
                    )
                }
                item { Spacer(Modifier.height(spacing.section + spacing.sm)) }
            } else {
                item { Spacer(Modifier.height(spacing.section)) }
            }
            item {
                MarketSectionTitle(
                    title = "市场概览",
                    modifier = Modifier.padding(horizontal = spacing.xl),
                )
            }
            item { Spacer(Modifier.height(spacing.lg)) }
            item {
                MarketOverviewContent(
                    indices = state.marketOverview,
                    isLoading = state.isLoading,
                    modifier = Modifier.padding(horizontal = spacing.xl),
                )
            }
            item { Spacer(Modifier.height(spacing.section + spacing.sm)) }
            if (isAllTargetsSelected) {
                item {
                    MarketSystemTargetsHeader(
                        title = state.targetTypeFilter.label,
                        itemCount = filteredSystemTargets.size,
                        modifier = Modifier.padding(horizontal = spacing.xl),
                    )
                }
            } else {
                item {
                    MarketListHeader(
                        title = selectedGroup?.name ?: "自选池",
                        itemCount = filteredVisibleItems.size,
                        onAddTargets = { onOpenAddTargets(selectedGroup?.id) },
                        modifier = Modifier.padding(horizontal = spacing.xl),
                    )
                }
            }
            item { Spacer(Modifier.height(spacing.lg)) }

            if (isAllTargetsSelected) {
                when {
                    state.isLoading && state.systemTargets.isEmpty() -> {
                        item {
                            MarketLoadingPanel(
                                text = "正在同步系统标的",
                                modifier = Modifier.padding(horizontal = spacing.xl),
                            )
                        }
                    }

                    state.systemTargets.isEmpty() -> {
                        item {
                            MarketEmptyPanel(
                                text = "暂未获取到系统标的",
                                modifier = Modifier.padding(horizontal = spacing.xl),
                            )
                        }
                    }

                    filteredSystemTargets.isEmpty() -> {
                        item {
                            MarketEmptyPanel(
                                text = "当前类型暂无标的",
                                modifier = Modifier.padding(horizontal = spacing.xl),
                            )
                        }
                    }

                    else -> {
                        if (
                            state.targetTypeFilter == MarketTargetTypeFilter.All &&
                            state.sortOption == MarketSortOption.Default
                        ) {
                            orderedSystemTargetTypes.forEach { targetType ->
                                val targets = targetsByType[targetType].orEmpty()
                                item(key = "type-header:$targetType") {
                                    MarketTargetTypeHeader(
                                        targetType = targetType,
                                        itemCount = targets.size,
                                        modifier = Modifier.padding(
                                            start = spacing.xl,
                                            top = spacing.xl,
                                            end = spacing.xl,
                                            bottom = spacing.sm,
                                        ),
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
                                            onOpenTargetDetail(
                                                target.targetType,
                                                target.targetCode,
                                                null,
                                            )
                                        },
                                    )
                                    if (index < targets.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = spacing.xl),
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                        )
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = filteredSystemTargets,
                                key = { _, target -> target.targetKey },
                                contentType = { _, _ -> "system-market-target" },
                            ) { index, target ->
                                MarketSystemTargetRow(
                                    target = target,
                                    onOpenDetail = {
                                        onOpenTargetDetail(
                                            target.targetType,
                                            target.targetCode,
                                            null,
                                        )
                                    },
                                )
                                if (index < filteredSystemTargets.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = spacing.xl),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                when {
                    state.isLoading && visibleItems.isEmpty() -> {
                        item {
                            MarketLoadingPanel(
                                text = "正在同步自选行情",
                                modifier = Modifier.padding(horizontal = spacing.xl),
                            )
                        }
                    }

                    visibleItems.isEmpty() -> {
                        item {
                            MarketEmptyPanel(
                                text = "当前自选池暂无标的",
                                modifier = Modifier.padding(horizontal = spacing.xl),
                            )
                        }
                    }

                    filteredVisibleItems.isEmpty() -> {
                        item {
                            MarketEmptyPanel(
                                text = "当前类型暂无标的",
                                modifier = Modifier.padding(horizontal = spacing.xl),
                            )
                        }
                    }

                    else -> {
                        itemsIndexed(
                            items = filteredVisibleItems,
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
                                onDelete = { onDeleteTarget(item.id) },
                            )
                            if (index < filteredVisibleItems.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = spacing.xl),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (state.syncMessage.isNotBlank()) {
                item {
                    Text(
                        text = state.syncMessage,
                        modifier = Modifier.padding(
                            start = spacing.xl,
                            top = spacing.lg,
                            end = spacing.xl,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    if (showSortSheet) {
        MarketSortBottomSheet(
            selectedOption = state.sortOption,
            onDismiss = { showSortSheet = false },
            onSelectOption = { option ->
                onSelectSortOption(option)
                showSortSheet = false
            },
        )
    }
}

@Composable
private fun MarketPageHeader(onSearch: () -> Unit) {
    val spacing = LocalFinanceSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = spacing.xl,
                top = spacing.section,
                end = spacing.sm,
                bottom = spacing.xl,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "行情",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        IconButton(onClick = onSearch) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "搜索行情",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MarketGroupTabs(
    groups: List<MarketWatchGroup>,
    selectedGroupId: String?,
    onSelectGroup: (String?) -> Unit,
    onManageGroups: () -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier
                .weight(1f)
                .selectableGroup(),
            contentPadding = PaddingValues(start = spacing.xl, end = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item(key = "all") {
                MarketGroupTab(
                    label = "全部",
                    selected = selectedGroupId == null,
                    onClick = { onSelectGroup(null) },
                )
            }
            items(
                items = groups,
                key = MarketWatchGroup::id,
                contentType = { "market-group-tab" },
            ) { group ->
                MarketGroupTab(
                    label = group.name,
                    selected = group.id == selectedGroupId,
                    onClick = { onSelectGroup(group.id) },
                )
            }
        }
        TextButton(
            onClick = onManageGroups,
            modifier = Modifier.heightIn(min = dimensions.minTouchTarget),
            contentPadding = PaddingValues(horizontal = spacing.sm),
        ) {
            Text(
                text = "管理",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MarketFilterToolbar(
    selectedFilter: MarketTargetTypeFilter,
    sortOption: MarketSortOption,
    onSelectFilter: (MarketTargetTypeFilter) -> Unit,
    onOpenSort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.xs),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = dimensions.minTouchTarget),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "标的范围",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                TextButton(
                    onClick = onOpenSort,
                    modifier = Modifier
                        .heightIn(min = dimensions.minTouchTarget)
                        .semantics {
                            contentDescription = "排序方式：${sortOption.label}"
                        },
                    contentPadding = PaddingValues(horizontal = spacing.sm),
                ) {
                    Text(
                        text = "排序：${sortOption.label}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (sortOption == MarketSortOption.Default) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        maxLines = 1,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarketTargetTypeFilter.entries.forEach { filter ->
                    val selected = filter == selectedFilter
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = dimensions.minTouchTarget)
                            .selectable(
                                selected = selected,
                                onClick = { onSelectFilter(filter) },
                                role = Role.RadioButton,
                            ),
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketSortBottomSheet(
    selectedOption: MarketSortOption,
    onDismiss: () -> Unit,
    onSelectOption: (MarketSortOption) -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .selectableGroup()
                .padding(
                    start = spacing.xl,
                    end = spacing.xl,
                    bottom = spacing.section,
                ),
        ) {
            Text(
                text = "排序方式",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(spacing.sm))
            MarketSortOption.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = dimensions.minTouchTarget)
                        .selectable(
                            selected = option == selectedOption,
                            onClick = { onSelectOption(option) },
                            role = Role.RadioButton,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = option == selectedOption,
                        onClick = null,
                    )
                    Spacer(Modifier.size(spacing.sm))
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketGroupTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    Column {
        Tab(
            selected = selected,
            onClick = onClick,
            modifier = Modifier.heightIn(min = LocalFinanceDimensions.current.minTouchTarget),
            selectedContentColor = MaterialTheme.colorScheme.primary,
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            text = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        HorizontalDivider(
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            thickness = spacing.xxs,
        )
    }
}

@Composable
private fun MarketSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun MarketAttentionSection(
    items: List<MarketAttentionItem>,
    totalItemCount: Int,
    onOpenTargetDetail: (String, String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MarketSectionTitle(title = "需要查看")
            Text(
                text = if (totalItemCount > items.size) {
                    "已触发 $totalItemCount 条 · 优先展示 ${items.size} 条"
                } else {
                    "已触发 $totalItemCount 条"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(spacing.lg))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(
                width = dimensions.outlineWidth,
                color = MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Column {
                items.forEachIndexed { index, attentionItem ->
                    MarketAttentionRow(
                        attentionItem = attentionItem,
                        onOpenDetail = {
                            onOpenTargetDetail(
                                attentionItem.item.targetType,
                                attentionItem.item.targetCode,
                                attentionItem.item.id,
                            )
                        },
                    )
                    if (index < items.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketAttentionRow(
    attentionItem: MarketAttentionItem,
    onOpenDetail: () -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    val semanticColors = LocalFinanceSemanticColors.current
    val item = attentionItem.item
    val alertText = attentionItem.alert.thresholdPercent?.let { threshold ->
        "提醒阈值 ±${threshold.asThresholdText()} 已越界"
    } ?: "提醒阈值已越界"
    val changeColor = item.changePercent.marketChangeColor(
        positive = semanticColors.positive,
        negative = semanticColors.negative,
        neutral = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimensions.compactRowHeight)
            .clickable(onClick = onOpenDetail)
            .padding(horizontal = spacing.lg, vertical = spacing.md)
            .semantics {
                contentDescription = item.targetName + "，" + alertText + "，" +
                    item.changePercent.asPercentText()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.targetName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(spacing.xxs))
            Text(
                text = alertText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = item.changePercent.asPercentText(),
                style = MaterialTheme.typography.titleMedium,
                color = changeColor,
            )
            Text(
                text = "已越界",
                style = MaterialTheme.typography.labelMedium,
                color = semanticColors.warning,
            )
        }
    }
}

@Composable
private fun MarketOverviewContent(
    indices: List<MarketIndexQuote>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    when {
        isLoading && indices.isEmpty() -> MarketLoadingPanel(
            text = "正在同步指数行情",
            modifier = modifier,
        )

        indices.isEmpty() -> MarketEmptyPanel(
            text = "暂未获取到指数行情",
            modifier = modifier,
        )

        else -> {
            Card(
                modifier = modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    width = LocalFinanceDimensions.current.outlineWidth,
                    color = MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column {
                    indices.forEachIndexed { index, item ->
                        MarketIndexRow(item = item)
                        if (index < indices.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketIndexRow(item: MarketIndexQuote) {
    val spacing = LocalFinanceSpacing.current
    val semanticColors = LocalFinanceSemanticColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalFinanceDimensions.current.compactRowHeight)
            .padding(horizontal = spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.latestPrice.asPriceText(),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = item.changePercent.asPercentText(),
            modifier = Modifier.widthIn(min = LocalFinanceSpacing.current.xxl * 3),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.titleMedium,
            color = item.changePercent.marketChangeColor(
                positive = semanticColors.positive,
                negative = semanticColors.negative,
                neutral = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

@Composable
private fun MarketSystemTargetsHeader(
    title: String,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(LocalFinanceSpacing.current.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MarketSectionTitle(
                title = if (title == MarketTargetTypeFilter.All.label) "全部标的" else "${title}标的",
            )
            Text(
                text = "$itemCount 个标的",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MarketListHeader(
    title: String,
    itemCount: Int,
    onAddTargets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(LocalFinanceSpacing.current.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MarketSectionTitle(title = title)
            Text(
                text = "$itemCount 个标的",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onAddTargets,
                modifier = Modifier.heightIn(min = LocalFinanceDimensions.current.minTouchTarget),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddCircleOutline,
                    contentDescription = null,
                )
                Spacer(Modifier.size(LocalFinanceSpacing.current.xs))
                Text("添加")
            }
        }
    }
}

@Composable
private fun MarketTargetTypeHeader(
    targetType: String,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { heading() },
        horizontalArrangement = Arrangement.spacedBy(LocalFinanceSpacing.current.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = targetType.marketTargetTypeLabel(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "$itemCount 个标的",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MarketSystemTargetRow(
    target: MarketSystemTarget,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val semanticColors = LocalFinanceSemanticColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
            .padding(horizontal = spacing.xl, vertical = spacing.lg)
            .semantics {
                contentDescription = target.targetName + "，" + target.targetCode + "，" +
                    target.latestPrice.asPriceText() + "，" + target.changePercent.asPercentText()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = target.targetName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = target.targetCode + " · " + target.targetType.marketTargetTypeLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MarketPriceChange(
            latestPrice = target.latestPrice,
            changePercent = target.changePercent,
            semanticColors = semanticColors,
        )
    }
}

@Composable
private fun MarketWatchTargetRow(
    item: MarketWatchItem,
    alert: MarketAlert?,
    onOpenDetail: () -> Unit,
    onOpenSettings: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val semanticColors = LocalFinanceSemanticColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
            .padding(
                start = spacing.xl,
                top = spacing.lg,
                end = spacing.sm,
                bottom = spacing.lg,
            ),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.targetName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.targetCode + " · " + item.targetType.marketTargetTypeLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MarketPriceChange(
                latestPrice = item.latestPrice,
                changePercent = item.changePercent,
                semanticColors = semanticColors,
            )
            MarketTargetActions(
                targetName = item.targetName,
                onOpenSettings = onOpenSettings,
                onDelete = onDelete,
            )
        }
        MarketAlertSummary(alert = alert)
    }
}

@Composable
private fun MarketPriceChange(
    latestPrice: Double?,
    changePercent: Double?,
    semanticColors: FinanceSemanticColors,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(LocalFinanceSpacing.current.xxs),
    ) {
        Text(
            text = latestPrice.asPriceText(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = changePercent.asPercentText(),
            style = MaterialTheme.typography.titleMedium,
            color = changePercent.marketChangeColor(
                positive = semanticColors.positive,
                negative = semanticColors.negative,
                neutral = MaterialTheme.colorScheme.onSurfaceVariant,
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
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "更多操作：$targetName",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("标的设置") },
                onClick = {
                    expanded = false
                    onOpenSettings()
                },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("从自选池移除") },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun MarketAlertSummary(alert: MarketAlert?) {
    val spacing = LocalFinanceSpacing.current
    val semanticColors = LocalFinanceSemanticColors.current
    when {
        alert == null -> Text(
            text = "未设置提醒",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (alert.enabled) {
                    "提醒已开启 · 阈值 ±${alert.thresholdPercent.asThresholdText()}"
                } else {
                    "提醒已停用 · 阈值 ±${alert.thresholdPercent.asThresholdText()}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (alert.enabled && alert.outOfThreshold) {
                Text(
                    text = "已越界",
                    style = MaterialTheme.typography.labelMedium,
                    color = semanticColors.warning,
                )
            }
        }
    }
}

@Composable
private fun MarketLoadingPanel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    Surface(
        modifier = modifier
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
private fun MarketEmptyPanel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
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

private fun MarketUiState.alertFor(item: MarketWatchItem): MarketAlert? =
    alerts.firstOrNull { it.targetKey == item.targetKey }

private data class MarketAttentionItem(
    val item: MarketWatchItem,
    val alert: MarketAlert,
)

private fun MarketUiState.findTarget(itemId: String?): MarketWatchItem? {
    if (itemId.isNullOrBlank()) return null
    return groups.asSequence()
        .flatMap { group -> group.items.asSequence() }
        .firstOrNull { item -> item.id == itemId }
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

@Composable
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
