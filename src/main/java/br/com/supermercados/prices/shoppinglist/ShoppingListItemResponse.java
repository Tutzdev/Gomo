package br.com.supermercados.prices.shoppinglist;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShoppingListItemResponse(
        UUID id,
        UUID productId,
        String productName,
        BigDecimal quantity,
        UUID catalogItemId,
        String imageUrl,
        Instant checkedAt,
        UUID checkedBy,
        String checkedByName) {

    public ShoppingListItemResponse(
        UUID id,
        UUID productId,
        String productName,
        BigDecimal quantity) {

        this(
            id,
            productId,
            productName,
            quantity,
            null,
            null);
    }

    public ShoppingListItemResponse(
        UUID id,
        UUID productId,
        String productName,
        BigDecimal quantity,
        UUID catalogItemId,
        String imageUrl) {

        this(
            id,
            productId,
            productName,
            quantity,
            catalogItemId,
            imageUrl,
            null,
            null,
            null);
    }
}
