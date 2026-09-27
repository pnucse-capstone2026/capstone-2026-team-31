package com.ai_got_this.account_service.client;

import com.ai_got_this.account_service.config.OrderExecutionPersisterProperties;
import com.ai_got_this.account_service.service.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class OrderExecutionPersisterOrdersClient {
    private final RestTemplate restTemplate;
    private final OrderExecutionPersisterProperties properties;

    public OrderExecutionPersisterOrdersClient(
            RestTemplate restTemplate,
            OrderExecutionPersisterProperties properties
    ) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public List<OrderProjectionResponse> latestOrders() {
        try {
            List<OrderProjectionResponse> result = restTemplate.exchange(
                    properties.baseUrl() + "/internal/v1/orders",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<OrderProjectionResponse>>() {}
            ).getBody();
            return result == null ? List.of() : result;
        } catch (RuntimeException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "ORDER_PERSISTER_UNAVAILABLE",
                    "Order data is temporarily unavailable."
            );
        }
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
    ) {}
}
