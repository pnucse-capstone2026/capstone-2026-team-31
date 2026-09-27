package com.ai_got_this.pnl_service.repository;

import com.ai_got_this.pnl_service.domain.OpenLot;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpenLotRepository extends JpaRepository<OpenLot, UUID> {
    List<OpenLot> findByTickerAndRemainingQuantityGreaterThanOrderByOpenedAtAsc(String ticker, BigDecimal quantity);
    List<OpenLot> findByRemainingQuantityGreaterThan(BigDecimal quantity);
}
