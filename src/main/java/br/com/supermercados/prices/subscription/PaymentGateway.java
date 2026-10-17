package br.com.supermercados.prices.subscription;

import java.net.URI;

/** The payment provider behind paid Premium. Calls go over the network and never run inside a transaction. */
public interface PaymentGateway {

    /** False while the provider is not configured; the plans page then offers only the free trial. */
    boolean available();

    /** Opens the provider's hosted page where the person pays the first cycle and the subscription starts. */
    Checkout openSubscriptionCheckout(BillingCycle cycle);

    /** Stops future charges right away. */
    void cancelSubscription(String subscriptionId);

    /** @param id the provider's checkout id, which the confirmation webhook carries back */
    record Checkout(String id, URI url) {
    }
}
