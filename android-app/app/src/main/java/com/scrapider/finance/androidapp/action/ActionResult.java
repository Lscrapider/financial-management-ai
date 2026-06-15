package com.scrapider.finance.androidapp.action;

public final class ActionResult {
    public final boolean success;
    public final int statusCode;
    public final String message;
    public final String value;

    private ActionResult(boolean success, int statusCode, String message, String value) {
        this.success = success;
        this.statusCode = statusCode;
        this.message = message == null ? "" : message;
        this.value = value == null ? "" : value;
    }

    static ActionResult success(int statusCode, String message, String value) {
        return new ActionResult(true, statusCode, message, value);
    }

    static ActionResult failure(int statusCode, String message) {
        return new ActionResult(false, statusCode, message, "");
    }
}
