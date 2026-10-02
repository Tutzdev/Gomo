package br.com.supermercados.prices.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A generic product as the shopper sees it, with the price range among the selected stores.
 * {@code productId} is a representative SKU for screens that still work per product.
 */
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
        Instant pricesCollectedAt) {
}
