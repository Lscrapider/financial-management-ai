package com.scrapider.finance.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
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
    public Queue sceneAnalysisCurrentGenerateQueue(
            @Value("${finance.scene-analysis.rabbitmq.current-scene-queue:finance.scene-analysis.current.generate}")
                    String queue) {
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
}
