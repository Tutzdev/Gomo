package br.com.supermercados.prices.collection;

public interface SupermarketCollector {

    CollectorMetadata metadata();

    CollectedCatalog collect();

    default boolean hasCurrentSource() {
        return true;
    }
}
