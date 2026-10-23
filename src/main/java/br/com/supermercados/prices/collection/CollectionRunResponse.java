package br.com.supermercados.prices.collection;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CollectionRunResponse(
        UUID id,
        String collectorCode,
        String supermarketName,
        String storeName,
        UUID sourceId,
        UUID storeId,
        Instant startedAt,
        Instant finishedAt,
        CollectionStatus status,
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

    @JsonProperty("observationsSkippedCount")
    public int observationsSkippedCount() {
        return Math.max(0, skippedCount - errorCount);
    }

    @JsonProperty("observationsInsertedCount")
    public int observationsInsertedCount() {
        return updatedCount;
    }

    public CollectionRunResponse(UUID id, String collectorCode, String supermarketName, String storeName,
            UUID sourceId, UUID storeId, Instant startedAt, Instant finishedAt, CollectionStatus status,
            int foundCount, int createdCount, int updatedCount, int skippedCount, int errorCount, String errorMessage) {
        this(id, collectorCode, supermarketName, storeName, sourceId, storeId, startedAt, finishedAt, status,
                foundCount, createdCount, updatedCount, skippedCount, errorCount, errorMessage, 0, 0, 0, 0, null);
    }

    static CollectionRunResponse from(CollectionRun run) {
        return new CollectionRunResponse(run.getId(), run.getCollectorCode(),
                run.getSupermarketName(), run.getStoreName(), run.getSourceId(), run.getStoreId(),
                run.getStartedAt(), run.getFinishedAt(), run.getStatus(), run.getFoundCount(),
                run.getCreatedCount(), run.getUpdatedCount(), run.getSkippedCount(),
                run.getErrorCount(), run.getErrorMessage(), run.getAvailableCount(), run.getUnavailableCount(),
                run.getUnknownAvailabilityCount(), run.getProductsUpdatedCount(), run.getSkipSummary());
    }
}
