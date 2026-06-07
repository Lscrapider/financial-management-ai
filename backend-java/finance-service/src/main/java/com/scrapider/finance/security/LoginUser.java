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
        return this.user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String roleCode = this.resolveRoleCode();
        if (roleCode == null || roleCode.isBlank()) {
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + roleCode));
    }

    public String getRoleCode() {
        return this.resolveRoleCode();
    }

    private String resolveRoleCode() {
        if ("admin".equals(this.user.getUsername())) {
            return "admin";
        }
        return this.user.getRoleCode();
    }

    @Override
    public String getPassword() {
        return this.user.getPassword();
    }

    @Override
    public String getUsername() {
        return this.user.getUsername();
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(this.user.getEnabled());
    }
}
