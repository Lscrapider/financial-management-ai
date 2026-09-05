package com.scrapider.finance.androidapp.feature.market

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.session.UserSession
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 行情入口：仅连接 ViewModel、事件流、回退和页面回调。 */
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
    var groupSavedSignal by rememberSaveable { mutableStateOf(0L) }

    LaunchedEffect(session.accessToken) {
        viewModel.loadForSession(session.accessToken)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                MarketEvent.GroupSaved -> groupSavedSignal += 1L
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
        groupSavedSignal = groupSavedSignal,
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
        onSaveGroup = viewModel::saveGroup,
        onDeleteGroup = viewModel::deleteGroup,
        onDeleteTarget = viewModel::deleteTarget,
        onNavigateBack = viewModel::navigateBack,
        modifier = modifier,
    )
}
