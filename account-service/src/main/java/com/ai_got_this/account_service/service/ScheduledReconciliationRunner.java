package com.ai_got_this.account_service.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledReconciliationRunner {
    private final AccountReconciliationService reconciliation;

    public ScheduledReconciliationRunner(AccountReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @Scheduled(fixedDelayString = "${alpaca.reconciliation-interval:PT5M}")
    public void reconcileConfiguredAccount() {
        reconciliation.reconcile();
    }
}
