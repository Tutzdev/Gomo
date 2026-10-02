package br.com.supermercados.prices.comparison;

import java.time.Instant;
import java.util.UUID;

import br.com.supermercados.prices.common.PageResponse;
import br.com.supermercados.prices.subscription.ComparisonAccess;

public record ShoppingListComparisonResponse(
        UUID shoppingListId,
        UUID cityId,
        String currency,
        Instant comparedAt,
        PageResponse<ShoppingStoreComparison> stores,
        ComparisonAccess access) {
}
