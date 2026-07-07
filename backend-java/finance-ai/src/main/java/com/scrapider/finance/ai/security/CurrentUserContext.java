package com.scrapider.finance.ai.security;

import com.scrapider.finance.domain.exception.BusinessException;
import java.lang.reflect.Method;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUserContext {

    private static final String ADMIN_AUTHORITY = "ROLE_admin";

    private CurrentUserContext() {
    }

    public static Long currentUserId() {
        Authentication authentication = currentAuthentication();
        Object principal = authentication.getPrincipal();
        try {
            Method getUser = principal.getClass().getMethod("getUser");
            Object user = getUser.invoke(principal);
            Method getId = user.getClass().getMethod("getId");
            Object id = getId.invoke(user);
            if (id instanceof Long userId) {
                return userId;
            }
            if (id instanceof Number number) {
                return number.longValue();
            }
        } catch (ReflectiveOperationException ex) {
            throw new BusinessException("登录用户 ID 不可用。", ex);
        }
        throw new BusinessException("登录用户 ID 不可用。");
    }

    public static Long ownerUserIdForQuery() {
        return isAdmin() ? null : currentUserId();
    }

    public static boolean isAdmin() {
        Authentication authentication = currentAuthentication();
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
    }

    private static Authentication currentAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException("请先登录。");
        }
        return authentication;
    }
}
