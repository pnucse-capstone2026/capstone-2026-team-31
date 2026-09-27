package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.dto.Order;

public interface Broker {
    void placeOrder(Order order);
}
