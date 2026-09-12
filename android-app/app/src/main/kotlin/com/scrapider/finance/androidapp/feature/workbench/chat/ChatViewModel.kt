package com.scrapider.finance.androidapp.feature.workbench.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrapider.finance.androidapp.core.network.ApiConfig
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.network.NetworkFailure
import com.scrapider.finance.androidapp.core.network.NetworkResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

internal class ChatViewModel(
    private val repository: ChatRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState = _uiState.asStateFlow()

    private val _socketCallbacks = Channel<SocketCallback>(Channel.UNLIMITED)
    private val eventChannel = Channel<ChatEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var sessionToken = ""
    private var entryKey = ""
    private var sessionEpoch = 0L
    private var active = false
    private var conversationId = ""
    private var nextBeforeId: String? = null
    private var failedHistoryBeforeId: String? = null
    private var currentMessageId: String? = null
    private var socketAttempt = 0L
    private var endedSocketAttempt = 0L
    private var socket: ChatSocket? = null
    private var connectJob: Job? = null
    private var historyJob: Job? = null
    private val liveBuffer = mutableListOf<ChatSocketEvent>()

    init {
        viewModelScope.launch {
            for (callback in _socketCallbacks) handleSocketCallback(callback)
        }
    }

    fun enter(accessToken: String, newEntryKey: String) {
        if (accessToken == sessionToken && newEntryKey == entryKey && sessionToken.isNotBlank()) return

        val shouldActivate = active
        active = false
        sessionEpoch++
        connectJob?.cancel()
        historyJob?.cancel()
        closeSocket()
        clearEvents()
        sessionToken = accessToken
        entryKey = newEntryKey
        conversationId = ""
        nextBeforeId = null
        failedHistoryBeforeId = null
        currentMessageId = null
        liveBuffer.clear()
        _uiState.value = ChatUiState()
        active = shouldActivate
        if (active) ensureConnected()
    }

    fun active(value: Boolean) {
        if (active == value) {
            if (value) ensureConnected()
            return
        }
        active = value
        if (value) {
            ensureConnected()
        } else {
            sessionEpoch++
            connectJob?.cancel()
            connectJob = null
            historyJob?.cancel()
            closeSocket()
            markInterrupted()
            _uiState.value = _uiState.value.copy(
                connectionState = if (sessionToken.isBlank()) ChatConnectionState.Idle else ChatConnectionState.Interrupted,
                historyLoading = false,
                progress = "",
            )
        }
    }

    fun leave() {
        active(false)
        clearEvents()
        liveBuffer.clear()
    }

    fun updateDraft(value: String) {
        _uiState.value = _uiState.value.copy(draft = value)
    }

    fun send() {
        val state = _uiState.value
        val content = state.draft.trim()
        val currentSocket = socket
        val currentConversation = conversationId
        if (!state.canSend || currentSocket == null || currentConversation.isBlank()) return

        val messageId = "msg-${System.currentTimeMillis()}-${UUID.randomUUID()}"
        currentMessageId = messageId
        _uiState.value = state.copy(
            draft = "",
            error = "",
            progress = "正在分析",
            isSending = true,
            messages = state.messages + ChatMessage(
                messageId = messageId,
                conversationId = currentConversation,
                role = ChatMessageRole.User,
                text = content,
                timeLabel = nowChatTimeLabel(),
            ),
        )
        if (!currentSocket.sendUserMessage(messageId, content)) {
            markSendInterrupted(messageId, NetworkFailure.Unavailable.userMessage)
        }
    }

    fun reconnect() {
        if (!active || _uiState.value.isSending) return
        connectJob?.cancel()
        connectJob = null
        historyJob?.cancel()
        sessionEpoch++
        liveBuffer.clear()
        _uiState.value = _uiState.value.copy(historyLoading = false)
        closeSocket()
        ensureConnected()
    }

    fun loadOlder() {
        val state = _uiState.value
        val beforeId = if (state.historyError.isNotBlank()) failedHistoryBeforeId else nextBeforeId
        if (beforeId == null && state.historyHasLoaded && state.historyError.isBlank()) return
        if (conversationId.isBlank() || state.historyLoading) return
        loadHistory(beforeId)
    }

    fun retryHistory() = loadOlder()

    private fun ensureConnected() {
        if (!active || sessionToken.isBlank() || socket != null || connectJob?.isActive == true) return
        val epoch = sessionEpoch
        _uiState.value = _uiState.value.copy(
            connectionState = ChatConnectionState.Connecting,
            error = "",
        )
        connectJob = viewModelScope.launch {
            var retriedHandshakeUnauthorized = false
            while (active && epoch == sessionEpoch) {
                when (val ticketResult = repository.issueTicket()) {
                    is NetworkResult.Failure -> {
                        if (epoch == sessionEpoch) failConnection(ticketResult.reason, notifySession = ticketResult.reason == NetworkFailure.Unauthorized)
                        return@launch
                    }
                    is NetworkResult.Success -> {
                        val ready = CompletableDeferred<ReadyResult>()
                        val attempt = ++socketAttempt
                        endedSocketAttempt = 0L
                        val currentSocket = repository.openSocket(
                            ticketResult.data.ticket,
                            object : ChatSocketListener {
                                override fun onOpen() = Unit

                                override fun onEvent(event: ChatSocketEvent) {
                                    if (event is ChatSocketEvent.SessionReady) {
                                        ready.complete(ReadyResult.Ready(event.conversationId))
                                    }
                                    _socketCallbacks.trySend(SocketCallback.Event(epoch, attempt, event))
                                }

                                override fun onFailure(failure: NetworkFailure) {
                                    if (!ready.isCompleted) {
                                        ready.complete(ReadyResult.Failed(failure))
                                    } else {
                                        _socketCallbacks.trySend(SocketCallback.Failure(epoch, attempt, failure))
                                    }
                                }

                                override fun onClosed() {
                                    if (!ready.isCompleted) {
                                        ready.complete(ReadyResult.Failed(NetworkFailure.Unavailable))
                                    } else {
                                        _socketCallbacks.trySend(SocketCallback.Closed(epoch, attempt))
                                    }
                                }
                            },
                        )
                        socket = currentSocket
                        val readyResult = withTimeoutOrNull(ApiConfig.CONNECT_TIMEOUT_MS) { ready.await() }
                            ?: ReadyResult.Failed(NetworkFailure.Unavailable)
                        when (readyResult) {
                            is ReadyResult.Ready -> {
                                if (!active || epoch != sessionEpoch || attempt != socketAttempt || endedSocketAttempt == attempt) return@launch
                                onReady(epoch, readyResult.conversationId)
                                return@launch
                            }
                            is ReadyResult.Failed -> {
                                if (socket === currentSocket) socket = null
                                if (socketAttempt == attempt) socketAttempt++
                                currentSocket.close()
                                if (readyResult.failure == NetworkFailure.Unauthorized && !retriedHandshakeUnauthorized) {
                                    retriedHandshakeUnauthorized = true
                                    continue
                                }
                                if (epoch == sessionEpoch) {
                                    failConnection(
                                        readyResult.failure,
                                        notifySession = false,
                                    )
                                }
                                return@launch
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onReady(epoch: Long, newConversationId: String) {
        if (epoch != sessionEpoch || newConversationId.isBlank()) return
        if (conversationId != newConversationId) {
            if (conversationId.isNotBlank()) liveBuffer.clear()
            conversationId = newConversationId
            nextBeforeId = null
            failedHistoryBeforeId = null
            currentMessageId = null
            knownMessageIds.clear()
            completedMessageIds.clear()
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages.filter { it.conversationId == newConversationId },
                historyHasLoaded = false,
                hasMore = false,
            )
        }
        _uiState.value = _uiState.value.copy(
            connectionState = ChatConnectionState.Connected,
            error = "",
        )
        loadHistory(beforeId = null)
    }

    private fun loadHistory(beforeId: String?) {
        val epoch = sessionEpoch
        val expectedConversation = conversationId
        if (expectedConversation.isBlank() || _uiState.value.historyLoading) return
        failedHistoryBeforeId = beforeId
        _uiState.value = _uiState.value.copy(historyLoading = true, historyError = "")
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            when (val result = repository.history(expectedConversation, beforeId)) {
                is NetworkResult.Failure -> {
                    if (epoch != sessionEpoch || expectedConversation != conversationId) return@launch
                    _uiState.value = _uiState.value.copy(
                        historyLoading = false,
                        historyError = result.reason.userMessage,
                    )
                    replayBufferedEvents()
                    handleFailure(result.reason)
                }
                is NetworkResult.Success -> {
                    if (epoch != sessionEpoch || expectedConversation != conversationId) return@launch
                    val page = result.data
                    if (page.conversationId != expectedConversation) {
                        _uiState.value = _uiState.value.copy(
                            historyLoading = false,
                            historyError = NetworkFailure.InvalidResponse.userMessage,
                        )
                        replayBufferedEvents()
                        return@launch
                    }
                    mergeHistory(page.messages)
                    nextBeforeId = page.nextBeforeId
                    failedHistoryBeforeId = null
                    _uiState.value = _uiState.value.copy(
                        historyLoading = false,
                        historyHasLoaded = true,
                        historyError = "",
                        hasMore = page.hasMore && page.nextBeforeId != null,
                    )
                    replayBufferedEvents()
                }
            }
        }
    }

    private fun mergeHistory(incoming: List<ChatMessage>) {
        val messages = _uiState.value.messages.associateByTo(linkedMapOf(), ChatMessage::key)
        incoming.forEach { message ->
            messages[message.key] = message
            if (message.role == ChatMessageRole.User) knownMessageIds += message.messageId
            else completedMessageIds += message.messageId
        }
        val currentCompleted = currentMessageId != null && currentMessageId in completedMessageIds
        if (currentCompleted) currentMessageId = null
        // 数据库 ID 与游标采用同一顺序；尚未落库的本地消息保持插入顺序排在末尾。
        _uiState.value = _uiState.value.copy(
            messages = messages.values.sortedBy { it.recordId.toLongOrNull() ?: Long.MAX_VALUE },
            isSending = if (currentCompleted) false else _uiState.value.isSending,
            progress = if (currentCompleted) "" else _uiState.value.progress,
        )
    }

    private val knownMessageIds = mutableSetOf<String>()
    private val completedMessageIds = mutableSetOf<String>()

    private fun replayBufferedEvents() {
        val buffered = liveBuffer.toList()
        liveBuffer.clear()
        buffered.forEach(::handleLiveEvent)
    }

    private fun handleSocketCallback(callback: SocketCallback) {
        if (!active || callback.epoch != sessionEpoch || callback.attempt != socketAttempt || callback.attempt == endedSocketAttempt) return
        when (callback) {
            is SocketCallback.Event -> {
                if (callback.event is ChatSocketEvent.SessionReady) return
                if (conversationId.isBlank() || _uiState.value.historyLoading) {
                    liveBuffer += callback.event
                } else {
                    handleLiveEvent(callback.event)
                }
            }
            is SocketCallback.Failure -> handleSocketEnded(callback.failure)
            is SocketCallback.Closed -> handleSocketEnded(NetworkFailure.Unavailable)
        }
    }

    private fun handleLiveEvent(event: ChatSocketEvent) {
        when (event) {
            is ChatSocketEvent.SessionReady -> Unit
            is ChatSocketEvent.Progress -> {
                if (conversationId.isBlank() || event.conversationId != conversationId) return
                if (event.messageId != currentMessageId || event.messageId in completedMessageIds) return
                _uiState.value = _uiState.value.copy(progress = progressLabel(event.status, event.content))
            }
            is ChatSocketEvent.Delta -> {
                if (conversationId.isBlank() || event.conversationId != conversationId) return
                // 重连不承诺补发中途片段；旧问题只接收最终完整回答以校正历史。
                if (event.messageId != currentMessageId || event.messageId in completedMessageIds) return
                val prior = _uiState.value.messages.firstOrNull { it.role == ChatMessageRole.Assistant && it.messageId == event.messageId }?.text.orEmpty()
                updateAssistant(event.messageId, prior + event.content, streaming = true, interrupted = false, timeLabel = "")
                if (event.messageId == currentMessageId) _uiState.value = _uiState.value.copy(progress = "正在生成回答")
            }
            is ChatSocketEvent.Final -> {
                if (conversationId.isBlank() || event.conversationId != conversationId) return
                if (!isKnownMessage(event.messageId)) return
                val existing = _uiState.value.messages.firstOrNull {
                    it.conversationId == conversationId && it.role == ChatMessageRole.Assistant && it.messageId == event.messageId
                }
                updateAssistant(
                    event.messageId,
                    event.content.ifBlank { existing?.text.orEmpty() },
                    streaming = false,
                    interrupted = false,
                    timeLabel = event.createdAt,
                )
                completedMessageIds += event.messageId
                if (currentMessageId == event.messageId) {
                    currentMessageId = null
                    _uiState.value = _uiState.value.copy(isSending = false, progress = "", error = "")
                }
            }
        }
    }

    private fun updateAssistant(
        messageId: String,
        text: String,
        streaming: Boolean,
        interrupted: Boolean,
        timeLabel: String,
    ) {
        val state = _uiState.value
        val index = state.messages.indexOfFirst {
            it.conversationId == conversationId && it.role == ChatMessageRole.Assistant && it.messageId == messageId
        }
        if (index >= 0) {
            val messages = state.messages.toMutableList()
            messages[index] = messages[index].copy(
                text = text,
                isStreaming = streaming,
                isInterrupted = interrupted,
                timeLabel = if (timeLabel.isBlank()) messages[index].timeLabel else chatTimeLabel(timeLabel),
            )
            _uiState.value = state.copy(messages = messages)
        } else if (state.messages.any {
                it.conversationId == conversationId && it.role == ChatMessageRole.User && it.messageId == messageId
            }) {
            _uiState.value = state.copy(
                messages = state.messages + ChatMessage(
                    messageId = messageId,
                    conversationId = conversationId,
                    role = ChatMessageRole.Assistant,
                    text = text,
                    timeLabel = chatTimeLabel(timeLabel),
                    isStreaming = streaming,
                    isInterrupted = interrupted,
                ),
            )
        }
    }

    private fun isKnownMessage(messageId: String): Boolean = knownMessageIds.contains(messageId) ||
        _uiState.value.messages.any {
            it.conversationId == conversationId && it.messageId == messageId
        }

    private fun handleSocketEnded(failure: NetworkFailure) {
        if (!active || socketAttempt == endedSocketAttempt) return
        endedSocketAttempt = socketAttempt
        socket = null
        markInterrupted()
        _uiState.value = _uiState.value.copy(
            connectionState = ChatConnectionState.Interrupted,
            error = failure.userMessage,
            progress = "",
            isSending = false,
        )
        handleFailure(failure)
    }

    private fun markSendInterrupted(messageId: String, error: String) {
        closeSocket()
        markInterrupted()
        val state = _uiState.value
        _uiState.value = state.copy(
            connectionState = ChatConnectionState.Interrupted,
            error = error,
            progress = "",
            isSending = false,
            messages = state.messages.map {
                if (it.role == ChatMessageRole.Assistant && it.messageId == messageId) {
                    it.copy(isStreaming = false, isInterrupted = true)
                } else it
            },
        )
    }

    private fun markInterrupted() {
        val messageId = currentMessageId
        val state = _uiState.value
        val messages = state.messages.map {
            if (it.isStreaming) it.copy(isStreaming = false, isInterrupted = true) else it
        }.toMutableList()
        if (messageId != null && messages.none { it.role == ChatMessageRole.Assistant && it.messageId == messageId }) {
            messages += ChatMessage(messageId, conversationId, ChatMessageRole.Assistant, "", "", isInterrupted = true)
        }
        _uiState.value = state.copy(
            messages = messages,
            isSending = false,
        )
        currentMessageId = null
    }

    private fun failConnection(failure: NetworkFailure, notifySession: Boolean) {
        socket = null
        _uiState.value = _uiState.value.copy(
            connectionState = ChatConnectionState.Failed,
            error = failure.userMessage,
            isSending = false,
            progress = "",
        )
        if (notifySession) eventChannel.trySend(ChatEvent.SessionExpired)
    }

    private fun handleFailure(failure: NetworkFailure) {
        if (failure == NetworkFailure.Unauthorized) eventChannel.trySend(ChatEvent.SessionExpired)
    }

    private fun closeSocket() {
        socketAttempt++
        socket?.close()
        socket = null
    }

    private fun clearEvents() {
        while (eventChannel.tryReceive().isSuccess) { /* 清除上一会话事件。 */ }
        while (_socketCallbacks.tryReceive().isSuccess) { /* 清除上一连接回调。 */ }
        knownMessageIds.clear()
        completedMessageIds.clear()
    }

    private fun progressLabel(status: String, content: String): String = content.ifBlank {
        when (status.trim().lowercase(Locale.ROOT)) {
            "running", "processing" -> "正在分析"
            "completed", "done" -> "正在整理回答"
            else -> "正在处理"
        }
    }

    override fun onCleared() {
        closeSocket()
        _socketCallbacks.close()
        eventChannel.close()
        super.onCleared()
    }

    class Factory(apiClient: FinanceApiClient) : ViewModelProvider.Factory {
        private val repository = ChatRepository(apiClient)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) return ChatViewModel(repository) as T
            throw IllegalArgumentException("未知的 AI 对话 ViewModel")
        }
    }

    private sealed interface SocketCallback {
        val epoch: Long
        val attempt: Long

        data class Event(override val epoch: Long, override val attempt: Long, val event: ChatSocketEvent) : SocketCallback
        data class Failure(override val epoch: Long, override val attempt: Long, val failure: NetworkFailure) : SocketCallback
        data class Closed(override val epoch: Long, override val attempt: Long) : SocketCallback
    }

    private sealed interface ReadyResult {
        data class Ready(val conversationId: String) : ReadyResult
        data class Failed(val failure: NetworkFailure) : ReadyResult
    }
}

private fun nowChatTimeLabel(): String = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

private fun chatTimeLabel(value: String): String = runCatching {
    OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
}.getOrElse { value.replace('T', ' ').take(16) }
