package com.ai_got_this.order_execution_persister.client;

import com.ai_got_this.order_execution_persister.config.AlpacaProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class AlpacaOrdersClient {
    private final RestTemplate restTemplate;
    private final AlpacaProperties properties;

    public AlpacaOrdersClient(RestTemplate restTemplate, AlpacaProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public List<BrokerOrder> getOrders() {
        BrokerOrder[] body = restTemplate.exchange(
                properties.getPaperTradingBaseUrl().replaceAll("/$", "") + "/v2/orders",
                HttpMethod.GET,
                new HttpEntity<Void>(headers()),
                BrokerOrder[].class
        ).getBody();

        return body == null ? List.of() : List.of(body);
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        h.set("APCA-API-KEY-ID", properties.getKey());
        h.set("APCA-API-SECRET-KEY", properties.getSecret());
        return h;
    }

    public record BrokerOrder(
            UUID id,
            String client_order_id,
            String symbol,
            String side,
            String type,
            String time_in_force,
            String status,
            BigDecimal qty,
            BigDecimal filled_qty,
            BigDecimal filled_avg_price,
            BigDecimal commission,
            Instant created_at,
            Instant updated_at,
            Instant filled_at,
            Instant canceled_at
    ) {}
}
