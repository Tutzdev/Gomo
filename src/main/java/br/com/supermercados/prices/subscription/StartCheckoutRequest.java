package br.com.supermercados.prices.subscription;

import jakarta.validation.constraints.NotNull;

public record StartCheckoutRequest(@NotNull BillingCycle billingCycle) {
}
