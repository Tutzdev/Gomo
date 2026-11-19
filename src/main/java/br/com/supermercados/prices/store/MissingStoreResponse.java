package br.com.supermercados.prices.store;

import java.time.Instant;
import java.util.UUID;

public record MissingStoreResponse(
        UUID storeId,
        String storeName,
        MissingPriceReason reason,
        Instant lastPriceCollectedAt) {}
