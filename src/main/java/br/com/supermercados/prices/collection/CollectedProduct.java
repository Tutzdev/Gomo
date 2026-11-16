package br.com.supermercados.prices.collection;

import java.math.BigDecimal;
import java.time.Instant;

import br.com.supermercados.prices.price.StockAvailability;

public record CollectedProduct(
        String sourceReference,
        String name,
        String gtin,
        String brand,
        String description,
        String category,
        BigDecimal regularPrice,
        BigDecimal promotionalPrice,
        String promotionCondition,
        Instant validUntil,
        Instant promotionValidUntil,
        StockAvailability availability,
        String imageUrl,
        String originUrl) {

    public CollectedProduct(
        String sourceReference,
        String name,
        String gtin,
        String brand,
        String description,
        String category,
        BigDecimal regularPrice,
        BigDecimal promotionalPrice,
        String promotionCondition,
        Instant validUntil,
        Instant promotionValidUntil,
        StockAvailability availability) {

        this(
            sourceReference,
            name,
            gtin,
            brand,
            description,
            category,
            regularPrice,
            promotionalPrice,
            promotionCondition,
            validUntil,
            promotionValidUntil,
            availability,
            null,
            null);
    }
}
