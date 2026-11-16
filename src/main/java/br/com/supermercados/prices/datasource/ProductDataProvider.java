package br.com.supermercados.prices.datasource;

import java.util.UUID;

import br.com.supermercados.prices.product.ProductObservation;

public interface ProductDataProvider {

    UUID sourceId();

    ProviderResult<ProductObservation> fetchProducts(ProviderRequest request);
}
