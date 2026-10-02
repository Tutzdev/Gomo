package br.com.supermercados.prices.subscription;

import java.math.BigDecimal;

/**
 * What Premium would save on the person's own lists, from current prices: buying each item where it is cheapest,
 * across {@code storeCount} stores, costs {@code savings} less than the best single store. Empty when no list
 * gets cheaper by splitting.
 */
public record PremiumValueResponse(String listName, BigDecimal savings, int storeCount) {

    public static PremiumValueResponse none() {
        return new PremiumValueResponse(null, null, 0);
    }
}
