package com.ai_got_this.pnl_service.service;

import java.math.BigDecimal;

public interface BalanceWarningEmitter {
    void balanceBecameNonPositive(String accountId, BigDecimal previousBalance, BigDecimal newBalance,
                                  String executionId, String ticker);
}
