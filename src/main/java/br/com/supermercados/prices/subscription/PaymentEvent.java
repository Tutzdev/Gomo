package br.com.supermercados.prices.subscription;

import java.time.Instant;

/**
 * What a provider webhook says about a subscription, already authenticated.
 *
 * @param id the delivery's id, repeated on every retry of the same event
 * @param checkoutId the checkout that started the subscription (on {@code SUBSCRIPTION_STARTED})
 * @param cycle the subscription's cycle, when the event states it
 * @param paidAt when the payment was confirmed, when the event states it
 * @param paymentsExhausted cancelled by the provider after the renewal retries failed
 */
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
