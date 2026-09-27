package com.ai_got_this.order_execution_persister.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEvent {
    private String stream;
    private String at;
    private String event;
    private String eventId;
    private String executionId;
    private String timestamp;
    private String price;
    private String qty;
    private String positionQty;
    private Map<String, String> positionQtys;
    private TradeUpdateOrder order;
    private List<TradeUpdateExecution> legs;
}
