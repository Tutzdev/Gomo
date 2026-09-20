package br.com.supermercados.prices.comparison;

import java.util.UUID;

import br.com.supermercados.prices.price.PriceQuote;
import br.com.supermercados.prices.price.MeasurementPrice;
import br.com.supermercados.prices.product.ProductResponse;

public record ProductStoreComparison(UUID storeId, String storeName, PriceQuote price,
        MeasurementPrice measurementPrice, ProductResponse matchedProduct, String priceSourceNote) {

    public ProductStoreComparison(UUID storeId, String storeName, PriceQuote price, MeasurementPrice measurementPrice) {
        this(storeId, storeName, price, measurementPrice, null, null);
    }
}
