package com.ai_got_this.order_execution_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeUpdateResponse {
    private String stream;
    private TradeUpdateData data;
}
