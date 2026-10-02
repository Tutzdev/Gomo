package br.com.supermercados.prices.shoppinglist;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/** Adds either a generic catalog item ("Coca-Cola 2 L") or a specific retailer product. */
public record AddItemRequest(
        UUID productId,
        UUID catalogItemId,
        @NotNull @Positive @DecimalMax("999999") @Digits(integer = 6, fraction = 3) BigDecimal quantity) {

    public AddItemRequest(UUID productId, BigDecimal quantity) {
        this(productId, null, quantity);
    }

    @AssertTrue(message = "Informe o produto.")
    boolean isProductInformed() {
        return productId != null || catalogItemId != null;
    }
}
