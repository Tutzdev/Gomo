package br.com.supermercados.prices.shoppinglist;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import br.com.supermercados.prices.basicitem.BasicItems;

public record ShoppingListItemResponse(
        UUID id,
        UUID productId,
        String productName,
        BigDecimal quantity,
        UUID catalogItemId,
        String imageUrl,
        Instant checkedAt,
        UUID checkedBy,
        String checkedByName,
        String basicItem,
        AnyBrandSuggestion anyBrandSuggestion) {

    private static final String ANY_BRAND = " (qualquer marca)";

    public ShoppingListItemResponse(
        UUID id,
        UUID productId,
        String productName,
        BigDecimal quantity,
        UUID catalogItemId,
        String imageUrl,
        Instant checkedAt,
        UUID checkedBy,
        String checkedByName,
        String basicItem) {

        this(
            id,
            productId,
            productName,
            quantity,
            catalogItemId,
            imageUrl,
            checkedAt,
            checkedBy,
            checkedByName,
            basicItem,
            null);
    }

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

    public ShoppingListItemResponse(
        UUID id,
        UUID productId,
        String productName,
        BigDecimal quantity,
        UUID catalogItemId,
        String imageUrl,
        Instant checkedAt,
        UUID checkedBy,
        String checkedByName) {

        this(
            id,
            productId,
            productName,
            quantity,
            catalogItemId,
            imageUrl,
            checkedAt,
            checkedBy,
            checkedByName,
            null);
    }

    public ShoppingListItemResponse {
        if (basicItem != null) {
            productName = BasicItems.find(basicItem).map(item -> item.name() + ANY_BRAND).orElse(productName);
        }
    }

    public boolean anyBrand() {
        return basicItem != null;
    }

    public ShoppingListItemResponse withAnyBrandSuggestion(AnyBrandSuggestion suggestion) {
        return new ShoppingListItemResponse(
            id,
            productId,
            productName,
            quantity,
            catalogItemId,
            imageUrl,
            checkedAt,
            checkedBy,
            checkedByName,
            basicItem,
            suggestion);
    }
}
