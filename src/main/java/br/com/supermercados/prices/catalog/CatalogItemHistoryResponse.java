package br.com.supermercados.prices.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Lowest price of the item per store and day since {@code since}; days without a collection have no point. */
public record CatalogItemHistoryResponse(UUID itemId, Instant since, List<StoreSeries> stores) {

    public CatalogItemHistoryResponse {
        stores = List.copyOf(stores);
    }

    public record StoreSeries(UUID storeId, String storeName, List<PricePoint> points) {

        public StoreSeries {
            points = List.copyOf(points);
        }
    }

    public record PricePoint(LocalDate day, BigDecimal price) {
    }
}
