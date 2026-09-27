package com.ai_got_this.pnl_service.repository;

import com.ai_got_this.pnl_service.domain.LotMatch;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LotMatchRepository extends JpaRepository<LotMatch, UUID> {
}
