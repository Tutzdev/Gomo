package br.com.supermercados.prices.catalog;

import java.util.List;
import java.util.UUID;

import br.com.supermercados.prices.price.PriceQuote;
import br.com.supermercados.prices.store.MissingStoreResponse;
import br.com.supermercados.prices.subscription.ComparisonAccess;

public record CatalogItemDetailResponse(
        CatalogItemResponse item,
        List<StoreOffer> offers,
        int storesWithoutPrice,
        List<MissingStoreResponse> missingStores,
        ComparisonAccess access) {

    public record StoreOffer(
            UUID storeId,
            String storeName,
            UUID productId,
            String storeProductName,
            String originUrl,
            PriceQuote price) {}
}
