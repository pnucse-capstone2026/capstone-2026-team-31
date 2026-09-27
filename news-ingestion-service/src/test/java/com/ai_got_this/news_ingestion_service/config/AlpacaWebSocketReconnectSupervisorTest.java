package com.ai_got_this.news_ingestion_service.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlpacaWebSocketReconnectSupervisorTest {
    private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");

    private FakeTaskScheduler scheduler;
    private RecordingConnectionManager connectionManager;
    private AlpacaWebSocketReconnectSupervisor supervisor;

    @BeforeEach
    void setUp() {
        scheduler = new FakeTaskScheduler();
        connectionManager = new RecordingConnectionManager();
        supervisor = new AlpacaWebSocketReconnectSupervisor(providerFor(connectionManager), scheduler,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void initialDisconnectedStateSchedulesAReconnectAfterFiveSeconds() {
        supervisor.ensureInitialConnection();

        assertEquals(List.of(NOW.plusSeconds(5)), scheduler.pendingExecutionTimes());
    }

    @Test
    void repeatedCloseAndTransportEventsScheduleOnlyOneReconnect() {
        supervisor.onConnectionClosed(org.springframework.web.socket.CloseStatus.SERVICE_RESTARTED);
        supervisor.onTransportError(new IllegalStateException("connection lost"));
        supervisor.onConnectionClosed(org.springframework.web.socket.CloseStatus.GOING_AWAY);

        assertEquals(1, scheduler.pendingTaskCount());
    }

    @Test
    void scheduledReconnectRestartsManagerAndVerifiesTheAsyncConnection() {
        connectionManager.start();
        supervisor.ensureInitialConnection();

        scheduler.runNext();

        assertEquals(2, connectionManager.openCount);
        assertEquals(List.of(NOW.plusSeconds(15)), scheduler.pendingExecutionTimes());
    }

    @Test
    void timedOutAttemptsUseCappedExponentialBackoff() {
        supervisor.ensureInitialConnection();
        scheduler.runNext();
        scheduler.runNext();

        assertEquals(List.of(NOW.plusSeconds(10)), scheduler.pendingExecutionTimes());

        scheduler.runNext();
        scheduler.runNext();
        assertEquals(List.of(NOW.plusSeconds(20)), scheduler.pendingExecutionTimes());
    }

    @Test
    void successfulConnectionResetsBackoffAndCancelsTheVerificationTask() {
        supervisor.ensureInitialConnection();
        scheduler.runNext();

        supervisor.onConnectionEstablished();

        assertEquals(0, scheduler.pendingTaskCount());
        supervisor.onConnectionClosed(org.springframework.web.socket.CloseStatus.GOING_AWAY);
        assertEquals(List.of(NOW.plusSeconds(5)), scheduler.pendingExecutionTimes());
    }

    @Test
    void shutdownCancelsPendingReconnectAndPreventsRestart() {
        supervisor.ensureInitialConnection();
        supervisor.stopReconnects();

        scheduler.runAll();

        assertEquals(0, connectionManager.openCount);
        assertEquals(0, connectionManager.closeCount);
        assertEquals(0, scheduler.pendingTaskCount());
    }

    @Test
    void missingConnectionManagerFailsSafelyWithoutSchedulingOrThrowing() {
        AlpacaWebSocketReconnectSupervisor unavailableSupervisor = new AlpacaWebSocketReconnectSupervisor(
                providerFor(null), scheduler, Clock.fixed(NOW, ZoneOffset.UTC));

        assertDoesNotThrow(unavailableSupervisor::ensureInitialConnection);
        assertEquals(0, scheduler.pendingTaskCount());
    }

    @Test
    void immediateSuccessfulReconnectDoesNotScheduleAnUnnecessaryTimeoutCheck() {
        connectionManager.connectOnOpen = true;
        supervisor.ensureInitialConnection();

        scheduler.runNext();

        assertTrue(connectionManager.isConnected());
        assertEquals(0, scheduler.pendingTaskCount());
    }

    private ObjectProvider<WebSocketConnectionManager> providerFor(WebSocketConnectionManager manager) {
        return new ObjectProvider<>() {
            @Override
            public WebSocketConnectionManager getIfAvailable() {
                return manager;
            }
        };
    }

    private static final class RecordingConnectionManager extends WebSocketConnectionManager {
        private boolean connected;
        private boolean connectOnOpen;
        private int openCount;
        private int closeCount;

        private RecordingConnectionManager() {
            super((WebSocketClient) null, new TextWebSocketHandler(), "ws://example.invalid/news");
        }

        @Override
        protected void openConnection() {
            openCount++;
            connected = connectOnOpen;
        }

        @Override
        protected void closeConnection() {
            closeCount++;
            connected = false;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }
    }

    private static final class FakeTaskScheduler implements TaskScheduler {
        private final List<FakeScheduledFuture> tasks = new ArrayList<>();

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            FakeScheduledFuture future = new FakeScheduledFuture(task, startTime);
            tasks.add(future);
            return future;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
            throw new UnsupportedOperationException();
        }

        void runNext() {
            tasks.stream()
                    .filter(task -> !task.isDone())
                    .min(Comparator.comparing(task -> task.startTime))
                    .orElseThrow()
                    .run();
        }

        void runAll() {
            while (pendingTaskCount() > 0) {
                runNext();
            }
        }

        int pendingTaskCount() {
            return (int) tasks.stream().filter(task -> !task.isDone()).count();
        }

        List<Instant> pendingExecutionTimes() {
            return tasks.stream()
                    .filter(task -> !task.isDone())
                    .map(task -> task.startTime)
                    .sorted()
                    .toList();
        }
    }

    private static final class FakeScheduledFuture implements ScheduledFuture<Object> {
        private final Runnable task;
        private final Instant startTime;
        private boolean cancelled;
        private boolean done;

        private FakeScheduledFuture(Runnable task, Instant startTime) {
            this.task = task;
            this.startTime = startTime;
        }

        void run() {
            if (!cancelled) {
                task.run();
            }
            done = true;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(Duration.between(NOW, startTime));
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled || done;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
