package br.com.supermercados.prices.collection;

import java.time.Instant;
import java.util.List;

public record CollectedCatalog(
        CollectedStore store,
        List<CollectedProduct> products,
        int foundCount,
        Instant collectedAt,
        List<String> warnings) {

    public CollectedCatalog {
        products = List.copyOf(products);
        warnings = List.copyOf(warnings);
    }

    public static boolean isSkippedProduct(String warning) {
        return warning.startsWith("Produto ");
    }
}
