package com.ai_got_this.direction_evaluation_service.repository;

import com.ai_got_this.direction_evaluation_service.domain.DirectionEvaluation;
import com.ai_got_this.direction_evaluation_service.domain.EvaluationStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectionEvaluationRepository extends JpaRepository<DirectionEvaluation, String> {
    List<DirectionEvaluation> findByStatusAndHorizonAtLessThanEqual(EvaluationStatus status, Instant horizonAt);
    List<DirectionEvaluation> findByStatus(EvaluationStatus status);
}
