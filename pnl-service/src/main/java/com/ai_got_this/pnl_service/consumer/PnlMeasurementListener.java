package com.ai_got_this.pnl_service.consumer;

import com.ai_got_this.pnl_service.service.PnlAccountingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PnlMeasurementListener {
    private static final Logger log = LoggerFactory.getLogger(PnlMeasurementListener.class);
    private final MeasurementMessageParser parser;
    private final PnlAccountingService accounting;

    public PnlMeasurementListener(MeasurementMessageParser parser, PnlAccountingService accounting) {
        this.parser = parser;
        this.accounting = accounting;
    }

    @RabbitListener(queues = "${measurement.rabbitmq.executions-queue:measurement.executions}")
    public void onExecution(byte[] body) {
        try {
            accounting.accountFill(parser.execution(body));
        } catch (Exception exception) {
            log.atWarn().addKeyValue("eventType", "execution_malformed").log("malformed_execution_measurement_event");
        }
    }

    @RabbitListener(queues = "${measurement.rabbitmq.market-ticks-queue:measurement.market-ticks}")
    public void onMarketTick(byte[] body) {
        try {
            accounting.recordMarketPrice(parser.marketPrice(body));
        } catch (Exception exception) {
            log.atWarn().addKeyValue("eventType", "market_tick_malformed").log("malformed_market_measurement_event");
        }
    }
}
