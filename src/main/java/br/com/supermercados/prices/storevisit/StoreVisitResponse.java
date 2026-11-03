package br.com.supermercados.prices.storevisit;

import java.time.Instant;
import java.util.UUID;

/** {@code currentUntil}: when these prices leave searches and comparisons unless the market is visited again. */
public record StoreVisitResponse(UUID storeId, Instant observedAt, int recordedPrices, Instant currentUntil) {
}
