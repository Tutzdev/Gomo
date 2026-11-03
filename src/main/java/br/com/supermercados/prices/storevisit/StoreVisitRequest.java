package br.com.supermercados.prices.storevisit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One visit to a physical market: the shelf prices written down at {@code observedAt}. */
public record StoreVisitRequest(
        @NotNull Instant observedAt,
        @NotEmpty @Size(max = 300) List<@Valid @NotNull Item> items) {

    /** {@code price} is the one on the shelf tag. */
    public record Item(
            @NotNull UUID productId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price) {
    }
}
