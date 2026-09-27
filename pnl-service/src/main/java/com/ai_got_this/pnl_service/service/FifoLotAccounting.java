package com.ai_got_this.pnl_service.service;

import com.ai_got_this.pnl_service.domain.AccountLedger;
import com.ai_got_this.pnl_service.domain.InsufficientLongPositionException;
import com.ai_got_this.pnl_service.domain.LotMatch;
import com.ai_got_this.pnl_service.domain.OpenLot;
import com.ai_got_this.pnl_service.domain.Side;
import com.ai_got_this.pnl_service.domain.SymbolPnl;
import com.ai_got_this.pnl_service.dto.ExecutionFillEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FifoLotAccounting {
    public FillAccountingChange apply(AccountLedger ledger, SymbolPnl symbol, List<OpenLot> suppliedLots,
                                      ExecutionFillEvent event, Side side) {
        List<OpenLot> lots = suppliedLots.stream()
                .filter(lot -> lot.getRemainingQuantity().signum() > 0)
                .sorted(Comparator.comparing(OpenLot::getOpenedAt))
                .toList();
        BigDecimal consideration = event.fillPrice().multiply(event.newlyFilledQuantity());

        if (side == Side.BUY) {
            OpenLot created = new OpenLot(event.ticker(), event.executionId(), event.timestamp(), event.fillPrice(),
                    event.newlyFilledQuantity());
            ledger.decreaseCash(consideration);
            return new FillAccountingChange(List.of(created), List.of(), BigDecimal.ZERO);
        }

        BigDecimal available = lots.stream()
                .map(OpenLot::getRemainingQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.compareTo(event.newlyFilledQuantity()) < 0) {
            throw new InsufficientLongPositionException("sell quantity exceeds tracked long lots");
        }

        BigDecimal remaining = event.newlyFilledQuantity();
        BigDecimal realized = BigDecimal.ZERO;
        List<LotMatch> matches = new ArrayList<>();
        for (OpenLot lot : lots) {
            if (remaining.signum() == 0) {
                break;
            }
            BigDecimal matched = remaining.min(lot.getRemainingQuantity());
            BigDecimal matchPnl = event.fillPrice().subtract(lot.getUnitCost()).multiply(matched);
            lot.closeQuantity(matched);
            matches.add(new LotMatch(event.executionId(), lot.getId(), event.ticker(), matched,
                    lot.getUnitCost(), event.fillPrice(), matchPnl));
            realized = realized.add(matchPnl);
            remaining = remaining.subtract(matched);
        }
        ledger.increaseCash(consideration);
        symbol.addRealizedPnl(realized);
        return new FillAccountingChange(List.of(), matches, realized);
    }

    public record FillAccountingChange(
            List<OpenLot> createdLots,
            List<LotMatch> matches,
            BigDecimal realizedPnl
    ) {
    }
}
