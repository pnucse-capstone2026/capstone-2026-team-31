package com.ai_got_this.order_execution_persister.service;

import com.ai_got_this.order_execution_persister.domain.OrderProjection;
import com.ai_got_this.order_execution_persister.dto.OrderEvent;
import com.ai_got_this.order_execution_persister.repository.OrderProjectionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderEventPersistenceService {
    private final OrderProjectionRepository orders;
    private final OrderProjectionMapper mapper;

    public OrderEventPersistenceService(
            OrderProjectionRepository orders,
            OrderProjectionMapper mapper
    ) {
        this.orders = orders;
        this.mapper = mapper;
    }

    @Transactional
    public void persist(OrderEvent event) {
        if (event == null || event.getOrder() == null || event.getOrder().getId() == null) {
            return;
        }

        UUID alpacaId;
        try {
            alpacaId = UUID.fromString(event.getOrder().getId());
        } catch (IllegalArgumentException ignored) {
            return;
        }

        OrderProjection projection = orders.findByAlpacaOrderId(alpacaId).orElseGet(() -> {
            OrderProjection value = new OrderProjection();
            value.setOrderId(UUID.randomUUID());
            value.setAlpacaOrderId(alpacaId);
            return value;
        });

        if (mapper.applyOrderEvent(projection, event)) {
            orders.save(projection);
        }
    }
}
