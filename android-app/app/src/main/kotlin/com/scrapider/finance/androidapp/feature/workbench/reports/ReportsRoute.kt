package com.scrapider.finance.androidapp.feature.workbench.reports

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.session.UserSession

/** 报告局部导航沿用当前项目的状态导航，底栏和 Snackbar 仍由 AppShell 承载。 */
@Composable
internal fun ReportsRoute(
    session: UserSession,
    apiClient: FinanceApiClient,
    entryKey: String,
    initialReportId: String?,
    onClose: () -> Unit,
    onSessionExpired: () -> Unit,
    onNotice: (String) -> Unit,
    onReportsChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory = remember(apiClient) { ReportsViewModel.Factory(apiClient) }
    val viewModel: ReportsViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSessionExpired by rememberUpdatedState(onSessionExpired)
    val currentOnNotice by rememberUpdatedState(onNotice)
    val currentOnReportsChanged by rememberUpdatedState(onReportsChanged)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val dismissKeyboard: () -> Unit = {
        keyboard?.hide()
        focus.clearFocus()
    }
    val navigateBack: () -> Unit = {
        dismissKeyboard()
        if (viewModel.shouldCloseOnBack()) onClose() else viewModel.back()
    }

    LaunchedEffect(session.accessToken, entryKey) {
        viewModel.enter(session.accessToken, entryKey, initialReportId)
    }
    LaunchedEffect(viewModel, session.accessToken) {
        viewModel.events.collect { event ->
            when (event) {
                ReportsEvent.SessionExpired -> currentOnSessionExpired()
                ReportsEvent.ReportsChanged -> currentOnReportsChanged()
                is ReportsEvent.Notice -> currentOnNotice(event.message)
            }
        }
    }
    LifecycleStartEffect(viewModel) {
        viewModel.setActive(true)
        onStopOrDispose { viewModel.setActive(false) }
    }
    BackHandler(onBack = navigateBack)

    val contentModifier = modifier.fillMaxSize().imePadding()
    if (!viewModel.belongsToSession(session.accessToken)) {
        ReportLoadingState(text = "正在加载研究报告", modifier = contentModifier)
        return
    }
    val savedPages = rememberSaveableStateHolder()
    val pageKey = when (state.page) {
        ReportPage.Detail -> "detail:${state.document?.reportId ?: state.document?.taskNo.orEmpty()}"
        ReportPage.History -> "history:${state.historyTarget?.key.orEmpty()}"
        else -> state.page.name
    }
    savedPages.SaveableStateProvider(pageKey) {
        when (state.page) {
            ReportPage.List -> ReportListScreen(
                state = state,
                typeChoices = reportTargetTypes,
                onBack = navigateBack,
                onQueryChange = viewModel::updateQuery,
                onSearch = { dismissKeyboard(); viewModel.refresh() },
                onTypeSelected = viewModel::selectType,
                onOpenLatest = viewModel::openTarget,
                onOpenHistory = viewModel::openHistory,
                onRefresh = { viewModel.refresh() },
                onLoadMore = viewModel::loadMore,
                onGenerate = { dismissKeyboard(); viewModel.openCreate() },
                modifier = contentModifier,
            )
            ReportPage.History -> ReportHistoryScreen(
                state = state,
                onBack = navigateBack,
                onSelectReport = { viewModel.openRecord(it.reportId) },
                onRefresh = viewModel::refreshHistory,
                modifier = contentModifier,
            )
            ReportPage.Detail -> ReportReaderScreen(
                state = state,
                onBack = navigateBack,
                onRefresh = viewModel::refreshDocument,
                onRegenerate = viewModel::regenerate,
                modifier = contentModifier,
            )
            ReportPage.Create -> ReportCreateScreen(
                state = state.create,
                targetTypeOptions = reportTargetTypes.filter { it.value.isNotBlank() },
                onBack = navigateBack,
                onRetry = viewModel::loadMetadata,
                onProfileSelected = viewModel::selectProfile,
                onReportTypeSelected = viewModel::selectReportType,
                onTargetTypeSelected = viewModel::selectTargetType,
                onTargetQueryChanged = viewModel::updateTargetQuery,
                onSearchTargets = { dismissKeyboard(); viewModel.searchTargets() },
                onRetrySearch = viewModel::searchTargets,
                onTargetSelected = { dismissKeyboard(); viewModel.selectTarget(it) },
                onSubmit = {
                    dismissKeyboard()
                    if (state.create.submissionUnconfirmed) viewModel.reviewSubmission() else viewModel.submit()
                },
                modifier = contentModifier,
            )
        }
    }
}
