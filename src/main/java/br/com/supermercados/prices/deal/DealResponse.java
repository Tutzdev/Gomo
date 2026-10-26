package br.com.supermercados.prices.deal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A product cheaper than usual at one store: {@code price} against the {@code usualPrice} of the city.
 *
 * @param discountPercent how much under the usual price, rounded
 * @param promotion whether the price is the store's promotion
 * @param lowestIn90Days as cheap as or cheaper than every day of the last 90
 */
public record DealResponse(
        UUID itemId,
        String name,
        String brand,
        String sizeLabel,
        String category,
        String imageUrl,
        UUID storeId,
        String storeName,
        BigDecimal price,
        BigDecimal usualPrice,
        int discountPercent,
        boolean promotion,
        boolean lowestIn90Days,
        Instant collectedAt) {
}
