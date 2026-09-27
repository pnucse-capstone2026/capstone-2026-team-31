package com.ai_got_this.account_service.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
public class PositionProjection {
    @Id
    private UUID positionId;

    private UUID alpacaAssetId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private BigDecimal quantity;

    private BigDecimal availableQuantity;
    private String side;
    private BigDecimal averageEntryPrice;
    private BigDecimal costBasis;
    private BigDecimal currentPrice;
    private BigDecimal marketValue;
    private BigDecimal unrealizedPnl;

    @Column(nullable = false)
    private Instant updatedAt;
}
