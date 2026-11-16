package br.com.supermercados.prices.subscription;

import java.time.Instant;

public record PaymentEvent(
        String id,
        Type type,
        String subscriptionId,
        String checkoutId,
        BillingCycle cycle,
        Instant paidAt,
        boolean paymentsExhausted) {

    public enum Type {
        SUBSCRIPTION_STARTED,
        SUBSCRIPTION_RENEWED,
        SUBSCRIPTION_CANCELLED,
        PAYMENT_FAILED,
        OTHER
    }
}
