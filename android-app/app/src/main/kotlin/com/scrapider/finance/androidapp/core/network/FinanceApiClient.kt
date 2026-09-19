package com.scrapider.finance.androidapp.core.network

import com.scrapider.finance.androidapp.core.session.SessionStore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.HttpUrl.Companion.toHttpUrl
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
    private val sessionStore: SessionStore? = null,
    baseUrl: String = ApiConfig.DEFAULT_BASE_URL,
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val normalizedBaseUrl = baseUrl.trimEnd('/')
    private val apiHost = normalizedBaseUrl.toHttpUrl().host
    private val refreshSessionCookieJar = sessionStore?.let {
        RefreshSessionCookieJar(sessionStore = it, apiHost = apiHost)
    }
    private val client = OkHttpClient.Builder()
        .apply { refreshSessionCookieJar?.let { cookieJar(it) } }
        .connectTimeout(ApiConfig.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(ApiConfig.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()
    private val refreshMutex = Mutex()

    @Volatile
    private var accessToken: String = ""

    fun setAccessToken(token: String) {
        accessToken = token
    }

    fun clearAuthentication() {
        accessToken = ""
        refreshSessionCookieJar?.clear()
    }

    suspend fun get(path: String): ApiHttpResponse = execute(
        requestBuilder(path)
            .get()
            .build(),
    )

    /** 原页预览仍经过同一鉴权客户端，不将访问令牌交给外部图片加载器。 */
    suspend fun getBytes(path: String): NetworkResult<ByteArray> {
        val response = executeWithRefresh(
            requestBuilder(path)
                .header("Accept", "image/*")
                .get()
                .build(),
        )
        responseFailure(response.statusCode, response.networkFailure)?.let {
            return NetworkResult.Failure(it)
        }
        return response.body?.let { NetworkResult.Success(it) }
            ?: NetworkResult.Failure(NetworkFailure.InvalidResponse)
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

    private fun requestBuilder(path: String, includeAccessToken: Boolean = true): Request.Builder = Request.Builder()
        .url(normalizedBaseUrl + path.ensureLeadingSlash())
        .header("Accept", "application/json")
        .apply {
            if (includeAccessToken && accessToken.isNotBlank()) {
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

    private suspend fun execute(request: Request): ApiHttpResponse {
        val response = executeWithRefresh(request)
        return ApiHttpResponse(
            statusCode = response.statusCode,
            body = response.body?.let { String(it, Charsets.UTF_8) }.orEmpty(),
            networkFailure = response.networkFailure,
        )
    }

    private suspend fun executeWithRefresh(request: Request): RawApiHttpResponse {
        val response = executeRaw(request)
        val failedAccessToken = request.accessTokenOrNull()
        if (
            response.statusCode != HTTP_UNAUTHORIZED ||
            response.networkFailure ||
            failedAccessToken == null ||
            request.isRefreshRequest() ||
            refreshSessionCookieJar?.hasRefreshSession() != true
        ) {
            return response
        }
        if (!refreshAccessToken(failedAccessToken)) {
            return response
        }
        return executeRaw(request.withAccessToken(accessToken))
    }

    private suspend fun refreshAccessToken(failedAccessToken: String): Boolean = refreshMutex.withLock {
        if (accessToken.isNotBlank() && accessToken != failedAccessToken) {
            return@withLock true
        }
        val response = executeRaw(
            requestBuilder(ApiConfig.REFRESH_PATH, includeAccessToken = false)
                .post(ByteArray(0).toRequestBody())
                .build(),
        )
        val refreshedAccessToken = response.readRefreshedAccessToken()
        if (refreshedAccessToken == null) {
            if (response.statusCode in listOf(HTTP_UNAUTHORIZED, HTTP_FORBIDDEN) ||
                response.statusCode in HTTP_SUCCESS_RANGE
            ) {
                clearAuthentication()
            }
            return@withLock false
        }
        setAccessToken(refreshedAccessToken)
        true
    }

    private suspend fun executeRaw(request: Request): RawApiHttpResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, exception: IOException) {
                    if (continuation.isActive) {
                        continuation.resume(RawApiHttpResponse(statusCode = -1, body = null, networkFailure = true))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val rawResponse = try {
                            RawApiHttpResponse(
                                statusCode = it.code,
                                body = it.body?.bytes(),
                                networkFailure = false,
                            )
                        } catch (_: IOException) {
                            RawApiHttpResponse(statusCode = -1, body = null, networkFailure = true)
                        }
                        if (continuation.isActive) {
                            continuation.resume(rawResponse)
                        }
                    }
                }
            },
        )
    }
}

private data class RawApiHttpResponse(
    val statusCode: Int,
    val body: ByteArray?,
    val networkFailure: Boolean,
)

private fun RawApiHttpResponse.readRefreshedAccessToken(): String? {
    if (networkFailure || statusCode !in HTTP_SUCCESS_RANGE) return null
    return runCatching {
        JSONObject(body?.let { String(it, Charsets.UTF_8) }.orEmpty())
            .takeIf { it.optInt("code", -1) == 0 }
            ?.optString("data", "")
            ?.takeIf(String::isNotBlank)
    }.getOrNull()
}

private fun Request.accessTokenOrNull(): String? {
    val authorization = header("Authorization") ?: return null
    if (!authorization.startsWith(BEARER_PREFIX)) return null
    return authorization.removePrefix(BEARER_PREFIX).takeIf(String::isNotBlank)
}

private fun Request.isRefreshRequest(): Boolean = url.encodedPath.endsWith(ApiConfig.REFRESH_PATH)

private fun Request.withAccessToken(token: String): Request = newBuilder()
    .header("Authorization", "$BEARER_PREFIX$token")
    .build()

private class RefreshSessionCookieJar(
    private val sessionStore: SessionStore,
    private val apiHost: String,
) : CookieJar {
    @Volatile
    private var refreshSid: String? = sessionStore.loadRefreshSid()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (url.host != apiHost) return
        cookies.filter { it.name == REFRESH_SESSION_COOKIE_NAME }.forEach { cookie ->
            if (cookie.value.isBlank() || cookie.expiresAt <= System.currentTimeMillis()) {
                clear()
            } else if (sessionStore.saveRefreshSid(cookie.value)) {
                refreshSid = cookie.value
            } else {
                clear()
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val currentRefreshSid = refreshSid ?: return emptyList()
        if (url.host != apiHost || !url.encodedPath.endsWith(ApiConfig.REFRESH_PATH)) {
            return emptyList()
        }
        return listOf(
            Cookie.Builder()
                .name(REFRESH_SESSION_COOKIE_NAME)
                .value(currentRefreshSid)
                .hostOnlyDomain(url.host)
                .path(url.encodedPath)
                .httpOnly()
                .apply {
                    if (url.scheme == HTTPS_SCHEME) secure()
                }
                .build(),
        )
    }

    fun hasRefreshSession(): Boolean = refreshSid?.isNotBlank() == true

    fun clear() {
        refreshSid = null
        sessionStore.clearRefreshSid()
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

private const val BEARER_PREFIX = "Bearer "
private const val REFRESH_SESSION_COOKIE_NAME = "refresh_sid"
private const val HTTPS_SCHEME = "https"
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private val HTTP_SUCCESS_RANGE = 200..299

private fun responseFailure(statusCode: Int, networkFailure: Boolean): NetworkFailure? = when {
    networkFailure -> NetworkFailure.Unavailable
    statusCode == 401 -> NetworkFailure.Unauthorized
    statusCode == 403 -> NetworkFailure.Forbidden
    statusCode !in 200..299 -> NetworkFailure.Service
    else -> null
}
