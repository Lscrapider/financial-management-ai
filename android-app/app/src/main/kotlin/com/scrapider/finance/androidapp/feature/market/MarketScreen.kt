package com.scrapider.finance.androidapp.feature.market

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.scrapider.finance.androidapp.feature.market.management.AddMarketTargetScreen
import com.scrapider.finance.androidapp.feature.market.management.MarketGroupManagementScreen
import com.scrapider.finance.androidapp.feature.market.management.MarketTargetSettingsScreen
import com.scrapider.finance.androidapp.feature.market.ui.MarketListScreen
import com.scrapider.finance.androidapp.feature.market.ui.MarketMissingTargetDetailScreen
import com.scrapider.finance.androidapp.feature.market.ui.MarketMissingTargetScreen
import com.scrapider.finance.androidapp.feature.market.ui.MarketSearchScreen
import com.scrapider.finance.androidapp.feature.market.ui.MarketTargetDetailScreen
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 仅根据当前 destination 分发页面，不持有 ViewModel 或页面本地交互状态。 */
@Composable
internal fun MarketScreen(
    state: MarketUiState,
    snackbarHostState: SnackbarHostState,
    bottomBar: @Composable () -> Unit,
    groupSavedSignal: Long,
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
    onSaveGroup: (String?, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onDeleteTarget: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val destination = state.destination) {
        MarketDestination.List -> MarketListScreen(
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

        MarketDestination.ManageGroups -> MarketGroupManagementScreen(
            groups = state.groups,
            isSaving = state.isSaving,
            groupSavedSignal = groupSavedSignal,
            snackbarHostState = snackbarHostState,
            onNavigateBack = onNavigateBack,
            onSaveGroup = onSaveGroup,
            onDeleteGroup = onDeleteGroup,
            modifier = modifier,
        )

        is MarketDestination.AddTargets -> AddMarketTargetScreen(
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

        is MarketDestination.TargetSettings -> {
            val target = state.findWatchItem(destination.itemId)
            if (target == null) {
                MarketMissingTargetScreen(
                    snackbarHostState = snackbarHostState,
                    onNavigateBack = onNavigateBack,
                    modifier = modifier,
                )
            } else {
                MarketTargetSettingsScreen(
                    target = target,
                    groupName = state.groups.firstOrNull { group -> group.id == target.groupId }?.name.orEmpty(),
                    alert = state.alertFor(target),
                    isSaving = state.isSaving,
                    snackbarHostState = snackbarHostState,
                    onNavigateBack = onNavigateBack,
                    onSave = onSaveTargetSettings,
                    modifier = modifier,
                )
            }
        }

        MarketDestination.Search -> MarketSearchScreen(
            targets = state.availableTargetSnapshots(),
            query = state.searchQuery,
            onNavigateBack = onNavigateBack,
            onQueryChange = onUpdateSearchQuery,
            onTargetSelected = { target ->
                onOpenTargetDetail(target.targetType, target.targetCode, target.watchItemId)
            },
            modifier = modifier,
        )

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
