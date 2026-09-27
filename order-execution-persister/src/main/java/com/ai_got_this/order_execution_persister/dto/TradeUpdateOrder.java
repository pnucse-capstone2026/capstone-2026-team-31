package com.ai_got_this.order_execution_persister.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeUpdateOrder {
    private String id;
    @JsonAlias("client_order_id")
    private String clientOrderId;
    @JsonAlias("created_at")
    private String createdAt;
    @JsonAlias("updated_at")
    private String updatedAt;
    @JsonAlias("filled_at")
    private String filledAt;
    @JsonAlias("canceled_at")
    private String canceledAt;
    private String symbol;
    private String qty;
    @JsonAlias("filled_qty")
    private String filledQty;
    @JsonAlias("filled_avg_price")
    private String filledAvgPrice;
    @JsonAlias("order_type")
    private String orderType;
    private String type;
    private String side;
    private String status;
    @JsonAlias("time_in_force")
    private String timeInForce;
    private List<TradeUpdateOrder> legs;
}
