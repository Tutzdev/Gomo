package br.com.supermercados.prices.shoppinglist;

import java.time.Instant;
import java.util.UUID;

public record ShoppingListSummary(
        UUID id,
        String name,
        ShoppingType shoppingType,
        Instant createdAt,
        Instant updatedAt,
        long version,
        boolean owned,
        String ownerName,
        int memberCount) {

    static ShoppingListSummary from(ShoppingList list) {
        return from(list, true, null, 0);
    }

    static ShoppingListSummary from(
        ShoppingList list,
        boolean owned,
        String ownerName,
        int memberCount) {

        return new ShoppingListSummary(
            list.getId(),
            list.getName(),
            list.getShoppingType(),
            list.getCreatedAt(),
            list.getUpdatedAt(),
            list.getVersion(),
            owned,
            ownerName,
            memberCount);
    }
}
