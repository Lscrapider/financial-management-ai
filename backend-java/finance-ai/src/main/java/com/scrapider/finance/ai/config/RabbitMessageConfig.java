package com.scrapider.finance.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMessageConfig {

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public TopicExchange sceneAnalysisTopicExchange(
            @Value("${finance.scene-analysis.rabbitmq.exchange:finance.scene-analysis.topic}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public TopicExchange sceneAnalysisRetryTopicExchange(
            @Value("${finance.scene-analysis.rabbitmq.retry-exchange:finance.scene-analysis.retry.topic}")
                    String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public TopicExchange sceneAnalysisDeadLetterExchange(
            @Value("${finance.scene-analysis.rabbitmq.dead-letter-exchange:finance.scene-analysis.dlx}")
                    String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue sceneAnalysisCurrentGenerateQueue(
            @Value("${finance.scene-analysis.rabbitmq.current-scene-queue:finance.scene-analysis.current.generate}") String queue,
            @Value("${finance.scene-analysis.rabbitmq.dead-letter-exchange:finance.scene-analysis.dlx}")
                    String deadLetterExchange) {
        return QueueBuilder.durable(queue)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey("scene.analysis.current.generate.dead")
                .build();
    }

    @Bean
    public Queue sceneAnalysisCurrentGenerateRetryQueue(
            @Value("${finance.scene-analysis.rabbitmq.current-scene-retry-queue:finance.scene-analysis.current.generate.retry}")
                    String queue,
            @Value("${finance.scene-analysis.rabbitmq.exchange:finance.scene-analysis.topic}") String exchange,
            @Value("${finance.scene-analysis.rabbitmq.retry-ttl-ms:30000}") Integer retryTtlMs) {
        return QueueBuilder.durable(queue)
                .ttl(retryTtlMs)
                .deadLetterExchange(exchange)
                .deadLetterRoutingKey("scene.analysis.current.generate")
                .build();
    }

    @Bean
    public Queue sceneAnalysisRetrievalEmbeddingQueue(
            @Value("${finance.scene-analysis.rabbitmq.retrieval-embedding-queue:finance.scene-analysis.retrieval.embedding}")
                    String queue,
            @Value("${finance.scene-analysis.rabbitmq.dead-letter-exchange:finance.scene-analysis.dlx}")
                    String deadLetterExchange) {
        return QueueBuilder.durable(queue)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey("scene.analysis.retrieval.embedding.dead")
                .build();
    }

    @Bean
    public Queue sceneAnalysisRetrievalEmbeddingRetryQueue(
            @Value("${finance.scene-analysis.rabbitmq.retrieval-embedding-retry-queue:finance.scene-analysis.retrieval.embedding.retry}")
                    String queue,
            @Value("${finance.scene-analysis.rabbitmq.exchange:finance.scene-analysis.topic}") String exchange,
            @Value("${finance.scene-analysis.rabbitmq.retry-ttl-ms:30000}") Integer retryTtlMs) {
        return QueueBuilder.durable(queue)
                .ttl(retryTtlMs)
                .deadLetterExchange(exchange)
                .deadLetterRoutingKey("scene.analysis.retrieval.embedding")
                .build();
    }

    @Bean
    public Queue sceneAnalysisDeadLetterQueue(
            @Value("${finance.scene-analysis.rabbitmq.dead-letter-queue:finance.scene-analysis.dlq}") String queue) {
        return new Queue(queue, true);
    }

    @Bean
    public Binding sceneAnalysisCurrentGenerateBinding(
            TopicExchange sceneAnalysisTopicExchange,
            Queue sceneAnalysisCurrentGenerateQueue,
            @Value("${finance.scene-analysis.rabbitmq.current-scene-routing-key:scene.analysis.current.generate}")
                    String routingKey) {
        return BindingBuilder.bind(sceneAnalysisCurrentGenerateQueue)
                .to(sceneAnalysisTopicExchange)
                .with(routingKey);
    }

    @Bean
    public Binding sceneAnalysisCurrentGenerateRetryBinding(
            TopicExchange sceneAnalysisRetryTopicExchange,
            Queue sceneAnalysisCurrentGenerateRetryQueue,
            @Value("${finance.scene-analysis.rabbitmq.current-scene-routing-key:scene.analysis.current.generate}")
                    String routingKey) {
        return BindingBuilder.bind(sceneAnalysisCurrentGenerateRetryQueue)
                .to(sceneAnalysisRetryTopicExchange)
                .with(routingKey + ".retry");
    }

    @Bean
    public Binding sceneAnalysisRetrievalEmbeddingBinding(
            TopicExchange sceneAnalysisTopicExchange,
            Queue sceneAnalysisRetrievalEmbeddingQueue,
            @Value("${finance.scene-analysis.rabbitmq.retrieval-embedding-routing-key:scene.analysis.retrieval.embedding}")
                    String routingKey) {
        return BindingBuilder.bind(sceneAnalysisRetrievalEmbeddingQueue)
                .to(sceneAnalysisTopicExchange)
                .with(routingKey);
    }

    @Bean
    public Binding sceneAnalysisRetrievalEmbeddingRetryBinding(
            TopicExchange sceneAnalysisRetryTopicExchange,
            Queue sceneAnalysisRetrievalEmbeddingRetryQueue,
            @Value("${finance.scene-analysis.rabbitmq.retrieval-embedding-routing-key:scene.analysis.retrieval.embedding}")
                    String routingKey) {
        return BindingBuilder.bind(sceneAnalysisRetrievalEmbeddingRetryQueue)
                .to(sceneAnalysisRetryTopicExchange)
                .with(routingKey + ".retry");
    }

    @Bean
    public Binding sceneAnalysisDeadLetterBinding(
            TopicExchange sceneAnalysisDeadLetterExchange,
            Queue sceneAnalysisDeadLetterQueue) {
        return BindingBuilder.bind(sceneAnalysisDeadLetterQueue)
                .to(sceneAnalysisDeadLetterExchange)
                .with("#");
    }
}
