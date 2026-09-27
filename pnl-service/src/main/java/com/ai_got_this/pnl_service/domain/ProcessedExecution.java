package com.ai_got_this.pnl_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "processed_execution")
public class ProcessedExecution {
    @Id
    @Column(name = "execution_id")
    private String executionId;
    @Column(name = "broker_order_id", nullable = false)
    private String brokerOrderId;
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
