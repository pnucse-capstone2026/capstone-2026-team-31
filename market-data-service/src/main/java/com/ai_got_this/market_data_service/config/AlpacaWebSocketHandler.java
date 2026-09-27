package com.ai_got_this.market_data_service.config;

import com.ai_got_this.market_data_service.dto.AlpacaBarMessage;
import com.ai_got_this.market_data_service.dto.AlpacaErrorMessage;
import com.ai_got_this.market_data_service.dto.AlpacaMessage;
import com.ai_got_this.market_data_service.dto.AlpacaSubscriptionMessage;
import com.ai_got_this.market_data_service.dto.AlpacaSuccessMessage;
import com.ai_got_this.market_data_service.dto.AlpacaUnknownMessage;
import com.ai_got_this.market_data_service.service.MarketDataComputationService;
import com.ai_got_this.market_data_service.service.WebSocketReconnectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Component
@Slf4j
public class AlpacaWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final MarketDataComputationService marketDataComputationService;
    private final AlpacaProperties alpacaProperties;
    private final WebSocketReconnectService webSocketReconnectService;
    private final StringBuilder messageBuffer = new StringBuilder();

    public AlpacaWebSocketHandler(
            ObjectMapper objectMapper,
            MarketDataComputationService marketDataComputationService,
            AlpacaProperties alpacaProperties,
            WebSocketReconnectService webSocketReconnectService
    ) {
        this.objectMapper = objectMapper;
        this.marketDataComputationService = marketDataComputationService;
        this.alpacaProperties = alpacaProperties;
        this.webSocketReconnectService = webSocketReconnectService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String subscriptionMessage = alpacaProperties.getSubscriptionMessage();
        log.info("Connected to Alpaca WebSocket server");
        webSocketReconnectService.onSuccessfulConnection();
        log.info("Subscribing to market data stream...");
        session.sendMessage(new TextMessage(subscriptionMessage));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.warn("WebSocket connection closed with status: {}", status);
        webSocketReconnectService.disconnect(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return true;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        messageBuffer.append(message.getPayload());
        if (message.isLast()) {
            String completeMessage = messageBuffer.toString();
            messageBuffer.setLength(0);

            try {
                AlpacaMessage[] alpacaMessages = objectMapper.readValue(completeMessage, AlpacaMessage[].class);
                for (AlpacaMessage alpacaMessage : alpacaMessages) {
                    processMessage(alpacaMessage);
                }
            }
            catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }

    private void processMessage(AlpacaMessage alpacaMessage) {
        try {
            if (alpacaMessage instanceof AlpacaErrorMessage errorMessage) {
                Integer errorCode = errorMessage.getCode();
                String errorMsg = errorMessage.getMsg();

                if (errorCode != null && (errorCode == 401 || errorCode == 402 || errorCode == 404)) {
                    log.error("Failed to authenticate with Alpaca WebSocket server: code: {} msg: {}", errorCode, errorMsg);
                }
                else {
                    log.error("Received error message from Alpaca's websocket: code: {} msg: {}", errorCode, errorMsg);
                }
            }

            if (alpacaMessage instanceof AlpacaSuccessMessage successMessage
                    && "authenticated".equals(successMessage.getMsg())) {
                log.info("Successfully authenticated with Alpaca WebSocket server");
            }

            if (alpacaMessage instanceof AlpacaSubscriptionMessage subscriptionMessage) {
                log.info("Alpaca WebSocket subscription acknowledged for bars: {}", subscriptionMessage.getBars());
            }

            if (alpacaMessage instanceof AlpacaBarMessage barMessage) {
                marketDataComputationService.processBar(barMessage);
            }

            if (alpacaMessage instanceof AlpacaUnknownMessage unknownMessage) {
                log.warn("Received unknown message from Alpaca's websocket: type: {} {}", unknownMessage.getType(), unknownMessage);
            }
        }
        catch (Exception e) {
            log.error(e.getMessage());
        }
    }
}
