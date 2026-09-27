package com.ai_got_this.pnl_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "symbol_pnl")
public class SymbolPnl {
    @Id
    private String ticker;

    @Column(name = "latest_market_price", precision = 24, scale = 8)
    private BigDecimal latestMarketPrice;

    @Column(name = "latest_price_at")
    private Instant latestPriceAt;

    @Column(name = "realized_pnl", nullable = false, precision = 24, scale = 8)
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    protected SymbolPnl() {
    }

    public SymbolPnl(String ticker) {
        this.ticker = ticker;
    }

    public String getTicker() {
        return ticker;
    }

    public BigDecimal getLatestMarketPrice() {
        return latestMarketPrice;
    }

    public Instant getLatestPriceAt() {
        return latestPriceAt;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public void addRealizedPnl(BigDecimal value) {
        realizedPnl = realizedPnl.add(value);
    }

    public void updateMarketPrice(BigDecimal price, Instant observedAt) {
        if (latestPriceAt == null || !observedAt.isBefore(latestPriceAt)) {
            latestMarketPrice = price;
            latestPriceAt = observedAt;
        }
    }
}
