package br.com.supermercados.prices.collection;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "collection_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionRun {

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String collectorCode;

    @Column(nullable = false, length = 160)
    private String supermarketName;

    @Column(nullable = false, length = 160)
    private String storeName;

    private UUID sourceId;

    private UUID storeId;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CollectionStatus status;

    @Column(nullable = false)
    private int foundCount;

    @Column(nullable = false)
    private int createdCount;

    @Column(nullable = false)
    private int updatedCount;

    @Column(nullable = false)
    private int skippedCount;

    @Column(nullable = false)
    private int errorCount;

    @Column(nullable = false)
    private int availableCount;

    @Column(nullable = false)
    private int unavailableCount;

    @Column(nullable = false)
    private int unknownAvailabilityCount;

    @Column(nullable = false)
    private int productsUpdatedCount;

    @Column(length = 2000)
    private String errorMessage;

    /** Every offer was imported, new products included; otherwise only known products were refreshed. */
    @Column(nullable = false)
    private boolean fullImport;

    CollectionRun(CollectorMetadata metadata, Instant startedAt, boolean fullImport) {
        id = UUID.randomUUID();
        collectorCode = metadata.code();
        supermarketName = metadata.supermarketName();
        storeName = metadata.storeName();
        this.startedAt = startedAt;
        this.fullImport = fullImport;
        status = CollectionStatus.RUNNING;
    }

    void finish(CollectionResult result, Instant finishedAt) {
        sourceId = result.sourceId();
        storeId = result.storeId();
        foundCount = result.foundCount();
        createdCount = result.createdCount();
        updatedCount = result.updatedCount();
        skippedCount = result.skippedCount();
        errorCount = result.errorCount();
        availableCount = result.availableCount();
        unavailableCount = result.unavailableCount();
        unknownAvailabilityCount = result.unknownAvailabilityCount();
        productsUpdatedCount = result.productsUpdatedCount();
        errorMessage = cleanMessage(result.errorMessage());
        status = errorCount == 0 ? CollectionStatus.SUCCESS : CollectionStatus.PARTIAL;
        this.finishedAt = finishedAt;
    }

    void fail(String message, Instant finishedAt) {
        errorCount = 1;
        errorMessage = cleanMessage(message);
        status = CollectionStatus.FAILED;
        this.finishedAt = finishedAt;
    }

    private String cleanMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String clean = message.strip();
        return clean.length() <= 2000 ? clean : clean.substring(0, 2000);
    }
}
