package com.scrapider.finance.androidapp.feature.workbench.chat

import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.network.NetworkFailure
import com.scrapider.finance.androidapp.core.network.NetworkResult
import com.scrapider.finance.androidapp.core.network.toEnvelope
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal class ChatRepository(
    private val apiClient: FinanceApiClient,
) {
    suspend fun issueTicket(): NetworkResult<ChatWebSocketTicket> =
        apiClient.postJson(TICKET_PATH, JSONObject()).toEnvelope().mapData { root ->
            val data = root.optJSONObject("data") ?: throw IllegalArgumentException("缺少票据数据")
            val ticket = data.requiredText("ticket")
            ChatWebSocketTicket(
                ticket = ticket,
                expiresInSeconds = data.optLong("expiresInSeconds").takeIf { it > 0 },
            )
        }

    suspend fun history(conversationId: String, beforeId: String?): NetworkResult<ChatHistoryPage> {
        if (conversationId.isBlank()) return NetworkResult.Failure(NetworkFailure.InvalidResponse)
        val path = buildString {
            append(HISTORY_PATH)
            append('/')
            append(conversationId.encoded())
            append("/messages")
            beforeId?.takeIf(String::isNotBlank)?.let {
                append("?beforeId=")
                append(it.encoded())
            }
        }
        return apiClient.get(path).toEnvelope().mapData { root ->
            val data = root.optJSONObject("data") ?: throw IllegalArgumentException("缺少历史数据")
            val pageConversationId = data.requiredText("conversationId")
            val historyMessages = data.optJSONArray("messages")
                ?: throw IllegalArgumentException("缺少历史消息")
            ChatHistoryPage(
                conversationId = pageConversationId,
                messages = historyMessages.objects().map { item ->
                    item.toMessage(pageConversationId)
                        ?: throw IllegalArgumentException("历史消息格式无效")
                },
                hasMore = data.optBoolean("hasMore", false),
                nextBeforeId = data.text("nextBeforeId").takeIf(String::isNotBlank),
            )
        }
    }

    fun openSocket(ticket: String, listener: ChatSocketListener): ChatSocket {
        val socket = apiClient.openWebSocket(
            "$SOCKET_PATH?ticket=${ticket.encoded()}",
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    listener.onOpen()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    when (val result = decode(text)) {
                        is NetworkResult.Failure -> {
                            listener.onFailure(result.reason)
                            webSocket.close(1003, "消息格式无效")
                        }
                        is NetworkResult.Success -> result.data?.let(listener::onEvent)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    listener.onFailure(
                        if (response?.code == 401) NetworkFailure.Unauthorized else NetworkFailure.Unavailable,
                    )
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    listener.onClosed()
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                    listener.onClosed()
                }
            },
        )
        return ChatSocket(socket)
    }

    private fun decode(text: String): NetworkResult<ChatSocketEvent?> = runCatching {
        val root = JSONObject(text)
        when (root.text("type")) {
            "session_ready" -> root.requiredText("conversationId").let(ChatSocketEvent::SessionReady)
            "agent_progress" -> ChatSocketEvent.Progress(
                conversationId = root.requiredText("conversationId"),
                messageId = root.requiredText("messageId"),
                status = root.text("status"),
                content = root.rawText("content"),
            )
            "answer_delta" -> ChatSocketEvent.Delta(
                conversationId = root.requiredText("conversationId"),
                messageId = root.requiredText("messageId"),
                content = root.requiredRawText("content"),
            )
            "final_answer" -> ChatSocketEvent.Final(
                conversationId = root.requiredText("conversationId"),
                messageId = root.requiredText("messageId"),
                content = root.requiredRawText("content"),
                createdAt = root.text("answeredAt").ifBlank { root.text("createdAt") },
            )
            else -> null
        }
    }.fold(
        onSuccess = { NetworkResult.Success(it) },
        onFailure = { NetworkResult.Failure(NetworkFailure.InvalidResponse) },
    )

    private companion object {
        const val TICKET_PATH = "/api/ai/chat/ws-ticket"
        const val HISTORY_PATH = "/api/ai/chat/conversations"
        const val SOCKET_PATH = "/api/ws/ai-chat"
    }
}

internal data class ChatWebSocketTicket(
    val ticket: String,
    val expiresInSeconds: Long?,
)

internal interface ChatSocketListener {
    fun onOpen()
    fun onEvent(event: ChatSocketEvent)
    fun onFailure(failure: NetworkFailure)
    fun onClosed()
}

internal class ChatSocket internal constructor(
    private val socket: WebSocket,
) {
    fun sendUserMessage(messageId: String, content: String): Boolean = socket.send(
        JSONObject()
            .put("type", "user_message")
            .put("messageId", messageId)
            .put("content", content)
            .toString(),
    )

    fun close() {
        socket.close(1000, "页面离开")
    }
}

internal sealed interface ChatSocketEvent {
    data class SessionReady(val conversationId: String) : ChatSocketEvent
    data class Progress(
        val conversationId: String,
        val messageId: String,
        val status: String,
        val content: String,
    ) : ChatSocketEvent
    data class Delta(
        val conversationId: String,
        val messageId: String,
        val content: String,
    ) : ChatSocketEvent
    data class Final(
        val conversationId: String,
        val messageId: String,
        val content: String,
        val createdAt: String,
    ) : ChatSocketEvent
}

private fun JSONObject.requiredText(key: String): String = text(key).ifBlank {
    throw IllegalArgumentException("缺少 $key")
}

private fun JSONObject.requiredRawText(key: String): String {
    if (!has(key) || isNull(key)) throw IllegalArgumentException("缺少 $key")
    return rawText(key)
}

private fun JSONObject.text(key: String): String = optString(key, "")
    .trim()
    .takeUnless { it == "null" }
    .orEmpty()

private fun JSONObject.rawText(key: String): String = optString(key, "")
    .takeUnless { it == "null" }
    .orEmpty()

private fun JSONObject.toMessage(conversationId: String): ChatMessage? {
    val messageId = text("messageId").ifBlank { text("id") }
    val role = ChatMessageRole.fromWire(text("role"))
    if (messageId.isBlank() || role == null) return null
    return ChatMessage(
        messageId = messageId,
        conversationId = conversationId,
        role = role,
        text = rawText("content"),
        timeLabel = chatTimeLabel(text("createdAt")),
        recordId = text("id"),
    )
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull(::optJSONObject)

private fun chatTimeLabel(value: String): String = runCatching {
    OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
}.getOrElse {
    value.replace('T', ' ').take(16)
}

private fun <T> NetworkResult<JSONObject>.mapData(map: (JSONObject) -> T): NetworkResult<T> = when (this) {
    is NetworkResult.Failure -> this
    is NetworkResult.Success -> runCatching { NetworkResult.Success(map(data)) }
        .getOrElse { NetworkResult.Failure(NetworkFailure.InvalidResponse) }
}

private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8")
