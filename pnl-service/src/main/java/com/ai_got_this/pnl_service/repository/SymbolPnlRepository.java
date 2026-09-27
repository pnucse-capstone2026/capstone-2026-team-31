package com.ai_got_this.pnl_service.repository;

import com.ai_got_this.pnl_service.domain.SymbolPnl;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SymbolPnlRepository extends JpaRepository<SymbolPnl, String> {
}
