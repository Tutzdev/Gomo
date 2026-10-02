package br.com.supermercados.prices.subscription;

import jakarta.validation.constraints.NotNull;

public record StartTrialRequest(@NotNull BillingCycle billingCycle) {
}
