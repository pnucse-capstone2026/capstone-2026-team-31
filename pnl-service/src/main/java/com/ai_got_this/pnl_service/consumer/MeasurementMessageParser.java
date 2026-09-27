package com.ai_got_this.pnl_service.consumer;

import com.ai_got_this.pnl_service.dto.ExecutionFillEvent;
import com.ai_got_this.pnl_service.dto.MarketPriceEvent;
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

    public ExecutionFillEvent execution(byte[] body) throws Exception {
        return objectMapper.readValue(body, ExecutionFillEvent.class);
    }

    public MarketPriceEvent marketPrice(byte[] body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        return new MarketPriceEvent(
                text(root, "eventId"),
                text(root, "ticker"),
                decimal(root.path("bar").path("Close")),
                Instant.parse(text(root, "timestamp")));
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("missing " + field);
        }
        return value.asText();
    }

    private static BigDecimal decimal(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            throw new IllegalArgumentException("missing bar.Close");
        }
        return new BigDecimal(value.asText());
    }
}
