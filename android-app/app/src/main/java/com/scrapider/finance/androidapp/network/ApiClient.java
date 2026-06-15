package com.scrapider.finance.androidapp.network;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;

public final class ApiClient {
    public interface Callback {
        void onComplete(ApiResult result);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String baseUrl;
    private String accessToken;

    public ApiClient() {
        this(ApiConfig.DEFAULT_BASE_URL);
    }

    public ApiClient(String baseUrl) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void get(String path, Callback callback) {
        executor.execute(() -> {
            ApiResult result = execute("GET", path, null);
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    public void postJson(String path, JSONObject payload, Callback callback) {
        executor.execute(() -> {
            ApiResult result = execute("POST", path, payload);
            mainHandler.post(() -> callback.onComplete(result));
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private ApiResult execute(String method, String path, JSONObject payload) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl + normalizePath(path));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(ApiConfig.CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(ApiConfig.READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            if (!isNullOrBlank(accessToken)) {
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            }
            if (payload != null) {
                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Content-Length", String.valueOf(body.length));
                try (OutputStream stream = connection.getOutputStream()) {
                    stream.write(body);
                }
            }

            int statusCode = connection.getResponseCode();
            String body = readBody(statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (statusCode >= 200 && statusCode < 300) {
                return ApiResult.success(statusCode, body);
            }
            return ApiResult.failure(statusCode, body, messageForStatus(statusCode));
        } catch (IOException exception) {
            return ApiResult.failure(-1, "", "后端未连接：" + safeMessage(exception));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String messageForStatus(int statusCode) {
        if (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return "后端已连接，当前未登录或访问令牌失效。";
        }
        if (statusCode == HttpURLConnection.HTTP_FORBIDDEN) {
            return "后端已连接，但当前账号没有权限。";
        }
        return "后端返回状态码 " + statusCode + "，请检查接口状态。";
    }

    private String readBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static String normalizeBaseUrl(String value) {
        if (isNullOrBlank(value)) {
            return ApiConfig.DEFAULT_BASE_URL;
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String normalizePath(String path) {
        if (isNullOrBlank(path)) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String safeMessage(IOException exception) {
        String message = exception.getMessage();
        return isNullOrBlank(message) ? exception.getClass().getSimpleName() : message;
    }

    private static boolean isNullOrBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
