package br.com.supermercados.prices.collection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.supermercados.prices.datasource.SourceObservation;
import br.com.supermercados.prices.price.PriceObservation;
import br.com.supermercados.prices.price.PriceRecord;
import br.com.supermercados.prices.price.PriceRecordRepository;
import br.com.supermercados.prices.price.PriceService;
import br.com.supermercados.prices.product.ProductIngestionOutcome;
import br.com.supermercados.prices.product.ProductIngestionResult;
import br.com.supermercados.prices.product.ProductIngestionService;
import br.com.supermercados.prices.product.ProductObservation;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CollectedCatalogIngestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CollectedCatalogIngestionService.class);

    private final CollectionCatalogService catalogs;
    private final ProductIngestionService products;
    private final CollectionReviewRepository reviews;
    private final PriceService prices;
    private final PriceRecordRepository priceRecords;
    private final PriceChangeGuard priceChangeGuard;
    private final PriceObservationReference observationReferences;

    public CollectionResult ingest(CollectorMetadata metadata, CollectedCatalog collectedCatalog) {
        return ingest(metadata, collectedCatalog, false);
    }

    public CollectionResult refreshExisting(CollectorMetadata metadata, CollectedCatalog collectedCatalog) {
        return ingest(metadata, collectedCatalog, true);
    }

    private CollectionResult ingest(CollectorMetadata metadata, CollectedCatalog collectedCatalog,
            boolean existingOnly) {
        CollectionCatalog catalog = catalogs.ensureCatalog(
                metadata, collectedCatalog.store(), collectedCatalog.collectedAt());
        MutableResult result = new MutableResult(collectedCatalog.foundCount());
        collectedCatalog.products().forEach(product -> {
            switch (product.availability()) {
                case AVAILABLE -> result.availableCount++;
                case UNAVAILABLE -> result.unavailableCount++;
                case UNKNOWN -> result.unknownAvailabilityCount++;
            }
        });
        collectedCatalog.warnings().forEach(result::addSkippedError);

        List<ProductPrice> collectedPrices = ingestProducts(
                metadata, collectedCatalog, catalog, result, existingOnly);
        ingestPrices(metadata, collectedCatalog, catalog, collectedPrices, result);

        return result.toResult(catalog.sourceId(), catalog.storeId());
    }

    private List<ProductPrice> ingestProducts(
            CollectorMetadata metadata,
            CollectedCatalog catalog,
            CollectionCatalog collection,
            MutableResult result, boolean existingOnly) {
        List<ProductPrice> collectedPrices = new ArrayList<>();

        for (CollectedProduct collectedProduct : catalog.products()) {
            try {
                SourceObservation source = new SourceObservation(
                        collection.sourceId(), collectedProduct.sourceReference(), catalog.collectedAt());
                ProductObservation observation = new ProductObservation(
                        collectedProduct.gtin(), collectedProduct.name(), collectedProduct.brand(),
                        collectedProduct.description(), null, null, collectedProduct.category(), source,
                        collectedProduct.imageUrl(), collectedProduct.originUrl());
                var matched = existingOnly ? products.linkExisting(observation)
                        : java.util.Optional.of(products.ingestWithOutcome(observation));
                if (matched.isEmpty()) {
                    reviews.record(collection.sourceId(), collection.storeId(), collectedProduct, catalog.collectedAt(),
                            "Sem correspondência confirmada no catálogo existente; revisar identidade e apresentação.");
                    result.skippedCount++;
                    continue;
                }
                ProductIngestionResult ingestion = matched.orElseThrow();
                reviews.resolve(collection.sourceId(), collection.storeId(), collectedProduct.sourceReference());
                if (ingestion.outcome() == ProductIngestionOutcome.CREATED) {
                    result.createdCount++;
                } else if (ingestion.outcome() == ProductIngestionOutcome.UPDATED) {
                    result.productsUpdatedCount++;
                }
                collectedPrices.add(new ProductPrice(ingestion.product().id(), collectedProduct));
            } catch (RuntimeException exception) {
                reviews.record(collection.sourceId(), collection.storeId(), collectedProduct, catalog.collectedAt(), safeMessage(exception));
                result.addSkippedError("Produto " + collectedProduct.sourceReference() + " ignorado: "
                        + safeMessage(exception));
                LOGGER.warn("Coletor {} ignorou produto {}: {}", metadata.code(),
                        collectedProduct.sourceReference(), safeMessage(exception));
            }
        }
        return collectedPrices;
    }

    private void ingestPrices(
            CollectorMetadata metadata,
            CollectedCatalog collectedCatalog,
            CollectionCatalog catalog,
            List<ProductPrice> collectedPrices,
            MutableResult result) {
        if (collectedPrices.isEmpty()) {
            return;
        }

        Map<UUID, PriceRecord> latestPrices = latestPrices(catalog, collectedPrices);
        Map<ProductPrice, String> references = new LinkedHashMap<>();
        for (ProductPrice collectedPrice : collectedPrices) {
            references.put(collectedPrice, observationReferences.create(
                    metadata.code(), metadata.storeSourceReference(),
                    collectedPrice.collectedProduct(), collectedCatalog.collectedAt()));
        }
        Set<String> existingReferences = existingReferences(catalog.sourceId(), references.values());

        for (Map.Entry<ProductPrice, String> entry : references.entrySet()) {
            ProductPrice collectedPrice = entry.getKey();
            CollectedProduct product = collectedPrice.collectedProduct();
            String sourceReference = entry.getValue();
            PriceRecord previous = latestPrices.get(collectedPrice.productId());
            if (!existingReferences.contains(sourceReference)
                    && priceChangeGuard.isSuspicious(product.regularPrice(), previous)) {
                result.skippedCount++;
                result.addError("Variação suspeita no produto " + product.sourceReference());
                reviews.record(catalog.sourceId(), catalog.storeId(), product, collectedCatalog.collectedAt(),
                        "Variação de preço exige revisão antes da publicação.");
                LOGGER.warn("Coletor {} rejeitou variação suspeita do produto {}",
                        metadata.code(), product.sourceReference());
                continue;
            }

            try {
                prices.appendObservation(new PriceObservation(
                        collectedPrice.productId(), catalog.storeId(), catalog.sourceId(), sourceReference,
                        product.regularPrice(), product.promotionalPrice(), "BRL",
                        collectedCatalog.collectedAt(), product.validUntil(),
                        product.promotionValidUntil(), product.promotionCondition(), product.availability(),
                        product.originUrl(), product.sourceReference(), product.name()));
                if (existingReferences.contains(sourceReference)) {
                    result.skippedCount++;
                } else {
                    result.updatedCount++;
                    existingReferences.add(sourceReference);
                }
            } catch (RuntimeException exception) {
                result.addSkippedError("Preço do produto " + product.sourceReference() + " ignorado: "
                        + safeMessage(exception));
                reviews.record(catalog.sourceId(), catalog.storeId(), product, collectedCatalog.collectedAt(), safeMessage(exception));
                LOGGER.warn("Coletor {} ignorou preço do produto {}: {}", metadata.code(),
                        product.sourceReference(), safeMessage(exception));
            }
        }
    }

    private Map<UUID, PriceRecord> latestPrices(CollectionCatalog catalog, List<ProductPrice> collectedPrices) {
        Set<UUID> productIds = new HashSet<>();
        collectedPrices.forEach(product -> productIds.add(product.productId()));
        Map<UUID, PriceRecord> latestPrices = new HashMap<>();
        priceRecords.findLatestForSourceStoreAndProducts(
                catalog.sourceId(), catalog.storeId(), productIds)
                .forEach(record -> latestPrices.put(record.getProductId(), record));
        return latestPrices;
    }

    private Set<String> existingReferences(UUID sourceId, java.util.Collection<String> references) {
        Set<String> existing = new HashSet<>();
        priceRecords.findAllBySourceIdAndSourceReferenceIn(sourceId, references)
                .forEach(record -> existing.add(record.getSourceReference()));
        return existing;
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private record ProductPrice(UUID productId, CollectedProduct collectedProduct) {
    }

    private static final class MutableResult {

        private final int foundCount;
        private final List<String> errors = new ArrayList<>();
        private int createdCount;
        private int updatedCount;
        private int skippedCount;
        private int availableCount;
        private int unavailableCount;
        private int unknownAvailabilityCount;
        private int productsUpdatedCount;

        private MutableResult(int foundCount) {
            this.foundCount = foundCount;
        }

        private void addError(String message) {
            errors.add(message);
        }

        private void addSkippedError(String message) {
            skippedCount++;
            addError(message);
        }

        private CollectionResult toResult(UUID sourceId, UUID storeId) {
            String errorMessage = errors.isEmpty() ? null : String.join(" | ", errors);
            return new CollectionResult(sourceId, storeId, foundCount, createdCount,
                    updatedCount, skippedCount, errors.size(), errorMessage,
                    availableCount, unavailableCount, unknownAvailabilityCount, productsUpdatedCount);
        }
    }
}
