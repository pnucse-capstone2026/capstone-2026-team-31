package com.ai_got_this.news_ingestion_service.config;

import com.ai_got_this.news_ingestion_service.dto.NewsHeadline;
import com.ai_got_this.news_ingestion_service.service.NewsPublisher;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.slf4j.LoggerFactory;

class AlpacaWebSocketHandlerTest {
    private RecordingPublisher newsPublisher;
    private RecordingReconnectSupervisor reconnectSupervisor;
    private AlpacaProperties properties;
    private TestableHandler handler;

    @BeforeEach
    void setUp() {
        newsPublisher = new RecordingPublisher();
        properties = new AlpacaProperties();
        properties.setSubscriptionMessage("{\"action\":\"subscribe\",\"news\":[\"*\"]}");
        reconnectSupervisor = new RecordingReconnectSupervisor();
        handler = new TestableHandler(new ObjectMapper(), newsPublisher, properties, reconnectSupervisor);
    }

    @Test
    void connectionSendsConfiguredSubscriptionPayloadExactlyOnce() throws IOException {
        SessionRecorder session = new SessionRecorder();
        handler.connect(session.session());
        assertEquals(List.of(properties.getSubscriptionMessage()), session.sentPayloads);
    }

    @Test
    void connectionPropagatesSubscriptionSendFailure() {
        SessionRecorder session = new SessionRecorder();
        session.sendFailure = new IOException("network unavailable");
        assertThrows(IOException.class, () -> handler.connect(session.session()));
    }

    @Test
    void connectionLifecycleEventsAreDelegatedToTheReconnectSupervisor() throws IOException {
        handler.connect(new SessionRecorder().session());
        handler.close(CloseStatus.GOING_AWAY);
        handler.transportError(new IllegalStateException("connection lost"));

        assertEquals(1, reconnectSupervisor.establishedCount);
        assertEquals(List.of(CloseStatus.GOING_AWAY), reconnectSupervisor.closeStatuses);
        assertEquals(List.of("connection lost"), reconnectSupervisor.transportErrors);
    }

    @Test
    void validNewsPublishesOneHeadlinePerSymbolWithExactBrokerContract() {
        handler.receive("[{\"T\":\"n\",\"id\":\"evt-1\",\"headline\":\"삼성전자 📈 beats estimates\",\"symbols\":[\"005930\",\"AAPL\"]}]");
        assertEquals(List.of(
                new Publication("financial-news-exchange", NewsHeadline.builder().ticker("005930").headline("삼성전자 📈 beats estimates").build(), "financial-news"),
                new Publication("financial-news-exchange", NewsHeadline.builder().ticker("AAPL").headline("삼성전자 📈 beats estimates").build(), "financial-news")), newsPublisher.publications);
    }

    @Test
    void everyArrayElementIsProcessedInOrder() {
        handler.receive("[{\"T\":\"n\",\"headline\":\"first\",\"symbols\":[\"AAA\"]},{\"T\":\"n\",\"headline\":\"second\",\"symbols\":[\"BBB\"]}]");
        assertEquals(List.of(
                new Publication("financial-news-exchange", NewsHeadline.builder().ticker("AAA").headline("first").build(), "financial-news"),
                new Publication("financial-news-exchange", NewsHeadline.builder().ticker("BBB").headline("second").build(), "financial-news")), newsPublisher.publications);
    }

    @Test
    void duplicateProviderMessagesArePublishedAgainBecauseNoDeduplicationIsImplemented() {
        String payload = "[{\"T\":\"n\",\"id\":\"same-provider-id\",\"headline\":\"duplicate\",\"symbols\":[\"AAPL\"]}]";
        handler.receive(payload);
        handler.receive(payload);
        assertEquals(2, newsPublisher.publications.size());
    }

    @Test
    void headlineAndSymbolAreForwardedWithoutNormalizationOrValidation() {
        handler.receive("[{\"T\":\"n\",\"headline\":\"  headline with spaces  \",\"symbols\":[\" aapl \"]}]");
        assertEquals(" aapl ", newsPublisher.publications.getFirst().news().getTicker());
        assertEquals("  headline with spaces  ", newsPublisher.publications.getFirst().news().getHeadline());
    }

    @Test
    void unknownFieldsOnNewsAreIgnoredAndKnownFieldsArePublished() {
        handler.receive("[{\"T\":\"n\",\"headline\":\"new provider field\",\"symbols\":[\"AAPL\"],\"new_field\":true}]");
        assertEquals(List.of(new Publication("financial-news-exchange", NewsHeadline.builder().ticker("AAPL").headline("new provider field").build(), "financial-news")), newsPublisher.publications);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-json", "{}", "null", "[]", "[{\"T\":\"n\",\"headline\":\"missing symbols\"}]", "[{\"T\":\"n\",\"headline\":\"symbols wrong type\",\"symbols\":\"AAPL\"}]"})
    void malformedOrIncompletePayloadsFailSafelyWithoutPublishing(String payload) {
        handler.receive(payload);
        assertEquals(List.of(), newsPublisher.publications);
    }

    @Test
    void emptySymbolListAndNonNewsProviderMessagesProduceNoPublication() {
        handler.receive("[{\"T\":\"n\",\"headline\":\"no symbols\",\"symbols\":[]}]");
        handler.receive("[{\"T\":\"error\",\"code\":401,\"msg\":\"unauthorized\"}]");
        handler.receive("[{\"T\":\"heartbeat\",\"sequence\":1}]");
        assertEquals(List.of(), newsPublisher.publications);
    }

    @Test
    void authenticatedAcknowledgementIsLoggedAtInfoLevel() {
        ListAppender<ILoggingEvent> logs = attachLogs();
        try {
            handler.receive("[{\"T\":\"success\",\"msg\":\"authenticated\"}]");
            assertTrue(logs.list.stream().anyMatch(event -> event.getLevel().toString().equals("INFO")
                    && event.getFormattedMessage().equals("Successfully authenticated with Alpaca WebSocket server")));
        }
        finally {
            detachLogs(logs);
        }
    }

    @Test
    void authenticationFailureAcknowledgementIsLoggedAtErrorLevel() {
        ListAppender<ILoggingEvent> logs = attachLogs();
        try {
            handler.receive("[{\"T\":\"error\",\"code\":402,\"msg\":\"auth failed\"}]");
            assertTrue(logs.list.stream().anyMatch(event -> event.getLevel().toString().equals("ERROR")
                    && event.getFormattedMessage().equals("Failed to authenticate with Alpaca WebSocket server: code: 402 msg: auth failed")));
        }
        finally {
            detachLogs(logs);
        }
    }

    @Test
    void publisherFailureDoesNotPreventASeparateLaterCallbackFromPublishing() {
        newsPublisher.failNext = true;
        handler.receive("[{\"T\":\"n\",\"headline\":\"first\",\"symbols\":[\"AAA\"]}]");
        handler.receive("[{\"T\":\"n\",\"headline\":\"second\",\"symbols\":[\"BBB\"]}]");
        assertEquals(List.of(new Publication("financial-news-exchange", NewsHeadline.builder().ticker("BBB").headline("second").build(), "financial-news")), newsPublisher.publications);
    }

    @Test
    void publisherFailureDoesNotPreventLaterMessagesInTheSameFrameFromPublishing() {
        newsPublisher.failNext = true;

        handler.receive("[{\"T\":\"n\",\"headline\":\"first\",\"symbols\":[\"AAA\"]},{\"T\":\"n\",\"headline\":\"second\",\"symbols\":[\"BBB\"]}]");

        assertEquals(List.of(new Publication("financial-news-exchange", NewsHeadline.builder().ticker("BBB").headline("second").build(), "financial-news")), newsPublisher.publications);
    }

    private record Publication(String exchange, NewsHeadline news, String routingKey) { }

    private ListAppender<ILoggingEvent> attachLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(AlpacaWebSocketHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachLogs(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(AlpacaWebSocketHandler.class)).detachAppender(appender);
    }

    private ObjectProvider<org.springframework.web.socket.client.WebSocketConnectionManager> unavailableConnectionManager() {
        return new ObjectProvider<>() {
            @Override
            public org.springframework.web.socket.client.WebSocketConnectionManager getIfAvailable() {
                return null;
            }
        };
    }

    private static final class RecordingPublisher extends NewsPublisher {
        private final List<Publication> publications = new ArrayList<>();
        private boolean failNext;
        private RecordingPublisher() { super(null); }
        @Override public void publishNews(String exchange, NewsHeadline news, String routingKey) {
            if (failNext) { failNext = false; throw new RuntimeException("broker unavailable"); }
            publications.add(new Publication(exchange, news, routingKey));
        }
    }

    private static final class RecordingReconnectSupervisor extends AlpacaWebSocketReconnectSupervisor {
        private int establishedCount;
        private final List<CloseStatus> closeStatuses = new ArrayList<>();
        private final List<String> transportErrors = new ArrayList<>();

        private RecordingReconnectSupervisor() {
            super(new ObjectProvider<>() {
                @Override
                public org.springframework.web.socket.client.WebSocketConnectionManager getIfAvailable() {
                    return null;
                }
            }, new ConcurrentTaskScheduler(), Clock.systemUTC());
        }

        @Override
        public void onConnectionEstablished() {
            establishedCount++;
        }

        @Override
        public void onConnectionClosed(CloseStatus status) {
            closeStatuses.add(status);
        }

        @Override
        public void onTransportError(Throwable exception) {
            transportErrors.add(exception.getMessage());
        }
    }

    private static final class SessionRecorder {
        private final List<String> sentPayloads = new ArrayList<>();
        private IOException sendFailure;
        private WebSocketSession session() {
            return (WebSocketSession) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{WebSocketSession.class}, (proxy, method, args) -> {
                if (method.getName().equals("sendMessage")) {
                    if (sendFailure != null) throw sendFailure;
                    sentPayloads.add(((TextMessage) args[0]).getPayload());
                }
                return defaultValue(method.getReturnType());
            });
        }
        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == double.class) return 0D;
            if (type == float.class) return 0F;
            if (type == short.class) return (short) 0;
            if (type == byte.class) return (byte) 0;
            if (type == char.class) return '\0';
            return null;
        }
    }

    private static final class TestableHandler extends AlpacaWebSocketHandler {
        private TestableHandler(ObjectMapper objectMapper, NewsPublisher newsPublisher, AlpacaProperties properties, AlpacaWebSocketReconnectSupervisor reconnectSupervisor) { super(objectMapper, newsPublisher, properties, reconnectSupervisor); }
        void connect(WebSocketSession session) throws IOException { afterConnectionEstablished(session); }
        void receive(String payload) { handleTextMessage(null, new TextMessage(payload)); }
        void close(CloseStatus status) { afterConnectionClosed(null, status); }
        void transportError(Throwable exception) { handleTransportError(null, exception); }
    }
}
