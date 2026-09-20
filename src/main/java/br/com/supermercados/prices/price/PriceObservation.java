package br.com.supermercados.prices.price;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A source reference identifies one immutable observation, not a mutable product page. */
public record PriceObservation(
        @NotNull UUID productId,
        @NotNull UUID storeId,
        @NotNull UUID sourceId,
        @NotBlank @Size(max = 500) String sourceReference,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal regularPrice,
        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal promotionalPrice,
        @NotNull @Pattern(regexp = "BRL") String currency,
        @NotNull Instant collectedAt,
        Instant validUntil,
        Instant promotionValidUntil,
        @Size(max = 500) String promotionCondition,
        @NotNull StockAvailability availability,
        @Size(max = 2048) @Pattern(regexp = "https://[^\\s]+") String originUrl,
        @Size(max = 2048) String sourceProductReference,
        @Size(max = 200) String sourceProductName) {

    public PriceObservation(UUID productId, UUID storeId, UUID sourceId, String sourceReference,
            BigDecimal regularPrice, BigDecimal promotionalPrice, String currency, Instant collectedAt,
            Instant validUntil, Instant promotionValidUntil, String promotionCondition, StockAvailability availability,
            String originUrl, String sourceProductReference) {
        this(productId, storeId, sourceId, sourceReference, regularPrice, promotionalPrice, currency,
                collectedAt, validUntil, promotionValidUntil, promotionCondition, availability, originUrl,
                sourceProductReference, null);
    }

    public PriceObservation(UUID productId, UUID storeId, UUID sourceId, String sourceReference,
            BigDecimal regularPrice, BigDecimal promotionalPrice, String currency, Instant collectedAt,
            Instant validUntil, Instant promotionValidUntil, String promotionCondition, StockAvailability availability,
            String originUrl) {
        this(productId, storeId, sourceId, sourceReference, regularPrice, promotionalPrice, currency,
                collectedAt, validUntil, promotionValidUntil, promotionCondition, availability, originUrl, null);
    }

    public PriceObservation(UUID productId, UUID storeId, UUID sourceId, String sourceReference,
            BigDecimal regularPrice, BigDecimal promotionalPrice, String currency, Instant collectedAt,
            Instant validUntil, Instant promotionValidUntil, String promotionCondition, StockAvailability availability) {
        this(productId, storeId, sourceId, sourceReference, regularPrice, promotionalPrice, currency,
                collectedAt, validUntil, promotionValidUntil, promotionCondition, availability, null);
    }

    public PriceObservation(
            UUID productId,
            UUID storeId,
            UUID sourceId,
            String sourceReference,
            BigDecimal regularPrice,
            BigDecimal promotionalPrice,
            String currency,
            Instant collectedAt,
            Instant validUntil,
            Instant promotionValidUntil,
            StockAvailability availability) {
        this(productId, storeId, sourceId, sourceReference, regularPrice, promotionalPrice,
                currency, collectedAt, validUntil, promotionValidUntil, null, availability);
    }
}
