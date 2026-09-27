package com.ai_got_this.market_data_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public Queue marketDataQueue() {
        return new Queue("market-data", true);
    }

    @Bean
    public DirectExchange marketDataExchange() {
        return new DirectExchange("market-data-exchange");
    }

    @Bean
    public Binding bindingMarketDataQueue(Queue marketDataQueue, DirectExchange marketDataExchange) {
        return BindingBuilder.bind(marketDataQueue).to(marketDataExchange).with("market-data");
    }
}
