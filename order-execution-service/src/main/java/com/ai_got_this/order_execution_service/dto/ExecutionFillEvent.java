package com.ai_got_this.order_execution_service.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Immutable measurement contract emitted only for newly executed broker quantity. */
public record ExecutionFillEvent(
        String executionId,
        String brokerOrderId,
        String ticker,
        String side,
        BigDecimal newlyFilledQuantity,
        BigDecimal fillPrice,
        Instant timestamp,
        String orderStatus
) {
}
