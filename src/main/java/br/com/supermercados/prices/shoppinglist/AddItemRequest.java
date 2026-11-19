package br.com.supermercados.prices.shoppinglist;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record AddItemRequest(
        UUID productId,
        UUID catalogItemId,
        @Pattern(regexp = "[a-z0-9-]{1,64}") String basicItem,
        @NotNull @Positive @DecimalMax("999999") @Digits(integer = 6, fraction = 3) BigDecimal quantity) {

    public AddItemRequest(UUID productId, BigDecimal quantity) {
        this(productId, null, null, quantity);
    }

    public AddItemRequest(UUID productId, UUID catalogItemId, BigDecimal quantity) {
        this(productId, catalogItemId, null, quantity);
    }

    public static AddItemRequest anyBrand(String basicItem, BigDecimal quantity) {
        return new AddItemRequest(null, null, basicItem, quantity);
    }

    @AssertTrue(message = "Informe o produto.")
    boolean isProductInformed() {
        return productId != null || catalogItemId != null || basicItem != null;
    }
}
