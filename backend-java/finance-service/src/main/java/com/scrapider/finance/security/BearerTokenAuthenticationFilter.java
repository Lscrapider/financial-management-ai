package com.scrapider.finance.security;

import com.scrapider.finance.domain.po.AppUserPO;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BearerTokenAuthenticationFilter.class);
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
            if (!this.tokenStore.isBlacklisted(token)) {
                try {
                    Claims claims = this.jwtUtils.parseToken(token);
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
                } catch (Exception ex) {
                    LOGGER.debug("Invalid bearer token, leave SecurityContext empty.", ex);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
