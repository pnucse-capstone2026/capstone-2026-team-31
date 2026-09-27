package com.ai_got_this.market_data_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonPropertyOrder({
        "Open", "High", "Low", "Close", "Volume", "Return", "MA_5", "MA_20", "Volatility_5", "RSI_14", "MACD"
})
public class MarketData {
    @JsonProperty("Open")
    private Double open;

    @JsonProperty("High")
    private Double high;

    @JsonProperty("Low")
    private Double low;

    @JsonProperty("Close")
    private Double close;

    @JsonProperty("Volume")
    private Long volume;

    /** Fractional close-to-close return, e.g. 0.012 for a 1.2% increase. */
    @JsonProperty("Return")
    private Double returnPercentage;

    @JsonProperty("MA_5")
    private Double movingAverage5;

    @JsonProperty("MA_20")
    private Double movingAverage20;

    /** Sample volatility of the latest five fractional returns. */
    @JsonProperty("Volatility_5")
    private Double volatility5;

    /** RSI expressed on its standard 0–100 scale. */
    @JsonProperty("RSI_14")
    private Double rsi14;

    @JsonProperty("MACD")
    private Double macd;
}
