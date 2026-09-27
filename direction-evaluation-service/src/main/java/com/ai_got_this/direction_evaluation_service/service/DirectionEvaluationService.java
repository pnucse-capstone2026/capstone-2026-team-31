package com.ai_got_this.direction_evaluation_service.service;

import com.ai_got_this.direction_evaluation_service.config.DirectionEvaluationProperties;
import com.ai_got_this.direction_evaluation_service.domain.Direction;
import com.ai_got_this.direction_evaluation_service.domain.DirectionEvaluation;
import com.ai_got_this.direction_evaluation_service.domain.EvaluationStatus;
import com.ai_got_this.direction_evaluation_service.domain.MarketTickRecord;
import com.ai_got_this.direction_evaluation_service.domain.PredictionInput;
import com.ai_got_this.direction_evaluation_service.dto.MarketPriceEvent;
import com.ai_got_this.direction_evaluation_service.dto.PredictionCreatedEvent;
import com.ai_got_this.direction_evaluation_service.repository.DirectionEvaluationStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DirectionEvaluationService {
    private static final Logger log = LoggerFactory.getLogger(DirectionEvaluationService.class);
    private final DirectionEvaluationStore store;
    private final DirectionOutcomeCalculator calculator;
    private final DirectionEvaluationProperties properties;

    public DirectionEvaluationService(DirectionEvaluationStore store, DirectionOutcomeCalculator calculator,
                                      DirectionEvaluationProperties properties) {
        this.store = store;
        this.calculator = calculator;
        this.properties = properties;
    }

    @Transactional
    public boolean recordPrediction(PredictionCreatedEvent event) {
        PredictionInput prediction = toPrediction(event);
        if (prediction == null) {
            log.atWarn().addKeyValue("eventType", "prediction_malformed").log("invalid_prediction_measurement_event");
            return false;
        }
        if (!store.registerPrediction(prediction)) {
            log.atInfo().addKeyValue("eventType", "prediction_duplicate").addKeyValue("predictionId", event.predictionId())
                    .log("duplicate_prediction_measurement_event");
            return false;
        }
        PredictionInput saved = store.findPrediction(prediction.getPredictionId()).orElseThrow();
        DirectionEvaluation evaluation = new DirectionEvaluation(saved, saved.getPredictionTimestamp().plus(properties.getHorizon()));
        store.saveEvaluation(evaluation);
        log.atInfo().addKeyValue("eventType", "prediction_recorded").addKeyValue("predictionId", saved.getPredictionId())
                .addKeyValue("ticker", saved.getTicker()).addKeyValue("direction", saved.getPredictedDirection())
                .addKeyValue("confidence", saved.getConfidence()).addKeyValue("latestPrice", saved.getLatestPrice())
                .addKeyValue("triggeredBy", saved.getTriggeredBy()).log("prediction_recorded");
        evaluateIfPossible(evaluation, saved);
        return true;
    }

    @Transactional
    public boolean recordMarketPrice(MarketPriceEvent event) {
        if (!validMarketPrice(event)) {
            log.atWarn().addKeyValue("eventType", "market_tick_malformed").log("invalid_market_measurement_event");
            return false;
        }
        MarketTickRecord tick = new MarketTickRecord(event.eventId(), normalize(event.ticker()), event.price(), event.timestamp());
        if (!store.registerMarketTick(tick)) {
            return false;
        }
        log.atInfo().addKeyValue("eventType", "market_price_recorded").addKeyValue("eventId", tick.getEventId())
                .addKeyValue("ticker", tick.getTicker()).addKeyValue("price", tick.getPrice()).log("market_price_recorded");
        for (DirectionEvaluation evaluation : store.pendingDueAt(tick.getObservedAt())) {
            store.findPrediction(evaluation.getPredictionId()).ifPresent(prediction -> evaluateIfPossible(evaluation, prediction));
        }
        return true;
    }

    private void evaluateIfPossible(DirectionEvaluation evaluation, PredictionInput prediction) {
        if (evaluation.getStatus() != EvaluationStatus.PENDING) {
            return;
        }
        store.firstMarketTickAtOrAfter(prediction.getTicker(), evaluation.getHorizonAt()).ifPresent(tick -> {
            DirectionOutcomeCalculator.Outcome outcome = calculator.calculate(prediction, tick);
            evaluation.complete(tick, outcome.actualReturn(), outcome.actualDirection(), outcome.correct());
            store.saveEvaluation(evaluation);
            log.atInfo().addKeyValue("eventType", "direction_evaluation_completed")
                    .addKeyValue("predictionId", prediction.getPredictionId()).addKeyValue("ticker", prediction.getTicker())
                    .addKeyValue("correct", outcome.correct()).addKeyValue("actualReturn", outcome.actualReturn())
                    .log("direction_evaluation_completed");
        });
    }

    private static PredictionInput toPrediction(PredictionCreatedEvent event) {
        if (event == null || blank(event.predictionId()) || blank(event.ticker()) || blank(event.triggeredBy())
                || event.predictionTimestamp() == null || !finitePositive(event.latestPrice())
                || !probability(event.probabilityUp()) || !probability(event.confidence())) {
            return null;
        }
        try {
            Direction direction = Direction.valueOf(event.direction().trim().toUpperCase(Locale.ROOT));
            if (direction == Direction.FLAT) {
                return null;
            }
            return new PredictionInput(event.predictionId(), normalize(event.ticker()), direction, event.probabilityUp(),
                    event.confidence(), event.latestPrice(), event.predictionTimestamp(), event.triggeredBy(), event.modelVersion());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean validMarketPrice(MarketPriceEvent event) {
        return event != null && !blank(event.eventId()) && !blank(event.ticker()) && event.timestamp() != null
                && finitePositive(event.price());
    }

    private static boolean finitePositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static boolean probability(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) >= 0 && value.compareTo(BigDecimal.ONE) <= 0;
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
