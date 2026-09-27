package com.ai_got_this.pnl_service.service;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StructuredBalanceWarningLogger implements BalanceWarningEmitter {
    private static final Logger log = LoggerFactory.getLogger(StructuredBalanceWarningLogger.class);

    @Override
    public void balanceBecameNonPositive(String accountId, BigDecimal previousBalance, BigDecimal newBalance,
                                         String executionId, String ticker) {
        log.atWarn().addKeyValue("eventType", "paper_balance_non_positive_transition")
                .addKeyValue("accountId", accountId).addKeyValue("previousBalance", previousBalance)
                .addKeyValue("newBalance", newBalance).addKeyValue("executionId", executionId)
                .addKeyValue("ticker", ticker).log("paper_balance_non_positive_transition");
    }
}
