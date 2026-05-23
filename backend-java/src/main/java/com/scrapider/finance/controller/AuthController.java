package com.scrapider.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scrapider.finance.domain.param.LoginParam;
import com.scrapider.finance.domain.param.RegisterParam;
import com.scrapider.finance.domain.po.AppUserPO;
import com.scrapider.finance.domain.vo.ApiResponseVO;
import com.scrapider.finance.domain.vo.LoginResultVO;
import com.scrapider.finance.domain.vo.UserInfoVO;
import com.scrapider.finance.mapper.AppUserMapper;
import com.scrapider.finance.security.LoginUser;
import com.scrapider.finance.security.TokenStore;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthenticationManager authenticationManager;
    private final AppUserMapper appUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;

    public AuthController(
            AuthenticationManager authenticationManager,
            AppUserMapper appUserMapper,
            PasswordEncoder passwordEncoder,
            TokenStore tokenStore) {
        this.authenticationManager = authenticationManager;
        this.appUserMapper = appUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
    }

    @PostMapping("/api/auth/login")
    public ApiResponseVO<LoginResultVO> login(@RequestBody LoginParam param) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(param.getUsername(), param.getPassword()));
        String token = tokenStore.createToken(authentication);
        return ApiResponseVO.success(new LoginResultVO(token));
    }

    @PostMapping("/api/auth/register")
    public ApiResponseVO<Void> register(@RequestBody RegisterParam param) {
        validateRegisterParam(param);

        Long existingCount = appUserMapper.selectCount(
                new LambdaQueryWrapper<AppUserPO>().eq(AppUserPO::getUsername, param.getUsername()));
        if (existingCount > 0) {
            throw new IllegalArgumentException("Username already exists.");
        }

        AppUserPO user = new AppUserPO();
        user.setUsername(param.getUsername());
        user.setPassword(passwordEncoder.encode(param.getPassword()));
        user.setRealName(param.getUsername());
        user.setRoleCode("admin");
        user.setAvatar("");
        user.setEnabled(true);
        user.setHomePath("/analytics");
        appUserMapper.insert(user);
        return ApiResponseVO.success(null);
    }

    @PostMapping("/api/auth/logout")
    public ApiResponseVO<Void> logout(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token != null) {
            tokenStore.removeToken(token);
        }
        return ApiResponseVO.success(null);
    }

    @GetMapping("/api/auth/codes")
    public ApiResponseVO<List<String>> codes() {
        return ApiResponseVO.success(List.of());
    }

    @GetMapping("/api/user/info")
    public ApiResponseVO<UserInfoVO> userInfo(@AuthenticationPrincipal LoginUser loginUser, HttpServletRequest request) {
        AppUserPO user = loginUser.getUser();
        String token = resolveToken(request);
        String roleCode = loginUser.getRoleCode();
        List<String> roles = roleCode == null || roleCode.isBlank() ? List.of() : List.of(roleCode);
        return ApiResponseVO.success(new UserInfoVO(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getRealName(),
                user.getAvatar(),
                roles,
                "",
                user.getHomePath(),
                token));
    }

    @PostMapping("/api/auth/refresh")
    public ApiResponseVO<String> refresh(HttpServletRequest request) {
        return ApiResponseVO.success(resolveToken(request));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponseVO<Void>> handleAuthenticationException() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponseVO.error("Username or password is incorrect."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseVO<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponseVO.error(ex.getMessage()));
    }

    private void validateRegisterParam(RegisterParam param) {
        if (param == null || isBlank(param.getUsername()) || isBlank(param.getPassword())) {
            throw new IllegalArgumentException("Username and password are required.");
        }
        if (!param.getPassword().equals(param.getConfirmPassword())) {
            throw new IllegalArgumentException("Password confirmation does not match.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
