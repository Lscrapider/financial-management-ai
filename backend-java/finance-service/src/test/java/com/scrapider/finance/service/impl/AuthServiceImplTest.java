package com.scrapider.finance.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.scrapider.finance.domain.dto.AuthSessionDTO;
import com.scrapider.finance.domain.param.ChangePasswordParam;
import com.scrapider.finance.domain.param.LoginParam;
import com.scrapider.finance.domain.param.RegisterParam;
import com.scrapider.finance.domain.param.UpdateNotificationParam;
import com.scrapider.finance.domain.param.UpdateProfileParam;
import com.scrapider.finance.domain.po.AppUserPO;
import com.scrapider.finance.manage.AppUserManage;
import com.scrapider.finance.security.JwtUtils;
import com.scrapider.finance.security.LoginUser;
import com.scrapider.finance.security.RefreshSessionStore;
import com.scrapider.finance.security.TokenStore;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceImplTest {

    private FakeAuthenticationManager authenticationManager;
    private FakeAppUserManage appUserManage;
    private FakeJwtUtils jwtUtils;
    private FakeRefreshSessionStore refreshSessionStore;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        this.authenticationManager = new FakeAuthenticationManager();
        this.appUserManage = new FakeAppUserManage();
        this.jwtUtils = new FakeJwtUtils();
        this.refreshSessionStore = new FakeRefreshSessionStore();
        this.authService = new AuthServiceImpl(
                this.authenticationManager,
                this.appUserManage,
                new NoopPasswordEncoder(),
                this.jwtUtils,
                new TokenStore(),
                this.refreshSessionStore,
                604_800_000L);
    }

    @Test
    void loginCreatesBackendRefreshSessionAndReturnsOpaqueSid() {
        this.authenticationManager.user = this.user(1L, "alice", "admin");
        this.jwtUtils.nextAccessToken = "access-token";

        AuthSessionDTO session = this.authService.login(param("alice", "password", "admin"));

        assertThat(session.accessToken()).isEqualTo("access-token");
        assertThat(session.refreshSid()).isNotBlank();
        assertThat(this.refreshSessionStore.savedSessionHash).isNotBlank();
        assertThat(this.refreshSessionStore.savedSessionHash).isNotEqualTo(session.refreshSid());
        assertThat(this.refreshSessionStore.savedUserId).isEqualTo(1L);
        assertThat(this.refreshSessionStore.savedExpiresAt).isAfter(LocalDateTime.now().plusDays(6));
    }

    @Test
    void refreshRotatesRefreshSessionAndReturnsNewAccessToken() {
        this.refreshSessionStore.activeSession =
                new RefreshSessionStore.RefreshSession(1L, LocalDateTime.now().plusDays(1));
        this.appUserManage.user = this.user(1L, "alice", "admin");
        this.jwtUtils.nextAccessToken = "new-access-token";

        AuthSessionDTO session = this.authService.refresh("old-refresh-sid");

        assertThat(session.accessToken()).isEqualTo("new-access-token");
        assertThat(session.refreshSid()).isNotBlank();
        assertThat(session.refreshSid()).isNotEqualTo("old-refresh-sid");
        assertThat(this.refreshSessionStore.saveCount).isEqualTo(1);
        assertThat(this.refreshSessionStore.revokedSessionHash).isNotBlank();
    }

    private static LoginParam param(String username, String password, String roleCode) {
        LoginParam param = new LoginParam();
        param.setUsername(username);
        param.setPassword(password);
        param.setRoleCode(roleCode);
        return param;
    }

    private AppUserPO user(Long id, String username, String roleCode) {
        AppUserPO user = new AppUserPO();
        user.setId(id);
        user.setUsername(username);
        user.setRoleCode(roleCode);
        user.setEnabled(true);
        return user;
    }

    private static class FakeAuthenticationManager implements AuthenticationManager {
        private AppUserPO user;

        @Override
        public Authentication authenticate(Authentication authentication) throws AuthenticationException {
            LoginUser loginUser = new LoginUser(this.user);
            return new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
        }
    }

    private static class FakeAppUserManage extends AppUserManage {
        private AppUserPO user;

        @Override
        public AppUserPO getById(java.io.Serializable id) {
            return this.user;
        }
    }

    private static class FakeRefreshSessionStore extends RefreshSessionStore {
        private RefreshSession activeSession;
        private String savedSessionHash;
        private Long savedUserId;
        private LocalDateTime savedExpiresAt;
        private String revokedSessionHash;
        private int saveCount;

        @Override
        public void saveSession(String sessionHash, Long userId, LocalDateTime expiresAt) {
            this.saveCount++;
            this.savedSessionHash = sessionHash;
            this.savedUserId = userId;
            this.savedExpiresAt = expiresAt;
        }

        @Override
        public RefreshSession getActiveBySessionHash(String sessionHash, LocalDateTime now) {
            return this.activeSession;
        }

        @Override
        public void revokeSession(String sessionHash) {
            this.revokedSessionHash = sessionHash;
        }
    }

    private static class FakeJwtUtils extends JwtUtils {
        private String nextAccessToken;

        private FakeJwtUtils() {
            super("VGhlUXVpY2tCcm93bkZveEp1bXBzT3ZlclRoZUxhenlEb2c=", 7_200_000L);
        }

        @Override
        public String generateAccessToken(Long userId, String username, String role) {
            return this.nextAccessToken;
        }
    }

    private static class NoopPasswordEncoder implements PasswordEncoder {
        @Override
        public String encode(CharSequence rawPassword) {
            return rawPassword.toString();
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return rawPassword.toString().equals(encodedPassword);
        }
    }
}
