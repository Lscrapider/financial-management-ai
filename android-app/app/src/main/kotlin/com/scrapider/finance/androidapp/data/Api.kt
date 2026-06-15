package com.scrapider.finance.androidapp.data

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

object ApiConfig {
    const val DEFAULT_BASE_URL = "http://192.168.0.107:8081"
    const val LOGIN_PATH = "/api/auth/login"
    const val USER_INFO_PATH = "/api/user/info"
    const val WATCH_GROUPS_PATH = "/api/watch-pool/groups"
    const val WATCH_ITEMS_PATH = "/api/watch-pool/items"
    const val STOCK_ALERTS_PATH = "/api/stock-alerts"
    const val STOCK_ALERT_TARGET_OPTIONS_PATH = "/api/stock-alerts/target-options"
    const val REPORT_TARGETS_PATH = "/api/ai/scene-analysis/tasks/reports/targets?pageNum=1&pageSize=4"
    const val STOCK_QUOTES_PATH = "/api/stocks/quotes"
    const val INDEX_QUOTES_PATH = "/api/indices/quotes"
    const val BOND_QUOTES_PATH = "/api/bonds/quotes"
    const val CONNECT_TIMEOUT_MS = 3500
    const val READ_TIMEOUT_MS = 5000
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
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val normalizedBaseUrl = baseUrl.trimEnd('/')
    private var accessToken: String = ""

    fun setAccessToken(token: String) {
        accessToken = token
    }

    fun get(path: String, callback: (ApiResult) -> Unit) {
        executor.execute {
            val result = execute("GET", path, null)
            mainHandler.post { callback(result) }
        }
    }

    fun postJson(path: String, payload: JSONObject, callback: (ApiResult) -> Unit) {
        executor.execute {
            val result = execute("POST", path, payload)
            mainHandler.post { callback(result) }
        }
    }

    fun delete(path: String, callback: (ApiResult) -> Unit) {
        executor.execute {
            val result = execute("DELETE", path, null)
            mainHandler.post { callback(result) }
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun execute(method: String, path: String, payload: JSONObject?): ApiResult {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(normalizedBaseUrl + normalizePath(path))
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = ApiConfig.CONNECT_TIMEOUT_MS
                readTimeout = ApiConfig.READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                if (accessToken.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $accessToken")
                }
                if (payload != null) {
                    val bytes = payload.toString().toByteArray(StandardCharsets.UTF_8)
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Content-Length", bytes.size.toString())
                    outputStream.use { stream: OutputStream -> stream.write(bytes) }
                }
            }
            val statusCode = connection.responseCode
            val body = readBody(if (statusCode >= 400) connection.errorStream else connection.inputStream)
            if (statusCode in 200..299) {
                ApiResult(true, statusCode, body, apiMessage(body, "接口调用成功。"))
            } else {
                ApiResult(false, statusCode, body, httpMessage(statusCode))
            }
        } catch (exception: IOException) {
            ApiResult(false, -1, "", "后端未连接：${exception.message ?: exception.javaClass.simpleName}")
        } finally {
            connection?.disconnect()
        }
    }

    private fun normalizePath(path: String): String = if (path.startsWith("/")) path else "/$path"

    private fun readBody(stream: InputStream?): String {
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            buildString {
                var line = reader.readLine()
                while (line != null) {
                    if (isNotEmpty()) append('\n')
                    append(line)
                    line = reader.readLine()
                }
            }
        }
    }

    private fun httpMessage(statusCode: Int): String = when (statusCode) {
        HttpURLConnection.HTTP_UNAUTHORIZED -> "后端已连接，当前未登录或访问令牌失效。"
        HttpURLConnection.HTTP_FORBIDDEN -> "后端已连接，但当前账号没有权限。"
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
