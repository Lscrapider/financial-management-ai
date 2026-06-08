package com.scrapider.finance.messaging;

import com.scrapider.finance.domain.dto.ConversationCleanupMessageDTO;
import com.scrapider.finance.service.AiChatConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ConversationCleanupListener {

    private static final Logger log = LoggerFactory.getLogger(ConversationCleanupListener.class);

    private final AiChatConversationService aiChatConversationService;

    public ConversationCleanupListener(AiChatConversationService aiChatConversationService) {
        this.aiChatConversationService = aiChatConversationService;
    }

    @RabbitListener(queues = "${finance.agent.rabbitmq.cleanup-queue:finance.agent.conversation.cleanup}")
    public void handle(ConversationCleanupMessageDTO message) {
        log.info(
                "conversation cleanup consumed userId={} conversationId={} cleanupVersion={}",
                message.userId(),
                message.conversationId(),
                message.cleanupVersion());
        this.aiChatConversationService.cleanup(message);
    }
}
