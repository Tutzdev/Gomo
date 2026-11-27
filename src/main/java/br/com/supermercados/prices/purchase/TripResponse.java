package br.com.supermercados.prices.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    public record TripItem(
            UUID itemId,
            UUID productId,
            String productName,
            BigDecimal quantity,
            UUID catalogItemId,
            String imageUrl,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            boolean promotion,
            Instant checkedAt,
            UUID checkedBy,
            String checkedByName) {}
}
