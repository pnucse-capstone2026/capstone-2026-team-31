package com.ai_got_this.order_execution_persister.web;

import com.ai_got_this.order_execution_persister.domain.OrderProjection;
import com.ai_got_this.order_execution_persister.repository.OrderProjectionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/orders")
public class InternalOrderProjectionController {
    private final OrderProjectionRepository orders;

    public InternalOrderProjectionController(OrderProjectionRepository orders) {
        this.orders = orders;
    }

    @GetMapping
    public List<OrderProjectionResponse> latestOrders() {
        return orders.findTop20ByOrderByUpdatedAtDesc().stream()
                .map(OrderProjectionResponse::from)
                .toList();
    }

    public record OrderProjectionResponse(
            UUID orderId,
            UUID alpacaOrderId,
            String clientOrderId,
            String symbol,
            String side,
            BigDecimal quantity,
            BigDecimal filledQuantity,
            BigDecimal averageFilledPrice,
            String orderType,
            String timeInForce,
            String status,
            BigDecimal fees,
            Instant createdAt,
            Instant updatedAt,
            Instant filledAt,
            Instant cancelledAt
    ) {
        static OrderProjectionResponse from(OrderProjection p) {
            return new OrderProjectionResponse(
                    p.getOrderId(),
                    p.getAlpacaOrderId(),
                    p.getClientOrderId(),
                    p.getSymbol(),
                    p.getSide(),
                    p.getQuantity(),
                    p.getFilledQuantity(),
                    p.getAverageFilledPrice(),
                    p.getOrderType(),
                    p.getTimeInForce(),
                    p.getStatus(),
                    p.getFees(),
                    p.getCreatedAt(),
                    p.getUpdatedAt(),
                    p.getFilledAt(),
                    p.getCancelledAt()
            );
        }
    }
}
