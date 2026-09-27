package com.ai_got_this.market_data_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

@Data
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "T",
        visible = true,
        defaultImpl = AlpacaUnknownMessage.class
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AlpacaBarMessage.class, name = "b"),
        @JsonSubTypes.Type(value = AlpacaErrorMessage.class, name = "error"),
        @JsonSubTypes.Type(value = AlpacaSuccessMessage.class, name = "success"),
        @JsonSubTypes.Type(value = AlpacaSubscriptionMessage.class, name = "subscription"),
        @JsonSubTypes.Type(value = AlpacaErrorMessage.class, name = "other")
})
public abstract class AlpacaMessage {
    @JsonProperty("T")
    private String type;
}
