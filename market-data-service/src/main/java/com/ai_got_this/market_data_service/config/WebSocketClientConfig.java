package com.ai_got_this.market_data_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;

@Configuration
@Slf4j
public class WebSocketClientConfig {
    private final AlpacaProperties alpacaProperties;

    public WebSocketClientConfig(AlpacaProperties alpacaProperties) {
        this.alpacaProperties = alpacaProperties;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public WebSocketConnectionManager webConnectionManager(AlpacaWebSocketHandler alpacaWebSocketHandler) {
        String apiKeyId = alpacaProperties.getKey();
        String apiSecretKey = alpacaProperties.getSecret();
        String marketStreamUrl = alpacaProperties.getMarketStreamUrl();
        if (!isValidConfiguration(apiKeyId, apiSecretKey, marketStreamUrl, alpacaProperties.getSubscriptionMessage())) {
            return null;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add("APCA-API-KEY-ID", apiKeyId);
        headers.add("APCA-API-SECRET-KEY", apiSecretKey);

        WebSocketConnectionManager manager = new WebSocketConnectionManager(
                new StandardWebSocketClient(),
                alpacaWebSocketHandler,
                marketStreamUrl
        );
        manager.setAutoStartup(true);
        manager.setHeaders(headers);
        return manager;
    }

    private boolean isValidConfiguration(String apiKeyId, String apiSecretKey, String marketStreamUrl, String subscriptionMessage) {
        if (apiKeyId == null || apiKeyId.isBlank() || apiSecretKey == null || apiSecretKey.isBlank()) {
            log.error("Alpaca WebSocket credentials are not configured");
            return false;
        }

        if (marketStreamUrl == null || marketStreamUrl.isBlank()) {
            log.error("Alpaca WebSocket market stream URL is not configured");
            return false;
        }

        try {
            String scheme = URI.create(marketStreamUrl).getScheme();
            if (!"ws".equals(scheme) && !"wss".equals(scheme)) {
                log.error("Alpaca WebSocket market stream URL must use ws or wss");
                return false;
            }
        }
        catch (IllegalArgumentException e) {
            log.error("Alpaca WebSocket market stream URL is invalid");
            return false;
        }

        if (subscriptionMessage == null || subscriptionMessage.isBlank()) {
            log.error("Alpaca WebSocket subscription message is not configured");
            return false;
        }

        try {
            JsonNode subscription = new ObjectMapper().readTree(subscriptionMessage);
            if (!subscription.isObject()) {
                log.error("Alpaca WebSocket subscription message must be a JSON object");
                return false;
            }
            if (!"subscribe".equals(subscription.path("action").asText()) || !subscription.path("bars").isArray()) {
                log.error("Alpaca WebSocket subscription message must subscribe to bars");
                return false;
            }
        }
        catch (Exception e) {
            log.error("Alpaca WebSocket subscription message is invalid");
            return false;
        }

        return true;
    }
}
