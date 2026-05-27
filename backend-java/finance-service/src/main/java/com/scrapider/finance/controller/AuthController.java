package com.scrapider.finance.controller;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.domain.constant.AuthConstant;
import com.scrapider.finance.domain.param.ChangePasswordParam;
import com.scrapider.finance.domain.param.LoginParam;
import com.scrapider.finance.domain.param.RegisterParam;
import com.scrapider.finance.domain.param.UpdateNotificationParam;
import com.scrapider.finance.domain.param.UpdateProfileParam;
import com.scrapider.finance.domain.vo.ApiResponseVO;
import com.scrapider.finance.domain.vo.LoginResultVO;
import com.scrapider.finance.domain.vo.UserInfoVO;
import com.scrapider.finance.security.LoginUser;
import com.scrapider.finance.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/auth/login")
    public ApiResponseVO<LoginResultVO> login(@RequestBody LoginParam param) {
        return ApiResponseVO.success(this.authService.login(param));
    }

    @PostMapping("/api/auth/register")
    public ApiResponseVO<Void> register(@RequestBody RegisterParam param) {
        this.authService.register(param);
        return ApiResponseVO.success(null);
    }

    @PostMapping("/api/auth/logout")
    public ApiResponseVO<Void> logout(HttpServletRequest request) {
        this.authService.logout(this.resolveToken(request));
        return ApiResponseVO.success(null);
    }

    @GetMapping("/api/auth/codes")
    public ApiResponseVO<List<String>> codes() {
        return ApiResponseVO.success(List.of());
    }

    @GetMapping("/api/user/info")
    public ApiResponseVO<UserInfoVO> userInfo(@AuthenticationPrincipal LoginUser loginUser, HttpServletRequest request) {
        return ApiResponseVO.success(
                this.authService.getUserInfo(loginUser, this.resolveToken(request)));
    }

    @PutMapping("/api/user/info")
    public ApiResponseVO<Void> updateProfile(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody UpdateProfileParam param) {
        this.authService.updateProfile(loginUser, param);
        return ApiResponseVO.success(null);
    }

    @PutMapping("/api/user/password")
    public ApiResponseVO<Void> changePassword(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody ChangePasswordParam param) {
        this.authService.changePassword(loginUser, param);
        return ApiResponseVO.success(null);
    }

    @PutMapping("/api/user/notification")
    public ApiResponseVO<Void> updateNotification(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody UpdateNotificationParam param) {
        this.authService.updateNotificationSetting(loginUser, param);
        return ApiResponseVO.success(null);
    }

    @PostMapping("/api/auth/refresh")
    public ApiResponseVO<String> refresh(HttpServletRequest request) {
        return ApiResponseVO.success(this.authService.refresh(this.resolveToken(request)));
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StrUtil.startWith(authorization, AuthConstant.BEARER_PREFIX)) {
            return authorization.substring(AuthConstant.BEARER_PREFIX.length());
        }
        return null;
    }
}
