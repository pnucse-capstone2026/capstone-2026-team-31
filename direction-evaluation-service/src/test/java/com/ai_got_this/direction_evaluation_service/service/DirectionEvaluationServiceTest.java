package com.ai_got_this.direction_evaluation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai_got_this.direction_evaluation_service.config.DirectionEvaluationProperties;
import com.ai_got_this.direction_evaluation_service.domain.Direction;
import com.ai_got_this.direction_evaluation_service.domain.EvaluationStatus;
import com.ai_got_this.direction_evaluation_service.dto.MarketPriceEvent;
import com.ai_got_this.direction_evaluation_service.dto.PredictionCreatedEvent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DirectionEvaluationServiceTest {
    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");
    private InMemoryDirectionEvaluationStore store;
    private DirectionEvaluationService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryDirectionEvaluationStore();
        DirectionEvaluationProperties properties = new DirectionEvaluationProperties();
        properties.setHorizon(Duration.ofHours(1));
        service = new DirectionEvaluationService(store, new DirectionOutcomeCalculator(), properties);
    }

    @Test
    void upPredictionWithHigherEvaluationPriceIsCorrect() {
        service.recordPrediction(prediction("up-1", "UP", "0.90"));
        service.recordMarketPrice(tick("tick-1", "101", 60));

        assertTrue(store.evaluations.getFirst().getCorrect());
        assertEquals(Direction.UP, store.evaluations.getFirst().getActualDirection());
    }

    @Test
    void downPredictionWithLowerEvaluationPriceIsCorrect() {
        service.recordPrediction(prediction("down-1", "DOWN", "0.90"));
        service.recordMarketPrice(tick("tick-1", "99", 60));

        assertTrue(store.evaluations.getFirst().getCorrect());
        assertEquals(Direction.DOWN, store.evaluations.getFirst().getActualDirection());
    }

    @Test
    void incorrectPredictionIsMarkedIncorrect() {
        service.recordPrediction(prediction("wrong-1", "UP", "0.90"));
        service.recordMarketPrice(tick("tick-1", "99", 60));

        assertFalse(store.evaluations.getFirst().getCorrect());
    }

    @Test
    void evaluationRemainsPendingBeforeConfiguredHorizon() {
        service.recordPrediction(prediction("pending-1", "UP", "0.90"));
        service.recordMarketPrice(tick("tick-1", "101", 30));

        assertEquals(EvaluationStatus.PENDING, store.evaluations.getFirst().getStatus());
    }

    @Test
    void firstValidMarketPriceAtOrAfterHorizonCompletesEvaluation() {
        service.recordPrediction(prediction("first-1", "UP", "0.90"));
        service.recordMarketPrice(tick("before", "120", 59));
        service.recordMarketPrice(tick("first", "101", 60));
        service.recordMarketPrice(tick("later", "120", 61));

        assertEquals(EvaluationStatus.COMPLETED, store.evaluations.getFirst().getStatus());
        assertMoney("0.01", store.evaluations.getFirst().getActualReturn());
    }

    @Test
    void duplicatePredictionsAndMarketTicksDoNotCreateDuplicateEvaluations() {
        PredictionCreatedEvent prediction = prediction("duplicate-1", "UP", "0.90");
        MarketPriceEvent tick = tick("tick-1", "101", 60);
        assertTrue(service.recordPrediction(prediction));
        assertFalse(service.recordPrediction(prediction));
        assertTrue(service.recordMarketPrice(tick));
        assertFalse(service.recordMarketPrice(tick));

        assertEquals(1, store.evaluations.size());
        assertEquals(EvaluationStatus.COMPLETED, store.evaluations.getFirst().getStatus());
    }

    @Test
    void lowConfidencePredictionsRemainEvaluableDespiteAStrategyHold() {
        service.recordPrediction(prediction("low-confidence", "UP", "0.55"));
        service.recordMarketPrice(tick("tick-1", "101", 60));

        assertTrue(store.evaluations.getFirst().getCorrect());
    }

    private static PredictionCreatedEvent prediction(String id, String direction, String confidence) {
        return new PredictionCreatedEvent(id, "AAPL", direction, new BigDecimal("0.90"), new BigDecimal(confidence),
                new BigDecimal("100"), BASE, "market_tick", "model-v1");
    }

    private static MarketPriceEvent tick(String id, String price, long minutesAfterPrediction) {
        return new MarketPriceEvent(id, "AAPL", new BigDecimal(price), BASE.plus(Duration.ofMinutes(minutesAfterPrediction)));
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
