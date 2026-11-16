package br.com.supermercados.prices.alert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
        Integer discountPercent) {}
