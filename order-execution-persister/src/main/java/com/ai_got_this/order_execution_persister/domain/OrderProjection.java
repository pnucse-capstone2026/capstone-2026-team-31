package com.ai_got_this.order_execution_persister.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@Table(name = "order_projection")
public class OrderProjection {
    @Id
    private UUID orderId;

    @Column(nullable = false, unique = true)
    private UUID alpacaOrderId;

    private String clientOrderId;
    private String symbol;
    private String side;
    private BigDecimal quantity;
    private BigDecimal filledQuantity;
    private BigDecimal averageFilledPrice;
    private String orderType;
    private String timeInForce;
    private String status;
    private BigDecimal fees;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant filledAt;
    private Instant cancelledAt;
}
