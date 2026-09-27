package com.ai_got_this.market_data_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlpacaSubscriptionMessage extends AlpacaMessage {
    @JsonProperty("bars")
    private List<String> bars;
}
