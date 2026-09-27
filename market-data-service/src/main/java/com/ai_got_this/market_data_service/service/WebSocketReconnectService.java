package com.ai_got_this.market_data_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketConnectionManager;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class WebSocketReconnectService {
    private static final long SESSION_CLOSE_TIMEOUT = TimeUnit.SECONDS.toNanos(30);
    private static final long CONNECTION_TIMEOUT = TimeUnit.SECONDS.toNanos(5);
    private static final int MAX_CONSECUTIVE_ATTEMPTS = 5;

    private final ObjectProvider<WebSocketConnectionManager> connectionManagerProvider;
    private final ScheduledExecutorService reconnectExecutor;

    private int consecutiveAttempts;
    private State state = State.DISCONNECTED;

    private enum State {
        DISCONNECTING,
        DISCONNECTED,
        WAITING_RETRY,
        CONNECTING,
        CONNECTED,
        TERMINATED
    }

    public WebSocketReconnectService(
            ObjectProvider<WebSocketConnectionManager> connectionManagerProvider,
            @Qualifier("alpacaReconnectExecutor") ScheduledExecutorService reconnectExecutor
    ) {
        this.connectionManagerProvider = connectionManagerProvider;
        this.reconnectExecutor = reconnectExecutor;
    }

    public void disconnect(WebSocketSession session) {
        reconnectExecutor.execute(() -> disconnectOnExecutorThread(session));
    }

    public void onSuccessfulConnection() {
        reconnectExecutor.execute(this::onSuccessfulConnectionOnExecutorThread);
    }

    private void disconnectOnExecutorThread(WebSocketSession session) {
        if (state != State.CONNECTED) {
            return;
        }

        state = State.DISCONNECTING;
        long waitStartedAt = System.nanoTime();
        while (session.isOpen()) {
            if (System.nanoTime() - waitStartedAt >= SESSION_CLOSE_TIMEOUT) {
                log.warn("Timed out waiting 30 seconds for the WebSocket session to close");
                state = State.DISCONNECTED;
                scheduleReconnectOnExecutorThread();
                return;
            }
        }

        scheduleReconnectOnExecutorThread();
    }

    private void scheduleReconnectOnExecutorThread() {
        if (consecutiveAttempts > MAX_CONSECUTIVE_ATTEMPTS) {
            log.warn("Too many consecutive connection attempts. No further retries.");
            state = State.TERMINATED;
            return;
        }

        long backoffDelayMillis = calculateBackoffDelay(consecutiveAttempts++);
        state = State.WAITING_RETRY;
        reconnectExecutor.schedule(this::connectOnExecutorThread, backoffDelayMillis, TimeUnit.MILLISECONDS);
    }

    private long calculateBackoffDelay(int connectionTrialCount) {
        int cappedTrialCount = Math.min(Math.max(connectionTrialCount, 0), 4);
        long delaySeconds = Math.min(5L * (1L << cappedTrialCount), 60L);
        return TimeUnit.SECONDS.toMillis(delaySeconds);
    }

    private void connectOnExecutorThread() {
        if (state != State.DISCONNECTED && state != State.WAITING_RETRY) {
            return;
        }

        state = State.CONNECTING;
        WebSocketConnectionManager connectionManager = connectionManagerProvider.getIfAvailable();
        if (connectionManager == null) {
            log.error("Alpaca WebSocket connection manager is not available; connection is being terminated");
            state = State.TERMINATED;
            return;
        }

        try {
            connectionManager.stop();
            connectionManager.start();
            reconnectExecutor.schedule(
                    () -> validateConnectionOnExecutorThread(connectionManager),
                    CONNECTION_TIMEOUT,
                    TimeUnit.NANOSECONDS
            );
        }
        catch (Exception e) {
            log.error("An error occurred while opening WebSocket connection", e);
            state = State.DISCONNECTED;
            scheduleReconnectOnExecutorThread();
        }
    }

    private void validateConnectionOnExecutorThread(WebSocketConnectionManager connectionManager) {
        if (connectionManager.isConnected()) {
            return;
        }

        log.warn("Connection to Alpaca WebSocket server failed");
        state = State.DISCONNECTED;
        scheduleReconnectOnExecutorThread();
    }

    private void onSuccessfulConnectionOnExecutorThread() {
        consecutiveAttempts = 0;
        state = State.CONNECTED;
    }
}
