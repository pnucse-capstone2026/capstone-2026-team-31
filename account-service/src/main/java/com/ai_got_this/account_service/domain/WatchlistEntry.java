package com.ai_got_this.account_service.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
public class WatchlistEntry extends AuditedEntity {
    @Id
    private UUID entryId;

    @Column(nullable = false)
    private UUID watchlistId;

    @Column(nullable = false, length = 16)
    private String ticker;

    @Column(nullable = false)
    private boolean isActive = true;
}
