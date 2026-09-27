package com.ai_got_this.order_execution_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeUpdateData {
    private String at;
    private String event;
    @JsonAlias("event_id")
    private String eventId;
    @JsonAlias("execution_id")
    private String executionId;
    private String timestamp;
    private String price;
    private String qty;
    @JsonAlias("position_qty")
    private String positionQty;
    @JsonAlias("position_qtys")
    private Map<String, String> positionQtys;
    private TradeUpdateOrder order;
    private List<TradeUpdateExecution> legs;
}
