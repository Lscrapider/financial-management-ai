package com.scrapider.finance.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class TokenStore {

    private final ConcurrentMap<String, Boolean> blacklist = new ConcurrentHashMap<>();

    public void blacklist(String token) {
        blacklist.put(token, Boolean.TRUE);
    }

    public boolean isBlacklisted(String token) {
        return blacklist.containsKey(token);
    }
}
