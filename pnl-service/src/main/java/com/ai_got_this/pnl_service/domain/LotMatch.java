package com.ai_got_this.pnl_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "lot_match")
public class LotMatch {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "closing_execution_id", nullable = false, updatable = false)
    private String closingExecutionId;

    @Column(name = "opening_lot_id", nullable = false, updatable = false)
    private UUID openingLotId;

    @Column(nullable = false, updatable = false)
    private String ticker;

    @Column(name = "matched_quantity", nullable = false, precision = 24, scale = 8, updatable = false)
    private BigDecimal matchedQuantity;

    @Column(name = "buy_price", nullable = false, precision = 24, scale = 8, updatable = false)
    private BigDecimal buyPrice;

    @Column(name = "sell_price", nullable = false, precision = 24, scale = 8, updatable = false)
    private BigDecimal sellPrice;

    @Column(name = "realized_pnl", nullable = false, precision = 24, scale = 8, updatable = false)
    private BigDecimal realizedPnl;

    protected LotMatch() {
    }

    public LotMatch(String closingExecutionId, UUID openingLotId, String ticker, BigDecimal matchedQuantity,
                    BigDecimal buyPrice, BigDecimal sellPrice, BigDecimal realizedPnl) {
        this.closingExecutionId = closingExecutionId;
        this.openingLotId = openingLotId;
        this.ticker = ticker;
        this.matchedQuantity = matchedQuantity;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.realizedPnl = realizedPnl;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }
}
