package br.com.supermercados.prices.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String gtin,
        String name,
        String normalizedName,
        String brand,
        String normalizedBrand,
        String description,
        String unit,
        BigDecimal quantity,
        String packageDescription,
        String category,
        UUID sourceId,
        String sourceReference,
        Instant collectedAt,
        Instant updatedAt,
        String imageUrl,
        String originUrl) {

    public static ProductResponse from(Product product) {
        return from(product, product.getName());
    }

    public static ProductResponse from(Product product, String displayName) {
        return new ProductResponse(
                product.getId(),
                product.getGtin(),
                displayName,
                product.getNormalizedName(),
                product.getBrand(),
                product.getNormalizedBrand(),
                product.getDescription(),
                product.getUnit(),
                product.getQuantity(),
                product.getPackageDescription(),
                product.getCategory(),
                product.getSourceId(),
                product.getSourceReference(),
                product.getCollectedAt(),
                product.getUpdatedAt(),
                product.getImageUrl(),
                product.getOriginUrl());
    }
}
