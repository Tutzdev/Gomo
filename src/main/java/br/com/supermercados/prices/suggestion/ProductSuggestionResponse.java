package br.com.supermercados.prices.suggestion;

import br.com.supermercados.prices.contribution.ContributionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductSuggestionResponse(
        UUID id,
        String name,
        String brand,
        String packageSize,
        String barcode,
        UUID storeId,
        BigDecimal price,
        String note,
        Instant submittedAt,
        ContributionStatus status,
        Instant decidedAt,
        String rejectionReason) {

    static ProductSuggestionResponse from(ProductSuggestion suggestion) {
        return new ProductSuggestionResponse(suggestion.getId(), suggestion.getName(), suggestion.getBrand(),
                suggestion.getPackageSize(), suggestion.getBarcode(), suggestion.getStoreId(), suggestion.getPrice(),
                suggestion.getNote(), suggestion.getSubmittedAt(), suggestion.getStatus(), suggestion.getDecidedAt(),
                suggestion.getRejectionReason());
    }
}
