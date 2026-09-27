package com.ai_got_this.order_execution_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Order {
    @JsonAlias("ticker")
    private String symbol;
    @JsonProperty("qty")
    @JsonAlias("quantity")
    private int quantity;
    @JsonAlias("action")
    private String side;
    @JsonAlias("order_type")
    private String type = "limit";
    @JsonProperty("limitPrice")
    @JsonAlias("limit_price")
    private BigDecimal limitPrice;
    private String reason;
}
