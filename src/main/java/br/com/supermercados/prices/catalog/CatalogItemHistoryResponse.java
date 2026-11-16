package br.com.supermercados.prices.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CatalogItemHistoryResponse(
        UUID itemId,
        Instant since,
        List<StoreSeries> stores,
        PriceVerdict verdict) {

    public CatalogItemHistoryResponse {
        stores = List.copyOf(stores);
    }

    public record StoreSeries(UUID storeId, String storeName, List<PricePoint> points) {

        public StoreSeries {
            points = List.copyOf(points);
        }
    }

    public record PricePoint(LocalDate day, BigDecimal price) {}
}
