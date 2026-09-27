package com.ai_got_this.order_execution_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    private final RabbitMqProperties rabbitMqProperties;

    public RabbitMqConfig(RabbitMqProperties rabbitMqProperties, ApplicationConfigurationValidator configurationValidator) {
        this.rabbitMqProperties = rabbitMqProperties;
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public Queue orderEventsPortfolioQueue() {
        return new Queue(rabbitMqProperties.getOrderEventsPortfolioQueue(), true);
    }

    @Bean
    public Queue orderEventsNotificationQueue() {
        return new Queue(rabbitMqProperties.getOrderEventsNotificationQueue(), true);
    }

    @Bean
    public Queue orderEventsPersisterQueue() {
        return new Queue(rabbitMqProperties.getOrderEventsPersisterQueue(), true);
    }

    @Bean
    public FanoutExchange orderEventsFanoutExchange() {
        return new FanoutExchange(rabbitMqProperties.getOrderEventsExchange(), true, false);
    }

    @Bean
    public Binding orderEventsPortfolioBinding(Queue orderEventsPortfolioQueue, FanoutExchange orderEventsFanoutExchange) {
        return BindingBuilder.bind(orderEventsPortfolioQueue).to(orderEventsFanoutExchange);
    }

    @Bean
    public Binding orderEventsNotificationBinding(Queue orderEventsNotificationQueue, FanoutExchange orderEventsFanoutExchange) {
        return BindingBuilder.bind(orderEventsNotificationQueue).to(orderEventsFanoutExchange);
    }

    @Bean
    public Binding orderEventsPersisterBinding(Queue orderEventsPersisterQueue, FanoutExchange orderEventsFanoutExchange) {
        return BindingBuilder.bind(orderEventsPersisterQueue).to(orderEventsFanoutExchange);
    }
}
