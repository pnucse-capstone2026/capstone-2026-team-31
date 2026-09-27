package com.ai_got_this.direction_evaluation_service.repository;

import com.ai_got_this.direction_evaluation_service.domain.PredictionInput;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PredictionInputRepository extends JpaRepository<PredictionInput, String> {
    @Modifying
    @Query(value = "insert into direction_eval.prediction_input "
            + "(prediction_id, ticker, predicted_direction, probability_up, confidence, latest_price, prediction_timestamp, triggered_by, model_version) "
            + "values (:predictionId, :ticker, :direction, :probabilityUp, :confidence, :latestPrice, :predictionTimestamp, :triggeredBy, :modelVersion) "
            + "on conflict (prediction_id) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("predictionId") String predictionId, @Param("ticker") String ticker,
                       @Param("direction") String direction, @Param("probabilityUp") BigDecimal probabilityUp,
                       @Param("confidence") BigDecimal confidence, @Param("latestPrice") BigDecimal latestPrice,
                       @Param("predictionTimestamp") Instant predictionTimestamp, @Param("triggeredBy") String triggeredBy,
                       @Param("modelVersion") String modelVersion);
}
