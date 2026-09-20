package br.com.supermercados.prices.comparison;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Item counts refer to distinct shopping-list lines; each line retains its requested quantity. */
public record ShoppingStoreComparison(
        UUID storeId,
        String storeName,
        int requestedItems,
        int pricedItems,
        int missingItems,
        BigDecimal subtotalKnown,
        boolean completeShoppingList,
        List<ComparisonItemResponse> items,
        String priceSourceNote) {

    public ShoppingStoreComparison(UUID storeId, String storeName, int requestedItems, int pricedItems,
            int missingItems, BigDecimal subtotalKnown, boolean completeShoppingList, List<ComparisonItemResponse> items) {
        this(storeId, storeName, requestedItems, pricedItems, missingItems, subtotalKnown, completeShoppingList, items, null);
    }

    public ShoppingStoreComparison {
        items = List.copyOf(items);
    }
}
