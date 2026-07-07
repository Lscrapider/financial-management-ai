package com.scrapider.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.converter.AuthConverter;
import com.scrapider.finance.domain.constant.AuthConstant;
import com.scrapider.finance.domain.dto.AuthSessionDTO;
import com.scrapider.finance.domain.exception.BusinessException;
import com.scrapider.finance.domain.param.ChangePasswordParam;
import com.scrapider.finance.domain.param.LoginParam;
import com.scrapider.finance.domain.param.RegisterParam;
import com.scrapider.finance.domain.param.UpdateNotificationParam;
import com.scrapider.finance.domain.param.UpdateProfileParam;
import com.scrapider.finance.domain.po.AppUserPO;
import com.scrapider.finance.domain.vo.UserInfoVO;
import com.scrapider.finance.manage.AppUserManage;
import com.scrapider.finance.security.JwtUtils;
import com.scrapider.finance.security.LoginUser;
import com.scrapider.finance.security.RefreshSessionStore;
import com.scrapider.finance.security.TokenStore;
import com.scrapider.finance.service.AuthService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private static final int REFRESH_SESSION_BYTE_LENGTH = 32;

    private final AuthenticationManager authenticationManager;
    private final AppUserManage appUserManage;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final TokenStore tokenStore;
    private final RefreshSessionStore refreshSessionStore;
    private final long refreshSessionExpirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            AppUserManage appUserManage,
            PasswordEncoder passwordEncoder,
            JwtUtils jwtUtils,
            TokenStore tokenStore,
            RefreshSessionStore refreshSessionStore,
            @Value("${jwt.refresh-session-expiration-ms}") long refreshSessionExpirationMs) {
        this.authenticationManager = authenticationManager;
        this.appUserManage = appUserManage;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.tokenStore = tokenStore;
        this.refreshSessionStore = refreshSessionStore;
        this.refreshSessionExpirationMs = refreshSessionExpirationMs;
    }

    @Override
    public AuthSessionDTO login(LoginParam param) {
        Authentication authentication = this.authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(param.getUsername(), param.getPassword()));
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        String roleCode = this.requireSupportedRoleCode(loginUser.getRoleCode());
        String requestedRoleCode = this.requireSupportedRoleCode(param.getRoleCode());
        if (!roleCode.equals(requestedRoleCode)) {
            throw new BusinessException("登录角色与当前用户不匹配。");
        }
        String accessToken = this.jwtUtils.generateAccessToken(
                loginUser.getUser().getId(),
                loginUser.getUsername(),
                roleCode);
        String refreshSid = this.createRefreshSession(loginUser.getUser().getId(), LocalDateTime.now());
        return new AuthSessionDTO(accessToken, refreshSid);
    }

    @Override
    public void register(RegisterParam param) {
        this.validateRegisterParam(param);

        if (this.appUserManage.existsByUsername(param.getUsername())) {
            throw new BusinessException("用户名已存在。");
        }

        AppUserPO user = AppUserPO.fromRegisterParam(
                param,
                this.passwordEncoder.encode(param.getPassword()));
        this.appUserManage.saveUser(user);
    }

    @Override
    public void logout(String accessToken, String refreshSid) {
        if (StrUtil.isNotBlank(accessToken)) {
            this.tokenStore.blacklist(accessToken);
        }
        if (StrUtil.isNotBlank(refreshSid)) {
            this.refreshSessionStore.revokeSession(this.hash(refreshSid));
        }
    }

    @Override
    public UserInfoVO getUserInfo(LoginUser loginUser, String token) {
        AppUserPO user = this.appUserManage.getById(loginUser.getUser().getId());
        if (user == null) {
            throw new BusinessException("用户不存在。");
        }
        return AuthConverter.toUserInfo(user, this.requireSupportedRoleCode(user.getRoleCode()), token);
    }

    @Override
    public AuthSessionDTO refresh(String refreshSid) {
        if (StrUtil.isBlank(refreshSid)) {
            throw new BusinessException("刷新会话不能为空。");
        }
        LocalDateTime now = LocalDateTime.now();
        String oldSessionHash = this.hash(refreshSid);
        RefreshSessionStore.RefreshSession session =
                this.refreshSessionStore.getActiveBySessionHash(oldSessionHash, now);
        if (session == null) {
            throw new BusinessException("刷新会话无效或已过期。");
        }
        AppUserPO user = this.appUserManage.getById(session.userId());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            this.refreshSessionStore.revokeSession(oldSessionHash);
            throw new BusinessException("用户不存在或已被禁用。");
        }

        String newRefreshSid = this.createRefreshSession(user.getId(), now);
        String accessToken = this.jwtUtils.generateAccessToken(
                user.getId(),
                user.getUsername(),
                this.requireSupportedRoleCode(user.getRoleCode()));
        this.refreshSessionStore.revokeSession(oldSessionHash);
        return new AuthSessionDTO(accessToken, newRefreshSid);
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
            throw new BusinessException("旧密码和新密码不能为空。");
        }
        if (!param.getNewPassword().equals(param.getConfirmPassword())) {
            throw new BusinessException("两次输入的新密码不一致。");
        }

        AppUserPO user = this.appUserManage.getById(loginUser.getUser().getId());
        if (!this.passwordEncoder.matches(param.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码不正确。");
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
            throw new BusinessException("用户名和密码不能为空。");
        }
        if (!param.getPassword().equals(param.getConfirmPassword())) {
            throw new BusinessException("两次输入的密码不一致。");
        }
    }

    private String createRefreshSession(Long userId, LocalDateTime now) {
        String refreshSid = this.generateRefreshSid();
        this.refreshSessionStore.saveSession(
                this.hash(refreshSid),
                userId,
                now.plus(Duration.ofMillis(this.refreshSessionExpirationMs)));
        return refreshSid;
    }

    private String generateRefreshSid() {
        byte[] bytes = new byte[REFRESH_SESSION_BYTE_LENGTH];
        this.secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String requireSupportedRoleCode(String roleCode) {
        String normalizedRoleCode = AuthConstant.normalizeRoleCode(roleCode);
        if (!AuthConstant.isSupportedRoleCode(normalizedRoleCode)) {
            throw new BusinessException("不支持的角色类型: " + roleCode);
        }
        return normalizedRoleCode;
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 算法不可用。", ex);
        }
    }
}
