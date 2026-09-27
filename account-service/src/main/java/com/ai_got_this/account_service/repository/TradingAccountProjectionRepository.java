package com.ai_got_this.account_service.repository;

import com.ai_got_this.account_service.domain.TradingAccountProjection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradingAccountProjectionRepository
        extends JpaRepository<TradingAccountProjection, UUID> {}
