package com.ai_got_this.order_execution_persister.service;

import com.ai_got_this.order_execution_persister.client.AlpacaOrdersClient;
import com.ai_got_this.order_execution_persister.domain.OrderProjection;
import com.ai_got_this.order_execution_persister.repository.OrderProjectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderReconciliationService {
    private final AlpacaOrdersClient broker;
    private final OrderProjectionRepository orders;
    private final OrderProjectionMapper mapper;

    public OrderReconciliationService(
            AlpacaOrdersClient broker,
            OrderProjectionRepository orders,
            OrderProjectionMapper mapper
    ) {
        this.broker = broker;
        this.orders = orders;
        this.mapper = mapper;
    }

    @Transactional
    public void reconcileOrders() {
        for (AlpacaOrdersClient.BrokerOrder order : broker.getOrders()) {
            if (order.id() == null || order.symbol() == null || order.updated_at() == null) {
                continue;
            }

            OrderProjection projection = orders.findByAlpacaOrderId(order.id())
                    .orElseGet(OrderProjection::new);
            mapper.applyBrokerOrder(projection, order);
            orders.save(projection);
        }
    }
}
