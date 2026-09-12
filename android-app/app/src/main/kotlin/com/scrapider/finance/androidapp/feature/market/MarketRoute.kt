package com.scrapider.finance.androidapp.feature.market

import android.os.SystemClock
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    val detail by viewModel.detailState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val snackbarHostState = remember { SnackbarHostState() }
    var groupSavedSignal by rememberSaveable { mutableStateOf(0L) }

    LaunchedEffect(viewModel, session.accessToken, lifecycle, state.destination, detail.period, detail.adjust) {
        viewModel.loadForSession(session.accessToken)
        val destination = state.destination
        if (destination == MarketDestination.List ||
            destination is MarketDestination.TargetDetail) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    val startedAt = SystemClock.elapsedRealtime()
                    viewModel.updateVisibleContent(destination)
                    val elapsed = SystemClock.elapsedRealtime() - startedAt
                    delay(if (elapsed < MARKET_REFRESH_INTERVAL_MS) MARKET_REFRESH_INTERVAL_MS - elapsed else MARKET_REFRESH_INTERVAL_MS)
                }
            }
        }
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
        detail = detail,
        onPeriodSelected = viewModel::selectChartPeriod,
        onAdjustSelected = viewModel::selectChartAdjust,
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
