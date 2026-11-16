package br.com.supermercados.prices.comparison;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ListSubstitutesResponse(
        UUID listId,
        UUID cityId,
        boolean locked,
        int substitutes,
        List<Substitution> items) {

    public ListSubstitutesResponse {
        items = List.copyOf(items);
    }

    public record Substitution(
            UUID itemId,
            String productName,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal pricePerMeasure,
            String measureUnit,
            UUID substituteCatalogItemId,
            String substituteName,
            String substituteBrand,
            String substituteSize,
            String substituteImageUrl,
            BigDecimal substitutePrice,
            BigDecimal substitutePricePerMeasure,
            UUID substituteStoreId,
            String substituteStoreName,
            BigDecimal suggestedQuantity,
            int savingsPercent) {}
}
