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
@Table(name = "prediction_input")
public class PredictionInput {
    @Id
    @Column(name = "prediction_id", updatable = false)
    private String predictionId;
    @Column(nullable = false, updatable = false)
    private String ticker;
    @Enumerated(EnumType.STRING)
    @Column(name = "predicted_direction", nullable = false, updatable = false)
    private Direction predictedDirection;
    @Column(name = "probability_up", nullable = false, precision = 12, scale = 8, updatable = false)
    private BigDecimal probabilityUp;
    @Column(nullable = false, precision = 12, scale = 8, updatable = false)
    private BigDecimal confidence;
    @Column(name = "latest_price", nullable = false, precision = 24, scale = 8, updatable = false)
    private BigDecimal latestPrice;
    @Column(name = "prediction_timestamp", nullable = false, updatable = false)
    private Instant predictionTimestamp;
    @Column(name = "triggered_by", nullable = false, updatable = false)
    private String triggeredBy;
    @Column(name = "model_version", updatable = false)
    private String modelVersion;

    protected PredictionInput() {
    }

    public PredictionInput(String predictionId, String ticker, Direction predictedDirection, BigDecimal probabilityUp,
                           BigDecimal confidence, BigDecimal latestPrice, Instant predictionTimestamp,
                           String triggeredBy, String modelVersion) {
        this.predictionId = predictionId;
        this.ticker = ticker;
        this.predictedDirection = predictedDirection;
        this.probabilityUp = probabilityUp;
        this.confidence = confidence;
        this.latestPrice = latestPrice;
        this.predictionTimestamp = predictionTimestamp;
        this.triggeredBy = triggeredBy;
        this.modelVersion = modelVersion;
    }

    public String getPredictionId() { return predictionId; }
    public String getTicker() { return ticker; }
    public Direction getPredictedDirection() { return predictedDirection; }
    public BigDecimal getProbabilityUp() { return probabilityUp; }
    public BigDecimal getConfidence() { return confidence; }
    public BigDecimal getLatestPrice() { return latestPrice; }
    public Instant getPredictionTimestamp() { return predictionTimestamp; }
    public String getTriggeredBy() { return triggeredBy; }
    public String getModelVersion() { return modelVersion; }
}
