package com.scrapider.finance.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshSessionCookieHandler {

    public static final String REFRESH_COOKIE_NAME = "refresh_sid";

    private static final String REFRESH_COOKIE_BASE_PATH = "/api/auth";

    private final long refreshCookieMaxAgeSeconds;
    private final boolean refreshCookieSecure;
    private final String refreshCookiePath;

    public RefreshSessionCookieHandler(
            @Value("${jwt.refresh-session-cookie-max-age-seconds}") long refreshCookieMaxAgeSeconds,
            @Value("${jwt.refresh-session-cookie-secure}") boolean refreshCookieSecure,
            @Value("${server.servlet.context-path:}") String contextPath) {
        this.refreshCookieMaxAgeSeconds = refreshCookieMaxAgeSeconds;
        this.refreshCookieSecure = refreshCookieSecure;
        this.refreshCookiePath = refreshCookiePath(contextPath);
    }

    public String resolveRefreshSid(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public void addRefreshCookie(HttpServletResponse response, String refreshSid) {
        response.addHeader(HttpHeaders.SET_COOKIE, this.buildCookie(refreshSid, this.refreshCookieMaxAgeSeconds));
    }

    public void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, this.buildCookie("", 0));
    }

    private String buildCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(this.refreshCookieSecure)
                .sameSite("Lax")
                .path(this.refreshCookiePath)
                .maxAge(maxAgeSeconds)
                .build()
                .toString();
    }

    private static String refreshCookiePath(String contextPath) {
        String normalized = contextPath == null ? "" : contextPath.trim();
        if (normalized.isBlank() || "/".equals(normalized)) {
            return REFRESH_COOKIE_BASE_PATH;
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + REFRESH_COOKIE_BASE_PATH;
    }
}
