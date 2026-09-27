package com.ai_got_this.market_data_service.service;

import com.ai_got_this.market_data_service.dto.MarketTick;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class MarketDataPublisherService {
    private final RabbitTemplate rabbitTemplate;

    public MarketDataPublisherService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishMarketData(String exchange, MarketTick marketTick, String routingKey) {
        rabbitTemplate.convertAndSend(exchange, routingKey, marketTick);
    }
}
