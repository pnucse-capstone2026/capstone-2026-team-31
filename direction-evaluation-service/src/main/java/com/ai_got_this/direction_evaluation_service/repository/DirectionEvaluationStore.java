package com.ai_got_this.direction_evaluation_service.repository;

import com.ai_got_this.direction_evaluation_service.domain.DirectionEvaluation;
import com.ai_got_this.direction_evaluation_service.domain.MarketTickRecord;
import com.ai_got_this.direction_evaluation_service.domain.PredictionInput;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Database boundary that keeps unit tests fully in-memory. */
public interface DirectionEvaluationStore {
    boolean registerPrediction(PredictionInput prediction);
    boolean registerMarketTick(MarketTickRecord tick);
    Optional<PredictionInput> findPrediction(String predictionId);
    Optional<MarketTickRecord> firstMarketTickAtOrAfter(String ticker, Instant horizonAt);
    List<DirectionEvaluation> pendingDueAt(Instant now);
    List<DirectionEvaluation> findByStatus(com.ai_got_this.direction_evaluation_service.domain.EvaluationStatus status);
    List<DirectionEvaluation> findAllEvaluations();
    void saveEvaluation(DirectionEvaluation evaluation);
}
