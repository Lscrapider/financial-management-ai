package com.scrapider.finance.config;

import com.scrapider.finance.security.BearerTokenAuthenticationFilter;
import com.scrapider.finance.security.JwtUtils;
import com.scrapider.finance.security.TokenStore;
import com.scrapider.finance.mapper.AppUserMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtUtils jwtUtils,
            TokenStore tokenStore,
            AppUserMapper appUserMapper) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(this.corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/refresh",
                                "/api/auth/logout")
                        .permitAll()
                        .requestMatchers("/api/ws/**").permitAll()
                        .requestMatchers("/api/ai/ocr/reviews/*/pages/*/image").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/ai/scene-analysis/tasks/*/callback").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/ai/knowledge-material/tasks/*/callback").permitAll()
                        .requestMatchers(
                                "/api/system-config/**",
                                "/api/stocks/sync",
                                "/api/stocks/sync/**",
                                "/api/indices/sync",
                                "/api/indices/sync/**",
                                "/api/bonds/sync",
                                "/api/bonds/sync/**",
                                "/api/knowledge/**",
                                "/api/ai/ocr/tasks/**",
                                "/api/ai/ocr/reviews/**",
                                "/api/ai/manual-knowledge/tasks/**",
                                "/api/ai/knowledge-material/**",
                                "/api/ai/console/**",
                                "/api/ai/token-usage/**")
                        .hasRole("admin")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(
                        new BearerTokenAuthenticationFilter(jwtUtils, tokenStore, appUserMapper),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOriginPattern("*");
        configuration.addAllowedHeader("*");
        configuration.addAllowedMethod("*");
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
