package com.scrapider.finance.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentRabbitConfig {

    @Bean
    public TopicExchange agentTopicExchange(
            @Value("${finance.agent.rabbitmq.exchange:finance.agent.topic}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public TopicExchange agentDeadLetterExchange(
            @Value("${finance.agent.rabbitmq.dead-letter-exchange:finance.agent.dlx}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue agentRunStartQueue(
            @Value("${finance.agent.rabbitmq.run-start-queue:finance.agent.run.start}") String queue,
            @Value("${finance.agent.rabbitmq.dead-letter-exchange:finance.agent.dlx}") String deadLetterExchange) {
        return QueueBuilder.durable(queue)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey("agent.run.start.dead")
                .build();
    }

    @Bean
    public Queue conversationCleanupDelayQueue(
            @Value("${finance.agent.rabbitmq.cleanup-delay-queue:finance.agent.conversation.cleanup.delay}") String queue,
            @Value("${finance.agent.rabbitmq.exchange:finance.agent.topic}") String exchange,
            @Value("${finance.agent.rabbitmq.cleanup-routing-key:conversation.cleanup}") String routingKey,
            @Value("${finance.agent.conversation-cleanup-delay-ms:1800000}") int cleanupDelayMs) {
        return QueueBuilder.durable(queue)
                .ttl(cleanupDelayMs)
                .deadLetterExchange(exchange)
                .deadLetterRoutingKey(routingKey)
                .build();
    }

    @Bean
    public Queue conversationCleanupQueue(
            @Value("${finance.agent.rabbitmq.cleanup-queue:finance.agent.conversation.cleanup}") String queue) {
        return QueueBuilder.durable(queue).build();
    }

    @Bean
    public Queue agentDeadLetterQueue(
            @Value("${finance.agent.rabbitmq.dead-letter-queue:finance.agent.dlq}") String queue) {
        return new Queue(queue, true);
    }

    @Bean
    public Binding agentRunStartBinding(
            TopicExchange agentTopicExchange,
            Queue agentRunStartQueue,
            @Value("${finance.agent.rabbitmq.run-start-routing-key:agent.run.start}") String routingKey) {
        return BindingBuilder.bind(agentRunStartQueue)
                .to(agentTopicExchange)
                .with(routingKey);
    }

    @Bean
    public Binding conversationCleanupDelayBinding(
            TopicExchange agentTopicExchange,
            Queue conversationCleanupDelayQueue,
            @Value("${finance.agent.rabbitmq.cleanup-delay-routing-key:conversation.cleanup.delay}") String routingKey) {
        return BindingBuilder.bind(conversationCleanupDelayQueue)
                .to(agentTopicExchange)
                .with(routingKey);
    }

    @Bean
    public Binding conversationCleanupBinding(
            TopicExchange agentTopicExchange,
            Queue conversationCleanupQueue,
            @Value("${finance.agent.rabbitmq.cleanup-routing-key:conversation.cleanup}") String routingKey) {
        return BindingBuilder.bind(conversationCleanupQueue)
                .to(agentTopicExchange)
                .with(routingKey);
    }

    @Bean
    public Binding agentDeadLetterBinding(
            TopicExchange agentDeadLetterExchange,
            Queue agentDeadLetterQueue) {
        return BindingBuilder.bind(agentDeadLetterQueue)
                .to(agentDeadLetterExchange)
                .with("#");
    }
}
