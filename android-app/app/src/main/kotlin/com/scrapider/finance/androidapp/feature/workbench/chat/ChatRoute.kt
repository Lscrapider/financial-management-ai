package com.scrapider.finance.androidapp.feature.workbench.chat

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.session.UserSession
import kotlinx.coroutines.launch

@Composable
internal fun ChatRoute(
    session: UserSession,
    apiClient: FinanceApiClient,
    entryKey: String,
    onClose: () -> Unit,
    onSessionExpired: () -> Unit,
    onNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory = remember(apiClient) { ChatViewModel.Factory(apiClient) }
    val viewModel: ChatViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentExpired by rememberUpdatedState(onSessionExpired)
    val currentNotice by rememberUpdatedState(onNotice)
    var initialized by remember(session.accessToken, entryKey) { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val dismissKeyboard = { focus.clearFocus(); keyboard?.hide(); Unit }
    val close = { dismissKeyboard(); viewModel.leave(); onClose() }

    DisposableEffect(viewModel, session.accessToken, entryKey) {
        viewModel.enter(session.accessToken, entryKey)
        initialized = true
        onDispose { viewModel.leave() }
    }
    LifecycleStartEffect(viewModel, session.accessToken, entryKey) {
        viewModel.active(true)
        onStopOrDispose { viewModel.active(false) }
    }
    LaunchedEffect(viewModel, session.accessToken, entryKey) {
        viewModel.events.collect { event ->
            when (event) {
                ChatEvent.SessionExpired -> currentExpired()
                is ChatEvent.Notice -> currentNotice(event.message)
            }
        }
    }
    BackHandler(onBack = close)
    val visibleState = if (initialized) state else ChatUiState(connectionState = ChatConnectionState.Connecting)
    ChatScreen(
        state = visibleState,
        canSend = initialized && state.canSend,
        isConnecting = visibleState.connectionState == ChatConnectionState.Connecting,
        onBack = close,
        onDraftChanged = viewModel::updateDraft,
        onSend = { dismissKeyboard(); viewModel.send() },
        onReconnect = viewModel::reconnect,
        onLoadOlder = viewModel::loadOlder,
        onCopy = { text ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("研究助手回答", text)))
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) currentNotice("回答已复制")
            }
        },
        modifier = modifier.imePadding(),
    )
}
