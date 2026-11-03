package br.com.supermercados.prices.store;

/** Where a market's prices come from: its own online store, or the shelf of a market that sells only in person. */
public enum StorePriceChannel {
    /** Collected from the market's own website. */
    ONLINE,
    /** Written down at the shelf by the Gomo team; see {@code app.prices.in-store-max-age}. */
    IN_STORE
}
