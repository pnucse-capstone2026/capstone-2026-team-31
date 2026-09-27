package com.ai_got_this.direction_evaluation_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "direction_evaluation")
public class DirectionEvaluation {
    @Id
    @Column(name = "prediction_id")
    private String predictionId;
    @Column(nullable = false)
    private String ticker;
    @Column(name = "horizon_at", nullable = false)
    private Instant horizonAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvaluationStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_band", nullable = false)
    private ConfidenceBand confidenceBand;
    @Column(name = "evaluation_event_id")
    private String evaluationEventId;
    @Column(name = "evaluation_price", precision = 24, scale = 8)
    private BigDecimal evaluationPrice;
    @Column(name = "evaluation_timestamp")
    private Instant evaluationTimestamp;
    @Column(name = "actual_return", precision = 24, scale = 12)
    private BigDecimal actualReturn;
    @Enumerated(EnumType.STRING)
    @Column(name = "actual_direction")
    private Direction actualDirection;
    @Column(name = "correct")
    private Boolean correct;

    protected DirectionEvaluation() {
    }

    public DirectionEvaluation(PredictionInput prediction, Instant horizonAt) {
        this.predictionId = prediction.getPredictionId();
        this.ticker = prediction.getTicker();
        this.horizonAt = horizonAt;
        this.status = EvaluationStatus.PENDING;
        this.confidenceBand = ConfidenceBand.from(prediction.getConfidence());
    }

    public String getPredictionId() { return predictionId; }
    public String getTicker() { return ticker; }
    public Instant getHorizonAt() { return horizonAt; }
    public EvaluationStatus getStatus() { return status; }
    public ConfidenceBand getConfidenceBand() { return confidenceBand; }
    public BigDecimal getActualReturn() { return actualReturn; }
    public Direction getActualDirection() { return actualDirection; }
    public Boolean getCorrect() { return correct; }

    public void complete(MarketTickRecord tick, BigDecimal returnValue, Direction direction, boolean isCorrect) {
        if (status == EvaluationStatus.COMPLETED) {
            return;
        }
        this.status = EvaluationStatus.COMPLETED;
        this.evaluationEventId = tick.getEventId();
        this.evaluationPrice = tick.getPrice();
        this.evaluationTimestamp = tick.getObservedAt();
        this.actualReturn = returnValue;
        this.actualDirection = direction;
        this.correct = isCorrect;
    }
}
