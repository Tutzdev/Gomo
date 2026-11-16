package br.com.supermercados.prices.storevisit;

import java.time.Instant;
import java.util.UUID;

public record StoreVisitResponse(
        UUID storeId,
        Instant observedAt,
        int recordedPrices,
        Instant currentUntil) {}
