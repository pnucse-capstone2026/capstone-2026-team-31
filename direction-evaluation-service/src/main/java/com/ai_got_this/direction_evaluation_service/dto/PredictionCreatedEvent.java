package com.ai_got_this.direction_evaluation_service.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PredictionCreatedEvent(
        String predictionId,
        String ticker,
        String direction,
        BigDecimal probabilityUp,
        BigDecimal confidence,
        BigDecimal latestPrice,
        Instant predictionTimestamp,
        String triggeredBy,
        String modelVersion
) {
}
