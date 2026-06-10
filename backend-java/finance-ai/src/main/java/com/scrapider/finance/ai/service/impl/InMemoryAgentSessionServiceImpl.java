package com.scrapider.finance.ai.service.impl;

import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.service.AgentSessionService;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InMemoryAgentSessionServiceImpl implements AgentSessionService {

    private static final Set<String> DEFAULT_SCOPES = Set.of(
            "market.quote",
            "market.kline",
            "market.intraday",
            "stock.fundamental_context",
            "convertible_bond.context",
            "scene_report.context",
            "scene.signal_data",
            "knowledge.search",
            "report.latest",
            "watch_pool.context",
            "conversation.history");

    private final ConcurrentMap<String, AgentSessionDTO> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> usedNonces = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration ttl;

    public InMemoryAgentSessionServiceImpl(
            @Value("${finance.agent.session-ttl-seconds:1800}") long sessionTtlSeconds) {
        this.ttl = Duration.ofSeconds(sessionTtlSeconds);
    }

    @Override
    public AgentSessionDTO create(Long userId, String username, String conversationId, String messageId) {
        this.clearExpired();
        AgentSessionDTO session = new AgentSessionDTO(
                UUID.randomUUID().toString(),
                this.newSecret(),
                userId,
                username,
                conversationId,
                messageId,
                DEFAULT_SCOPES,
                Instant.now().plus(this.ttl));
        this.sessions.put(session.agentSessionId(), session);
        this.usedNonces.put(session.agentSessionId(), ConcurrentHashMap.newKeySet());
        return session;
    }

    @Override
    public Optional<AgentSessionDTO> findActive(String agentSessionId) {
        if (agentSessionId == null || agentSessionId.isBlank()) {
            return Optional.empty();
        }
        AgentSessionDTO session = this.sessions.get(agentSessionId);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expired(Instant.now())) {
            this.sessions.remove(agentSessionId);
            this.usedNonces.remove(agentSessionId);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    @Override
    public boolean markNonceUsed(String agentSessionId, String nonce) {
        if (nonce == null || nonce.isBlank()) {
            return false;
        }
        Set<String> nonces = this.usedNonces.computeIfAbsent(agentSessionId, key -> ConcurrentHashMap.newKeySet());
        return nonces.add(nonce);
    }

    private String newSecret() {
        byte[] secret = new byte[32];
        this.secureRandom.nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    private void clearExpired() {
        Instant now = Instant.now();
        for (Map.Entry<String, AgentSessionDTO> entry : this.sessions.entrySet()) {
            if (entry.getValue().expired(now)) {
                this.sessions.remove(entry.getKey());
                this.usedNonces.remove(entry.getKey());
            }
        }
    }
}
