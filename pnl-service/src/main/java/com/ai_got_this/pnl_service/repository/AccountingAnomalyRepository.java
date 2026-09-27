package com.ai_got_this.pnl_service.repository;

import com.ai_got_this.pnl_service.domain.AccountingAnomaly;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountingAnomalyRepository extends JpaRepository<AccountingAnomaly, UUID> {
}
