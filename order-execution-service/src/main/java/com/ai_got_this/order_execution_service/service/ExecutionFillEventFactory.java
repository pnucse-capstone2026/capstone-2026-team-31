package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.dto.ExecutionFillEvent;
import com.ai_got_this.order_execution_service.dto.OrderEvent;
import com.ai_got_this.order_execution_service.dto.TradeUpdateOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/** Selects only discrete broker fill notifications; order status updates are not executions. */
public final class ExecutionFillEventFactory {
    private ExecutionFillEventFactory() {
    }

    public static Optional<ExecutionFillEvent> from(OrderEvent source) {
        if (source == null || !isFillEvent(source.getEvent()) || source.getOrder() == null) {
            return Optional.empty();
        }

        TradeUpdateOrder order = source.getOrder();
        String executionId = firstNonBlank(source.getExecutionId(), source.getEventId());
        BigDecimal quantity = decimal(source.getQty());
        BigDecimal price = decimal(source.getPrice());
        Instant occurredAt = instant(firstNonBlank(source.getTimestamp(), source.getAt(), order.getFilledAt()));

        if (isBlank(executionId) || isBlank(order.getId()) || isBlank(order.getSymbol())
                || isBlank(order.getSide()) || quantity == null || quantity.signum() <= 0
                || price == null || price.signum() <= 0 || occurredAt == null) {
            return Optional.empty();
        }

        return Optional.of(new ExecutionFillEvent(
                executionId,
                order.getId(),
                order.getSymbol().trim().toUpperCase(Locale.ROOT),
                order.getSide().trim().toUpperCase(Locale.ROOT),
                quantity,
                price,
                occurredAt,
                order.getStatus()));
    }

    private static boolean isFillEvent(String event) {
        if (event == null) {
            return false;
        }
        String normalized = event.trim().toLowerCase(Locale.ROOT);
        return "fill".equals(normalized) || "partial_fill".equals(normalized);
    }

    private static BigDecimal decimal(String value) {
        try {
            return value == null ? null : new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Instant instant(String value) {
        try {
            return isBlank(value) ? null : Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
