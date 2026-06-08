package com.scrapider.finance.service.impl;

import com.scrapider.finance.domain.dto.AgentRunStartMessageDTO;
import com.scrapider.finance.service.AgentMessagePublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RabbitAgentMessagePublisherImpl implements AgentMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String runStartRoutingKey;

    public RabbitAgentMessagePublisherImpl(
            RabbitTemplate rabbitTemplate,
            @Value("${finance.agent.rabbitmq.exchange:finance.agent.topic}") String exchange,
            @Value("${finance.agent.rabbitmq.run-start-routing-key:agent.run.start}") String runStartRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.runStartRoutingKey = runStartRoutingKey;
    }

    @Override
    public void publishRunStart(AgentRunStartMessageDTO message) {
        this.rabbitTemplate.convertAndSend(this.exchange, this.runStartRoutingKey, message);
    }
}
