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
    public Binding agentDeadLetterBinding(
            TopicExchange agentDeadLetterExchange,
            Queue agentDeadLetterQueue) {
        return BindingBuilder.bind(agentDeadLetterQueue)
                .to(agentDeadLetterExchange)
                .with("#");
    }
}
