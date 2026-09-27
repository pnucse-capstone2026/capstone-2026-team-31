package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.config.AlpacaProperties;
import com.ai_got_this.order_execution_service.config.AlpacaWebSocketHandler;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

/** Maintains one trade-update stream for the configured account. */
@Service
public class TradeUpdateConnectionRegistry {
    private final WebSocketClient client; private final ObjectMapper mapper; private final PublisherService publisher; private final AlpacaProperties alpaca;
    private CompletableFuture<?> connection;
    public TradeUpdateConnectionRegistry(WebSocketClient client, ObjectMapper mapper, PublisherService publisher, AlpacaProperties alpaca) { this.client = client; this.mapper = mapper; this.publisher = publisher; this.alpaca = alpaca; }
    @EventListener(ApplicationReadyEvent.class) public void connect() { connection = client.execute(new AlpacaWebSocketHandler(mapper, publisher, alpaca), alpaca.getStreamUrl()); }
    @PreDestroy public void close() { if (connection != null) connection.cancel(true); }
}
