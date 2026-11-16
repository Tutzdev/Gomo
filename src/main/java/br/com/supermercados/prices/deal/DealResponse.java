package br.com.supermercados.prices.deal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
        Instant collectedAt) {}
