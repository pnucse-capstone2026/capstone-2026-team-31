package com.ai_got_this.news_ingestion_service.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AlpacaMessageDeserializationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void polymorphicTypeInformationMapsNewsAndPreservesProviderFields() {
        AlpacaMessage message = objectMapper.readValue("{\"T\":\"n\",\"id\":\"id-7\",\"headline\":\"테스트 🚀\",\"symbols\":[\"005930\"]}", AlpacaMessage.class);

        AlpacaNewsMessage news = assertInstanceOf(AlpacaNewsMessage.class, message);
        assertEquals("n", news.getType());
        assertEquals("id-7", news.getId());
        assertEquals("테스트 🚀", news.getHeadline());
        assertEquals("005930", news.getSymbols().getFirst());
    }

    @Test
    void unmappedTypeUsesUnknownMessageAndRetainsNestedFields() {
        AlpacaMessage message = objectMapper.readValue("{\"T\":\"heartbeat\",\"sequence\":3,\"details\":{\"status\":\"ok\"}}", AlpacaMessage.class);

        AlpacaUnknownMessage unknown = assertInstanceOf(AlpacaUnknownMessage.class, message);
        assertEquals("heartbeat", unknown.getType());
        assertEquals(3, unknown.getPropertiesMap().get("sequence"));
        assertEquals("ok", ((java.util.Map<?, ?>) unknown.getPropertiesMap().get("details")).get("status"));
    }

    @Test
    void successAcknowledgementMapsToSuccessMessage() {
        AlpacaMessage message = objectMapper.readValue("{\"T\":\"success\",\"msg\":\"authenticated\"}", AlpacaMessage.class);

        AlpacaSuccessMessage success = assertInstanceOf(AlpacaSuccessMessage.class, message);
        assertEquals("authenticated", success.getMsg());
    }
}
