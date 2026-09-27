package com.ai_got_this.direction_evaluation_service.domain;

import java.math.BigDecimal;

public enum ConfidenceBand {
    LOW,
    MEDIUM,
    HIGH;

    public static ConfidenceBand from(BigDecimal confidence) {
        if (confidence.compareTo(new BigDecimal("0.65")) < 0) {
            return LOW;
        }
        return confidence.compareTo(new BigDecimal("0.80")) < 0 ? MEDIUM : HIGH;
    }
}
