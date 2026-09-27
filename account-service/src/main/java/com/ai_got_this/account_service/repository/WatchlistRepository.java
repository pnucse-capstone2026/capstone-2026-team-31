package com.ai_got_this.account_service.repository;

import com.ai_got_this.account_service.domain.Watchlist;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistRepository extends JpaRepository<Watchlist, UUID> {
    List<Watchlist> findAllByOrderByNameAsc();

    Optional<Watchlist> findByName(String name);
}
