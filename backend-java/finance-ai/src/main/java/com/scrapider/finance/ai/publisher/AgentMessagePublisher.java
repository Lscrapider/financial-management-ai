package com.scrapider.finance.ai.publisher;

import com.scrapider.finance.ai.domain.dto.AgentRunStartMessageDTO;
import com.scrapider.finance.ai.domain.dto.ConversationCleanupMessageDTO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AgentMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(AgentMessagePublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String runStartRoutingKey;
    private final String cleanupDelayRoutingKey;

    public AgentMessagePublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${finance.agent.rabbitmq.exchange:finance.agent.topic}") String exchange,
            @Value("${finance.agent.rabbitmq.run-start-routing-key:agent.run.start}") String runStartRoutingKey,
            @Value("${finance.agent.rabbitmq.cleanup-delay-routing-key:conversation.cleanup.delay}")
                    String cleanupDelayRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.runStartRoutingKey = runStartRoutingKey;
        this.cleanupDelayRoutingKey = cleanupDelayRoutingKey;
    }

    public void publishRunStart(AgentRunStartMessageDTO message) {
        log.info(
                "publish agent run start sessionId={} conversationId={} messageId={} dataGatewayUrl={} callbackUrl={}",
                message.agentSessionId(),
                message.conversationId(),
                message.messageId(),
                message.dataGatewayUrl(),
                message.callbackUrl());
        this.rabbitTemplate.convertAndSend(this.exchange, this.runStartRoutingKey, message);
    }

    public void publishConversationCleanup(ConversationCleanupMessageDTO message) {
        log.info(
                "publish conversation cleanup userId={} conversationId={} cleanupVersion={} delayMinutes={}",
                message.userId(),
                message.conversationId(),
                message.cleanupVersion(),
                message.delayMinutes());
        this.rabbitTemplate.convertAndSend(this.exchange, this.cleanupDelayRoutingKey, message);
    }
}
