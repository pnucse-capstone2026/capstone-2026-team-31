package com.ai_got_this.direction_evaluation_service.consumer;

import com.ai_got_this.direction_evaluation_service.service.DirectionEvaluationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class DirectionEvaluationListener {
    private static final Logger log = LoggerFactory.getLogger(DirectionEvaluationListener.class);
    private final MeasurementMessageParser parser;
    private final DirectionEvaluationService evaluations;

    public DirectionEvaluationListener(MeasurementMessageParser parser, DirectionEvaluationService evaluations) {
        this.parser = parser;
        this.evaluations = evaluations;
    }

    @RabbitListener(queues = "${measurement.rabbitmq.predictions-queue:measurement.predictions}")
    public void onPrediction(byte[] body) {
        try {
            evaluations.recordPrediction(parser.prediction(body));
        } catch (Exception exception) {
            log.atWarn().addKeyValue("eventType", "prediction_malformed").log("malformed_prediction_measurement_event");
        }
    }

    @RabbitListener(queues = "${measurement.rabbitmq.market-ticks-queue:measurement.market-ticks}")
    public void onMarketTick(byte[] body) {
        try {
            evaluations.recordMarketPrice(parser.marketPrice(body));
        } catch (Exception exception) {
            log.atWarn().addKeyValue("eventType", "market_tick_malformed").log("malformed_market_measurement_event");
        }
    }
}
