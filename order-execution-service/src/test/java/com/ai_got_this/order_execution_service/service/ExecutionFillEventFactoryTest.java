package com.ai_got_this.order_execution_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai_got_this.order_execution_service.dto.OrderEvent;
import com.ai_got_this.order_execution_service.dto.TradeUpdateOrder;
import org.junit.jupiter.api.Test;

class ExecutionFillEventFactoryTest {
    @Test
    void createsAContractOnlyForAnActualNewFill() {
        OrderEvent event = fillEvent("partial_fill", "2.5", "101.25");

        var fill = ExecutionFillEventFactory.from(event).orElseThrow();

        assertEquals("execution-1", fill.executionId());
        assertEquals("AAPL", fill.ticker());
        assertEquals("BUY", fill.side());
        assertEquals(0, fill.newlyFilledQuantity().compareTo(new java.math.BigDecimal("2.5")));
    }

    @Test
    void ignoresNonFillOrZeroQuantityUpdates() {
        assertTrue(ExecutionFillEventFactory.from(fillEvent("new", "0", "101.25")).isEmpty());
        assertTrue(ExecutionFillEventFactory.from(fillEvent("rejected", "2", "101.25")).isEmpty());
        assertTrue(ExecutionFillEventFactory.from(fillEvent("fill", "0", "101.25")).isEmpty());
    }

    private static OrderEvent fillEvent(String eventType, String quantity, String price) {
        TradeUpdateOrder order = new TradeUpdateOrder();
        order.setId("order-1");
        order.setSymbol("aapl");
        order.setSide("buy");
        order.setStatus("partially_filled");
        order.setFilledAt("2026-01-01T00:00:00Z");

        OrderEvent event = new OrderEvent();
        event.setEvent(eventType);
        event.setEventId("execution-1");
        event.setQty(quantity);
        event.setPrice(price);
        event.setTimestamp("2026-01-01T00:00:00Z");
        event.setOrder(order);
        return event;
    }
}
