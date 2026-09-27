package com.ai_got_this.market_data_service.config;

import com.ai_got_this.market_data_service.dto.AlpacaBarMessage;
import com.ai_got_this.market_data_service.service.MarketDataComputationService;
import com.ai_got_this.market_data_service.service.WebSocketReconnectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AlpacaWebSocketHandlerTest {
    private MarketDataComputationService computationService;
    private WebSocketReconnectService reconnectService;
    private AlpacaWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        computationService = mock(MarketDataComputationService.class);
        reconnectService = mock(WebSocketReconnectService.class);
        AlpacaProperties properties = new AlpacaProperties();
        properties.setSubscriptionMessage("{\"action\":\"subscribe\",\"bars\":[\"AAPL\"]}");
        handler = new AlpacaWebSocketHandler(new ObjectMapper(), computationService, properties, reconnectService);
    }

    @Test
    void establishedConnectionMarksReconnectSuccessfulAndSendsConfiguredSubscription() throws IOException {
        WebSocketSession session = mock(WebSocketSession.class);

        handler.afterConnectionEstablished(session);

        verify(reconnectService).onSuccessfulConnection();
        ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(message.capture());
        assertEquals("{\"action\":\"subscribe\",\"bars\":[\"AAPL\"]}", message.getValue().getPayload());
    }

    @Test
    void subscriptionSendFailureIsPropagatedToConnectionManager() throws IOException {
        WebSocketSession session = mock(WebSocketSession.class);
        doThrow(new IOException("socket closed")).when(session).sendMessage(any(TextMessage.class));

        org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> handler.afterConnectionEstablished(session));
        verify(reconnectService).onSuccessfulConnection();
    }

    @Test
    void closedConnectionIsDelegatedToReconnectService() {
        WebSocketSession session = mock(WebSocketSession.class);

        handler.afterConnectionClosed(session, CloseStatus.GOING_AWAY);

        verify(reconnectService).disconnect(session);
    }

    @Test
    void partialMessagesAreBufferedUntilTheLastFragment() {
        WebSocketSession session = mock(WebSocketSession.class);

        assertDoesNotThrow(() -> handler.handleMessage(session, new TextMessage("[{\"T\":\"b\",\"S\":\"AAPL\",", false)));
        verifyNoInteractions(computationService);
        assertDoesNotThrow(() -> handler.handleMessage(session, new TextMessage("\"o\":1,\"h\":2,\"l\":1,\"c\":2,\"v\":1}]", true)));

        verify(computationService).processBar(any(AlpacaBarMessage.class));
    }

    @Test
    void validBarIsForwardedToTheComputationService() {
        deliver("[{\"T\":\"b\",\"S\":\"AAPL\",\"o\":100.25,\"h\":105.5,\"l\":99.75,\"c\":104.0,\"v\":123456,\"n\":17,\"t\":\"2026-08-17T00:00:00Z\"}]");

        ArgumentCaptor<AlpacaBarMessage> bar = ArgumentCaptor.forClass(AlpacaBarMessage.class);
        verify(computationService).processBar(bar.capture());
        assertEquals("AAPL", bar.getValue().getSymbol());
        assertEquals(104.0, bar.getValue().getClose());
        assertEquals(123456L, bar.getValue().getVolume());
    }

    @Test
    void unknownAndProviderErrorMessagesAreNeverForwardedForComputation() {
        deliver("[{\"T\":\"error\",\"code\":401,\"msg\":\"rejected\"}]");
        deliver("[{\"T\":\"success\",\"msg\":\"authenticated\"}]");
        deliver("[{\"T\":\"subscription\",\"bars\":[\"AAPL\"],\"quotes\":[\"AAPL\"]}]");

        verifyNoInteractions(computationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-json", "{}", "[]", "[null]", "{\"T\":\"b\"}"})
    void malformedOrUnexpectedPayloadsAreContainedAndNeverForwarded(String payload) {
        assertDoesNotThrow(() -> handler.handleMessage(mock(WebSocketSession.class), new TextMessage(payload)));

        verifyNoInteractions(computationService);
    }

    @Test
    void computationFailureIsContainedSoOneBadEventDoesNotEscapeWebSocketCallback() {
        doThrow(new IllegalStateException("computation failed")).when(computationService).processBar(any(AlpacaBarMessage.class));

        deliver("[{\"T\":\"b\",\"S\":\"AAPL\",\"o\":1,\"h\":2,\"l\":1,\"c\":2,\"v\":1}]");
        verify(computationService).processBar(any(AlpacaBarMessage.class));
    }

    @Test
    void everyBarInProviderBatchIsForwardedInOrder() {
        deliver("[{\"T\":\"b\",\"S\":\"AAPL\",\"o\":1,\"h\":2,\"l\":1,\"c\":2,\"v\":1},{\"T\":\"b\",\"S\":\"MSFT\",\"o\":3,\"h\":4,\"l\":3,\"c\":4,\"v\":2}]");

        ArgumentCaptor<AlpacaBarMessage> bars = ArgumentCaptor.forClass(AlpacaBarMessage.class);
        verify(computationService, org.mockito.Mockito.times(2)).processBar(bars.capture());
        assertEquals(java.util.List.of("AAPL", "MSFT"), bars.getAllValues().stream().map(AlpacaBarMessage::getSymbol).toList());
    }

    private void deliver(String payload) {
        assertDoesNotThrow(() -> handler.handleMessage(mock(WebSocketSession.class), new TextMessage(payload)));
    }
}
