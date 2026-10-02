package br.com.supermercados.prices.shoppinglist;

import java.math.BigDecimal;
import java.util.UUID;

public record ShoppingListItemResponse(UUID id, UUID productId, String productName, BigDecimal quantity,
        UUID catalogItemId, String imageUrl) {

    public ShoppingListItemResponse(UUID id, UUID productId, String productName, BigDecimal quantity) {
        this(id, productId, productName, quantity, null, null);
    }
}
