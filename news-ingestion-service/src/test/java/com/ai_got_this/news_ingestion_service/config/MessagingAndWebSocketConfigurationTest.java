/*package com.ai_got_this.news_ingestion_service.config;

import com.ai_got_this.news_ingestion_service.service.NewsPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Clock;

class MessagingAndWebSocketConfigurationTest {
    @Test
    void rabbitConfigurationDeclaresDurableQueueDirectExchangeAndMatchingBinding() {
        RabbitMqConfig config = new RabbitMqConfig();
        Queue queue = config.financialNewsQueue();
        DirectExchange exchange = config.financialNewsExchange();
        Binding binding = config.bindingFinancialNewsQueue(queue, exchange);

        assertInstanceOf(JacksonJsonMessageConverter.class, config.jsonMessageConverter());
        assertEquals("financial-news", queue.getName());
        assertTrue(queue.isDurable());
        assertEquals("financial-news-exchange", exchange.getName());
        assertEquals("financial-news", binding.getRoutingKey());
        assertEquals("financial-news", binding.getDestination());
        assertEquals("financial-news-exchange", binding.getExchange());
    }

    @Test
    void websocketConfigurationCarriesConfiguredCredentialsAndEnablesAutoStartupWithoutConnecting() {
        AlpacaProperties properties = new AlpacaProperties();
        properties.setKey("test-key");
        properties.setSecret("test-secret");
        properties.setNewsStreamUrl("ws://example.invalid/news");
        properties.setSubscriptionMessage("{\"action\":\"subscribe\",\"news\":[\"*\"]}");
        WebSocketClientConfig config = new WebSocketClientConfig(properties);
        AlpacaWebSocketHandler handler = handlerFor(properties);

        WebSocketConnectionManager manager = config.webConnectionManager(handler);

        assertTrue(manager.isAutoStartup());
        assertEquals("test-key", manager.getHeaders().getFirst("APCA-API-KEY-ID"));
        assertEquals("test-secret", manager.getHeaders().getFirst("APCA-API-SECRET-KEY"));
        assertTrue(!manager.isRunning());
    }

    @Test
    void malformedSubscriptionConfigurationIsLoggedAndDoesNotCreateAConnectionManager() {
        AlpacaProperties properties = new AlpacaProperties();
        properties.setKey("test-key");
        properties.setSecret("test-secret");
        properties.setNewsStreamUrl("wss://example.invalid/news");
        properties.setSubscriptionMessage("not-json");
        WebSocketClientConfig config = new WebSocketClientConfig(properties);
        AlpacaWebSocketHandler handler = handlerFor(properties);

        assertNull(config.webConnectionManager(handler));
    }
}
*/