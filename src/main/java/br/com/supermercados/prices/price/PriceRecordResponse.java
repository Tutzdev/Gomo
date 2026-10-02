package br.com.supermercados.prices.price;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Historical values describe the observation time and are not a current-price assertion. */
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
        PriceOriginType originType,
        UUID contributionId,
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
                record.getOriginType(),
                record.getContributionId(),
                record.getOriginUrl(),
                record.getSourceProductReference(),
                record.getSourceProductName(),
                PriceSalesChannel.fromSourceReference(record.getSourceProductReference()));
    }
}
