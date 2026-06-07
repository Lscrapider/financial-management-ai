package com.scrapider.finance.domain.constant;

import java.util.Locale;
import java.util.Set;

public final class AuthConstant {

    public static final String BEARER_PREFIX = "Bearer ";
    public static final String ADMIN_ROLE_CODE = "admin";
    public static final String USER_ROLE_CODE = "user";
    public static final String DEFAULT_ROLE_CODE = USER_ROLE_CODE;
    public static final String DEFAULT_AVATAR = "";
    public static final String DEFAULT_HOME_PATH = "/investment-workbench";
    public static final String DEFAULT_USER_DESC = "";
    private static final Set<String> SUPPORTED_ROLE_CODES = Set.of(ADMIN_ROLE_CODE, USER_ROLE_CODE);

    private AuthConstant() {
    }

    public static String normalizeRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        return roleCode.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isSupportedRoleCode(String roleCode) {
        String normalizedRoleCode = normalizeRoleCode(roleCode);
        return normalizedRoleCode != null && SUPPORTED_ROLE_CODES.contains(normalizedRoleCode);
    }
}
