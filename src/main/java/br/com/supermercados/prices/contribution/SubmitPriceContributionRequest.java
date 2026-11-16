package br.com.supermercados.prices.contribution;

import br.com.supermercados.prices.price.StockAvailability;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SubmitPriceContributionRequest(
        @NotNull UUID productId,
        @NotNull UUID storeId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal regularPrice,
        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal promotionalPrice,
        @NotNull @Pattern(regexp = "BRL") String currency,
        @NotNull Instant observedAt,
        Instant validUntil,
        Instant promotionValidUntil,
        @NotNull StockAvailability availability) {}
