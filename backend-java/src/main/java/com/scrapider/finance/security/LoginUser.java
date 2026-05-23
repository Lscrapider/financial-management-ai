package com.scrapider.finance.security;

import com.scrapider.finance.domain.po.AppUserPO;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class LoginUser implements UserDetails {

    private final AppUserPO user;

    public LoginUser(AppUserPO user) {
        this.user = user;
    }

    public AppUserPO getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String roleCode = resolveRoleCode();
        if (roleCode == null || roleCode.isBlank()) {
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + roleCode));
    }

    public String getRoleCode() {
        return resolveRoleCode();
    }

    private String resolveRoleCode() {
        if ("admin".equals(user.getUsername())) {
            return "admin";
        }
        return user.getRoleCode();
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(user.getEnabled());
    }
}
