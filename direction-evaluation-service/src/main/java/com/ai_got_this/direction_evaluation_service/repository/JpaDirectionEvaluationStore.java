package com.ai_got_this.direction_evaluation_service.repository;

import com.ai_got_this.direction_evaluation_service.domain.DirectionEvaluation;
import com.ai_got_this.direction_evaluation_service.domain.EvaluationStatus;
import com.ai_got_this.direction_evaluation_service.domain.MarketTickRecord;
import com.ai_got_this.direction_evaluation_service.domain.PredictionInput;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaDirectionEvaluationStore implements DirectionEvaluationStore {
    private final PredictionInputRepository predictions;
    private final MarketTickRepository marketTicks;
    private final DirectionEvaluationRepository evaluations;

    public JpaDirectionEvaluationStore(PredictionInputRepository predictions, MarketTickRepository marketTicks,
                                       DirectionEvaluationRepository evaluations) {
        this.predictions = predictions;
        this.marketTicks = marketTicks;
        this.evaluations = evaluations;
    }

    @Override
    public boolean registerPrediction(PredictionInput prediction) {
        return predictions.insertIfAbsent(prediction.getPredictionId(), prediction.getTicker(),
                prediction.getPredictedDirection().name(), prediction.getProbabilityUp(), prediction.getConfidence(),
                prediction.getLatestPrice(), prediction.getPredictionTimestamp(), prediction.getTriggeredBy(),
                prediction.getModelVersion()) == 1;
    }

    @Override
    public boolean registerMarketTick(MarketTickRecord tick) {
        return marketTicks.insertIfAbsent(tick.getEventId(), tick.getTicker(), tick.getPrice(), tick.getObservedAt()) == 1;
    }

    @Override public Optional<PredictionInput> findPrediction(String predictionId) { return predictions.findById(predictionId); }
    @Override public Optional<MarketTickRecord> firstMarketTickAtOrAfter(String ticker, Instant horizonAt) {
        return marketTicks.findFirstByTickerAndObservedAtGreaterThanEqualOrderByObservedAtAsc(ticker, horizonAt);
    }
    @Override public List<DirectionEvaluation> pendingDueAt(Instant now) {
        return evaluations.findByStatusAndHorizonAtLessThanEqual(EvaluationStatus.PENDING, now);
    }
    @Override public List<DirectionEvaluation> findByStatus(EvaluationStatus status) { return evaluations.findByStatus(status); }
    @Override public List<DirectionEvaluation> findAllEvaluations() { return evaluations.findAll(); }
    @Override public void saveEvaluation(DirectionEvaluation evaluation) { evaluations.save(evaluation); }
}
