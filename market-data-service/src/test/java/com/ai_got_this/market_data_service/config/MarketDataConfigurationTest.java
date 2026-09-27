package com.ai_got_this.market_data_service.config;

import com.ai_got_this.market_data_service.service.MarketDataComputationService;
import com.ai_got_this.market_data_service.service.WebSocketReconnectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.web.socket.client.WebSocketConnectionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class MarketDataConfigurationTest {
    @Test
    void rabbitConfigurationDeclaresDurableQueueDirectExchangeAndMatchingBinding() {
        RabbitMqConfig config = new RabbitMqConfig();
        Queue queue = config.marketDataQueue();
        DirectExchange exchange = config.marketDataExchange();
        Binding binding = config.bindingMarketDataQueue(queue, exchange);

        assertInstanceOf(JacksonJsonMessageConverter.class, config.jsonMessageConverter());
        assertEquals("market-data", queue.getName());
        assertTrue(queue.isDurable());
        assertEquals("market-data-exchange", exchange.getName());
        assertEquals("market-data", binding.getRoutingKey());
        assertEquals("market-data", binding.getDestination());
        assertEquals("market-data-exchange", binding.getExchange());
    }

    @Test
    void websocketConfigurationCarriesCredentialsAndEnablesAutoStartupWithoutConnecting() {
        AlpacaProperties properties = validProperties();
        WebSocketConnectionManager manager = new WebSocketClientConfig(properties).webConnectionManager(handlerFor(properties));

        assertNotNull(manager);
        assertTrue(manager.isAutoStartup());
        assertEquals("test-key", manager.getHeaders().getFirst("APCA-API-KEY-ID"));
        assertEquals("test-secret", manager.getHeaders().getFirst("APCA-API-SECRET-KEY"));
        assertTrue(!manager.isRunning());
    }

    @Test
    void websocketConfigurationRejectsBlankCredentialsAndMalformedSubscriptionPayload() {
        AlpacaProperties properties = validProperties();
        properties.setKey(" ");
        properties.setSecret("");
        properties.setSubscriptionMessage("not json");

        assertNull(new WebSocketClientConfig(properties).webConnectionManager(handlerFor(properties)));
    }

    @Test
    void websocketConfigurationRejectsInvalidUrlAndNonBarSubscription() {
        AlpacaProperties invalidUrl = validProperties();
        invalidUrl.setMarketStreamUrl("https://example.invalid/market");
        AlpacaProperties invalidSubscription = validProperties();
        invalidSubscription.setSubscriptionMessage("{\"action\":\"subscribe\",\"quotes\":[\"AAPL\"]}");

        assertNull(new WebSocketClientConfig(invalidUrl).webConnectionManager(handlerFor(invalidUrl)));
        assertNull(new WebSocketClientConfig(invalidSubscription).webConnectionManager(handlerFor(invalidSubscription)));
    }

    private AlpacaProperties validProperties() {
        AlpacaProperties properties = new AlpacaProperties();
        properties.setKey("test-key");
        properties.setSecret("test-secret");
        properties.setMarketStreamUrl("ws://example.invalid/market");
        properties.setSubscriptionMessage("{\"action\":\"subscribe\",\"bars\":[\"AAPL\"]}");
        return properties;
    }

    private AlpacaWebSocketHandler handlerFor(AlpacaProperties properties) {
        return new AlpacaWebSocketHandler(new ObjectMapper(), mock(MarketDataComputationService.class), properties,
                mock(WebSocketReconnectService.class));
    }
}
