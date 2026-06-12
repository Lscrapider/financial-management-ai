package com.scrapider.finance.ai.service.impl;

import com.scrapider.finance.ai.domain.vo.AiChatWebSocketTicketVO;
import com.scrapider.finance.ai.service.AiChatWebSocketTicketService;
import com.scrapider.finance.security.LoginUser;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class InMemoryAiChatWebSocketTicketServiceImpl implements AiChatWebSocketTicketService {

    private static final int TICKET_BYTES = 32;
    private static final Duration TICKET_TTL = Duration.ofSeconds(60);

    private final ConcurrentMap<String, TicketRecord> tickets = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public AiChatWebSocketTicketVO issue(LoginUser loginUser) {
        if (loginUser == null) {
            throw new IllegalArgumentException("登录用户不能为空");
        }
        this.clearExpired();
        String ticket = this.newTicket();
        this.tickets.put(ticket, new TicketRecord(loginUser, Instant.now().plus(TICKET_TTL)));
        return new AiChatWebSocketTicketVO(ticket, TICKET_TTL.toSeconds());
    }

    @Override
    public Optional<LoginUser> consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        TicketRecord record = this.tickets.remove(ticket);
        if (record == null || record.expired()) {
            return Optional.empty();
        }
        return Optional.of(record.loginUser());
    }

    private String newTicket() {
        byte[] bytes = new byte[TICKET_BYTES];
        this.secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void clearExpired() {
        for (Map.Entry<String, TicketRecord> entry : this.tickets.entrySet()) {
            if (entry.getValue().expired()) {
                this.tickets.remove(entry.getKey());
            }
        }
    }

    private record TicketRecord(LoginUser loginUser, Instant expiresAt) {

        private boolean expired() {
            return Instant.now().isAfter(this.expiresAt);
        }
    }
}
