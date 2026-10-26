package br.com.supermercados.prices.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A list at the market the person chose, for the "No mercado" mode: what each item should cost there and what
 * is already in the cart.
 *
 * @param expectedTotal the items with a price at this store
 */
public record TripResponse(
        UUID listId,
        String listName,
        UUID storeId,
        String storeName,
        UUID cityId,
        List<TripItem> items,
        int pricedItems,
        BigDecimal expectedTotal) {

    public TripResponse {
        items = List.copyOf(items);
    }

    /**
     * @param storeProductId the SKU this store sells for the item, the one a shelf-price contribution is about
     * @param unitPrice {@code null} when Gomo has no current price for it at this store
     */
    public record TripItem(
            UUID itemId,
            UUID productId,
            String productName,
            BigDecimal quantity,
            UUID catalogItemId,
            String imageUrl,
            UUID storeProductId,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            boolean promotion,
            Instant checkedAt,
            UUID checkedBy) {
    }
}
