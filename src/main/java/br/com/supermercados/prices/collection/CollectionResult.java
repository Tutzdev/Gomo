package br.com.supermercados.prices.collection;

import java.util.UUID;

public record CollectionResult(
        UUID sourceId,
        UUID storeId,
        int foundCount,
        int createdCount,
        int updatedCount,
        int skippedCount,
        int errorCount,
        String errorMessage,
        int availableCount,
        int unavailableCount,
        int unknownAvailabilityCount,
        int productsUpdatedCount,
        String skipSummary) {

    public CollectionResult(UUID sourceId, UUID storeId, int foundCount, int createdCount,
            int updatedCount, int skippedCount, int errorCount, String errorMessage) {
        this(sourceId, storeId, foundCount, createdCount, updatedCount, skippedCount, errorCount,
                errorMessage, 0, 0, 0, 0, null);
    }
}
