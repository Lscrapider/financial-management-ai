package com.scrapider.finance.interceptor;

import com.scrapider.finance.domain.po.AppVisitLogPO;
import com.scrapider.finance.manage.AppVisitLogManage;
import com.scrapider.finance.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AppVisitLogInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTRIBUTE = "appVisitStartTime";

    private final AppVisitLogManage appVisitLogManage;

    public AppVisitLogInterceptor(AppVisitLogManage appVisitLogManage) {
        this.appVisitLogManage = appVisitLogManage;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (HttpMethod.OPTIONS.matches(request.getMethod()) || this.isConsoleMetricsRequest(request)) {
            return;
        }
        AppVisitLogPO log = new AppVisitLogPO();
        log.setUsername(this.currentUsername());
        log.setRequestMethod(request.getMethod());
        log.setRequestUri(request.getRequestURI());
        log.setStatusCode(response.getStatus());
        log.setDurationMs(this.durationMs(request));
        log.setRemoteAddr(this.remoteAddr(request));
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setOccurredAt(LocalDateTime.now());
        this.appVisitLogManage.saveLog(log);
    }

    private boolean isConsoleMetricsRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/api/ai/console");
    }

    private Long durationMs(HttpServletRequest request) {
        Object startTime = request.getAttribute(START_TIME_ATTRIBUTE);
        if (!(startTime instanceof Long startTimeMs)) {
            return null;
        }
        return System.currentTimeMillis() - startTimeMs;
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser loginUser) {
            return loginUser.getUsername();
        }
        return authentication.getName();
    }

    private String remoteAddr(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
