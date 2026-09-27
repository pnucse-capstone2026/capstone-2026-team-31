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
import org.springframework.stereotype.Repository;

@Repository
public class JpaPnlStore implements PnlStore {
    private final ProcessedExecutionRepository processedExecutions;
    private final ProcessedMarketTickRepository processedMarketTicks;
    private final AccountLedgerRepository ledgers;
    private final SymbolPnlRepository symbols;
    private final OpenLotRepository lots;
    private final LotMatchRepository matches;
    private final AccountingAnomalyRepository anomalies;

    public JpaPnlStore(
            ProcessedExecutionRepository processedExecutions,
            ProcessedMarketTickRepository processedMarketTicks,
            AccountLedgerRepository ledgers,
            SymbolPnlRepository symbols,
            OpenLotRepository lots,
            LotMatchRepository matches,
            AccountingAnomalyRepository anomalies
    ) {
        this.processedExecutions = processedExecutions;
        this.processedMarketTicks = processedMarketTicks;
        this.ledgers = ledgers;
        this.symbols = symbols;
        this.lots = lots;
        this.matches = matches;
        this.anomalies = anomalies;
    }

    @Override
    public boolean registerExecution(ExecutionFillEvent event, Instant recordedAt) {
        return processedExecutions.insertIfAbsent(event.executionId(), event.brokerOrderId(), recordedAt) == 1;
    }

    @Override
    public boolean registerMarketTick(MarketPriceEvent event) {
        return processedMarketTicks.insertIfAbsent(event.eventId(), event.ticker(), event.timestamp()) == 1;
    }

    @Override
    public AccountLedger loadOrCreateLedger(String accountId, BigDecimal startingBalance, Instant createdAt) {
        return ledgers.findById(accountId)
                .orElseGet(() -> ledgers.save(new AccountLedger(accountId, startingBalance, createdAt)));
    }

    @Override
    public SymbolPnl loadOrCreateSymbol(String ticker) {
        return symbols.findById(ticker).orElseGet(() -> symbols.save(new SymbolPnl(ticker)));
    }

    @Override
    public List<OpenLot> findOpenLots(String ticker) {
        return lots.findByTickerAndRemainingQuantityGreaterThanOrderByOpenedAtAsc(ticker, BigDecimal.ZERO);
    }

    @Override
    public List<OpenLot> findAllOpenLots() {
        return lots.findByRemainingQuantityGreaterThan(BigDecimal.ZERO);
    }

    @Override
    public List<SymbolPnl> findAllSymbols() {
        return symbols.findAll();
    }

    @Override
    public AccountLedger findLedger(String accountId) {
        return ledgers.findById(accountId).orElse(null);
    }

    @Override
    public void saveLedger(AccountLedger ledger) {
        ledgers.save(ledger);
    }

    @Override
    public void saveSymbol(SymbolPnl symbol) {
        symbols.save(symbol);
    }

    @Override
    public void saveLots(List<OpenLot> values) {
        lots.saveAll(values);
    }

    @Override
    public void saveMatches(List<LotMatch> values) {
        matches.saveAll(values);
    }

    @Override
    public void saveAnomaly(AccountingAnomaly anomaly) {
        anomalies.save(anomaly);
    }
}
