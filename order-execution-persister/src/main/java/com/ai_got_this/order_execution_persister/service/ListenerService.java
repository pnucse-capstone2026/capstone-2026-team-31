package com.ai_got_this.order_execution_persister.service;

import com.ai_got_this.order_execution_persister.config.RabbitMqProperties;
import com.ai_got_this.order_execution_persister.dto.OrderEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class ListenerService {
    private final OrderEventPersistenceService orderEventPersistenceService;
    private final RabbitMqProperties rabbitMqProperties;

    public ListenerService(OrderEventPersistenceService orderEventPersistenceService, RabbitMqProperties rabbitMqProperties) {
        this.orderEventPersistenceService = orderEventPersistenceService;
        this.rabbitMqProperties = rabbitMqProperties;
    }

    @RabbitListener(queues = "#{rabbitMqProperties.orderEventsPersisterQueue}")
    public void listen(OrderEvent orderEvent) {
        orderEventPersistenceService.persist(orderEvent);
    }
}
