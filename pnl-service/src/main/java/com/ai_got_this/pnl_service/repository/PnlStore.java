package com.ai_got_this.pnl_service.repository;

import com.ai_got_this.pnl_service.domain.AccountLedger;
import com.ai_got_this.pnl_service.domain.AccountingAnomaly;
import com.ai_got_this.pnl_service.domain.LotMatch;
import com.ai_got_this.pnl_service.domain.OpenLot;
import com.ai_got_this.pnl_service.domain.SymbolPnl;
import com.ai_got_this.pnl_service.dto.ExecutionFillEvent;
import com.ai_got_this.pnl_service.dto.MarketPriceEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Persistence boundary used by accounting and replaceable with a unit-test fake. */
public interface PnlStore {
    boolean registerExecution(ExecutionFillEvent event, Instant recordedAt);
    boolean registerMarketTick(MarketPriceEvent event);
    AccountLedger loadOrCreateLedger(String accountId, BigDecimal startingBalance, Instant createdAt);
    SymbolPnl loadOrCreateSymbol(String ticker);
    List<OpenLot> findOpenLots(String ticker);
    List<OpenLot> findAllOpenLots();
    List<SymbolPnl> findAllSymbols();
    AccountLedger findLedger(String accountId);
    void saveLedger(AccountLedger ledger);
    void saveSymbol(SymbolPnl symbol);
    void saveLots(List<OpenLot> lots);
    void saveMatches(List<LotMatch> matches);
    void saveAnomaly(AccountingAnomaly anomaly);
}
