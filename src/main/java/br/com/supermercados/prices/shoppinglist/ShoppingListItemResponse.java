package br.com.supermercados.prices.shoppinglist;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** {@code checkedAt} and {@code checkedBy}: the item went into the cart, in the "No mercado" mode. */
public record ShoppingListItemResponse(UUID id, UUID productId, String productName, BigDecimal quantity,
        UUID catalogItemId, String imageUrl, Instant checkedAt, UUID checkedBy) {

    public ShoppingListItemResponse(UUID id, UUID productId, String productName, BigDecimal quantity) {
        this(id, productId, productName, quantity, null, null);
    }

    public ShoppingListItemResponse(UUID id, UUID productId, String productName, BigDecimal quantity,
            UUID catalogItemId, String imageUrl) {
        this(id, productId, productName, quantity, catalogItemId, imageUrl, null, null);
    }
}
