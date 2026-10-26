package br.com.supermercados.prices.deal;

import br.com.supermercados.prices.common.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The deals of a city. Without a store, each product appears once, at its cheapest store; with one, every deal of
 * that store. {@code stores} and {@code categories} count the deals behind each filter.
 *
 * @param onlyMyLists the page was narrowed to the products of the person's lists (Premium)
 */
public record DealsResponse(
        UUID cityId,
        Instant computedAt,
        int minimumDiscountPercent,
        PageResponse<DealResponse> deals,
        List<StoreDeals> stores,
        List<CategoryDeals> categories,
        boolean onlyMyLists) {

    public DealsResponse {
        stores = List.copyOf(stores);
        categories = List.copyOf(categories);
    }

    public record StoreDeals(UUID storeId, String storeName, int deals) {
    }

    public record CategoryDeals(String category, int deals) {
    }
}
