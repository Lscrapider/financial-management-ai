package com.scrapider.finance.security;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class TokenStore {

    private final ConcurrentMap<String, Authentication> tokens = new ConcurrentHashMap<>();

    public String createToken(Authentication authentication) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, authentication);
        return token;
    }

    public Optional<Authentication> getAuthentication(String token) {
        return Optional.ofNullable(tokens.get(token));
    }

    public void removeToken(String token) {
        tokens.remove(token);
    }
}
