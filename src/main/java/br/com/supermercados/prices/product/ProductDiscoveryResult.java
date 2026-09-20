package br.com.supermercados.prices.product;

import java.util.List;

import br.com.supermercados.prices.comparison.ProductStoreComparison;

public record ProductDiscoveryResult(ProductResponse product, List<ProductStoreComparison> offers,
        int catalogEntries, boolean identityConfirmed, String displayName) {
}
