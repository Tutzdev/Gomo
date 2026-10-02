package br.com.supermercados.prices.comparison;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

import br.com.supermercados.prices.common.PageResponse;
import br.com.supermercados.prices.product.ProductResponse;
import br.com.supermercados.prices.subscription.ComparisonAccess;

public record ProductComparisonResponse(
        UUID productId,
        String productName,
        UUID cityId,
        String currency,
        Instant comparedAt,
        PageResponse<ProductStoreComparison> stores,
        List<ProductResponse> possibleMatches,
        ComparisonAccess access) {
}
