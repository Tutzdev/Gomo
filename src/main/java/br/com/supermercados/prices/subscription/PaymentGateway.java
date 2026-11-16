package br.com.supermercados.prices.subscription;

import java.net.URI;

public interface PaymentGateway {

    boolean available();

    Checkout openSubscriptionCheckout(BillingCycle cycle);

    void cancelSubscription(String subscriptionId);

    record Checkout(String id, URI url) {}
}
