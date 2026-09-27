package com.ai_got_this.pnl_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "open_lot")
public class OpenLot {
    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String ticker;

    @Column(name = "entry_execution_id", nullable = false, updatable = false)
    private String entryExecutionId;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "unit_cost", nullable = false, precision = 24, scale = 8, updatable = false)
    private BigDecimal unitCost;

    @Column(name = "original_quantity", nullable = false, precision = 24, scale = 8, updatable = false)
    private BigDecimal originalQuantity;

    @Column(name = "remaining_quantity", nullable = false, precision = 24, scale = 8)
    private BigDecimal remainingQuantity;

    protected OpenLot() {
    }

    public OpenLot(String ticker, String entryExecutionId, Instant openedAt, BigDecimal unitCost, BigDecimal quantity) {
        this.id = UUID.randomUUID();
        this.ticker = ticker;
        this.entryExecutionId = entryExecutionId;
        this.openedAt = openedAt;
        this.unitCost = unitCost;
        this.originalQuantity = quantity;
        this.remainingQuantity = quantity;
    }

    public UUID getId() {
        return id;
    }

    public String getTicker() {
        return ticker;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getRemainingQuantity() {
        return remainingQuantity;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public void closeQuantity(BigDecimal quantity) {
        remainingQuantity = remainingQuantity.subtract(quantity);
    }
}
