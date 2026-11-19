package br.com.supermercados.prices.basicitem;

import java.util.List;
import java.util.UUID;

public record BasicItemCoverageResponse(
        List<Store> stores,
        List<Item> items) {

    public record Store(
            UUID id,
            String name,
            int pricedItems) {}

    public record Item(
            String slug,
            String name,
            String aisle,
            List<UUID> pricedStoreIds) {}
}
