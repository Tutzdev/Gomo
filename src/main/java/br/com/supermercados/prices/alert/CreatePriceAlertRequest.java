package br.com.supermercados.prices.alert;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code kind} defaults to {@link PriceAlertKind#TARGET_PRICE}, so clients that only send {@code targetPrice}
 * keep working. Each kind takes exactly its own field: a price, or a percentage below the average.
 */
public record CreatePriceAlertRequest(
        @NotNull UUID productId,
        @NotNull UUID cityId,
        PriceAlertKind kind,
        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal targetPrice,
        @Min(5) @Max(50) Integer discountPercent) {

    public CreatePriceAlertRequest {
        kind = kind == null ? PriceAlertKind.TARGET_PRICE : kind;
    }

    public CreatePriceAlertRequest(UUID productId, UUID cityId, BigDecimal targetPrice) {
        this(productId, cityId, PriceAlertKind.TARGET_PRICE, targetPrice, null);
    }

    public static CreatePriceAlertRequest belowAverage(UUID productId, UUID cityId, int discountPercent) {
        return new CreatePriceAlertRequest(productId, cityId, PriceAlertKind.BELOW_AVERAGE, null, discountPercent);
    }

    boolean hasOnlyItsOwnField() {
        return kind == PriceAlertKind.TARGET_PRICE
                ? targetPrice != null && discountPercent == null
                : discountPercent != null && targetPrice == null;
    }
}
