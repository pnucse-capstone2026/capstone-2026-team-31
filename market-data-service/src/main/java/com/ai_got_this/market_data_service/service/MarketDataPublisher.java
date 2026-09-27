package com.ai_got_this.market_data_service.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * @deprecated Use {@link MarketDataPublisherService}. Kept as a source-compatible
 * adapter for callers that still construct the former publisher type directly.
 */
@Deprecated(forRemoval = false)
public class MarketDataPublisher extends MarketDataPublisherService {

    public MarketDataPublisher(RabbitTemplate rabbitTemplate) {
        super(rabbitTemplate);
    }
}
