package br.com.supermercados.prices.shoppinglist;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param owned the person asking is the owner; a member edits and checks items but does not rename, delete or
 *     share the list
 * @param members the people the owner shared the list with
 */
public record ShoppingListResponse(UUID id, String name, ShoppingType shoppingType,
        List<ShoppingListItemResponse> items, Instant createdAt, Instant updatedAt, long version, boolean owned,
        String ownerName, List<ShoppingListMemberResponse> members) {

    public ShoppingListResponse {
        items = List.copyOf(items);
        members = List.copyOf(members);
    }

    public ShoppingListResponse(UUID id, String name, ShoppingType shoppingType,
            List<ShoppingListItemResponse> items, Instant createdAt, Instant updatedAt, long version) {
        this(id, name, shoppingType, items, createdAt, updatedAt, version, true, null, List.of());
    }
}
