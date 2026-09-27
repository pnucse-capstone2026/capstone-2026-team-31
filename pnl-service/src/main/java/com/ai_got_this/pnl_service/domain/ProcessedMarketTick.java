package com.ai_got_this.pnl_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "processed_market_tick")
public class ProcessedMarketTick {
    @Id
    @Column(name = "event_id")
    private String eventId;
    @Column(nullable = false)
    private String ticker;
    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;
}
