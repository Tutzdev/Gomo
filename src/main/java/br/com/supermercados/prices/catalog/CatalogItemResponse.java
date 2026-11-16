package br.com.supermercados.prices.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CatalogItemResponse(
        UUID id,
        String name,
        String brand,
        String sizeLabel,
        String category,
        String imageUrl,
        UUID productId,
        int storeCount,
        int pricedStores,
        BigDecimal lowestPrice,
        BigDecimal highestPrice,
        String lowestPriceStore,
        Instant pricesCollectedAt) {}
