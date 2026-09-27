package com.ai_got_this.pnl_service.repository;

import com.ai_got_this.pnl_service.domain.ProcessedMarketTick;
import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface ProcessedMarketTickRepository extends JpaRepository<ProcessedMarketTick, String> {
    @Modifying
    @Query(value = "insert into pnl.processed_market_tick (event_id, ticker, observed_at) "
            + "values (:eventId, :ticker, :observedAt) on conflict (event_id) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId, @Param("ticker") String ticker,
                       @Param("observedAt") Instant observedAt);
}
