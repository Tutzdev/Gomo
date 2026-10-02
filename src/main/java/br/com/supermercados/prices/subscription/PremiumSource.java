package br.com.supermercados.prices.subscription;

/** Why an account currently has Premium access; {@code NONE} means it is on the free plan. */
public enum PremiumSource {
    SUBSCRIPTION,
    TRIAL,
    ADMIN,
    NONE
}
