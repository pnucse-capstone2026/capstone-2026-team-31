package com.ai_got_this.pnl_service.repository;

import com.ai_got_this.pnl_service.domain.ProcessedExecution;
import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface ProcessedExecutionRepository extends JpaRepository<ProcessedExecution, String> {
    @Modifying
    @Query(value = "insert into pnl.processed_execution (execution_id, broker_order_id, recorded_at) "
            + "values (:executionId, :brokerOrderId, :recordedAt) on conflict (execution_id) do nothing",
            nativeQuery = true)
    int insertIfAbsent(@Param("executionId") String executionId, @Param("brokerOrderId") String brokerOrderId,
                       @Param("recordedAt") Instant recordedAt);
}
