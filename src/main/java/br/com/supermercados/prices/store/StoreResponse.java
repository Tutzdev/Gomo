package br.com.supermercados.prices.store;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
        String priceSourceNote) {

    public StoreResponse(UUID id, UUID supermarketChainId, UUID cityId, String name, String address,
            BigDecimal latitude, BigDecimal longitude, boolean active, UUID sourceId, String sourceReference,
            Instant collectedAt, Instant updatedAt, Instant lastPriceCollectedAt) {
        this(id, supermarketChainId, cityId, name, address, latitude, longitude, active, sourceId,
                sourceReference, collectedAt, updatedAt, lastPriceCollectedAt, null);
    }

    public static StoreResponse from(Store store) {
        return from(store, null);
    }

    public static StoreResponse from(Store store, Instant lastPriceCollectedAt) {
        return new StoreResponse(store.getId(), store.getSupermarketChainId(), store.getCityId(), store.getName(),
                store.getAddress(), store.getLatitude(), store.getLongitude(), store.isActive(),
                store.getSourceId(), store.getSourceReference(), store.getCollectedAt(), store.getUpdatedAt(),
                lastPriceCollectedAt, store.getPriceSourceNote());
    }
}
