package com.ai_got_this.market_data_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlpacaBarMessage extends AlpacaMessage {
    @JsonProperty("S")
    private String symbol;
    @JsonProperty("o")
    private Double open;
    @JsonProperty("h")
    private Double high;
    @JsonProperty("l")
    private Double low;
    @JsonProperty("c")
    private Double close;
    @JsonProperty("v")
    private Long volume;
    @JsonProperty("n")
    private Integer tradeCount;
    @JsonProperty("t")
    private String timestamp;
}
