package com.ai_got_this.direction_evaluation_service.service;

import com.ai_got_this.direction_evaluation_service.domain.DirectionEvaluation;
import com.ai_got_this.direction_evaluation_service.domain.EvaluationStatus;
import com.ai_got_this.direction_evaluation_service.domain.MarketTickRecord;
import com.ai_got_this.direction_evaluation_service.domain.PredictionInput;
import com.ai_got_this.direction_evaluation_service.repository.DirectionEvaluationStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class InMemoryDirectionEvaluationStore implements DirectionEvaluationStore {
    private final Map<String, PredictionInput> predictions = new HashMap<>();
    private final Map<String, MarketTickRecord> marketTicks = new HashMap<>();
    final List<DirectionEvaluation> evaluations = new ArrayList<>();

    @Override public boolean registerPrediction(PredictionInput prediction) {
        return predictions.putIfAbsent(prediction.getPredictionId(), prediction) == null;
    }
    @Override public boolean registerMarketTick(MarketTickRecord tick) {
        return marketTicks.putIfAbsent(tick.getEventId(), tick) == null;
    }
    @Override public Optional<PredictionInput> findPrediction(String predictionId) { return Optional.ofNullable(predictions.get(predictionId)); }
    @Override public Optional<MarketTickRecord> firstMarketTickAtOrAfter(String ticker, Instant horizonAt) {
        return marketTicks.values().stream().filter(tick -> tick.getTicker().equals(ticker) && !tick.getObservedAt().isBefore(horizonAt))
                .min(Comparator.comparing(MarketTickRecord::getObservedAt));
    }
    @Override public List<DirectionEvaluation> pendingDueAt(Instant now) {
        return evaluations.stream().filter(item -> item.getStatus() == EvaluationStatus.PENDING && !item.getHorizonAt().isAfter(now)).toList();
    }
    @Override public List<DirectionEvaluation> findByStatus(EvaluationStatus status) {
        return evaluations.stream().filter(item -> item.getStatus() == status).toList();
    }
    @Override public List<DirectionEvaluation> findAllEvaluations() { return List.copyOf(evaluations); }
    @Override public void saveEvaluation(DirectionEvaluation evaluation) {
        if (!evaluations.contains(evaluation)) {
            evaluations.add(evaluation);
        }
    }
}
