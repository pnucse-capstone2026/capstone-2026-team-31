package com.ai_got_this.order_execution_persister.service;

import com.ai_got_this.order_execution_persister.client.AlpacaOrdersClient;
import com.ai_got_this.order_execution_persister.domain.OrderProjection;
import com.ai_got_this.order_execution_persister.dto.OrderEvent;
import com.ai_got_this.order_execution_persister.dto.TradeUpdateOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OrderProjectionMapper {
    public void applyBrokerOrder(OrderProjection projection, AlpacaOrdersClient.BrokerOrder order) {
        projection.setOrderId(projection.getOrderId() == null ? UUID.randomUUID() : projection.getOrderId());
        projection.setAlpacaOrderId(order.id());
        projection.setClientOrderId(order.client_order_id());
        projection.setSymbol(order.symbol());
        projection.setSide(order.side());
        projection.setOrderType(order.type());
        projection.setTimeInForce(order.time_in_force());
        projection.setStatus(order.status());
        projection.setQuantity(order.qty());
        projection.setFilledQuantity(order.filled_qty());
        projection.setAverageFilledPrice(order.filled_avg_price());
        projection.setFees(order.commission());
        projection.setCreatedAt(order.created_at());
        projection.setUpdatedAt(order.updated_at());
        projection.setFilledAt(order.filled_at());
        projection.setCancelledAt(order.canceled_at());
    }

    public boolean applyOrderEvent(OrderProjection projection, OrderEvent event) {
        TradeUpdateOrder order = event.getOrder();
        Instant updatedAt = firstInstant(order.getUpdatedAt(), event.getTimestamp(), event.getAt());
        if (updatedAt != null && projection.getUpdatedAt() != null && updatedAt.isBefore(projection.getUpdatedAt())) {
            return false;
        }

        setIfPresent(order.getClientOrderId(), projection::setClientOrderId);
        setIfPresent(order.getSymbol(), projection::setSymbol);
        setIfPresent(order.getSide(), projection::setSide);
        setIfPresent(order.getOrderType(), projection::setOrderType);
        setIfPresent(order.getType(), projection::setOrderType);
        setIfPresent(order.getTimeInForce(), projection::setTimeInForce);
        setIfPresent(order.getStatus(), projection::setStatus);
        setDecimalIfPresent(order.getQty(), projection::setQuantity);
        setDecimalIfPresent(order.getFilledQty(), projection::setFilledQuantity);
        setDecimalIfPresent(order.getFilledAvgPrice(), projection::setAverageFilledPrice);
        setInstantIfPresent(order.getCreatedAt(), projection::setCreatedAt);
        setInstantIfPresent(order.getFilledAt(), projection::setFilledAt);
        setInstantIfPresent(order.getCanceledAt(), projection::setCancelledAt);
        if (updatedAt != null) {
            projection.setUpdatedAt(updatedAt);
        }
        return true;
    }

    private Instant firstInstant(String... values) {
        for (String value : values) {
            Instant instant = toInstant(value);
            if (instant != null) {
                return instant;
            }
        }
        return null;
    }

    private void setIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private void setDecimalIfPresent(String value, java.util.function.Consumer<BigDecimal> setter) {
        if (value == null) {
            return;
        }
        try {
            setter.accept(new BigDecimal(value));
        } catch (NumberFormatException ignored) {
        }
    }

    private void setInstantIfPresent(String value, java.util.function.Consumer<Instant> setter) {
        Instant instant = toInstant(value);
        if (instant != null) {
            setter.accept(instant);
        }
    }

    private Instant toInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
