package br.com.supermercados.prices.shoppinglist;

import java.time.Instant;
import java.util.UUID;

/** Someone the list is shared with: the name is what the others see next to what that person checked off. */
public record ShoppingListMemberResponse(UUID userId, String name, Instant joinedAt) {
}
