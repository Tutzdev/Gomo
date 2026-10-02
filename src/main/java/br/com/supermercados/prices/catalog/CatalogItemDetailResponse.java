package br.com.supermercados.prices.catalog;

import java.util.List;
import java.util.UUID;

import br.com.supermercados.prices.price.PriceQuote;

/** Stores with a current price for the item, cheapest first; {@code storesWithoutPrice} had none to compare. */
public record CatalogItemDetailResponse(CatalogItemResponse item, List<StoreOffer> offers, int storesWithoutPrice) {

    /** What one store charges, under the name that store uses for the product. */
    public record StoreOffer(UUID storeId, String storeName, UUID productId, String storeProductName,
            String originUrl, PriceQuote price) {
    }
}
