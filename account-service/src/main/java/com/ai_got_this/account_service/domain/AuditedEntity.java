package com.ai_got_this.account_service.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Data;

@Data
@MappedSuperclass
public abstract class AuditedEntity {
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void created() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void updated() {
        updatedAt = Instant.now();
    }
}
