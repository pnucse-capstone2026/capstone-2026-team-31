package com.ai_got_this.order_execution_service.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("rabbitmq.assets")
@Validated
@Data
public class RabbitMqProperties {
    @NotBlank
    private String ordersApprovedQueue;
    @NotBlank
    private String orderEventsPortfolioQueue;
    @NotBlank
    private String orderEventsNotificationQueue;
    @NotBlank
    private String orderEventsPersisterQueue;
    @NotBlank
    private String orderEventsExchange;
    @NotBlank
    private String measurementExecutionsExchange;
    @NotBlank
    private String measurementExecutionsRoutingKey;
}
