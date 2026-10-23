package br.com.supermercados.prices.store;

/** Whether a market's prices can be compared right now, as shown on the markets page. */
public enum StorePriceStatus {
    /** The newest price is within the price max age: the market is in searches and comparisons. */
    CURRENT,
    /** The market had prices, but its collection stopped and they expired ("fora do ar"). */
    OUTDATED,
    /** No price was ever collected for this market. */
    NO_PRICES
}
