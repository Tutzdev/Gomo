package br.com.supermercados.prices.store;

import br.com.supermercados.prices.price.PriceQuote;
import br.com.supermercados.prices.product.ProductResponse;

public record StoreProductResponse(ProductResponse product, PriceQuote price) {}
