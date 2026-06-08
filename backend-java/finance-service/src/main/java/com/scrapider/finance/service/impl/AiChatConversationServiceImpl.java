package com.scrapider.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.domain.dto.AiChatConversationBindingDTO;
import com.scrapider.finance.domain.dto.ConversationCleanupMessageDTO;
import com.scrapider.finance.domain.po.AiChatConversationPO;
import com.scrapider.finance.domain.po.AiChatMessagePO;
import com.scrapider.finance.domain.po.AiUserMemoryPO;
import com.scrapider.finance.manage.AiChatConversationManage;
import com.scrapider.finance.manage.AiChatMessageManage;
import com.scrapider.finance.manage.AiUserMemoryManage;
import com.scrapider.finance.service.AgentMessagePublisher;
import com.scrapider.finance.service.AiChatConversationService;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiChatConversationServiceImpl implements AiChatConversationService {

    private static final int CLEANUP_SUMMARY_MESSAGE_LIMIT = 50;
    private static final int CLEANUP_SUMMARY_CONTENT_LIMIT = 4000;
    private static final DateTimeFormatter MEMORY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiChatConversationManage conversationManage;
    private final AiChatMessageManage messageManage;
    private final AiUserMemoryManage userMemoryManage;
    private final AgentMessagePublisher agentMessagePublisher;
    private final ObjectMapper objectMapper;
    private final int cleanupDelayMinutes;
    private final ConcurrentMap<String, AtomicInteger> activeCounts = new ConcurrentHashMap<>();

    public AiChatConversationServiceImpl(
            AiChatConversationManage conversationManage,
            AiChatMessageManage messageManage,
            AiUserMemoryManage userMemoryManage,
            AgentMessagePublisher agentMessagePublisher,
            ObjectMapper objectMapper,
            @Value("${finance.agent.conversation-cleanup-delay-minutes:30}") int cleanupDelayMinutes) {
        this.conversationManage = conversationManage;
        this.messageManage = messageManage;
        this.userMemoryManage = userMemoryManage;
        this.agentMessagePublisher = agentMessagePublisher;
        this.objectMapper = objectMapper;
        this.cleanupDelayMinutes = cleanupDelayMinutes;
    }

    @Override
    public AiChatConversationBindingDTO bind(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("conversation userId is required");
        }
        AiChatConversationPO conversation = this.conversationManage.findLatestActiveByUserId(userId);
        if (conversation == null) {
            conversation = AiChatConversationPO.create(userId);
            this.conversationManage.save(conversation);
        }
        return this.bindActiveConversation(userId, conversation);
    }

    @Override
    public AiChatConversationBindingDTO bind(Long userId, String conversationId) {
        if (userId == null || StrUtil.isBlank(conversationId)) {
            throw new IllegalArgumentException("conversation userId and conversationId are required");
        }
        AiChatConversationPO conversation = this.ensureConversation(userId, conversationId);
        return this.bindActiveConversation(userId, conversation);
    }

    private AiChatConversationBindingDTO bindActiveConversation(Long userId, AiChatConversationPO conversation) {
        long cleanupVersion = (conversation.getCleanupVersion() == null ? 0L : conversation.getCleanupVersion()) + 1;
        this.conversationManage.markActive(conversation.getId(), cleanupVersion);
        String conversationId = conversation.getConversationId();
        this.activeCounts.computeIfAbsent(this.key(userId, conversationId), key -> new AtomicInteger())
                .incrementAndGet();
        return new AiChatConversationBindingDTO(userId, conversationId, cleanupVersion);
    }

    @Override
    public void release(Long userId, String conversationId) {
        if (userId == null || StrUtil.isBlank(conversationId)) {
            return;
        }
        String key = this.key(userId, conversationId);
        AtomicInteger counter = this.activeCounts.get(key);
        if (counter == null) {
            return;
        }
        int activeCount = counter.updateAndGet(value -> Math.max(0, value - 1));
        if (activeCount > 0) {
            return;
        }
        this.activeCounts.remove(key);
        AiChatConversationPO conversation = this.conversationManage.findByUserIdAndConversationId(userId, conversationId);
        if (conversation == null) {
            return;
        }
        this.agentMessagePublisher.publishConversationCleanup(ConversationCleanupMessageDTO.of(
                userId,
                conversationId,
                conversation.getCleanupVersion(),
                this.cleanupDelayMinutes));
    }

    @Override
    public void saveUserMessage(Long userId, String conversationId, String messageId, String content) {
        this.saveMessage(userId, conversationId, messageId, AiChatMessagePO.ROLE_USER, content);
    }

    @Override
    public void saveAssistantMessage(Long userId, String conversationId, String messageId, String content) {
        this.saveMessage(userId, conversationId, messageId, AiChatMessagePO.ROLE_ASSISTANT, content);
    }

    @Override
    public List<Map<String, Object>> listHistory(
            Long userId,
            String conversationId,
            String excludeMessageId,
            int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 30));
        return this.messageManage.listRecent(userId, conversationId, excludeMessageId, normalizedLimit).stream()
                .map(message -> Map.<String, Object>of(
                        "role", message.getRole(),
                        "content", message.getContent(),
                        "messageId", message.getMessageId(),
                        "createdAt", message.getCreatedAt().toString()))
                .toList();
    }

    @Override
    public void cleanup(ConversationCleanupMessageDTO message) {
        if (message == null || message.userId() == null || StrUtil.isBlank(message.conversationId())) {
            return;
        }
        AiChatConversationPO conversation = this.conversationManage.findByUserIdAndConversationId(
                message.userId(),
                message.conversationId());
        if (conversation == null || !java.util.Objects.equals(conversation.getCleanupVersion(), message.cleanupVersion())) {
            return;
        }
        if (this.activeCounts.containsKey(this.key(message.userId(), message.conversationId()))) {
            return;
        }
        this.saveConversationSummary(message.userId(), message.conversationId());
        this.messageManage.deleteByUserIdAndConversationId(message.userId(), message.conversationId());
        this.conversationManage.markCleaned(conversation.getId());
    }

    private AiChatConversationPO ensureConversation(Long userId, String conversationId) {
        AiChatConversationPO conversation = this.conversationManage.findByConversationId(conversationId);
        if (conversation == null) {
            conversation = AiChatConversationPO.create(userId, conversationId);
            this.conversationManage.save(conversation);
            return conversation;
        }
        if (!userId.equals(conversation.getUserId())) {
            throw new IllegalArgumentException("conversation does not belong to current user");
        }
        return conversation;
    }

    private void saveMessage(Long userId, String conversationId, String messageId, String role, String content) {
        if (userId == null || StrUtil.hasBlank(conversationId, messageId, role, content)) {
            return;
        }
        this.ensureConversation(userId, conversationId);
        this.messageManage.save(AiChatMessagePO.create(userId, conversationId, messageId, role, content, null));
    }

    private void saveConversationSummary(Long userId, String conversationId) {
        List<AiChatMessagePO> messages = this.messageManage.listRecent(
                userId,
                conversationId,
                null,
                CLEANUP_SUMMARY_MESSAGE_LIMIT);
        if (messages.isEmpty() || this.userMemoryManage.existsConversationSummary(userId, conversationId)) {
            return;
        }
        String content = this.buildConversationSummary(messages);
        String metadataJson = this.buildMemoryMetadataJson(messages.size());
        this.userMemoryManage.save(AiUserMemoryPO.conversationSummary(
                userId,
                conversationId,
                "AI Chat 对话摘要",
                content,
                metadataJson));
    }

    private String buildConversationSummary(List<AiChatMessagePO> messages) {
        StringBuilder builder = new StringBuilder();
        builder.append("本摘要由 cleanup 消费流程根据短期消息确定性生成，用于后续长期记忆召回。\n");
        for (AiChatMessagePO message : messages) {
            builder.append('[')
                    .append(message.getCreatedAt().format(MEMORY_TIME_FORMATTER))
                    .append("] ")
                    .append(message.getRole())
                    .append(": ")
                    .append(StrUtil.blankToDefault(message.getContent(), ""))
                    .append('\n');
            if (builder.length() >= CLEANUP_SUMMARY_CONTENT_LIMIT) {
                break;
            }
        }
        return StrUtil.maxLength(builder.toString(), CLEANUP_SUMMARY_CONTENT_LIMIT);
    }

    private String buildMemoryMetadataJson(int messageCount) {
        try {
            return this.objectMapper.writeValueAsString(Map.of(
                    "summaryMode", "deterministic_cleanup",
                    "messageCount", messageCount));
        } catch (JsonProcessingException e) {
            return "{\"summaryMode\":\"deterministic_cleanup\",\"messageCount\":" + messageCount + "}";
        }
    }

    private String key(Long userId, String conversationId) {
        return userId + ":" + conversationId;
    }
}
