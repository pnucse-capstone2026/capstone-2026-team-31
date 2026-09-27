package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.config.RabbitMqProperties;
import com.ai_got_this.order_execution_service.dto.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ListenerService {
    private final Broker broker;
    private final OrderValidator orderValidator;
    private final RabbitMqProperties rabbitMqProperties;

    public ListenerService(
            Broker broker,
            OrderValidator orderValidator,
            RabbitMqProperties rabbitMqProperties) {
        this.broker = broker;
        this.orderValidator = orderValidator;
        this.rabbitMqProperties = rabbitMqProperties;
    }

    @RabbitListener(queues = "#{rabbitMqProperties.ordersApprovedQueue}")
    public void listen(Order order) {
        var rejectionReason = orderValidator.validate(order);
        if (rejectionReason.isPresent()) {
            log.warn("Rejected order before broker submission: {}", rejectionReason.get());
            return;
        }
        broker.placeOrder(order);
    }

}
