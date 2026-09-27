package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.config.RabbitMqProperties;
import com.ai_got_this.order_execution_service.dto.ExecutionFillEvent;
import com.ai_got_this.order_execution_service.dto.OrderEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class PublisherService {
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMqProperties rabbitMqProperties;

    public PublisherService(RabbitTemplate rabbitTemplate, RabbitMqProperties rabbitMqProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitMqProperties = rabbitMqProperties;
    }

    public void publishOrderEvent(OrderEvent message) {
        rabbitTemplate.convertAndSend(rabbitMqProperties.getOrderEventsExchange(), "", message);
    }

    public void publishExecutionFill(ExecutionFillEvent event) {
        rabbitTemplate.convertAndSend(
                rabbitMqProperties.getMeasurementExecutionsExchange(),
                rabbitMqProperties.getMeasurementExecutionsRoutingKey(),
                event);
    }
}
