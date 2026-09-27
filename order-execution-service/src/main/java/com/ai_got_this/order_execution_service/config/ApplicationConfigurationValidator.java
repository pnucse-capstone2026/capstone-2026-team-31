package com.ai_got_this.order_execution_service.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class ApplicationConfigurationValidator {
    private final AlpacaProperties alpacaProperties;
    private final RabbitMqProperties rabbitMqProperties;

    public ApplicationConfigurationValidator(AlpacaProperties alpacaProperties, RabbitMqProperties rabbitMqProperties) {
        this.alpacaProperties = alpacaProperties;
        this.rabbitMqProperties = rabbitMqProperties;
    }

    @PostConstruct
    public void validate() {
        List<String> errors = new ArrayList<>();
        validateUrl(alpacaProperties.getStreamUrl(), "alpaca.api.stream-url", "ws", "wss", errors);
        validateUrl(alpacaProperties.getOrderEndpoint(), "alpaca.api.order-endpoint", "http", "https", errors);
        validateSubscription(errors);

        if (!errors.isEmpty()) {
            log.error("Invalid application configuration: {}", String.join("; ", errors));
            throw new IllegalStateException("Invalid application configuration; see the preceding error log for details");
        }
    }

    private void validateUrl(String value, String propertyName, String expectedScheme, String alternativeScheme, List<String> errors) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(value);
            boolean validScheme = expectedScheme.equals(uri.getScheme()) || alternativeScheme != null && alternativeScheme.equals(uri.getScheme());
            if (!validScheme || uri.getHost() == null) {
                errors.add(propertyName + " must be a valid " + expectedScheme + (alternativeScheme == null ? "" : " or " + alternativeScheme) + " URL");
            }
        }
        catch (IllegalArgumentException exception) {
            errors.add(propertyName + " must be a valid URL");
        }
    }

    private void validateSubscription(List<String> errors) {
        try {
            JsonNode subscription = new ObjectMapper().readTree(alpacaProperties.getSubscriptionMessage());
            if (!subscription.isObject()
                    || !"listen".equals(subscription.path("action").asText())
                    || !containsTradeUpdates(subscription.path("data").path("streams"))) {
                errors.add("alpaca.api.subscription-message must be a listen request for trade_updates");
            }
        }
        catch (Exception exception) {
            errors.add("alpaca.api.subscription-message must be valid JSON");
        }
    }

    private boolean containsTradeUpdates(JsonNode streams) {
        if (!streams.isArray()) {
            return false;
        }
        for (JsonNode stream : streams) {
            if ("trade_updates".equals(stream.asText())) {
                return true;
            }
        }
        return false;
    }
}
