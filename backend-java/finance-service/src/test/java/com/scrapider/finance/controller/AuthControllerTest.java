package com.scrapider.finance.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.domain.dto.AuthSessionDTO;
import com.scrapider.finance.domain.param.ChangePasswordParam;
import com.scrapider.finance.domain.param.LoginParam;
import com.scrapider.finance.domain.param.RegisterParam;
import com.scrapider.finance.domain.param.UpdateNotificationParam;
import com.scrapider.finance.domain.param.UpdateProfileParam;
import com.scrapider.finance.domain.vo.UserInfoVO;
import com.scrapider.finance.security.LoginUser;
import com.scrapider.finance.security.RefreshSessionCookieHandler;
import com.scrapider.finance.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private static final String REFRESH_COOKIE_NAME = RefreshSessionCookieHandler.REFRESH_COOKIE_NAME;

    private FakeAuthService authService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        this.authService = new FakeAuthService();
        this.mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(
                        this.authService,
                        new RefreshSessionCookieHandler(604_800L, false)))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Test
    void loginReturnsOnlyAccessTokenAndSetsHttpOnlyRefreshCookie() throws Exception {
        this.authService.nextSession = new AuthSessionDTO("access-token", "refresh-sid");

        this.mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(this.objectMapper.writeValueAsString(loginParam())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshSid").doesNotExist())
                .andExpect(cookie().value(REFRESH_COOKIE_NAME, "refresh-sid"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
    }

    @Test
    void refreshReadsRefreshCookieAndReturnsOnlyNewAccessToken() throws Exception {
        this.authService.nextSession = new AuthSessionDTO("new-access-token", "new-refresh-sid");

        this.mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(REFRESH_COOKIE_NAME, "old-refresh-sid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("new-access-token"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("new-refresh-sid")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("new-access-token"))));

        org.assertj.core.api.Assertions.assertThat(this.authService.lastRefreshSid).isEqualTo("old-refresh-sid");
    }

    private static LoginParam loginParam() {
        LoginParam param = new LoginParam();
        param.setUsername("alice");
        param.setPassword("password");
        return param;
    }

    private static class FakeAuthService implements AuthService {
        private AuthSessionDTO nextSession;
        private String lastRefreshSid;

        @Override
        public AuthSessionDTO login(LoginParam param) {
            return this.nextSession;
        }

        @Override
        public void register(RegisterParam param) {
        }

        @Override
        public void logout(String accessToken, String refreshSid) {
        }

        @Override
        public UserInfoVO getUserInfo(LoginUser loginUser, String token) {
            return null;
        }

        @Override
        public AuthSessionDTO refresh(String refreshSid) {
            this.lastRefreshSid = refreshSid;
            return this.nextSession;
        }

        @Override
        public void updateProfile(LoginUser loginUser, UpdateProfileParam param) {
        }

        @Override
        public void changePassword(LoginUser loginUser, ChangePasswordParam param) {
        }

        @Override
        public void updateNotificationSetting(LoginUser loginUser, UpdateNotificationParam param) {
        }
    }
}
