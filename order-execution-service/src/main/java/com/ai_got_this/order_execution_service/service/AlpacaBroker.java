package com.ai_got_this.order_execution_service.service;

import com.ai_got_this.order_execution_service.config.AlpacaProperties;
import com.ai_got_this.order_execution_service.dto.Order;
import com.ai_got_this.order_execution_service.dto.OrderEvent;
import com.ai_got_this.order_execution_service.dto.TradeUpdateOrder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

/** Places every approved order on the single installation-wide Alpaca account. */
@Service
public class AlpacaBroker implements Broker {
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;
    private final AlpacaProperties alpaca;
    private final PublisherService publisher;

    public AlpacaBroker(RestTemplate restTemplate, ObjectMapper mapper, AlpacaProperties alpaca, PublisherService publisher) {
        this.restTemplate = restTemplate;
        this.mapper = mapper; this.alpaca = alpaca;
        this.publisher = publisher;
    }

    @Override public void placeOrder(Order order) {
        try {
            String response = restTemplate.exchange(alpaca.getOrderEndpoint(), HttpMethod.POST, new HttpEntity<>(request(order), headers()), String.class).getBody();
            TradeUpdateOrder brokerOrder = mapper.readValue(response, TradeUpdateOrder.class);

            if (brokerOrder != null && brokerOrder.getId() != null)
                publisher.publishOrderEvent(OrderEvent.httpOrderCreated(brokerOrder));
        }
        catch (Exception exception) { throw new IllegalStateException("Alpaca rejected or did not confirm the order.", exception); }
    }
    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        h.set("APCA-API-KEY-ID", alpaca.getKey());
        h.set("APCA-API-SECRET-KEY", alpaca.getSecret());
        return h;
    }

    private Map<String, String> request(Order order) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("symbol", order.getSymbol());
        payload.put("qty", String.valueOf(order.getQuantity()));
        payload.put("side", order.getSide().trim().toLowerCase(Locale.ROOT));
        payload.put("type", order.getType().trim().toLowerCase(Locale.ROOT));
        payload.put("limit_price", order.getLimitPrice().toPlainString());
        payload.put("time_in_force", "day");
        return payload;
    }
}
