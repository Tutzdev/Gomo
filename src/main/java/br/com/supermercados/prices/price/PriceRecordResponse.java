package br.com.supermercados.prices.price;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PriceRecordResponse(
        UUID id,
        UUID productId,
        UUID storeId,
        BigDecimal regularPrice,
        BigDecimal promotionalPrice,
        String currency,
        StockAvailability availability,
        Instant collectedAt,
        Instant recordedAt,
        Instant validUntil,
        Instant promotionValidUntil,
        String promotionCondition,
        UUID sourceId,
        String sourceReference,
        String originUrl,
        String sourceProductReference,
        String sourceProductName,
        PriceSalesChannel salesChannel) {

    public static PriceRecordResponse from(PriceRecord record) {
        return new PriceRecordResponse(
            record.getId(),
            record.getProductId(),
            record.getStoreId(),
            record.getRegularPrice(),
            record.getPromotionalPrice(),
            record.getCurrency(),
            record.getAvailability(),
            record.getCollectedAt(),
            record.getRecordedAt(),
            record.getValidUntil(),
            record.getPromotionValidUntil(),
            record.getPromotionCondition(),
            record.getSourceId(),
            record.getSourceReference(),
            record.getOriginUrl(),
            record.getSourceProductReference(),
            record.getSourceProductName(),
            PriceSalesChannel.of(record));
    }
}
