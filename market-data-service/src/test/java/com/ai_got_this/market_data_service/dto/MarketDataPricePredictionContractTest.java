package com.ai_got_this.market_data_service.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketDataPricePredictionContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesTheExactPriceFeatureNamesAndMetadataOrder() throws Exception {
        MarketData marketData = MarketData.builder()
                .open(180.0)
                .high(182.0)
                .low(179.0)
                .close(181.0)
                .volume(50_000_000L)
                .returnPercentage(0.012)
                .movingAverage5(179.5)
                .movingAverage20(175.2)
                .volatility5(0.020)
                .rsi14(61.5)
                .macd(1.20)
                .build();

        JsonNode bar = objectMapper.readTree(objectMapper.writeValueAsString(new MarketTick(
                "AAPL", marketData, "event-1", "2026-01-01T00:00:00Z")))
                .required("bar");

        assertEquals(
                List.of("Open", "High", "Low", "Close", "Volume", "Return", "MA_5", "MA_20", "Volatility_5", "RSI_14", "MACD"),
                bar.properties().stream().map(java.util.Map.Entry::getKey).toList());
        assertEquals(0.012, bar.required("Return").asDouble());
        assertEquals(179.5, bar.required("MA_5").asDouble());
    }
}
