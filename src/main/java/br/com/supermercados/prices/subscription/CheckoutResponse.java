package br.com.supermercados.prices.subscription;

import java.net.URI;

/** Where to send the person to pay; the provider brings them back to the plans page. */
public record CheckoutResponse(URI checkoutUrl) {
}
