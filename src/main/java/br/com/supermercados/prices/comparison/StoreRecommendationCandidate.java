package br.com.supermercados.prices.comparison;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import br.com.supermercados.prices.price.StockAvailability;

public record StoreRecommendationCandidate(
        UUID storeId,
        String storeName,
        BigDecimal total,
        int requestedItems,
        int pricedItems,
        int missingItems,
        List<UUID> missingProductIds,
        boolean completeShoppingList,
        boolean stockUncertain) {

    public StoreRecommendationCandidate {
        missingProductIds = List.copyOf(missingProductIds);
    }

    static StoreRecommendationCandidate from(ShoppingStoreComparison comparison) {
        List<UUID> missingProductIds = comparison.items().stream()
                .filter(item -> item.lineTotal() == null)
                .map(ComparisonItemResponse::productId)
                .toList();

        boolean stockUncertain = comparison.items().stream()
                .filter(item -> item.lineTotal() != null)
                .anyMatch(item -> item.price().availability() == StockAvailability.UNKNOWN);

        return new StoreRecommendationCandidate(
            comparison.storeId(),
            comparison.storeName(),
            comparison.subtotalKnown(),
            comparison.requestedItems(),
            comparison.pricedItems(),
            comparison.missingItems(),
            missingProductIds,
            comparison.completeShoppingList(),
            stockUncertain);
    }
}
