package br.com.supermercados.prices.collection;

import java.time.Instant;
import java.util.List;

/**
 * {@code warnings} mixes two things collectors report as text: a product the store described in a way the rules
 * do not accept (always written "Produto …": no average weight, unconfirmed price per kg, variations without
 * their own id) and a real problem with the collection itself (a category cut short, paging that changed).
 */
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

    /** A single product left out by the rules, as opposed to a collection that did not finish. */
    public static boolean isSkippedProduct(String warning) {
        return warning.startsWith("Produto ");
    }
}
