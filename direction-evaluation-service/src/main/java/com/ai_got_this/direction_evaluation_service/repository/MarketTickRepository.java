package com.ai_got_this.direction_evaluation_service.repository;

import com.ai_got_this.direction_evaluation_service.domain.MarketTickRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketTickRepository extends JpaRepository<MarketTickRecord, String> {
    @Modifying
    @Query(value = "insert into direction_eval.market_tick (event_id, ticker, price, observed_at) "
            + "values (:eventId, :ticker, :price, :observedAt) on conflict (event_id) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId, @Param("ticker") String ticker,
                       @Param("price") BigDecimal price, @Param("observedAt") Instant observedAt);

    Optional<MarketTickRecord> findFirstByTickerAndObservedAtGreaterThanEqualOrderByObservedAtAsc(String ticker, Instant observedAt);
}
