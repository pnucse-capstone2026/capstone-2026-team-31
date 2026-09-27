package com.ai_got_this.direction_evaluation_service.service;

import com.ai_got_this.direction_evaluation_service.domain.ConfidenceBand;
import com.ai_got_this.direction_evaluation_service.domain.DirectionEvaluation;
import com.ai_got_this.direction_evaluation_service.domain.EvaluationStatus;
import com.ai_got_this.direction_evaluation_service.repository.DirectionEvaluationStore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DirectionEvaluationReadService {
    private final DirectionEvaluationStore store;

    public DirectionEvaluationReadService(DirectionEvaluationStore store) {
        this.store = store;
    }

    public Summary summary() {
        List<DirectionEvaluation> completed = store.findByStatus(EvaluationStatus.COMPLETED);
        return metrics(completed);
    }

    public Map<String, Summary> byTicker() {
        return store.findByStatus(EvaluationStatus.COMPLETED).stream()
                .collect(Collectors.groupingBy(DirectionEvaluation::getTicker, Collectors.collectingAndThen(Collectors.toList(), this::metrics)));
    }

    public Map<ConfidenceBand, Summary> byConfidenceBand() {
        return store.findByStatus(EvaluationStatus.COMPLETED).stream().collect(Collectors.groupingBy(
                DirectionEvaluation::getConfidenceBand, Collectors.collectingAndThen(Collectors.toList(), this::metrics)));
    }

    public List<PendingEvaluation> pending() {
        return store.findByStatus(EvaluationStatus.PENDING).stream()
                .map(item -> new PendingEvaluation(item.getPredictionId(), item.getTicker(), item.getHorizonAt(), item.getConfidenceBand()))
                .toList();
    }

    private Summary metrics(List<DirectionEvaluation> evaluations) {
        long correct = evaluations.stream().filter(item -> Boolean.TRUE.equals(item.getCorrect())).count();
        BigDecimal accuracy = evaluations.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(correct).divide(BigDecimal.valueOf(evaluations.size()), 6, RoundingMode.HALF_UP);
        return new Summary(evaluations.size(), correct, accuracy);
    }

    public record Summary(long totalEvaluated, long correctPredictions, BigDecimal directionalAccuracy) {
    }

    public record PendingEvaluation(String predictionId, String ticker, java.time.Instant horizonAt, ConfidenceBand confidenceBand) {
    }
}
