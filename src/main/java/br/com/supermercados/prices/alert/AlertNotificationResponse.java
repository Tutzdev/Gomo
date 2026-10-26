package br.com.supermercados.prices.alert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A price that reached an alert, with what the screen needs to say it in one line: which product, in which
 * store, and the condition the person chose ({@code targetPrice} or {@code discountPercent}, by {@code alertKind}).
 */
public record AlertNotificationResponse(
        UUID id,
        UUID alertId,
        UUID priceRecordId,
        UUID storeId,
        BigDecimal unitPrice,
        Instant createdAt,
        Instant readAt,
        UUID productId,
        String productName,
        String storeName,
        PriceAlertKind alertKind,
        BigDecimal targetPrice,
        Integer discountPercent) {
}
