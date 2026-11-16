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

public record StoreVisitRequest(
        @NotNull Instant observedAt,
        @NotEmpty @Size(max = 300) List<@Valid @NotNull Item> items) {

    public record Item(
            @NotNull UUID productId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price) {}
}
