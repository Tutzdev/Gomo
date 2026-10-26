package br.com.supermercados.prices.alert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** {@code targetPrice} is set for a TARGET_PRICE alert and {@code discountPercent} for a BELOW_AVERAGE one. */
public record PriceAlertResponse(
        UUID id,
        UUID productId,
        UUID cityId,
        PriceAlertKind kind,
        BigDecimal targetPrice,
        Integer discountPercent,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        Instant lastNotifiedAt) {

    static PriceAlertResponse from(PriceAlert alert) {
        return new PriceAlertResponse(alert.getId(), alert.getProductId(), alert.getCityId(), alert.getKind(),
                alert.getTargetPrice(), alert.getDiscountPercent(), alert.isActive(), alert.getCreatedAt(),
                alert.getUpdatedAt(), alert.getLastNotifiedAt());
    }
}
