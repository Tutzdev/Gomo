package br.com.supermercados.prices.collection;

public interface SupermarketCollector {

    CollectorMetadata metadata();

    CollectedCatalog collect();

    /**
     * Whether the scheduled collections should run this collector now. A source that is known to have nothing
     * current (e.g. reviewed flyers that expired) is skipped instead of failing four times a day; it can still
     * be run explicitly by its code.
     */
    default boolean hasCurrentSource() {
        return true;
    }
}
