package com.scrapider.finance.androidapp.network;

public final class ApiResult {
    public final boolean success;
    public final int statusCode;
    public final String body;
    public final String message;

    private ApiResult(boolean success, int statusCode, String body, String message) {
        this.success = success;
        this.statusCode = statusCode;
        this.body = body == null ? "" : body;
        this.message = message == null ? "" : message;
    }

    public static ApiResult success(int statusCode, String body) {
        return new ApiResult(true, statusCode, body, "后端已连接，用户信息已同步。");
    }

    public static ApiResult success(int statusCode, String body, String message) {
        return new ApiResult(true, statusCode, body, message);
    }

    public static ApiResult failure(int statusCode, String body, String message) {
        return new ApiResult(false, statusCode, body, message);
    }

    public boolean backendReachable() {
        return statusCode > 0;
    }
}
