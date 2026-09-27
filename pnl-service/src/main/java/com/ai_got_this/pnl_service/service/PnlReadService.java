package com.ai_got_this.pnl_service.service;

import com.ai_got_this.pnl_service.config.PnlProperties;
import com.ai_got_this.pnl_service.domain.OpenLot;
import com.ai_got_this.pnl_service.domain.SymbolPnl;
import com.ai_got_this.pnl_service.repository.PnlStore;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PnlReadService {
    private final PnlStore store;
    private final PnlProperties properties;

    public PnlReadService(PnlStore store, PnlProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    public PortfolioPnl portfolio() {
        List<SymbolPnl> symbols = store.findAllSymbols();
        Map<String, SymbolPnl> symbolsByTicker = symbols.stream()
                .collect(Collectors.toMap(SymbolPnl::getTicker, Function.identity()));
        Map<String, List<OpenLot>> lotsByTicker = store.findAllOpenLots().stream()
                .collect(Collectors.groupingBy(OpenLot::getTicker));
        List<SymbolPnlView> perSymbol = new ArrayList<>();
        for (String ticker : union(symbolsByTicker.keySet(), lotsByTicker.keySet())) {
            SymbolPnl symbol = symbolsByTicker.get(ticker);
            List<OpenLot> lots = lotsByTicker.getOrDefault(ticker, List.of());
            BigDecimal openQuantity = lots.stream()
                    .map(OpenLot::getRemainingQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal costBasis = lots.stream()
                    .map(lot -> lot.getUnitCost().multiply(lot.getRemainingQuantity()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal unrealized = lots.stream()
                    .map(lot -> unrealized(lot, symbol))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            perSymbol.add(new SymbolPnlView(
                    ticker,
                    openQuantity,
                    costBasis,
                    symbol == null ? BigDecimal.ZERO : symbol.getRealizedPnl(),
                    unrealized,
                    symbol == null ? null : symbol.getLatestMarketPrice()));
        }
        BigDecimal realized = perSymbol.stream()
                .map(SymbolPnlView::realizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unrealized = perSymbol.stream()
                .map(SymbolPnlView::unrealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var ledger = store.findLedger(properties.getAccountId());
        return new PortfolioPnl(
                properties.getAccountId(),
                ledger == null ? properties.getStartingBalance() : ledger.getCashBalance(),
                realized,
                unrealized,
                perSymbol);
    }

    private static BigDecimal unrealized(OpenLot lot, SymbolPnl symbol) {
        if (symbol == null || symbol.getLatestMarketPrice() == null) {
            return BigDecimal.ZERO;
        }
        return symbol.getLatestMarketPrice().subtract(lot.getUnitCost()).multiply(lot.getRemainingQuantity());
    }

    private static List<String> union(java.util.Set<String> first, java.util.Set<String> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).distinct().sorted().toList();
    }

    public record PortfolioPnl(
            String accountId,
            BigDecimal cashBalance,
            BigDecimal realizedPnl,
            BigDecimal unrealizedPnl,
            List<SymbolPnlView> symbols
    ) {
    }

    public record SymbolPnlView(
            String ticker,
            BigDecimal openQuantity,
            BigDecimal costBasis,
            BigDecimal realizedPnl,
            BigDecimal unrealizedPnl,
            BigDecimal latestMarketPrice
    ) {
    }
}
