package com.ai_got_this.direction_evaluation_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "market_tick")
public class MarketTickRecord {
    @Id
    @Column(name = "event_id")
    private String eventId;
    @Column(nullable = false)
    private String ticker;
    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal price;
    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    protected MarketTickRecord() {
    }

    public MarketTickRecord(String eventId, String ticker, BigDecimal price, Instant observedAt) {
        this.eventId = eventId;
        this.ticker = ticker;
        this.price = price;
        this.observedAt = observedAt;
    }

    public String getEventId() { return eventId; }
    public String getTicker() { return ticker; }
    public BigDecimal getPrice() { return price; }
    public Instant getObservedAt() { return observedAt; }
}
