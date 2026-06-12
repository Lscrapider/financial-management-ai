package com.scrapider.finance.ai.security;

import cn.hutool.core.util.StrUtil;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SceneAnalysisCallbackTokenStore {

    public static final String HEADER_NAME = "X-Scene-Analysis-Callback-Token";

    private final ConcurrentMap<String, CallbackToken> tokens = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration ttl;

    public SceneAnalysisCallbackTokenStore(
            @Value("${finance.scene-analysis.callback-token.ttl-seconds:21600}") long ttlSeconds) {
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public String issue(String taskNo) {
        this.clearExpired();
        String token = this.newToken();
        this.tokens.put(taskNo, new CallbackToken(token, Instant.now().plus(this.ttl)));
        return token;
    }

    public boolean matches(String taskNo, String token) {
        if (StrUtil.isBlank(taskNo) || StrUtil.isBlank(token)) {
            return false;
        }
        CallbackToken stored = this.tokens.get(taskNo);
        if (stored == null) {
            return false;
        }
        if (stored.expired(Instant.now())) {
            this.tokens.remove(taskNo);
            return false;
        }
        return stored.value().equals(token);
    }

    public void revoke(String taskNo) {
        if (StrUtil.isNotBlank(taskNo)) {
            this.tokens.remove(taskNo);
        }
    }

    private String newToken() {
        byte[] token = new byte[32];
        this.secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private void clearExpired() {
        Instant now = Instant.now();
        for (Map.Entry<String, CallbackToken> entry : this.tokens.entrySet()) {
            if (entry.getValue().expired(now)) {
                this.tokens.remove(entry.getKey());
            }
        }
    }

    private record CallbackToken(String value, Instant expiresAt) {

        boolean expired(Instant now) {
            return !this.expiresAt.isAfter(now);
        }
    }
}
