package com.ai_got_this.pnl_service.repository;

import com.ai_got_this.pnl_service.domain.AccountLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountLedgerRepository extends JpaRepository<AccountLedger, String> {
}
