package com.ai_got_this.pnl_service.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class RecordingBalanceWarningEmitter implements BalanceWarningEmitter {
    final List<Warning> warnings = new ArrayList<>();

    @Override
    public void balanceBecameNonPositive(String accountId, BigDecimal previousBalance, BigDecimal newBalance,
                                         String executionId, String ticker) {
        warnings.add(new Warning(accountId, previousBalance, newBalance, executionId, ticker));
    }

    record Warning(
            String accountId,
            BigDecimal previousBalance,
            BigDecimal newBalance,
            String executionId,
            String ticker
    ) {
    }
}
