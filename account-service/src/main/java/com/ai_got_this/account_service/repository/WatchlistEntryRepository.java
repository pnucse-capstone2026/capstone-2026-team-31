package com.ai_got_this.account_service.repository;

import com.ai_got_this.account_service.domain.WatchlistEntry;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistEntryRepository extends JpaRepository<WatchlistEntry, UUID> {
    List<WatchlistEntry> findByWatchlistId(UUID id);

    List<WatchlistEntry> findByWatchlistIdInAndIsActiveTrue(Collection<UUID> ids);
}
