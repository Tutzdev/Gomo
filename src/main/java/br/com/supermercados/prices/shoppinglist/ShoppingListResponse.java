package br.com.supermercados.prices.shoppinglist;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ShoppingListResponse(
        UUID id,
        String name,
        ShoppingType shoppingType,
        List<ShoppingListItemResponse> items,
        Instant createdAt,
        Instant updatedAt,
        long version,
        boolean owned,
        String ownerName,
        List<ShoppingListMemberResponse> members) {

    public ShoppingListResponse {
        items = List.copyOf(items);
        members = List.copyOf(members);
    }

    public ShoppingListResponse(
        UUID id,
        String name,
        ShoppingType shoppingType,
        List<ShoppingListItemResponse> items,
        Instant createdAt,
        Instant updatedAt,
        long version) {

        this(
            id,
            name,
            shoppingType,
            items,
            createdAt,
            updatedAt,
            version,
            true,
            null,
            List.of());
    }
}
