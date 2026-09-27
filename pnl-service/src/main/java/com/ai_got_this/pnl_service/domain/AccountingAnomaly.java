package com.ai_got_this.pnl_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounting_anomaly")
public class AccountingAnomaly {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "execution_id", nullable = false, unique = true, updatable = false)
    private String executionId;

    @Column(nullable = false, updatable = false)
    private String ticker;

    @Column(name = "anomaly_type", nullable = false, updatable = false)
    private String anomalyType;

    @Column(nullable = false, updatable = false)
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AccountingAnomaly() {
    }

    public AccountingAnomaly(
            String executionId,
            String ticker,
            String anomalyType,
            String details,
            Instant occurredAt
    ) {
        this.executionId = executionId;
        this.ticker = ticker;
        this.anomalyType = anomalyType;
        this.details = details;
        this.occurredAt = occurredAt;
    }
}
