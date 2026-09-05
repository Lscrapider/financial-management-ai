package com.scrapider.finance.androidapp.core.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
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

    suspend fun postJson(path: String, payload: JSONObject): ApiHttpResponse = execute(
        requestBuilder(path)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build(),
    )

    suspend fun delete(path: String): ApiHttpResponse = execute(
        requestBuilder(path)
            .delete()
            .build(),
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
    if (networkFailure) return NetworkResult.Failure(NetworkFailure.Unavailable)
    if (statusCode == 401) return NetworkResult.Failure(NetworkFailure.Unauthorized)
    if (statusCode == 403) return NetworkResult.Failure(NetworkFailure.Forbidden)
    if (statusCode !in 200..299) return NetworkResult.Failure(NetworkFailure.Service)

    return runCatching { NetworkResult.Success(JSONObject(body)) }
        .getOrElse { NetworkResult.Failure(NetworkFailure.InvalidResponse) }
}

fun ApiHttpResponse.toJsonArrayPayload(): NetworkResult<JSONArray> {
    if (networkFailure) return NetworkResult.Failure(NetworkFailure.Unavailable)
    if (statusCode == 401) return NetworkResult.Failure(NetworkFailure.Unauthorized)
    if (statusCode == 403) return NetworkResult.Failure(NetworkFailure.Forbidden)
    if (statusCode !in 200..299) return NetworkResult.Failure(NetworkFailure.Service)

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
