package com.ai_got_this.account_service.web;

import com.ai_got_this.account_service.client.OrderExecutionPersisterOrdersClient;
import com.ai_got_this.account_service.domain.PositionProjection;
import com.ai_got_this.account_service.domain.TradingAccountProjection;
import com.ai_got_this.account_service.repository.PositionProjectionRepository;
import com.ai_got_this.account_service.repository.TradingAccountProjectionRepository;
import com.ai_got_this.account_service.service.AccountReconciliationService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

@RestController
@RequestMapping({"/api/v1", "/internal/v1"})
public class PortfolioController {
    private final TradingAccountProjectionRepository accounts;
    private final PositionProjectionRepository positions;
    private final OrderExecutionPersisterOrdersClient orders;
    private final AccountReconciliationService reconciliation;

    public PortfolioController(
            TradingAccountProjectionRepository accounts,
            PositionProjectionRepository positions,
            OrderExecutionPersisterOrdersClient orders,
            AccountReconciliationService reconciliation
    ) {
        this.accounts = accounts;
        this.positions = positions;
        this.orders = orders;
        this.reconciliation = reconciliation;
    }

    @GetMapping({"/account", "/portfolio"})
    public Portfolio portfolio() {
        return snapshot();
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        Portfolio p = snapshot();
        return new Dashboard(p.account(), p.positions(), p.asOf(), p.freshness());
    }

    @GetMapping("/orders")
    public List<OrderExecutionPersisterOrdersClient.OrderProjectionResponse> orders() {
        return orders.latestOrders();
    }

    @PostMapping("/account/reconcile")
    public Portfolio reconcile() {
        reconciliation.reconcile();
        return snapshot();
    }

    private Portfolio snapshot() {
        TradingAccountProjection account = accounts.findAll()
                .stream()
                .findFirst()
                .orElse(null);
        Instant asOf = account == null ? null : account.getLastSyncedAt();
        String freshness = asOf == null
                ? "UNAVAILABLE"
                : asOf.isBefore(Instant.now().minus(Duration.ofMinutes(10))) ? "STALE" : "FRESH";
        return new Portfolio(account, positions.findAllByOrderBySymbolAsc(), asOf, freshness);
    }

    public record Portfolio(
            TradingAccountProjection account,
            List<PositionProjection> positions,
            Instant asOf,
            String freshness
    ) {}

    public record Dashboard(
            TradingAccountProjection account,
            List<PositionProjection> positions,
            Instant asOf,
            String freshness
    ) {}
}
