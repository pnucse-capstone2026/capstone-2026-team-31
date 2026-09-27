package com.ai_got_this.market_data_service.dto;

/**
 * RabbitMQ contract consumed by the Price Prediction Service.
 * The symbol identifies the rolling series and the bar contains its features.
 */
public record MarketTick(String ticker, MarketData bar, String eventId, String timestamp) {
}
