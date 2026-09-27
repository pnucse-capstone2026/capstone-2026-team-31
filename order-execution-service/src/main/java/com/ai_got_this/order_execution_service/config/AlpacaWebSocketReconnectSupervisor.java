package com.ai_got_this.order_execution_service.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.client.WebSocketConnectionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

@Slf4j
public class AlpacaWebSocketReconnectSupervisor {
    private static final Duration INITIAL_RECONNECT_DELAY = Duration.ofSeconds(5);
    private static final Duration MAX_RECONNECT_DELAY = Duration.ofMinutes(1);
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(15);

    private final ObjectProvider<WebSocketConnectionManager> connectionManagerProvider;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final Object monitor = new Object();

    private ScheduledFuture<?> reconnectTask;
    private ScheduledFuture<?> connectionCheckTask;
    private boolean applicationStopping;
    private boolean connectionAttemptInProgress;
    private int consecutiveFailures;

    public AlpacaWebSocketReconnectSupervisor(
            ObjectProvider<WebSocketConnectionManager> connectionManagerProvider,
            @Qualifier("alpacaReconnectScheduler") TaskScheduler scheduler,
            Clock clock
    ) {
        this.connectionManagerProvider = connectionManagerProvider;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureInitialConnection() {
        scheduleReconnectIfNeeded();
    }

    public void onConnectionEstablished() {
        synchronized (monitor) {
            connectionAttemptInProgress = false;
            consecutiveFailures = 0;
            cancel(connectionCheckTask);
            connectionCheckTask = null;
        }
    }

    public void onConnectionClosed(CloseStatus status) {
        log.warn("WebSocket connection closed: {}", status);
        scheduleReconnectIfNeeded();
    }

    public void onTransportError(Throwable exception) {
        log.error("WebSocket transport error: {}", exception.getMessage());
        scheduleReconnectIfNeeded();
    }

    @EventListener(ContextClosedEvent.class)
    public void stopReconnects() {
        synchronized (monitor) {
            applicationStopping = true;
            cancel(reconnectTask);
            cancel(connectionCheckTask);
            reconnectTask = null;
            connectionCheckTask = null;
        }
    }

    @PreDestroy
    public void destroy() {
        stopReconnects();
    }

    private void scheduleReconnectIfNeeded() {
        synchronized (monitor) {
            if (applicationStopping || connectionAttemptInProgress || isReconnectScheduled()) {
                return;
            }

            WebSocketConnectionManager manager = connectionManagerProvider.getIfAvailable();
            if (manager == null) {
                log.error("Alpaca WebSocket connection manager is not available");
                return;
            }
            if (manager.isConnected()) {
                consecutiveFailures = 0;
                return;
            }

            Duration delay = reconnectDelay();
            log.info("Attempting to reconnect to Alpaca WebSocket server in {} seconds...", delay.toSeconds());
            reconnectTask = scheduler.schedule(this::reconnect, Instant.now(clock).plus(delay));
        }
    }

    private void reconnect() {
        WebSocketConnectionManager manager;
        synchronized (monitor) {
            reconnectTask = null;
            if (applicationStopping) {
                return;
            }
            manager = connectionManagerProvider.getIfAvailable();
            if (manager == null) {
                log.error("Alpaca WebSocket connection manager is not available");
                return;
            }
            if (manager.isConnected()) {
                consecutiveFailures = 0;
                return;
            }
            connectionAttemptInProgress = true;
        }

        try {
            manager.stop();
            manager.start();
            if (!manager.isConnected()) {
                synchronized (monitor) {
                    connectionCheckTask = scheduler.schedule(this::verifyConnection, Instant.now(clock).plus(CONNECTION_TIMEOUT));
                }
            }
        }
        catch (Exception e) {
            log.error("Failed to reconnect to Alpaca WebSocket server: {}", e.getMessage());
            connectionAttemptFailed();
        }
    }

    private void verifyConnection() {
        WebSocketConnectionManager manager = connectionManagerProvider.getIfAvailable();
        if (manager != null && manager.isConnected()) {
            onConnectionEstablished();
            return;
        }
        log.warn("Alpaca WebSocket connection attempt timed out");
        connectionAttemptFailed();
    }

    private void connectionAttemptFailed() {
        synchronized (monitor) {
            connectionAttemptInProgress = false;
            connectionCheckTask = null;
            consecutiveFailures++;
        }
        scheduleReconnectIfNeeded();
    }

    private Duration reconnectDelay() {
        long multiplier = 1L << Math.min(consecutiveFailures, 4);
        long seconds = Math.min(INITIAL_RECONNECT_DELAY.toSeconds() * multiplier, MAX_RECONNECT_DELAY.toSeconds());
        return Duration.ofSeconds(seconds);
    }

    private boolean isReconnectScheduled() {
        return reconnectTask != null && !reconnectTask.isDone();
    }

    private void cancel(ScheduledFuture<?> task) {
        if (task != null) {
            task.cancel(false);
        }
    }
}
