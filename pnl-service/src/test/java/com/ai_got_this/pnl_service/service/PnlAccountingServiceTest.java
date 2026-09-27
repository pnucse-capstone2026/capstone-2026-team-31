package com.ai_got_this.pnl_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ai_got_this.pnl_service.config.PnlProperties;
import com.ai_got_this.pnl_service.domain.AccountingOutcome;
import com.ai_got_this.pnl_service.dto.ExecutionFillEvent;
import com.ai_got_this.pnl_service.dto.MarketPriceEvent;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PnlAccountingServiceTest {
    private InMemoryPnlStore store;
    private RecordingBalanceWarningEmitter warnings;
    private PnlAccountingService service;
    private PnlReadService readService;

    @BeforeEach
    void setUp() {
        store = new InMemoryPnlStore();
        warnings = new RecordingBalanceWarningEmitter();
        PnlProperties properties = new PnlProperties();
        properties.setStartingBalance(new BigDecimal("10000.00"));
        service = new PnlAccountingService(store, new FifoLotAccounting(), warnings, properties);
        readService = new PnlReadService(store, properties);
    }

    @Test
    void buyFillCreatesOpenLotAndDecreasesDerivedCash() {
        assertEquals(AccountingOutcome.ACCOUNTED, service.accountFill(fill("buy-1", "BUY", "10", "100", "FILLED")));

        assertMoney("9000", readService.portfolio().cashBalance());
        assertMoney("10", readService.portfolio().symbols().getFirst().openQuantity());
        assertMoney("1000", readService.portfolio().symbols().getFirst().costBasis());
    }

    @Test
    void sellFillUsesFifoLotsAndCalculatesProfit() {
        service.accountFill(fill("buy-1", "BUY", "10", "100", "FILLED"));
        service.accountFill(fill("buy-2", "BUY", "10", "110", "FILLED"));
        service.accountFill(fill("sell-1", "SELL", "15", "120", "FILLED"));

        assertMoney("250", readService.portfolio().realizedPnl());
        assertMoney("5", readService.portfolio().symbols().getFirst().openQuantity());
        assertMoney("9700", readService.portfolio().cashBalance());
    }

    @Test
    void lossIsCalculatedCorrectly() {
        service.accountFill(fill("buy-1", "BUY", "4", "100", "FILLED"));
        service.accountFill(fill("sell-1", "SELL", "4", "95", "FILLED"));

        assertMoney("-20", readService.portfolio().realizedPnl());
    }

    @Test
    void marketPriceChangesUnrealizedPnl() {
        service.accountFill(fill("buy-1", "BUY", "49", "100", "FILLED"));
        service.recordMarketPrice(new MarketPriceEvent(
                "tick-1",
                "AAPL",
                new BigDecimal("102"),
                Instant.parse("2026-01-01T01:00:00Z")));

        assertMoney("98", readService.portfolio().unrealizedPnl());
    }

    @Test
    void unfilledOrderStatusesDoNotAffectPnl() {
        service.accountFill(fill("new-1", "BUY", "2", "100", "NEW"));
        service.accountFill(fill("reject-1", "BUY", "2", "100", "REJECTED"));
        service.accountFill(fill("cancel-1", "BUY", "2", "100", "CANCELED"));

        assertMoney("10000", readService.portfolio().cashBalance());
        assertEquals(0, store.lots.size());
    }

    @Test
    void duplicateFillIsIgnoredByIdempotencyKey() {
        ExecutionFillEvent fill = fill("buy-1", "BUY", "2", "100", "FILLED");
        service.accountFill(fill);
        assertEquals(AccountingOutcome.DUPLICATE, service.accountFill(fill));

        assertMoney("9800", readService.portfolio().cashBalance());
        assertEquals(1, store.lots.size());
    }

    @Test
    void positiveToNonPositiveBalanceEmitsOneStructuredWarningForTheTransition() {
        service.accountFill(fill("buy-1", "BUY", "100", "100", "FILLED"));
        service.accountFill(fill("buy-2", "BUY", "1", "1", "FILLED"));

        assertEquals(1, warnings.warnings.size());
        assertMoney("10000", warnings.warnings.getFirst().previousBalance());
        assertMoney("0", warnings.warnings.getFirst().newBalance());
    }

    @Test
    void sellExceedingLongPositionIsAnAccountingErrorWithoutStateCorruption() {
        service.accountFill(fill("buy-1", "BUY", "1", "100", "FILLED"));
        assertEquals(AccountingOutcome.ANOMALY, service.accountFill(fill("sell-1", "SELL", "2", "110", "FILLED")));

        assertMoney("9900", readService.portfolio().cashBalance());
        assertMoney("1", readService.portfolio().symbols().getFirst().openQuantity());
        assertEquals(1, store.anomalies.size());
    }

    private static ExecutionFillEvent fill(
            String executionId,
            String side,
            String quantity,
            String price,
            String status
    ) {
        return new ExecutionFillEvent(executionId, "order-" + executionId, "AAPL", side,
                new BigDecimal(quantity), new BigDecimal(price), Instant.parse("2026-01-01T00:00:00Z"), status);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
