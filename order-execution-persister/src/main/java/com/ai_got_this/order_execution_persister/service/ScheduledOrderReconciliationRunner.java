package com.ai_got_this.order_execution_persister.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledOrderReconciliationRunner {
    private final OrderReconciliationService reconciliation;

    public ScheduledOrderReconciliationRunner(OrderReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    //@Scheduled(fixedDelayString = "${order-reconciliation.interval:PT5M}")
    public void reconcileConfiguredAccount() {
        reconciliation.reconcileOrders();
    }
}
