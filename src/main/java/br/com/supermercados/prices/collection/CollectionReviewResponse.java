package br.com.supermercados.prices.collection;

import java.time.Instant;
import java.util.UUID;

public record CollectionReviewResponse(UUID sourceId, UUID storeId, String sourceReference,
        Instant collectedAt, String reason, CollectedProduct offer) {
}
