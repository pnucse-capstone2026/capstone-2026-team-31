package com.ai_got_this.pnl_service.service;

import com.ai_got_this.pnl_service.config.PnlProperties;
import com.ai_got_this.pnl_service.domain.AccountLedger;
import com.ai_got_this.pnl_service.domain.AccountingAnomaly;
import com.ai_got_this.pnl_service.domain.AccountingOutcome;
import com.ai_got_this.pnl_service.domain.InsufficientLongPositionException;
import com.ai_got_this.pnl_service.domain.Side;
import com.ai_got_this.pnl_service.domain.SymbolPnl;
import com.ai_got_this.pnl_service.dto.ExecutionFillEvent;
import com.ai_got_this.pnl_service.dto.MarketPriceEvent;
import com.ai_got_this.pnl_service.repository.PnlStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PnlAccountingService {
    private static final Logger log = LoggerFactory.getLogger(PnlAccountingService.class);

    private final PnlStore store;
    private final FifoLotAccounting fifoLotAccounting;
    private final BalanceWarningEmitter balanceWarningEmitter;
    private final PnlProperties properties;

    public PnlAccountingService(PnlStore store, FifoLotAccounting fifoLotAccounting,
                                BalanceWarningEmitter balanceWarningEmitter, PnlProperties properties) {
        this.store = store;
        this.fifoLotAccounting = fifoLotAccounting;
        this.balanceWarningEmitter = balanceWarningEmitter;
        this.properties = properties;
    }

    @Transactional
    public AccountingOutcome accountFill(ExecutionFillEvent event) {
        Side side = validFillSide(event);
        if (side == null) {
            log.atWarn().addKeyValue("eventType", "execution_ignored").log("ignored_execution_event");
            return AccountingOutcome.IGNORED;
        }
        if (!store.registerExecution(event, Instant.now())) {
            log.atInfo().addKeyValue("eventType", "execution_duplicate").addKeyValue("executionId", event.executionId())
                    .log("duplicate_execution_event");
            return AccountingOutcome.DUPLICATE;
        }

        AccountLedger ledger = store.loadOrCreateLedger(
                properties.getAccountId(),
                properties.getStartingBalance(),
                Instant.now());
        SymbolPnl symbol = store.loadOrCreateSymbol(event.ticker());
        BigDecimal previousBalance = ledger.getCashBalance();
        try {
            var change = fifoLotAccounting.apply(ledger, symbol, store.findOpenLots(event.ticker()), event, side);
            store.saveLedger(ledger);
            store.saveSymbol(symbol);
            store.saveLots(change.createdLots());
            store.saveMatches(change.matches());
            if (previousBalance.signum() > 0 && ledger.getCashBalance().signum() <= 0) {
                balanceWarningEmitter.balanceBecameNonPositive(properties.getAccountId(), previousBalance,
                        ledger.getCashBalance(), event.executionId(), event.ticker());
            }
            log.atInfo()
                    .addKeyValue("eventType", "execution_fill_accounted")
                    .addKeyValue("executionId", event.executionId())
                    .addKeyValue("brokerOrderId", event.brokerOrderId())
                    .addKeyValue("ticker", event.ticker())
                    .addKeyValue("side", side)
                    .addKeyValue("fillPrice", event.fillPrice())
                    .addKeyValue("filledQuantity", event.newlyFilledQuantity())
                    .addKeyValue("realizedPnl", change.realizedPnl())
                    .addKeyValue("cashBalance", ledger.getCashBalance())
                    .log("execution_fill_accounted");
            return AccountingOutcome.ACCOUNTED;
        } catch (InsufficientLongPositionException exception) {
            store.saveAnomaly(new AccountingAnomaly(event.executionId(), event.ticker(), "INSUFFICIENT_LONG_POSITION",
                    exception.getMessage(), event.timestamp()));
            log.atError()
                    .addKeyValue("eventType", "execution_accounting_anomaly")
                    .addKeyValue("executionId", event.executionId())
                    .addKeyValue("ticker", event.ticker())
                    .log("execution_accounting_anomaly");
            return AccountingOutcome.ANOMALY;
        }
    }

    @Transactional
    public boolean recordMarketPrice(MarketPriceEvent event) {
        if (!validMarketPrice(event)) {
            log.atWarn().addKeyValue("eventType", "market_tick_ignored").log("ignored_market_tick");
            return false;
        }
        if (!store.registerMarketTick(event)) {
            return false;
        }
        SymbolPnl symbol = store.loadOrCreateSymbol(event.ticker());
        symbol.updateMarketPrice(event.price(), event.timestamp());
        store.saveSymbol(symbol);
        log.atInfo()
                .addKeyValue("eventType", "market_price_recorded")
                .addKeyValue("eventId", event.eventId())
                .addKeyValue("ticker", event.ticker())
                .addKeyValue("price", event.price())
                .log("market_price_recorded");
        return true;
    }

    private static Side validFillSide(ExecutionFillEvent event) {
        if (event == null || blank(event.executionId()) || blank(event.brokerOrderId()) || blank(event.ticker())
                || event.newlyFilledQuantity() == null || event.newlyFilledQuantity().signum() <= 0
                || event.fillPrice() == null || event.fillPrice().signum() <= 0 || event.timestamp() == null
                || !isFillStatus(event.orderStatus())) {
            return null;
        }
        try {
            return Side.valueOf(event.side().trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean validMarketPrice(MarketPriceEvent event) {
        return event != null && !blank(event.eventId()) && !blank(event.ticker()) && event.timestamp() != null
                && event.price() != null && event.price().signum() > 0;
    }

    private static boolean isFillStatus(String value) {
        return value != null && ("FILLED".equalsIgnoreCase(value) || "PARTIALLY_FILLED".equalsIgnoreCase(value));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
