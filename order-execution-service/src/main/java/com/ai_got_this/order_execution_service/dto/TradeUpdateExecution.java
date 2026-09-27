package com.ai_got_this.order_execution_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeUpdateExecution {
    @JsonAlias("execution_id")
    private String executionId;
    private String qty;
    private String price;
    @JsonAlias("order_id")
    private String orderId;
    private String symbol;
    private String timestamp;
}
