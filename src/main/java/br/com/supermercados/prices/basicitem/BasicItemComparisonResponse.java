package br.com.supermercados.prices.basicitem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.supermercados.prices.price.PriceQuote;
import br.com.supermercados.prices.store.MissingStoreResponse;
import br.com.supermercados.prices.subscription.ComparisonAccess;

public record BasicItemComparisonResponse(
        BasicItemResponse item,
        Instant comparedAt,
        List<StoreOffer> offers,
        List<MissingStoreResponse> missingStores,
        ComparisonAccess access) {

    public record StoreOffer(
            UUID storeId,
            String storeName,
            UUID productId,
            UUID catalogItemId,
            String productName,
            String packageLabel,
            BigDecimal pricePerMeasure,
            boolean typicalPackage,
            String originUrl,
            PriceQuote price) {}
}
