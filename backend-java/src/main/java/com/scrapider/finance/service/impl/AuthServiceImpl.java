package com.scrapider.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.domain.constant.AuthConstant;
import com.scrapider.finance.domain.param.LoginParam;
import com.scrapider.finance.domain.param.RegisterParam;
import com.scrapider.finance.domain.po.AppUserPO;
import com.scrapider.finance.domain.vo.LoginResultVO;
import com.scrapider.finance.domain.vo.UserInfoVO;
import com.scrapider.finance.manage.AppUserManage;
import com.scrapider.finance.security.LoginUser;
import com.scrapider.finance.security.TokenStore;
import com.scrapider.finance.service.AuthService;
import java.util.List;
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
    private final TokenStore tokenStore;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            AppUserManage appUserManage,
            PasswordEncoder passwordEncoder,
            TokenStore tokenStore) {
        this.authenticationManager = authenticationManager;
        this.appUserManage = appUserManage;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
    }

    @Override
    public LoginResultVO login(LoginParam param) {
        Authentication authentication = this.authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(param.getUsername(), param.getPassword()));
        return new LoginResultVO(this.tokenStore.createToken(authentication));
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
            this.tokenStore.removeToken(token);
        }
    }

    @Override
    public UserInfoVO getUserInfo(LoginUser loginUser, String token) {
        AppUserPO user = loginUser.getUser();
        String roleCode = loginUser.getRoleCode();
        List<String> roles = StrUtil.isBlank(roleCode) ? List.of() : List.of(roleCode);
        return new UserInfoVO(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getRealName(),
                user.getAvatar(),
                roles,
                AuthConstant.DEFAULT_USER_DESC,
                user.getHomePath(),
                token);
    }

    @Override
    public String refresh(String token) {
        return token;
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
