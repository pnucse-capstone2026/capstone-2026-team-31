package com.ai_got_this.pnl_service.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketPriceEvent(String eventId, String ticker, BigDecimal price, Instant timestamp) {
}
