package com.scrapider.finance.security;

import com.scrapider.finance.domain.po.AppUserPO;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;
    private final TokenStore tokenStore;

    public BearerTokenAuthenticationFilter(JwtUtils jwtUtils, TokenStore tokenStore) {
        this.jwtUtils = jwtUtils;
        this.tokenStore = tokenStore;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            String token = authorization.substring(BEARER_PREFIX.length());
            if (!tokenStore.isBlacklisted(token)) {
                try {
                    Claims claims = jwtUtils.parseToken(token);
                    Long userId = Long.valueOf(claims.getSubject());
                    String username = claims.get("username", String.class);
                    String role = claims.get("role", String.class);

                    AppUserPO user = new AppUserPO();
                    user.setId(userId);
                    user.setUsername(username);
                    user.setRoleCode(role);

                    LoginUser loginUser = new LoginUser(user);
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (Exception ignored) {
                    // Token invalid or expired — leave SecurityContext empty
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
