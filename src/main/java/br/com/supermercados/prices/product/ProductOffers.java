package br.com.supermercados.prices.product;

import java.util.List;
import java.util.UUID;

import br.com.supermercados.prices.comparison.ProductStoreComparison;

public record ProductOffers(UUID productId, List<ProductStoreComparison> offers) {}
