package br.com.supermercados.prices.alert;

/** What makes an alert fire. */
public enum PriceAlertKind {
    /** A price at or below the amount the person chose. */
    TARGET_PRICE,
    /** A price at least {@code discountPercent}% below the 30-day average of the city's daily lowest price. */
    BELOW_AVERAGE
}
