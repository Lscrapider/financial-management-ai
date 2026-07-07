package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.service.AgentSessionService;
import com.scrapider.finance.ai.service.AgentSignatureService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class HmacAgentSignatureServiceImpl implements AgentSignatureService {

    private static final String SESSION_ID_HEADER = "X-Agent-Session-Id";
    private static final String TIMESTAMP_HEADER = "X-Agent-Timestamp";
    private static final String NONCE_HEADER = "X-Agent-Nonce";
    private static final String SIGNATURE_HEADER = "X-Agent-Signature";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AgentSessionService agentSessionService;
    private final Duration signatureWindow;

    public HmacAgentSignatureServiceImpl(
            AgentSessionService agentSessionService,
            @Value("${finance.agent.signature-window-seconds:60}") long signatureWindowSeconds) {
        this.agentSessionService = agentSessionService;
        this.signatureWindow = Duration.ofSeconds(signatureWindowSeconds);
    }

    @Override
    public AgentSessionDTO verify(HttpServletRequest request, String rawBody) {
        String agentSessionId = request.getHeader(SESSION_ID_HEADER);
        String timestamp = request.getHeader(TIMESTAMP_HEADER);
        String nonce = request.getHeader(NONCE_HEADER);
        String signature = request.getHeader(SIGNATURE_HEADER);
        if (StrUtil.hasBlank(agentSessionId, timestamp, nonce, signature)) {
            throw new AccessDeniedException("Agent 签名请求头不能为空。");
        }
        AgentSessionDTO session = this.agentSessionService.findActive(agentSessionId)
                .orElseThrow(() -> new AccessDeniedException("Agent 会话无效或已过期。"));
        this.verifyTimestamp(timestamp);
        String expected = this.sign(session.sessionSecret(), this.canonicalString(request, timestamp, nonce, rawBody));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new AccessDeniedException("Agent 签名无效。");
        }
        if (!this.agentSessionService.markNonceUsed(agentSessionId, nonce)) {
            throw new AccessDeniedException("Agent 请求 nonce 已被使用。");
        }
        return session;
    }

    private void verifyTimestamp(String timestamp) {
        long epochMillis;
        try {
            epochMillis = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            throw new AccessDeniedException("Agent 时间戳无效。");
        }
        Instant requestTime = Instant.ofEpochMilli(epochMillis);
        Instant now = Instant.now();
        if (requestTime.isBefore(now.minus(this.signatureWindow)) || requestTime.isAfter(now.plus(this.signatureWindow))) {
            throw new AccessDeniedException("Agent 时间戳已过期。");
        }
    }

    private String canonicalString(HttpServletRequest request, String timestamp, String nonce, String rawBody) {
        return request.getMethod()
                + "\n"
                + request.getRequestURI()
                + "\n"
                + timestamp
                + "\n"
                + nonce
                + "\n"
                + this.sha256Hex(rawBody == null ? "" : rawBody);
    }

    private String sign(String secret, String canonicalString) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Agent 请求签名失败。", ex);
        }
    }

    private String sha256Hex(String rawBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawBody.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Agent 请求摘要计算失败。", ex);
        }
    }
}
