package com.ai_got_this.pnl_service.dto;

import java.math.BigDecimal;
import java.time.Instant;

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
