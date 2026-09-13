package com.scrapider.finance.androidapp.feature.workbench.knowledge

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.session.UserSession
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportInfoState
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportLoadingScreen
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportTopBar
import com.scrapider.finance.androidapp.feature.workbench.reports.reportTargetTypes
import kotlinx.coroutines.flow.collect

@Composable
internal fun KnowledgeRoute(
    session: UserSession,
    apiClient: FinanceApiClient,
    onClose: () -> Unit,
    onSessionExpired: () -> Unit,
    onNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentModifier = modifier.fillMaxSize().imePadding()
    if (!session.isAdmin) {
        BackHandler(onBack = onClose)
        KnowledgeRestrictedScreen(onBack = onClose, modifier = contentModifier)
        return
    }

    val factory = remember(apiClient) { KnowledgeViewModel.Factory(apiClient) }
    val viewModel: KnowledgeViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSessionExpired by rememberUpdatedState(onSessionExpired)
    val currentOnNotice by rememberUpdatedState(onNotice)
    // 会话绑定必须主动触发重组，不能依赖提前返回后尚未读取的界面状态。
    var initialized by remember(session.accessToken) { mutableStateOf(false) }
    val targetTypeOptions = remember {
        reportTargetTypes.filter { it.value.isNotBlank() }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val dismissInput: () -> Unit = {
        keyboard?.hide()
        focus.clearFocus()
    }
    val navigateBack: () -> Unit = {
        dismissInput()
        onClose()
    }

    LaunchedEffect(session.accessToken) {
        viewModel.enter(session.accessToken)
        initialized = true
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                KnowledgeEvent.SessionExpired -> currentOnSessionExpired()
                is KnowledgeEvent.Notice -> currentOnNotice(event.message)
            }
        }
    }
    LifecycleStartEffect(viewModel) {
        viewModel.setActive(true)
        onStopOrDispose { viewModel.setActive(false) }
    }
    BackHandler(onBack = navigateBack)

    if (!initialized || !viewModel.belongsToSession(session.accessToken)) {
        ReportLoadingScreen(
            title = "知识检索",
            text = "正在加载知识检索",
            onBack = navigateBack,
            modifier = contentModifier,
        )
        return
    }

    KnowledgeScreen(
        state = state,
        targetTypeOptions = targetTypeOptions,
        onBack = navigateBack,
        onModeSelected = viewModel::selectMode,
        onQueryTextChanged = viewModel::updateQueryText,
        onProfileSelected = viewModel::selectProfile,
        onTargetTypeSelected = viewModel::selectTargetType,
        onTargetQueryChanged = viewModel::updateTargetQuery,
        onSearchTargets = {
            dismissInput()
            viewModel.searchTargets()
        },
        onTargetSelected = {
            dismissInput()
            viewModel.selectTarget(it)
        },
        onSubmit = {
            dismissInput()
            viewModel.submit()
        },
        onRetryMetadata = viewModel::loadMetadata,
        onRetryTargetSearch = {
            dismissInput()
            viewModel.searchTargets()
        },
        onRetryTask = viewModel::refreshTask,
        onRetryAfterUnconfirmed = {
            dismissInput()
            viewModel.retryAfterUnconfirmed()
        },
        modifier = contentModifier,
    )
}

@Composable
private fun KnowledgeRestrictedScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ReportTopBar(
            title = "知识检索",
            onBack = onBack,
        )
        ReportInfoState(
            text = "当前账号没有访问该功能的权限。",
            modifier = Modifier.padding(
                horizontal = com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing.current.xl,
                vertical = com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing.current.lg,
            ),
        )
    }
}
