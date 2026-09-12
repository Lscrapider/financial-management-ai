package com.scrapider.finance.androidapp.feature.workbench.chat

import androidx.compose.runtime.Immutable
import java.util.Locale

internal const val CHAT_MAX_MESSAGE_LENGTH = 1_000

internal enum class ChatMessageRole {
    User,
    Assistant,
    ;

    companion object {
        fun fromWire(value: String): ChatMessageRole? = when (value.trim().lowercase(Locale.ROOT)) {
            "user" -> User
            "assistant" -> Assistant
            else -> null
        }
    }
}

internal enum class ChatConnectionState(val label: String) {
    Idle("未连接"),
    Connecting("正在连接"),
    Connected("已连接"),
    Interrupted("连接中断"),
    Failed("连接失败"),
}

@Immutable
internal data class ChatMessage(
    val messageId: String,
    val conversationId: String,
    val role: ChatMessageRole,
    val text: String,
    val timeLabel: String,
    val isStreaming: Boolean = false,
    val isInterrupted: Boolean = false,
    val recordId: String = "",
) {
    /** 只依赖会话、角色和消息协议 id，历史补齐 record id 时不会改变列表 key。 */
    val key: String get() = "$conversationId:${role.name}:$messageId"
}

@Immutable
internal data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val connectionState: ChatConnectionState = ChatConnectionState.Idle,
    val historyLoading: Boolean = false,
    val historyError: String = "",
    val historyHasLoaded: Boolean = false,
    val hasMore: Boolean = false,
    val progress: String = "",
    val isSending: Boolean = false,
    val error: String = "",
) {
    val canSend: Boolean
        get() = draft.trim().isNotEmpty() &&
            draft.length <= CHAT_MAX_MESSAGE_LENGTH &&
            connectionState == ChatConnectionState.Connected &&
            historyHasLoaded &&
            !isSending

    val canReconnect: Boolean
        get() = connectionState != ChatConnectionState.Connecting && !isSending
}

internal data class ChatHistoryPage(
    val conversationId: String,
    val messages: List<ChatMessage>,
    val hasMore: Boolean,
    val nextBeforeId: String?,
)

internal sealed interface ChatEvent {
    data object SessionExpired : ChatEvent
    data class Notice(val message: String) : ChatEvent
}
