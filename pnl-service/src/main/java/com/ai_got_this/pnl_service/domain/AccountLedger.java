package com.ai_got_this.pnl_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "account_ledger")
public class AccountLedger {
    @Id
    @Column(name = "account_id", nullable = false, updatable = false)
    private String accountId;

    @Column(name = "cash_balance", nullable = false, precision = 24, scale = 8)
    private BigDecimal cashBalance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AccountLedger() {
    }

    public AccountLedger(String accountId, BigDecimal cashBalance, Instant createdAt) {
        this.accountId = accountId;
        this.cashBalance = cashBalance;
        this.createdAt = createdAt;
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public void decreaseCash(BigDecimal amount) {
        cashBalance = cashBalance.subtract(amount);
    }

    public void increaseCash(BigDecimal amount) {
        cashBalance = cashBalance.add(amount);
    }
}
