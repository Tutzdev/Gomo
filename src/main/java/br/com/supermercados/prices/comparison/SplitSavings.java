package br.com.supermercados.prices.comparison;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * How much less the {@code comparedItems} items cost when each one is bought where it is cheapest, instead of
 * all at the best single store (the cheapest complete store, or the one that covers the most items). Unlike
 * {@code savingsAgainstCompleteStore}, it exists even when no store has the whole list.
 */
public record SplitSavings(UUID storeId, String storeName, int comparedItems, BigDecimal savings) {
}
