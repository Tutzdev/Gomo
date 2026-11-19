package br.com.supermercados.prices.basicitem;

import java.math.BigDecimal;

public record BasicItemResponse(
        String slug,
        String name,
        String aisle,
        String measure,
        String typicalPackage,
        int pricedStores,
        int comparedStores,
        BigDecimal lowestPricePerMeasure,
        String lowestPriceStore) {

    static BasicItemResponse of(
        BasicItem item,
        int pricedStores,
        int comparedStores,
        BasicItemOffer cheapest,
        String cheapestStoreName) {

        return new BasicItemResponse(
            item.slug(),
            item.name(),
            item.aisle(),
            item.measure().label(),
            item.typicalLabel(),
            pricedStores,
            comparedStores,
            cheapest == null ? null : cheapest.pricePerMeasure(),
            cheapestStoreName);
    }
}
