package com.scrapider.finance.androidapp.core.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class ApiHttpResponse(
    val statusCode: Int,
    val body: String,
    val networkFailure: Boolean,
)

class FinanceApiClient(
    baseUrl: String = ApiConfig.DEFAULT_BASE_URL,
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val normalizedBaseUrl = baseUrl.trimEnd('/')
    private val client = OkHttpClient.Builder()
        .connectTimeout(ApiConfig.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(ApiConfig.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    @Volatile
    private var accessToken: String = ""

    fun setAccessToken(token: String) {
        accessToken = token
    }

    suspend fun get(path: String): ApiHttpResponse = execute(
        requestBuilder(path)
            .get()
            .build(),
    )

    /** 原页预览仍经过同一鉴权客户端，不将访问令牌交给外部图片加载器。 */
    suspend fun getBytes(path: String): NetworkResult<ByteArray> = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(requestBuilder(path).get().build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(NetworkResult.Failure(NetworkFailure.Unavailable))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val result = try {
                        val failure = responseFailure(it.code, false)
                        when {
                            failure != null -> NetworkResult.Failure(failure)
                            it.body == null -> NetworkResult.Failure(NetworkFailure.InvalidResponse)
                            else -> NetworkResult.Success(it.body!!.bytes())
                        }
                    } catch (_: IOException) {
                        NetworkResult.Failure(NetworkFailure.Unavailable)
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            }
        })
    }

    suspend fun postJson(path: String, payload: JSONObject): ApiHttpResponse = execute(
        requestBuilder(path)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build(),
    )

    suspend fun putJson(path: String, payload: JSONObject): ApiHttpResponse = execute(
        requestBuilder(path)
            .put(payload.toString().toRequestBody(jsonMediaType))
            .build(),
    )

    suspend fun postMultipart(path: String, parts: List<MultipartBody.Part>): ApiHttpResponse = execute(
        requestBuilder(path)
            .post(MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                parts.forEach(::addPart)
            }.build())
            .build(),
    )

    suspend fun delete(path: String): ApiHttpResponse = execute(
        requestBuilder(path)
            .delete()
            .build(),
    )

    /**
     * 使用与 HTTP 相同的 base URL（包括部署前缀）建立 WebSocket；票据留在 query 中，
     * 不把 Bearer 凭证拼进 URL。
     */
    internal fun openWebSocket(path: String, listener: WebSocketListener): WebSocket =
        client.newWebSocket(
            Request.Builder()
                .url(webSocketUrl(path))
                .header("Accept", "application/json")
                .build(),
            listener,
        )

    fun close() {
        client.dispatcher.cancelAll()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun requestBuilder(path: String): Request.Builder = Request.Builder()
        .url(normalizedBaseUrl + path.ensureLeadingSlash())
        .header("Accept", "application/json")
        .apply {
            if (accessToken.isNotBlank()) {
                header("Authorization", "Bearer $accessToken")
            }
        }

    private fun webSocketUrl(path: String): String {
        val url = normalizedBaseUrl + path.ensureLeadingSlash()
        return when {
            url.startsWith("https://") -> "wss://${url.removePrefix("https://")}"
            url.startsWith("http://") -> "ws://${url.removePrefix("http://")}"
            else -> url
        }
    }

    private suspend fun execute(request: Request): ApiHttpResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, exception: IOException) {
                    if (continuation.isActive) {
                        continuation.resume(ApiHttpResponse(statusCode = -1, body = "", networkFailure = true))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (continuation.isActive) {
                            continuation.resume(
                                ApiHttpResponse(
                                    statusCode = it.code,
                                    body = it.body?.string().orEmpty(),
                                    networkFailure = false,
                                ),
                            )
                        }
                    }
                }
            },
        )
    }
}

fun ApiHttpResponse.toJsonPayload(): NetworkResult<JSONObject> {
    responseFailure(statusCode, networkFailure)?.let { return NetworkResult.Failure(it) }

    return runCatching { NetworkResult.Success(JSONObject(body)) }
        .getOrElse { NetworkResult.Failure(NetworkFailure.InvalidResponse) }
}

fun ApiHttpResponse.toJsonArrayPayload(): NetworkResult<JSONArray> {
    responseFailure(statusCode, networkFailure)?.let { return NetworkResult.Failure(it) }

    return runCatching { NetworkResult.Success(JSONArray(body)) }
        .getOrElse { NetworkResult.Failure(NetworkFailure.InvalidResponse) }
}

fun ApiHttpResponse.toEnvelope(): NetworkResult<JSONObject> = when (val payload = toJsonPayload()) {
    is NetworkResult.Failure -> payload
    is NetworkResult.Success -> {
        if (payload.data.optInt("code", -1) == 0) {
            payload
        } else {
            NetworkResult.Failure(NetworkFailure.Service)
        }
    }
}

private fun String.ensureLeadingSlash(): String = if (startsWith('/')) this else "/$this"

private fun responseFailure(statusCode: Int, networkFailure: Boolean): NetworkFailure? = when {
    networkFailure -> NetworkFailure.Unavailable
    statusCode == 401 -> NetworkFailure.Unauthorized
    statusCode == 403 -> NetworkFailure.Forbidden
    statusCode !in 200..299 -> NetworkFailure.Service
    else -> null
}
