package br.com.supermercados.prices.comparison;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The cheapest store for each item. When {@code locked}, the plan hides which store sells each item: only the
 * totals, the savings and {@code storeCount} remain.
 */
public record ShoppingCombinationResponse(
        int requestedItems,
        int pricedItems,
        List<UUID> missingProductIds,
        BigDecimal subtotalKnown,
        boolean completeShoppingList,
        BigDecimal savingsAgainstCompleteStore,
        List<StorePurchase> stores,
        boolean locked,
        int storeCount) {

    public ShoppingCombinationResponse {
        missingProductIds = List.copyOf(missingProductIds);
        stores = List.copyOf(stores);
    }

    public ShoppingCombinationResponse(int requestedItems, int pricedItems, List<UUID> missingProductIds,
            BigDecimal subtotalKnown, boolean completeShoppingList, BigDecimal savingsAgainstCompleteStore,
            List<StorePurchase> stores) {
        this(requestedItems, pricedItems, missingProductIds, subtotalKnown, completeShoppingList,
                savingsAgainstCompleteStore, stores, false, stores.size());
    }

    public ShoppingCombinationResponse lockedPreview() {
        return new ShoppingCombinationResponse(requestedItems, pricedItems, missingProductIds, subtotalKnown,
                completeShoppingList, savingsAgainstCompleteStore, List.of(), true, stores.size());
    }

    public record StorePurchase(UUID storeId, String storeName, BigDecimal subtotal,
            List<ComparisonItemResponse> items) {

        public StorePurchase {
            items = List.copyOf(items);
        }
    }
}
