package com.scrapider.finance.androidapp.feature.workbench.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.scrapider.finance.androidapp.R
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.designsystem.rememberFinanceSignalColors
import com.scrapider.finance.androidapp.feature.workbench.reports.ReportTopBar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
internal fun ChatScreen(
    state: ChatUiState,
    canSend: Boolean,
    isConnecting: Boolean,
    onBack: () -> Unit,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onReconnect: () -> Unit,
    onLoadOlder: () -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var followLatest by rememberSaveable { mutableStateOf(true) }
    val awayFromLatest by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    val latest = state.messages.lastOrNull()
    val reversedMessages = remember(state.messages) { state.messages.asReversed() }

    LaunchedEffect(listState) {
        snapshotFlow { Triple(listState.isScrollInProgress, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .distinctUntilChanged().collect { (scrolling, index, offset) ->
                if (scrolling) followLatest = index == 0 && offset == 0
                else if (index == 0 && offset == 0) followLatest = true
            }
    }
    LaunchedEffect(latest?.key, latest?.text, state.progress) {
        if (followLatest && !listState.isScrollInProgress) listState.scrollToItem(0)
    }

    Column(modifier.fillMaxSize()) {
        ReportTopBar("AI 研究助手", onBack = onBack)
        if (state.error.isNotBlank()) {
            ChatNotice(state.error, onReconnect, enabled = !isConnecting && !state.isSending)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.messages.isEmpty()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.xl, vertical = spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    when {
                        isConnecting || state.connectionState == ChatConnectionState.Idle -> ChatProgress("正在连接研究助手")
                        state.historyLoading -> ChatProgress("正在读取对话记录")
                        state.historyError.isNotBlank() -> {
                            Text(state.historyError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = onLoadOlder) { Text("重试读取记录") }
                        }
                        state.historyHasLoaded -> ChatWelcome(onDraftChanged)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = spacing.xl, vertical = spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxl),
                ) {
                    if (state.isSending) item("progress", contentType = "progress") {
                        ChatProgress(state.progress.ifBlank { "正在整理回答" })
                    }
                    items(reversedMessages, key = { it.key }, contentType = { it.role }) { message ->
                        ChatMessageItem(message, onCopy)
                    }
                    if (state.historyLoading) item("history-loading", contentType = "progress") {
                        ChatProgress("正在读取对话记录")
                    }
                    if (state.historyError.isNotBlank()) item("history-error", contentType = "error") {
                        Column {
                            Text(state.historyError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = onLoadOlder, enabled = !state.historyLoading) { Text("重试读取记录") }
                        }
                    }
                    else if (state.hasMore) item("older", contentType = "older") {
                        TextButton(onClick = { followLatest = false; onLoadOlder() }, enabled = !state.historyLoading,
                            modifier = Modifier.fillMaxWidth()) { Text("查看更早的对话") }
                    }
                }
            }
            if (awayFromLatest) FilledTonalButton(
                onClick = { followLatest = true; scope.launch { listState.scrollToItem(0) } },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = spacing.sm),
            ) { Text("回到最新") }
        }
        ChatComposer(state.draft, canSend, state.isSending, CHAT_MAX_MESSAGE_LENGTH, onDraftChanged,
            onSend = { followLatest = true; onSend() })
    }
}

@Composable
private fun ChatMessageItem(message: ChatMessage, onCopy: (String) -> Unit) {
    val spacing = LocalFinanceSpacing.current
    val secondary = rememberFinanceSignalColors().onNeutralContainer
    if (message.role == ChatMessageRole.User) {
        Column(Modifier.fillMaxWidth().padding(start = spacing.section), horizontalAlignment = Alignment.End) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large) {
                SelectionContainer {
                    Text(message.text, Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text(message.timeLabel, Modifier.padding(top = spacing.xs), style = MaterialTheme.typography.bodySmall, color = secondary)
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Icon(painterResource(R.drawable.ic_phosphor_brain_duotone), null,
                    Modifier.size(LocalFinanceDimensions.current.iconSize), tint = MaterialTheme.colorScheme.primary)
                Text("研究助手", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            if (message.text.isNotEmpty()) SelectionContainer { ChatAnswer(message.text, Modifier.fillMaxWidth()) }
            if (message.isInterrupted) Text("连接已中断，回答可能不完整。", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            if (!message.isStreaming && message.text.isNotEmpty()) Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(message.timeLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = secondary)
                TextButton(onClick = { onCopy(message.text) }) { Text("复制回答") }
            }
        }
    }
}

@Composable
private fun ChatWelcome(onPrompt: (String) -> Unit) {
    val spacing = LocalFinanceSpacing.current
    Column(Modifier.fillMaxWidth().padding(vertical = spacing.section), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text("从一个研究问题开始", style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
        Text("描述你关注的标的或疑问，结合已有资料继续追问。", style = MaterialTheme.typography.bodyLarge,
            color = rememberFinanceSignalColors().onNeutralContainer)
        listOf("帮我梳理今天关注标的的研究要点", "分析一个标的前，需要检查哪些风险？").forEach { prompt ->
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "填入问题") { onPrompt(prompt) }
                    .heightIn(min = LocalFinanceDimensions.current.minTouchTarget).padding(vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text(prompt, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Icon(painterResource(R.drawable.ic_phosphor_arrow_right), null,
                    Modifier.size(LocalFinanceDimensions.current.iconSize), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ChatProgress(text: String) {
    val dimensions = LocalFinanceDimensions.current
    Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(LocalFinanceSpacing.current.sm), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(dimensions.iconSize), strokeWidth = dimensions.outlineWidth)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = rememberFinanceSignalColors().onNeutralContainer)
    }
}

@Composable
private fun ChatNotice(text: String, onRetry: () -> Unit, enabled: Boolean) {
    Column(Modifier.padding(horizontal = LocalFinanceSpacing.current.xl)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry, enabled = enabled) { Text("重新连接") }
    }
}
