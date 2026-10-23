package br.com.supermercados.prices.store;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A store; {@code priceStatus} is only filled where shoppers see it (the markets list and page). */
public record StoreResponse(
        UUID id,
        UUID supermarketChainId,
        UUID cityId,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean active,
        UUID sourceId,
        String sourceReference,
        Instant collectedAt,
        Instant updatedAt,
        Instant lastPriceCollectedAt,
        String priceSourceNote,
        StorePriceStatus priceStatus) {

    public StoreResponse(UUID id, UUID supermarketChainId, UUID cityId, String name, String address,
            BigDecimal latitude, BigDecimal longitude, boolean active, UUID sourceId, String sourceReference,
            Instant collectedAt, Instant updatedAt, Instant lastPriceCollectedAt) {
        this(id, supermarketChainId, cityId, name, address, latitude, longitude, active, sourceId,
                sourceReference, collectedAt, updatedAt, lastPriceCollectedAt, null, null);
    }

    public static StoreResponse from(Store store) {
        return from(store, null);
    }

    public static StoreResponse from(Store store, Instant lastPriceCollectedAt) {
        return from(store, lastPriceCollectedAt, null);
    }

    public static StoreResponse from(Store store, Instant lastPriceCollectedAt, StorePriceStatus priceStatus) {
        return new StoreResponse(store.getId(), store.getSupermarketChainId(), store.getCityId(), store.getName(),
                store.getAddress(), store.getLatitude(), store.getLongitude(), store.isActive(),
                store.getSourceId(), store.getSourceReference(), store.getCollectedAt(), store.getUpdatedAt(),
                lastPriceCollectedAt, store.getPriceSourceNote(), priceStatus);
    }
}
