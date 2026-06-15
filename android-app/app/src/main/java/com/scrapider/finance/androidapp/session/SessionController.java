package com.scrapider.finance.androidapp.session;

import com.scrapider.finance.androidapp.network.ApiClient;
import com.scrapider.finance.androidapp.network.ApiConfig;
import com.scrapider.finance.androidapp.network.ApiResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class SessionController {
    public interface Callback {
        void onComplete(ApiResult result, SessionState state);
    }

    private final ApiClient apiClient;
    private SessionState state = SessionState.loggedOut();

    public SessionController(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public SessionState state() {
        return state;
    }

    public void login(String username, String password, String roleCode, Callback callback) {
        if (isBlank(username) || isBlank(password)) {
            callback.onComplete(ApiResult.failure(0, "", "请输入账号和密码。"), state);
            return;
        }

        JSONObject payload = new JSONObject();
        try {
            payload.put("username", username.trim());
            payload.put("password", password);
            payload.put("roleCode", roleCode);
        } catch (JSONException exception) {
            callback.onComplete(ApiResult.failure(0, "", "登录参数组装失败。"), state);
            return;
        }

        apiClient.postJson(ApiConfig.LOGIN_PATH, payload, result -> {
            if (!result.success || !isApiSuccess(result.body)) {
                callback.onComplete(asBusinessFailure(result, "登录失败，请检查账号、密码和角色。"), state);
                return;
            }
            String token = accessToken(result.body);
            if (isBlank(token)) {
                callback.onComplete(ApiResult.failure(result.statusCode, result.body, "登录成功但未返回访问令牌。"), state);
                return;
            }
            apiClient.setAccessToken(token);
            state = SessionState.authenticated(token, "", username.trim(), "", new ArrayList<>());
            refreshUserInfo(callback);
        });
    }

    public void refreshUserInfo(Callback callback) {
        apiClient.get(ApiConfig.USER_INFO_PATH, result -> {
            if (!result.success || !isApiSuccess(result.body)) {
                callback.onComplete(asBusinessFailure(result, result.message), state);
                return;
            }
            SessionState parsed = parseUserInfo(result.body);
            if (!parsed.authenticated) {
                callback.onComplete(ApiResult.failure(result.statusCode, result.body, "用户信息解析失败。"), state);
                return;
            }
            state = parsed;
            apiClient.setAccessToken(state.accessToken);
            callback.onComplete(ApiResult.success(result.statusCode, result.body, "后端用户信息已同步。"), state);
        });
    }

    private SessionState parseUserInfo(String body) {
        try {
            JSONObject data = new JSONObject(body).optJSONObject("data");
            if (data == null) {
                return SessionState.loggedOut();
            }
            return SessionState.authenticated(
                    data.optString("token", state.accessToken),
                    data.optString("userId", ""),
                    data.optString("username", ""),
                    data.optString("realName", ""),
                    data.optString("email", ""),
                    data.optString("phone", ""),
                    data.optBoolean("emailNotification", false),
                    roles(data.optJSONArray("roles")));
        } catch (JSONException exception) {
            return SessionState.loggedOut();
        }
    }

    private List<String> roles(JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.length(); i++) {
            String role = array.optString(i, "");
            if (!isBlank(role)) {
                values.add(role);
            }
        }
        return values;
    }

    private String accessToken(String body) {
        try {
            JSONObject data = new JSONObject(body).optJSONObject("data");
            return data == null ? "" : data.optString("accessToken", "");
        } catch (JSONException exception) {
            return "";
        }
    }

    private boolean isApiSuccess(String body) {
        try {
            return new JSONObject(body).optInt("code", -1) == 0;
        } catch (JSONException exception) {
            return false;
        }
    }

    private ApiResult asBusinessFailure(ApiResult result, String fallback) {
        return ApiResult.failure(result.statusCode, result.body, apiMessage(result.body, fallback));
    }

    private String apiMessage(String body, String fallback) {
        try {
            JSONObject root = new JSONObject(body);
            String message = root.optString("message", "");
            String error = root.optString("error", "");
            if (!isBlank(error)) {
                return error;
            }
            if (!isBlank(message) && !"ok".equals(message)) {
                return message;
            }
        } catch (JSONException ignored) {
        }
        return isBlank(fallback) ? "接口返回异常。" : fallback;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
