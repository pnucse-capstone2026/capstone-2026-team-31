package com.ai_got_this.order_execution_service.config;

import com.ai_got_this.order_execution_service.dto.OrderEvent;
import com.ai_got_this.order_execution_service.dto.TradeUpdateResponse;
import com.ai_got_this.order_execution_service.service.ExecutionFillEventFactory;
import com.ai_got_this.order_execution_service.service.PublisherService;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Translates the one configured account's broker updates into order events. */
public class AlpacaWebSocketHandler extends BinaryWebSocketHandler {
    private final ObjectMapper mapper; private final PublisherService publisher; private final AlpacaProperties alpaca; private final AtomicBoolean subscribed = new AtomicBoolean();
    public AlpacaWebSocketHandler(ObjectMapper mapper, PublisherService publisher, AlpacaProperties alpaca) { this.mapper = mapper; this.publisher = publisher; this.alpaca = alpaca; }
    @Override public void afterConnectionEstablished(WebSocketSession session) throws IOException { session.sendMessage(new TextMessage(mapper.writeValueAsString(Map.of("action", "auth", "key", alpaca.getKey(), "secret", alpaca.getSecret())))); }
    @Override protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try { ByteBuffer body = message.getPayload(); byte[] bytes = new byte[body.remaining()]; body.get(bytes); JsonNode root = mapper.readTree(bytes); if (root.isArray()) for (JsonNode item : root) process(session, item); else process(session, root); } catch (Exception ignored) { }
    }
    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) { try { process(session, mapper.readTree(message.getPayload())); } catch (Exception ignored) { } }
    private void process(WebSocketSession session, JsonNode message) throws Exception {
        String stream = message.path("stream").asText();
        if ("authorization".equals(stream) && ("authorized".equals(message.path("data").path("status").asText()) || "authenticated".equals(message.path("data").path("status").asText()))) { if (subscribed.compareAndSet(false, true)) session.sendMessage(new TextMessage(alpaca.getSubscriptionMessage())); return; }
        if ("trade_updates".equals(stream)) {
            OrderEvent orderEvent = new OrderEvent(mapper.treeToValue(message, TradeUpdateResponse.class));
            publisher.publishOrderEvent(orderEvent);
            ExecutionFillEventFactory.from(orderEvent).ifPresent(publisher::publishExecutionFill);
        }
    }
}
