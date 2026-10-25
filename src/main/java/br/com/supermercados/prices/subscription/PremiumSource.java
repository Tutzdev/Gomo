package br.com.supermercados.prices.subscription;

/**
 * Why an account currently has Premium access; {@code NONE} means it is on the free plan. {@code BETA} is the
 * open beta, while there is no charging (see {@link PremiumAccess}).
 */
public enum PremiumSource {
    SUBSCRIPTION,
    TRIAL,
    ADMIN,
    BETA,
    NONE
}
