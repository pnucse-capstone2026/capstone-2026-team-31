package com.ai_got_this.pnl_service.service;

import com.ai_got_this.pnl_service.domain.AccountLedger;
import com.ai_got_this.pnl_service.domain.AccountingAnomaly;
import com.ai_got_this.pnl_service.domain.LotMatch;
import com.ai_got_this.pnl_service.domain.OpenLot;
import com.ai_got_this.pnl_service.domain.SymbolPnl;
import com.ai_got_this.pnl_service.dto.ExecutionFillEvent;
import com.ai_got_this.pnl_service.dto.MarketPriceEvent;
import com.ai_got_this.pnl_service.repository.PnlStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class InMemoryPnlStore implements PnlStore {
    private final Set<String> executions = new HashSet<>();
    private final Set<String> marketTicks = new HashSet<>();
    private final Map<String, AccountLedger> ledgers = new HashMap<>();
    private final Map<String, SymbolPnl> symbols = new HashMap<>();
    final List<OpenLot> lots = new ArrayList<>();
    final List<LotMatch> matches = new ArrayList<>();
    final List<AccountingAnomaly> anomalies = new ArrayList<>();

    @Override
    public boolean registerExecution(ExecutionFillEvent event, Instant recordedAt) {
        return executions.add(event.executionId());
    }

    @Override
    public boolean registerMarketTick(MarketPriceEvent event) {
        return marketTicks.add(event.eventId());
    }

    @Override
    public AccountLedger loadOrCreateLedger(String accountId, BigDecimal startingBalance, Instant createdAt) {
        return ledgers.computeIfAbsent(accountId, ignored -> new AccountLedger(accountId, startingBalance, createdAt));
    }

    @Override
    public SymbolPnl loadOrCreateSymbol(String ticker) {
        return symbols.computeIfAbsent(ticker, SymbolPnl::new);
    }

    @Override
    public List<OpenLot> findOpenLots(String ticker) {
        return lots.stream().filter(lot -> lot.getTicker().equals(ticker) && lot.getRemainingQuantity().signum() > 0)
                .sorted(Comparator.comparing(OpenLot::getOpenedAt)).toList();
    }

    @Override
    public List<OpenLot> findAllOpenLots() {
        return lots.stream().filter(lot -> lot.getRemainingQuantity().signum() > 0).toList();
    }

    @Override
    public List<SymbolPnl> findAllSymbols() {
        return List.copyOf(symbols.values());
    }

    @Override
    public AccountLedger findLedger(String accountId) {
        return ledgers.get(accountId);
    }

    @Override
    public void saveLedger(AccountLedger ledger) {
    }

    @Override
    public void saveSymbol(SymbolPnl symbol) {
    }

    @Override
    public void saveLots(List<OpenLot> values) {
        lots.addAll(values);
    }

    @Override
    public void saveMatches(List<LotMatch> values) {
        matches.addAll(values);
    }

    @Override
    public void saveAnomaly(AccountingAnomaly anomaly) {
        anomalies.add(anomaly);
    }
}
