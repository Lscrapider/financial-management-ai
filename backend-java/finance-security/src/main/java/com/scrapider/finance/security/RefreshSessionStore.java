package com.scrapider.finance.security;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class RefreshSessionStore {

    private final ConcurrentMap<String, RefreshSession> sessions = new ConcurrentHashMap<>();

    public void saveSession(String sessionHash, Long userId, LocalDateTime expiresAt) {
        this.sessions.put(sessionHash, new RefreshSession(userId, expiresAt));
    }

    public RefreshSession getActiveBySessionHash(String sessionHash, LocalDateTime now) {
        RefreshSession session = this.sessions.get(sessionHash);
        if (session == null) {
            return null;
        }
        if (!session.expiresAt().isAfter(now)) {
            this.sessions.remove(sessionHash);
            return null;
        }
        return session;
    }

    public void revokeSession(String sessionHash) {
        this.sessions.remove(sessionHash);
    }

    public record RefreshSession(Long userId, LocalDateTime expiresAt) {
    }
}
