package com.ai_got_this.order_execution_persister.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    private final RabbitMqProperties rabbitMqProperties;

    public RabbitMqConfig(RabbitMqProperties rabbitMqProperties) {
        this.rabbitMqProperties = rabbitMqProperties;
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public Queue orderEventsPersisterQueue() {
        return new Queue(rabbitMqProperties.getOrderEventsPersisterQueue(), true);
    }
}
