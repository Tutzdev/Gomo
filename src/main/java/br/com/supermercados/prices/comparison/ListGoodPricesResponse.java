package br.com.supermercados.prices.comparison;

import br.com.supermercados.prices.catalog.PriceVerdict;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The items of a list that are a good buy today: under the usual price, or the lowest in the history.
 * {@code stockUp} marks the pantry items at the lowest price of the period, the ones worth buying for the next
 * weeks too ("Hora de estocar"). On the free plan only the counts come, {@code locked}.
 */
public record ListGoodPricesResponse(
        UUID listId,
        UUID cityId,
        boolean locked,
        int stockUpItems,
        int belowAverageItems,
        List<GoodPrice> items) {

    public ListGoodPricesResponse {
        items = List.copyOf(items);
    }

    /**
     * @param differencePercent against the usual price; negative is cheaper
     * @param periodDays calendar days of history behind "the lowest of the period"
     */
    public record GoodPrice(
            UUID itemId,
            UUID productId,
            String productName,
            UUID catalogItemId,
            BigDecimal quantity,
            PriceVerdict.Kind kind,
            BigDecimal currentPrice,
            UUID storeId,
            String storeName,
            BigDecimal usualPrice,
            int differencePercent,
            BigDecimal lowestPrice,
            int periodDays,
            boolean stockUp) {
    }
}
