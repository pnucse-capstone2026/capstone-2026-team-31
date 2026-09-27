package com.ai_got_this.order_execution_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@Configuration
public class WebSocketClientConfig {
    private AlpacaProperties alpacaProperties;

    public WebSocketClientConfig(AlpacaProperties alpacaProperties, ApplicationConfigurationValidator configurationValidator) {
        this.alpacaProperties = alpacaProperties;
    }

    @Bean
    public WebSocketClient alpacaWebSocketClient() {
        return new StandardWebSocketClient();
    }
}
