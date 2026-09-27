package com.ai_got_this.order_execution_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
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
    private java.util.Map<String, String> positionQtys;
    private TradeUpdateOrder order;
    private java.util.List<TradeUpdateExecution> legs;

    public OrderEvent(TradeUpdateResponse tradeUpdateResponse) {
        TradeUpdateData data = tradeUpdateResponse.getData();
        stream = tradeUpdateResponse.getStream();
        if (data != null) {
            at = data.getAt();
            event = data.getEvent();
            eventId = data.getEventId();
            executionId = data.getExecutionId();
            timestamp = data.getTimestamp();
            price = data.getPrice();
            qty = data.getQty();
            positionQty = data.getPositionQty();
            positionQtys = data.getPositionQtys();
            order = data.getOrder();
            legs = data.getLegs();
        }
    }

    public static OrderEvent httpOrderCreated(TradeUpdateOrder order) {
        OrderEvent event = new OrderEvent();
        String timestamp = Instant.now().toString();
        event.setStream("HTTP");
        event.setEvent("create");
        event.setAt(timestamp);
        event.setTimestamp(timestamp);
        event.setOrder(order);
        return event;
    }
}
