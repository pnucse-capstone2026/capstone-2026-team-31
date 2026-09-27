package com.ai_got_this.pnl_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class StructuredBalanceWarningLoggerTest {
    @Test
    void emitsTheStructuredWarnFieldsOnce() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(StructuredBalanceWarningLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(context);
        appender.start();
        logger.addAppender(appender);
        try {
            new StructuredBalanceWarningLogger().balanceBecameNonPositive("paper-account", new BigDecimal("1.00"),
                    BigDecimal.ZERO, "execution-1", "AAPL");

            assertEquals(1, appender.list.size());
            ILoggingEvent event = appender.list.getFirst();
            Map<String, Object> fields = event.getKeyValuePairs().stream()
                    .collect(Collectors.toMap(item -> item.key, item -> item.value));
            assertEquals(Level.WARN, event.getLevel());
            assertEquals("paper_balance_non_positive_transition", fields.get("eventType"));
            assertEquals("paper-account", fields.get("accountId"));
            assertEquals("execution-1", fields.get("executionId"));
            assertEquals("AAPL", fields.get("ticker"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
