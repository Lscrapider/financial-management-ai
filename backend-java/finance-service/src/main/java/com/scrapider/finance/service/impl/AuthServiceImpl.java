package com.scrapider.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.converter.AuthConverter;
import com.scrapider.finance.domain.constant.AuthConstant;
import com.scrapider.finance.domain.param.ChangePasswordParam;
import com.scrapider.finance.domain.param.LoginParam;
import com.scrapider.finance.domain.param.RegisterParam;
import com.scrapider.finance.domain.param.UpdateNotificationParam;
import com.scrapider.finance.domain.param.UpdateProfileParam;
import com.scrapider.finance.domain.po.AppUserPO;
import com.scrapider.finance.domain.vo.LoginResultVO;
import com.scrapider.finance.domain.vo.UserInfoVO;
import com.scrapider.finance.manage.AppUserManage;
import com.scrapider.finance.security.JwtUtils;
import com.scrapider.finance.security.LoginUser;
import com.scrapider.finance.security.TokenStore;
import com.scrapider.finance.service.AuthService;
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserManage appUserManage;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final TokenStore tokenStore;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            AppUserManage appUserManage,
            PasswordEncoder passwordEncoder,
            JwtUtils jwtUtils,
            TokenStore tokenStore) {
        this.authenticationManager = authenticationManager;
        this.appUserManage = appUserManage;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.tokenStore = tokenStore;
    }

    @Override
    public LoginResultVO login(LoginParam param) {
        Authentication authentication = this.authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(param.getUsername(), param.getPassword()));
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        String token = this.jwtUtils.generateToken(
                loginUser.getUser().getId(),
                loginUser.getUsername(),
                loginUser.getRoleCode());
        return AuthConverter.toLoginResult(token);
    }

    @Override
    public void register(RegisterParam param) {
        this.validateRegisterParam(param);

        if (this.appUserManage.existsByUsername(param.getUsername())) {
            throw new IllegalArgumentException("Username already exists.");
        }

        AppUserPO user = AppUserPO.fromRegisterParam(
                param,
                this.passwordEncoder.encode(param.getPassword()));
        this.appUserManage.saveUser(user);
    }

    @Override
    public void logout(String token) {
        if (StrUtil.isNotBlank(token)) {
            this.tokenStore.blacklist(token);
        }
    }

    @Override
    public UserInfoVO getUserInfo(LoginUser loginUser, String token) {
        AppUserPO user = this.appUserManage.getById(loginUser.getUser().getId());
        if (user == null) {
            throw new IllegalArgumentException("User not found.");
        }
        return AuthConverter.toUserInfo(user, loginUser.getRoleCode(), token);
    }

    @Override
    public String refresh(String token) {
        if (StrUtil.isBlank(token)) {
            throw new IllegalArgumentException("Token is required.");
        }
        Claims claims = this.jwtUtils.parseTokenLenient(token);
        Long userId = Long.valueOf(claims.getSubject());
        String username = claims.get("username", String.class);
        String role = claims.get("role", String.class);
        return this.jwtUtils.generateToken(userId, username, role);
    }

    @Override
    public void updateProfile(LoginUser loginUser, UpdateProfileParam param) {
        AppUserPO user = this.appUserManage.getById(loginUser.getUser().getId());
        if (param.getRealName() != null) {
            user.setRealName(param.getRealName());
        }
        if (param.getIntroduction() != null) {
            user.setIntroduction(param.getIntroduction());
        }
        if (param.getEmail() != null) {
            user.setEmail(param.getEmail());
        }
        if (param.getPhone() != null) {
            user.setPhone(param.getPhone());
        }
        this.appUserManage.updateById(user);
    }

    @Override
    public void changePassword(LoginUser loginUser, ChangePasswordParam param) {
        if (param == null
                || StrUtil.isBlank(param.getOldPassword())
                || StrUtil.isBlank(param.getNewPassword())) {
            throw new IllegalArgumentException("Old password and new password are required.");
        }
        if (!param.getNewPassword().equals(param.getConfirmPassword())) {
            throw new IllegalArgumentException("Password confirmation does not match.");
        }

        AppUserPO user = this.appUserManage.getById(loginUser.getUser().getId());
        if (!this.passwordEncoder.matches(param.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Old password is incorrect.");
        }

        user.setPassword(this.passwordEncoder.encode(param.getNewPassword()));
        this.appUserManage.updateById(user);
    }

    @Override
    public void updateNotificationSetting(LoginUser loginUser, UpdateNotificationParam param) {
        AppUserPO user = loginUser.getUser();
        if (param.getEmailNotification() != null) {
            user.setEmailNotification(param.getEmailNotification());
        }
        this.appUserManage.updateById(user);
    }

    private void validateRegisterParam(RegisterParam param) {
        if (param == null
                || StrUtil.isBlank(param.getUsername())
                || StrUtil.isBlank(param.getPassword())) {
            throw new IllegalArgumentException("Username and password are required.");
        }
        if (!param.getPassword().equals(param.getConfirmPassword())) {
            throw new IllegalArgumentException("Password confirmation does not match.");
        }
    }
}
