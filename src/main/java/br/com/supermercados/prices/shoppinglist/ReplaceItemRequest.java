package br.com.supermercados.prices.shoppinglist;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/** Swaps a list item for another generic item, such as a cheaper equivalent, keeping its place in the list. */
public record ReplaceItemRequest(
        @NotNull UUID catalogItemId,
        @NotNull @Positive @DecimalMax("999999") @Digits(integer = 6, fraction = 3) BigDecimal quantity) {
}
