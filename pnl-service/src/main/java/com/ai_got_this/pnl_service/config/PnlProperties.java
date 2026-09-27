package com.ai_got_this.pnl_service.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("measurement.pnl")
public class PnlProperties {
    private String accountId = "paper-account";
    private BigDecimal startingBalance = new BigDecimal("100000.00");

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getStartingBalance() {
        return startingBalance;
    }

    public void setStartingBalance(BigDecimal startingBalance) {
        this.startingBalance = startingBalance;
    }
}
