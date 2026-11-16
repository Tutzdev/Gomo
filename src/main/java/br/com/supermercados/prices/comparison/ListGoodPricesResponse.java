package br.com.supermercados.prices.comparison;

import br.com.supermercados.prices.catalog.PriceVerdict;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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
            boolean stockUp) {}
}
