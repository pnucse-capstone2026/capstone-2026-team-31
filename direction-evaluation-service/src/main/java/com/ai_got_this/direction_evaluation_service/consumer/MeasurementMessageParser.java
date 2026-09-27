package com.ai_got_this.direction_evaluation_service.consumer;

import com.ai_got_this.direction_evaluation_service.dto.MarketPriceEvent;
import com.ai_got_this.direction_evaluation_service.dto.PredictionCreatedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class MeasurementMessageParser {
    private final ObjectMapper objectMapper;

    public MeasurementMessageParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PredictionCreatedEvent prediction(byte[] body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        return new PredictionCreatedEvent(
                text(root, "predictionId"), text(root, "ticker"), text(root, "direction"),
                decimal(root, "probabilityUp"), decimal(root, "confidence"), decimal(root, "latestPrice"),
                instant(root.path("timestamp")), text(root, "triggeredBy"), optionalText(root, "modelVersion"));
    }

    public MarketPriceEvent marketPrice(byte[] body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        return new MarketPriceEvent(text(root, "eventId"), text(root, "ticker"),
                decimal(root.path("bar"), "Close"), instant(root.path("timestamp")));
    }

    private static String text(JsonNode root, String field) {
        String value = optionalText(root, field);
        if (value == null) {
            throw new IllegalArgumentException("missing " + field);
        }
        return value;
    }

    private static String optionalText(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isMissingNode() || node.isNull() || node.asText().isBlank() ? null : node.asText();
    }

    private static BigDecimal decimal(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull()) {
            throw new IllegalArgumentException("missing " + field);
        }
        return new BigDecimal(node.asText());
    }

    private static Instant instant(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            throw new IllegalArgumentException("missing timestamp");
        }
        if (node.isNumber()) {
            return Instant.ofEpochSecond(node.longValue(), node.decimalValue().remainder(BigDecimal.ONE).movePointRight(9).longValue());
        }
        return Instant.parse(node.asText());
    }
}
