package br.com.supermercados.prices.contribution;

import br.com.supermercados.prices.price.StockAvailability;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PriceContributionResponse(
        UUID id,
        UUID contributorId,
        UUID productId,
        UUID storeId,
        BigDecimal regularPrice,
        BigDecimal promotionalPrice,
        String currency,
        Instant observedAt,
        Instant submittedAt,
        Instant validUntil,
        Instant promotionValidUntil,
        StockAvailability availability,
        ContributionStatus status,
        UUID moderatorId,
        Instant decidedAt,
        String rejectionReason,
        UUID priceRecordId) {

    static PriceContributionResponse from(PriceContribution contribution) {
        return new PriceContributionResponse(
            contribution.getId(),
            contribution.getContributorId(),
            contribution.getProductId(),
            contribution.getStoreId(),
            contribution.getRegularPrice(),
            contribution.getPromotionalPrice(),
            contribution.getCurrency(),
            contribution.getObservedAt(),
            contribution.getSubmittedAt(),
            contribution.getValidUntil(),
            contribution.getPromotionValidUntil(),
            contribution.getAvailability(),
            contribution.getStatus(),
            contribution.getModeratorId(),
            contribution.getDecidedAt(),
            contribution.getRejectionReason(),
            contribution.getPriceRecordId());
    }
}
