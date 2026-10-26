package br.com.supermercados.prices.comparison;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Cheaper equivalents of the items of a list: the same kind of product in the same aisle, from another brand or in
 * another size, that costs less per kilogram, litre or unit. On the free plan only the count comes, {@code locked}.
 */
public record ListSubstitutesResponse(UUID listId, UUID cityId, boolean locked, int substitutes,
        List<Substitution> items) {

    public ListSubstitutesResponse {
        items = List.copyOf(items);
    }

    /**
     * @param suggestedQuantity how many of the substitute buy about as much as the list asks for
     * @param savingsPercent how much cheaper per measure, rounded
     */
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
            int savingsPercent) {
    }
}
