package com.scrapider.finance.androidapp.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SessionState {
    public final boolean authenticated;
    public final String accessToken;
    public final String userId;
    public final String username;
    public final String realName;
    public final String email;
    public final String phone;
    public final boolean emailNotification;
    public final List<String> roles;

    private SessionState(
            boolean authenticated,
            String accessToken,
            String userId,
            String username,
            String realName,
            String email,
            String phone,
            boolean emailNotification,
            List<String> roles) {
        this.authenticated = authenticated;
        this.accessToken = accessToken == null ? "" : accessToken;
        this.userId = userId == null ? "" : userId;
        this.username = username == null ? "" : username;
        this.realName = realName == null ? "" : realName;
        this.email = email == null ? "" : email;
        this.phone = phone == null ? "" : phone;
        this.emailNotification = emailNotification;
        this.roles = Collections.unmodifiableList(new ArrayList<>(roles));
    }

    public static SessionState loggedOut() {
        return new SessionState(false, "", "", "", "", "", "", false, Collections.emptyList());
    }

    public static SessionState authenticated(String accessToken, String userId, String username, String realName, List<String> roles) {
        return authenticated(accessToken, userId, username, realName, "", "", false, roles);
    }

    public static SessionState authenticated(
            String accessToken,
            String userId,
            String username,
            String realName,
            String email,
            String phone,
            boolean emailNotification,
            List<String> roles) {
        return new SessionState(
                true,
                accessToken,
                userId,
                username,
                realName,
                email,
                phone,
                emailNotification,
                roles == null ? Collections.emptyList() : roles);
    }

    public boolean isAdmin() {
        return roles.contains("admin");
    }

    public String displayName() {
        if (!realName.isEmpty()) {
            return realName;
        }
        if (!username.isEmpty()) {
            return username;
        }
        return "当前用户";
    }

    public String roleLabel() {
        return isAdmin() ? "管理员" : "普通用户";
    }
}
