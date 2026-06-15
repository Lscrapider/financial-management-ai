package com.scrapider.finance.androidapp.data

import android.os.Handler
import android.os.Looper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiConfig {
    const val DEFAULT_BASE_URL = "http://192.168.0.107:8081"
    const val LOGIN_PATH = "/api/auth/login"
    const val USER_INFO_PATH = "/api/user/info"
    const val USER_PASSWORD_PATH = "/api/user/password"
    const val WATCH_GROUPS_PATH = "/api/watch-pool/groups"
    const val WATCH_ITEMS_PATH = "/api/watch-pool/items"
    const val STOCK_ALERTS_PATH = "/api/stock-alerts"
    const val STOCK_ALERT_TARGET_OPTIONS_PATH = "/api/stock-alerts/target-options"
    const val REPORT_TARGETS_PATH = "/api/ai/scene-analysis/tasks/reports/targets?pageNum=1&pageSize=4"
    const val SCENE_REPORT_TARGETS_PATH = "/api/ai/scene-analysis/tasks/reports/targets"
    const val SCENE_REPORTS_PATH = "/api/ai/scene-analysis/tasks/reports"
    const val SCENE_ANALYSIS_TASKS_PATH = "/api/ai/scene-analysis/tasks"
    const val SCENE_ANALYSIS_TARGET_SEARCH_PATH = "/api/ai/scene-analysis/targets/search"
    const val SCENE_ANALYSIS_REPORT_TYPES_PATH = "/api/ai/scene-analysis/config-profiles/report-types"
    const val SCENE_ANALYSIS_CONFIG_PROFILES_PATH = "/api/ai/scene-analysis/config-profiles"
    const val KNOWLEDGE_MATERIAL_TASKS_PATH = "/api/ai/knowledge-material/tasks"
    const val OCR_TASKS_PATH = "/api/ai/ocr/tasks"
    const val OCR_TASKS_PAGE_PATH = "/api/ai/ocr/tasks/page"
    const val OCR_REVIEWS_PATH = "/api/ai/ocr/reviews"
    const val MANUAL_KNOWLEDGE_TASKS_PATH = "/api/ai/manual-knowledge/tasks"
    const val MANUAL_KNOWLEDGE_TASKS_PAGE_PATH = "/api/ai/manual-knowledge/tasks/page"
    const val STOCK_QUOTES_PATH = "/api/stocks/quotes"
    const val INDEX_QUOTES_PATH = "/api/indices/quotes"
    const val BOND_QUOTES_PATH = "/api/bonds/quotes"
    const val SYSTEM_CONFIG_STOCKS_PATH = "/api/system-config/stocks"
    const val SYSTEM_CONFIG_BONDS_PATH = "/api/system-config/bonds"
    const val SYSTEM_CONFIG_TARGET_DELETE_PATH = "/api/system-config/targets/delete"
    const val CONNECT_TIMEOUT_MS = 3500
    const val READ_TIMEOUT_MS = 5000
    const val SYSTEM_CONFIG_READ_TIMEOUT_MS = 120_000
}

data class ApiResult(
    val success: Boolean,
    val statusCode: Int,
    val body: String,
    val message: String,
)

class ApiClient(
    baseUrl: String = ApiConfig.DEFAULT_BASE_URL,
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val normalizedBaseUrl = baseUrl.trimEnd('/')
    private val client = OkHttpClient.Builder()
        .connectTimeout(ApiConfig.CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(ApiConfig.READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .build()
    private var accessToken: String = ""

    fun setAccessToken(token: String) {
        accessToken = token
    }

    fun get(path: String, callback: (ApiResult) -> Unit) {
        enqueue("GET", path, null, callback)
    }

    fun postJson(path: String, payload: JSONObject, callback: (ApiResult) -> Unit) {
        enqueue("POST", path, payload, callback)
    }

    fun postJson(path: String, payload: JSONObject, readTimeoutMs: Int, callback: (ApiResult) -> Unit) {
        val request = request("POST", path, payload)
        client.newBuilder()
            .readTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .build()
            .newCall(request)
            .enqueue(apiCallback(callback))
    }

    fun putJson(path: String, payload: JSONObject, callback: (ApiResult) -> Unit) {
        enqueue("PUT", path, payload, callback)
    }

    fun postMultipartFiles(path: String, files: List<OcrUploadFile>, callback: (ApiResult) -> Unit) {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        files.forEach { file ->
            val mediaType = file.contentType.ifBlank { "application/octet-stream" }.toMediaType()
            builder.addFormDataPart("file", file.name, file.bytes.toRequestBody(mediaType))
        }
        val request = requestBuilder(path)
            .post(builder.build())
            .build()
        client.newCall(request).enqueue(apiCallback(callback))
    }

    fun delete(path: String, callback: (ApiResult) -> Unit) {
        enqueue("DELETE", path, null, callback)
    }

    fun shutdown() {
        client.dispatcher.cancelAll()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun enqueue(method: String, path: String, payload: JSONObject?, callback: (ApiResult) -> Unit) {
        val request = request(method, path, payload)
        client.newCall(request).enqueue(apiCallback(callback))
    }

    private fun apiCallback(callback: (ApiResult) -> Unit): Callback =
        object : Callback {
            override fun onFailure(call: Call, exception: IOException) {
                deliver(
                    ApiResult(
                        false,
                        -1,
                        "",
                        "后端未连接：${exception.message ?: exception.javaClass.simpleName}",
                    ),
                    callback,
                )
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val statusCode = it.code
                    val body = it.body?.string().orEmpty()
                    deliver(
                        if (it.isSuccessful) {
                            ApiResult(true, statusCode, body, apiMessage(body, "接口调用成功。"))
                        } else {
                            ApiResult(false, statusCode, body, httpMessage(statusCode))
                        },
                        callback,
                    )
                }
            }
        }

    private fun request(method: String, path: String, payload: JSONObject?): Request {
        val builder = requestBuilder(path)
        return when (method) {
            "POST" -> builder.post(payload.toJsonRequestBody()).build()
            "PUT" -> builder.put(payload.toJsonRequestBody()).build()
            "DELETE" -> {
                if (payload == null) {
                    builder.delete().build()
                } else {
                    builder.delete(payload.toJsonRequestBody()).build()
                }
            }
            else -> builder.get().build()
        }
    }

    private fun requestBuilder(path: String): Request.Builder {
        val builder = Request.Builder()
            .url(normalizedBaseUrl + normalizePath(path))
            .header("Accept", "application/json")
        if (accessToken.isNotBlank()) {
            builder.header("Authorization", "Bearer $accessToken")
        }
        return builder
    }

    private fun JSONObject?.toJsonRequestBody() =
        (this?.toString() ?: "{}").toRequestBody(jsonMediaType)

    private fun normalizePath(path: String): String = if (path.startsWith("/")) path else "/$path"

    private fun deliver(result: ApiResult, callback: (ApiResult) -> Unit) {
        mainHandler.post { callback(result) }
    }

    private fun httpMessage(statusCode: Int): String = when (statusCode) {
        401 -> "后端已连接，当前未登录或访问令牌失效。"
        403 -> "后端已连接，但当前账号没有权限。"
        else -> "后端返回状态码 $statusCode，请检查接口状态。"
    }
}

fun isApiSuccess(body: String): Boolean = runCatching {
    JSONObject(body).optInt("code", -1) == 0
}.getOrDefault(false)

fun apiMessage(body: String, fallback: String): String = runCatching {
    val root = JSONObject(body)
    val error = root.optString("error", "")
    val message = root.optString("message", "")
    when {
        error.isNotBlank() -> error
        message.isNotBlank() && message != "ok" -> message
        else -> fallback
    }
}.getOrDefault(fallback)
