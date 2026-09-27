package com.ai_got_this.order_execution_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    @JsonAlias("submitted_at")
    private String submittedAt;
    @JsonAlias("filled_at")
    private String filledAt;
    @JsonAlias("expired_at")
    private String expiredAt;
    @JsonAlias("cancel_requested_at")
    private String cancelRequestedAt;
    @JsonAlias("canceled_at")
    private String canceledAt;
    @JsonAlias("failed_at")
    private String failedAt;
    @JsonAlias("replaced_at")
    private String replacedAt;
    @JsonAlias("replaced_by")
    private String replacedBy;
    private String replaces;
    @JsonAlias("asset_id")
    private String assetId;
    private String symbol;
    @JsonAlias("asset_class")
    private String assetClass;
    private String notional;
    private String qty;
    @JsonAlias("filled_qty")
    private String filledQty;
    @JsonAlias("filled_avg_price")
    private String filledAvgPrice;
    @JsonAlias("order_class")
    private String orderClass;
    @JsonAlias("order_type")
    private String orderType;
    private String type;
    private String side;
    private String status;
    @JsonAlias("time_in_force")
    private String timeInForce;
    @JsonAlias("limit_price")
    private String limitPrice;
    @JsonAlias("stop_price")
    private String stopPrice;
    @JsonAlias("extended_hours")
    private Boolean extendedHours;
    private List<TradeUpdateOrder> legs;
    @JsonAlias("trail_percent")
    private String trailPercent;
    @JsonAlias("trail_price")
    private String trailPrice;
    private String hwm;
    @JsonAlias("ratio_qty")
    private String ratioQty;
}
